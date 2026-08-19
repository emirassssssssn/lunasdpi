#include "core/engine.h"

#include "core/log.h"
#include "dns/dns_engine.h"
#include "dns/dns_parse.h"
#include "dns/hosts_table.h"
#include "dpi/http_inspect.h"
#include "dpi/tls_sni.h"
#include "packet/checksum.h"
#include "packet/tcp.h"
#include "tcp/tcp_relay.h"
#include "tun/tun_io.h"
#include "udp/udp_nat.h"

#include <arpa/inet.h>
#include <cerrno>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <poll.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <unistd.h>

namespace luna {
namespace {

constexpr uint64_t kTcpIdleMs = 300000;
constexpr uint64_t kUdpIdleMs = 60000;
constexpr size_t kMaxPending = 256 * 1024;

StrategyFlags resolve_flags(const EngineConfig& config, const RuleConfig* rule) {
    if (rule == nullptr) {
        return passthrough_flags();
    }
    if (rule->strategy == DpiMode::Automatic) {
        return flags_for_mode(config.mode, config.custom);
    }
    if (rule->strategy == DpiMode::Custom) {
        return flags_for_mode(DpiMode::Custom, rule->custom);
    }
    return flags_for_mode(rule->strategy, rule->custom);
}

const char* mode_name(DpiMode mode) {
    switch (mode) {
    case DpiMode::Automatic:
        return "automatic";
    case DpiMode::Basic:
        return "basic";
    case DpiMode::Balanced:
        return "balanced";
    case DpiMode::Aggressive:
        return "aggressive";
    case DpiMode::Custom:
        return "custom";
    }
    return "automatic";
}

} // namespace

uint32_t random_u32() {
    uint32_t value = 0;
    const int fd = ::open("/dev/urandom", O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        const ssize_t n = ::read(fd, &value, sizeof(value));
        ::close(fd);
        if (n == static_cast<ssize_t>(sizeof(value))) {
            return value;
        }
    }
    const auto ticks = std::chrono::steady_clock::now().time_since_epoch().count();
    return static_cast<uint32_t>(ticks);
}

Engine::Engine() = default;

Engine::~Engine() {
    stop();
}

bool Engine::start(int tun_fd, EngineConfig config, ProtectFn protect, void* user, DnsResolveFn dns_resolve,
                   void* dns_user) {
    stop();
    if (tun_fd < 0 || protect == nullptr) {
        stats_.set_error("Invalid TUN descriptor or socket protector");
        return false;
    }
    config.mtu = clamp_mtu(config.mtu);
    if (config.custom.fragment_size < 1 || config.custom.fragment_size > 256) {
        config.custom.fragment_size = 2;
    }
    g_log_level = config.log_level;
    config_ = std::move(config);
    protect_ = protect;
    protect_user_ = user;
    dns_resolve_ = dns_resolve;
    dns_user_ = dns_user;
    tun_fd_ = tun_fd;

    rebuild_matcher();
    {
        std::lock_guard<std::mutex> lock(hosts_mu_);
        hosts_.replace(config_.hosts);
    }

    dns_servers_.clear();
    isp_dns_servers_.clear();
    for (const auto& item : config_.system_dns) {
        uint32_t ip = 0;
        if (parse_ipv4_string(item, &ip) && ip != 0) {
            isp_dns_servers_.push_back(ip);
        }
    }
    const std::vector<std::string>* dns_src = nullptr;
    if (config_.dns_mode == DnsMode::System) {
        dns_src = !config_.system_dns.empty() ? &config_.system_dns : &config_.custom_dns;
    } else if (!config_.custom_dns.empty()) {
        dns_src = &config_.custom_dns;
    } else if (!config_.system_dns.empty()) {
        dns_src = &config_.system_dns;
    }
    if (dns_src != nullptr) {
        for (const auto& item : *dns_src) {
            uint32_t ip = 0;
            if (parse_ipv4_string(item, &ip) && ip != 0) {
                dns_servers_.push_back(ip);
            }
        }
    }

    const int flags = fcntl(tun_fd_, F_GETFL, 0);
    fcntl(tun_fd_, F_SETFL, flags | O_NONBLOCK);

    epoll_fd_ = epoll_create1(EPOLL_CLOEXEC);
    if (epoll_fd_ < 0) {
        stats_.set_error("Could not create epoll instance");
        return false;
    }
    if (!add_epoll(tun_fd_, EPOLLIN)) {
        stats_.set_error("Could not watch TUN descriptor");
        ::close(epoll_fd_);
        epoll_fd_ = -1;
        return false;
    }

    stats_.engine_alive.store(1);
    stats_.current_strategy = mode_name(config_.mode);
    running_.store(true);
    dns_stop_.store(false);
    dns_thread_ = std::thread([this]() { dns_worker(); });
    thread_ = std::thread([this]() { thread_main(); });
    LOGI("native engine started mtu=%d rules=%zu hosts=%zu bypass_dns=%zu isp_dns=%zu doh=%d", config_.mtu,
         config_.rules.size(), config_.hosts.size(), dns_servers_.size(), isp_dns_servers_.size(),
         dns_resolve_ != nullptr && config_.dns_mode != DnsMode::System ? 1 : 0);
    return true;
}

void Engine::stop() {
    running_.store(false);
    if (epoll_fd_ >= 0) {
        epoll_event ev{};
        ev.events = EPOLLIN;
        ev.data.fd = tun_fd_;
        epoll_ctl(epoll_fd_, EPOLL_CTL_MOD, tun_fd_, &ev);
    }
    if (thread_.joinable()) {
        thread_.join();
    }
    dns_stop_.store(true);
    dns_cv_.notify_all();
    if (dns_thread_.joinable()) {
        dns_thread_.join();
    }
    {
        std::lock_guard<std::mutex> lock(dns_mu_);
        while (!dns_jobs_.empty()) {
            dns_jobs_.pop();
        }
        while (!dns_results_.empty()) {
            dns_results_.pop();
        }
    }
    drop_all_sessions();
    if (epoll_fd_ >= 0) {
        ::close(epoll_fd_);
        epoll_fd_ = -1;
    }
    if (tun_fd_ >= 0) {
        ::close(tun_fd_);
        tun_fd_ = -1;
    }
    stats_.engine_alive.store(0);
}

void Engine::on_network_changed() {
    network_changed_.store(true);
}

void Engine::update_rules(std::vector<RuleConfig> rules) {
    if (!running_.load()) {
        return;
    }
    std::lock_guard<std::mutex> lock(rules_mu_);
    pending_rules_ = std::move(rules);
    rules_dirty_.store(true);
}

void Engine::update_hosts(std::vector<HostMapping> mappings) {
    std::lock_guard<std::mutex> lock(hosts_mu_);
    hosts_.replace(std::move(mappings));
    LOGI("hosts reloaded count=%zu", hosts_.size());
}

void Engine::rebuild_matcher() {
    matcher_.clear();
    int rule_i = 0;
    for (const auto& rule : config_.rules) {
        if (rule.enabled) {
            matcher_.add_rule(rule_i, rule.name, rule.domains);
        }
        rule_i++;
    }
}

void Engine::apply_pending_rules() {
    std::vector<RuleConfig> next;
    {
        std::lock_guard<std::mutex> lock(rules_mu_);
        next = std::move(pending_rules_);
        pending_rules_.clear();
    }
    config_.rules = std::move(next);
    rebuild_matcher();
    drop_all_sessions();
    LOGI("rules reloaded count=%zu", config_.rules.size());
}

void Engine::snapshot_stats(uint64_t* packets_processed, uint64_t* packets_modified, uint64_t* packets_dropped,
                            uint64_t* bytes_in, uint64_t* bytes_out, uint64_t* dns_queries, int* active_tcp,
                            int* active_udp, int* native_errors, int* alive, char* last_error, int last_error_cap,
                            char* strategy, int strategy_cap) const {
    if (packets_processed) *packets_processed = stats_.packets_processed.load();
    if (packets_modified) *packets_modified = stats_.packets_modified.load();
    if (packets_dropped) *packets_dropped = stats_.packets_dropped.load();
    if (bytes_in) *bytes_in = stats_.bytes_in.load();
    if (bytes_out) *bytes_out = stats_.bytes_out.load();
    if (dns_queries) *dns_queries = stats_.dns_queries.load();
    if (active_tcp) *active_tcp = sessions_.tcp_count();
    if (active_udp) *active_udp = sessions_.udp_count();
    if (native_errors) *native_errors = stats_.native_errors.load();
    if (alive) *alive = stats_.engine_alive.load();
    if (last_error && last_error_cap > 0) {
        const std::string err = stats_.copy_error();
        std::snprintf(last_error, static_cast<size_t>(last_error_cap), "%s", err.c_str());
    }
    if (strategy && strategy_cap > 0) {
        std::snprintf(strategy, static_cast<size_t>(strategy_cap), "%s", stats_.current_strategy.c_str());
    }
}

void Engine::thread_main() {
    epoll_event events[64];
    uint64_t last_expire = now_ms();
    while (running_.load()) {
        const int n = epoll_wait(epoll_fd_, events, 64, 500);
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            stats_.set_error("epoll_wait failed");
            break;
        }
        for (int i = 0; i < n; ++i) {
            const int fd = events[i].data.fd;
            if (fd == tun_fd_) {
                handle_tun();
            } else {
                handle_socket(fd, events[i].events);
            }
        }
        drain_dns_results();
        if (rules_dirty_.exchange(false)) {
            apply_pending_rules();
        }
        const uint64_t now = now_ms();
        if (network_changed_.exchange(false)) {
            drop_all_sessions();
        }
        if (now - last_expire > 2000) {
            expire_sessions();
            reassembler_.expire(now);
            last_expire = now;
        }
    }
}

void Engine::handle_tun() {
    while (running_.load()) {
        const ssize_t n = tun_read(tun_fd_, tun_buf_, sizeof(tun_buf_));
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                return;
            }
            stats_.set_error("TUN read failed");
            return;
        }
        if (n == 0) {
            return;
        }
        stats_.packets_processed.fetch_add(1);
        stats_.bytes_in.fetch_add(static_cast<uint64_t>(n));
        const uint8_t version = tun_buf_[0] >> 4;
        if (version == 6) {
            if (config_.ipv6_mode == Ipv6Mode::Block) {
                stats_.packets_dropped.fetch_add(1);
            } else {
                stats_.packets_dropped.fetch_add(1);
            }
            continue;
        }
        if (version != 4) {
            stats_.packets_dropped.fetch_add(1);
            continue;
        }
        std::vector<uint8_t> assembled;
        if (!reassembler_.offer(tun_buf_, static_cast<size_t>(n), now_ms(), &assembled)) {
            Ipv4Packet probe{};
            if (!parse_ipv4(tun_buf_, static_cast<size_t>(n), &probe)) {
                stats_.packets_dropped.fetch_add(1);
            }
            continue;
        }
        handle_ipv4(assembled.data(), assembled.size());
    }
}

void Engine::handle_ipv4(const uint8_t* data, size_t len) {
    Ipv4Packet ip{};
    if (!parse_ipv4(data, len, &ip)) {
        stats_.packets_dropped.fetch_add(1);
        return;
    }
    if (ip.protocol == kProtoTcp) {
        handle_tcp(ip, ip.payload, ip.payload_len);
    } else if (ip.protocol == kProtoUdp) {
        handle_udp(ip, ip.payload, ip.payload_len);
    } else {
        stats_.packets_dropped.fetch_add(1);
    }
}

void Engine::handle_tcp(const Ipv4Packet& ip, const uint8_t* transport, size_t transport_len) {
    TcpSegment tcp{};
    if (!parse_tcp(transport, transport_len, &tcp)) {
        stats_.packets_dropped.fetch_add(1);
        return;
    }
    FiveTuple key{ip.src, ip.dst, tcp.src_port, tcp.dst_port, kProtoTcp};
    TcpSession* session = sessions_.find_tcp(key);

    if ((tcp.flags & kTcpRst) != 0) {
        if (session != nullptr) {
            close_tcp_session(key, false);
        }
        return;
    }

    if ((tcp.flags & kTcpSyn) != 0 && (tcp.flags & kTcpAck) == 0) {
        if (session != nullptr) {
            return;
        }
        TcpSession created;
        created.tuple = key;
        created.client_next_seq = tcp.seq + 1;
        created.server_isn = random_u32();
        created.server_next_seq = created.server_isn;
        created.client_window = tcp.window == 0 ? 65535 : tcp.window;
        created.last_activity_ms = now_ms();
        created.sock_fd = open_tcp_socket(ip.dst, tcp.dst_port);
        if (created.sock_fd < 0) {
            inject_tcp(ip.dst, ip.src, tcp.dst_port, tcp.src_port, 0, tcp.seq + 1, kTcpRst | kTcpAck, nullptr, 0,
                       0, false);
            stats_.set_error("TCP connect socket failed");
            return;
        }
        apply_strategy(&created.dpi, ip.dst);
        session = sessions_.insert_tcp(key, std::move(created));
        sessions_.bind_tcp_fd(session->sock_fd, key);
        add_epoll(session->sock_fd, EPOLLIN | EPOLLOUT | EPOLLERR | EPOLLHUP);
        return;
    }

    if (session == nullptr) {
        if ((tcp.flags & kTcpAck) != 0) {
            inject_tcp(ip.dst, ip.src, tcp.dst_port, tcp.src_port, tcp.ack, tcp.seq, kTcpRst | kTcpAck, nullptr, 0,
                       0, false);
        }
        return;
    }

    session->last_activity_ms = now_ms();
    session->client_window = tcp.window == 0 ? session->client_window : tcp.window;

    if ((tcp.flags & kTcpAck) != 0 && tcp.payload_len == 0 && (tcp.flags & kTcpFin) == 0) {
        return;
    }

    if (tcp.payload_len > 0) {
        if (tcp.seq != session->client_next_seq) {
            if (seq_before(tcp.seq, session->client_next_seq)) {
                inject_tcp(ip.dst, ip.src, tcp.dst_port, tcp.src_port, session->server_next_seq,
                           session->client_next_seq, kTcpAck, nullptr, 0, session->advertised_window, false);
            }
            return;
        }
        session->client_next_seq += static_cast<uint32_t>(tcp.payload_len);
        inject_tcp(ip.dst, ip.src, tcp.dst_port, tcp.src_port, session->server_next_seq, session->client_next_seq,
                   kTcpAck, nullptr, 0, 65535, false);

        if (!session->dpi.identified && tcp.payload_len > 0) {
            if (payload_looks_like_tls_client_hello(tcp.payload, tcp.payload_len)) {
                std::string sni;
                size_t off = 0;
                if (extract_sni(tcp.payload, tcp.payload_len, &sni, &off)) {
                    session->dpi.hostname = sni;
                }
            } else if (payload_looks_like_http(tcp.payload, tcp.payload_len)) {
                std::string host;
                if (extract_http_host(tcp.payload, tcp.payload_len, &host)) {
                    session->dpi.hostname = host;
                }
            }
        }
        apply_strategy(&session->dpi, ip.dst);

        bool modified = false;
        const auto chunks = dpi_.process(&session->dpi, tcp.payload, tcp.payload_len, &modified);
        if (modified) {
            stats_.packets_modified.fetch_add(1);
        }
        if (!send_dpi_chunks(session, chunks)) {
            close_tcp_session(key, true);
            return;
        }
    }

    if ((tcp.flags & kTcpFin) != 0) {
        if (tcp.seq == session->client_next_seq) {
            session->client_next_seq += 1;
        }
        session->fin_from_client = true;
        inject_tcp(ip.dst, ip.src, tcp.dst_port, tcp.src_port, session->server_next_seq, session->client_next_seq,
                   kTcpAck, nullptr, 0, 65535, false);
        if (session->sock_fd >= 0) {
            ::shutdown(session->sock_fd, SHUT_WR);
        }
        if (session->fin_from_socket) {
            close_tcp_session(key, false);
        }
    }
}

void Engine::handle_udp(const Ipv4Packet& ip, const uint8_t* transport, size_t transport_len) {
    UdpDatagram udp{};
    if (!parse_udp(transport, transport_len, &udp)) {
        stats_.packets_dropped.fetch_add(1);
        return;
    }
    if (udp.dst_port == 53 || ip.dst == config_.tun_dns_ipv4) {
        handle_dns(ip, udp);
        return;
    }
    if (config_.block_quic && udp.dst_port == 443) {
        inject_icmp_port_unreach(ip);
        stats_.packets_dropped.fetch_add(1);
        return;
    }
    FiveTuple key{ip.src, ip.dst, udp.src_port, udp.dst_port, kProtoUdp};
    UdpSession* session = sessions_.find_udp(key);
    if (session == nullptr) {
        UdpSession created;
        created.tuple = key;
        created.sock_fd = open_udp_socket();
        created.last_activity_ms = now_ms();
        if (created.sock_fd < 0) {
            stats_.set_error("UDP socket failed");
            return;
        }
        session = sessions_.insert_udp(key, std::move(created));
        sessions_.bind_udp_fd(session->sock_fd, key);
        add_epoll(session->sock_fd, EPOLLIN | EPOLLERR | EPOLLHUP);
    }
    session->last_activity_ms = now_ms();
    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(udp.dst_port);
    addr.sin_addr.s_addr = htonl(ip.dst);
    const ssize_t n = ::sendto(session->sock_fd, udp.payload, udp.payload_len, 0,
                               reinterpret_cast<sockaddr*>(&addr), sizeof(addr));
    if (n > 0) {
        stats_.bytes_out.fetch_add(static_cast<uint64_t>(n));
    }
}

void Engine::handle_dns(const Ipv4Packet& ip, const UdpDatagram& udp) {
    stats_.dns_queries.fetch_add(1);
    DnsMessage msg{};
    const bool parsed = parse_dns(udp.payload, udp.payload_len, &msg) && !msg.questions.empty();
    std::string qname;
    uint16_t qtype = 0;
    if (parsed) {
        qname = msg.questions[0].qname;
        qtype = msg.questions[0].qtype;
        LOGD("dns query %s type=%u", qname.c_str(), qtype);
    }
    uint32_t mapped = 0;
    if (parsed && lookup_host(qname, &mapped)) {
        LOGD("hosts hit %s type=%u", qname.c_str(), qtype);
        std::vector<uint8_t> resp;
        const bool built = (qtype == kDnsTypeA) ? dns_make_a_reply(udp.payload, udp.payload_len, mapped, &resp)
                                                : dns_make_noerror(udp.payload, udp.payload_len, &resp);
        if (built) {
            inject_udp(ip.dst, ip.src, udp.dst_port, udp.src_port, resp.data(), resp.size());
        } else {
            stats_.packets_dropped.fetch_add(1);
        }
        return;
    }
    const bool matched = parsed && has_active_rule_for_domain(qname);
    if (matched) {
        const bool sinkhole_aaaa = config_.ipv6_mode == Ipv6Mode::Block && qtype == kDnsTypeAaaa;
        const bool sinkhole_http3 =
            config_.block_quic && (qtype == kDnsTypeHttps || qtype == kDnsTypeSvcb);
        if (sinkhole_aaaa || sinkhole_http3) {
            std::vector<uint8_t> resp;
            if (dns_make_noerror(udp.payload, udp.payload_len, &resp)) {
                inject_udp(ip.dst, ip.src, udp.dst_port, udp.src_port, resp.data(), resp.size());
            }
            return;
        }
        if (config_.dns_mode != DnsMode::System && dns_resolve_ != nullptr) {
            enqueue_dns(ip, udp);
            return;
        }
        const uint32_t resolver = pick_dns_server();
        if (resolver == 0) {
            stats_.packets_dropped.fetch_add(1);
            stats_.set_error("No DNS resolver configured");
            return;
        }
        forward_dns_query(ip, udp, resolver);
        return;
    }

    const uint32_t isp = pick_isp_dns_server();
    if (isp == 0) {
        std::vector<uint8_t> resp;
        if (dns_make_servfail(udp.payload, udp.payload_len, &resp)) {
            inject_udp(ip.dst, ip.src, udp.dst_port, udp.src_port, resp.data(), resp.size());
        } else {
            stats_.packets_dropped.fetch_add(1);
        }
        return;
    }
    forward_dns_query(ip, udp, isp);
}

void Engine::forward_dns_query(const Ipv4Packet& ip, const UdpDatagram& udp, uint32_t resolver) {
    FiveTuple key{ip.src, ip.dst, udp.src_port, udp.dst_port, kProtoUdp};
    UdpSession* session = sessions_.find_udp(key);
    if (session == nullptr) {
        UdpSession created;
        created.tuple = key;
        created.is_dns = true;
        created.sock_fd = open_udp_socket();
        created.last_activity_ms = now_ms();
        if (created.sock_fd < 0) {
            stats_.set_error("DNS socket failed");
            return;
        }
        session = sessions_.insert_udp(key, std::move(created));
        sessions_.bind_udp_fd(session->sock_fd, key);
        add_epoll(session->sock_fd, EPOLLIN | EPOLLERR | EPOLLHUP);
    }
    session->is_dns = true;
    session->last_activity_ms = now_ms();
    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(53);
    addr.sin_addr.s_addr = htonl(resolver);
    ::sendto(session->sock_fd, udp.payload, udp.payload_len, 0, reinterpret_cast<sockaddr*>(&addr), sizeof(addr));
}

void Engine::handle_socket(int fd, uint32_t events) {
    if (TcpSession* tcp = sessions_.find_tcp_by_fd(fd)) {
        if ((events & (EPOLLERR | EPOLLHUP)) != 0 && (events & EPOLLIN) == 0) {
            int err = 0;
            socklen_t len = sizeof(err);
            getsockopt(fd, SOL_SOCKET, SO_ERROR, &err, &len);
            if (!tcp->syn_acked) {
                inject_tcp(tcp->tuple.dst_ip, tcp->tuple.src_ip, tcp->tuple.dst_port, tcp->tuple.src_port, 0,
                           tcp->client_next_seq, kTcpRst | kTcpAck, nullptr, 0, 0, false);
            }
            close_tcp_session(tcp->tuple, false);
            return;
        }
        if (!tcp->syn_acked && (events & EPOLLOUT) != 0) {
            int err = 0;
            socklen_t len = sizeof(err);
            getsockopt(fd, SOL_SOCKET, SO_ERROR, &err, &len);
            if (err != 0) {
                inject_tcp(tcp->tuple.dst_ip, tcp->tuple.src_ip, tcp->tuple.dst_port, tcp->tuple.src_port, 0,
                           tcp->client_next_seq, kTcpRst | kTcpAck, nullptr, 0, 0, false);
                close_tcp_session(tcp->tuple, false);
                return;
            }
            tcp->syn_acked = true;
            tcp->state = TcpState::Established;
            inject_tcp(tcp->tuple.dst_ip, tcp->tuple.src_ip, tcp->tuple.dst_port, tcp->tuple.src_port,
                       tcp->server_isn, tcp->client_next_seq, kTcpSyn | kTcpAck, nullptr, 0, 65535, true);
            tcp->server_next_seq = tcp->server_isn + 1;
            if (!tcp->pending_chunks.empty()) {
                std::vector<DpiChunk> chunks;
                chunks.reserve(tcp->pending_chunks.size());
                for (auto& part : tcp->pending_chunks) {
                    DpiChunk chunk;
                    chunk.bytes = std::move(part);
                    chunks.push_back(std::move(chunk));
                }
                tcp->pending_chunks.clear();
                if (!send_dpi_chunks(tcp, chunks)) {
                    close_tcp_session(tcp->tuple, true);
                    return;
                }
            } else if (!tcp->pending_out.empty()) {
                send_to_socket(tcp, tcp->pending_out.data(), tcp->pending_out.size());
                tcp->pending_out.clear();
            }
            mod_epoll(fd, EPOLLIN | EPOLLERR | EPOLLHUP);
        }
        if ((events & EPOLLIN) != 0) {
            uint8_t buf[8192];
            while (true) {
                const ssize_t n = ::recv(fd, buf, sizeof(buf), 0);
                if (n > 0) {
                    tcp->last_activity_ms = now_ms();
                    stats_.bytes_out.fetch_add(static_cast<uint64_t>(n));
                    if (inject_tcp(tcp->tuple.dst_ip, tcp->tuple.src_ip, tcp->tuple.dst_port, tcp->tuple.src_port,
                                   tcp->server_next_seq, tcp->client_next_seq, kTcpAck | kTcpPsh, buf,
                                   static_cast<size_t>(n), 65535, false)) {
                        tcp->server_next_seq += static_cast<uint32_t>(n);
                    }
                    continue;
                }
                if (n == 0) {
                    inject_tcp(tcp->tuple.dst_ip, tcp->tuple.src_ip, tcp->tuple.dst_port, tcp->tuple.src_port,
                               tcp->server_next_seq, tcp->client_next_seq, kTcpFin | kTcpAck, nullptr, 0, 65535,
                               false);
                    tcp->server_next_seq += 1;
                    tcp->fin_from_socket = true;
                    if (tcp->fin_from_client) {
                        close_tcp_session(tcp->tuple, false);
                    }
                    return;
                }
                if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    break;
                }
                close_tcp_session(tcp->tuple, true);
                return;
            }
        }
        return;
    }

    if (UdpSession* udp = sessions_.find_udp_by_fd(fd)) {
        uint8_t buf[4096];
        sockaddr_in from{};
        socklen_t from_len = sizeof(from);
        const ssize_t n = ::recvfrom(fd, buf, sizeof(buf), 0, reinterpret_cast<sockaddr*>(&from), &from_len);
        if (n < 0) {
            if (errno != EAGAIN && errno != EWOULDBLOCK) {
                close_udp_session(udp->tuple);
            }
            return;
        }
        udp->last_activity_ms = now_ms();
        stats_.bytes_out.fetch_add(static_cast<uint64_t>(n));
        if (udp->is_dns) {
            remember_dns_answers(buf, static_cast<size_t>(n));
        }
        inject_udp(udp->tuple.dst_ip, udp->tuple.src_ip, udp->tuple.dst_port, udp->tuple.src_port, buf,
                   static_cast<size_t>(n));
    }
}

bool Engine::protect_fd(int fd) {
    if (protect_ == nullptr) {
        return false;
    }
    return protect_(protect_user_, fd);
}

bool Engine::add_epoll(int fd, uint32_t events) {
    epoll_event ev{};
    ev.events = events;
    ev.data.fd = fd;
    return epoll_ctl(epoll_fd_, EPOLL_CTL_ADD, fd, &ev) == 0;
}

bool Engine::mod_epoll(int fd, uint32_t events) {
    epoll_event ev{};
    ev.events = events;
    ev.data.fd = fd;
    return epoll_ctl(epoll_fd_, EPOLL_CTL_MOD, fd, &ev) == 0;
}

void Engine::del_epoll(int fd) {
    epoll_ctl(epoll_fd_, EPOLL_CTL_DEL, fd, nullptr);
}

bool Engine::inject_tcp(uint32_t src, uint32_t dst, uint16_t sport, uint16_t dport, uint32_t seq, uint32_t ack,
                        uint8_t flags, const uint8_t* payload, size_t payload_len, uint16_t window, bool mss) {
    std::lock_guard<std::mutex> lock(tun_write_mu_);
    return inject_tcp_packet(tun_fd_, config_.mtu, src, dst, sport, dport, seq, ack, flags, payload, payload_len,
                             window, mss);
}

bool Engine::inject_udp(uint32_t src, uint32_t dst, uint16_t sport, uint16_t dport, const uint8_t* payload,
                        size_t payload_len) {
    std::lock_guard<std::mutex> lock(tun_write_mu_);
    return inject_udp_packet(tun_fd_, src, dst, sport, dport, payload, payload_len);
}

int Engine::open_tcp_socket(uint32_t dst_ip, uint16_t dst_port) {
    return create_protected_tcp_socket(dst_ip, dst_port, protect_, protect_user_);
}

int Engine::open_udp_socket() {
    return create_protected_udp_socket(protect_, protect_user_);
}

void Engine::close_tcp_session(const FiveTuple& key, bool rst) {
    TcpSession* session = sessions_.find_tcp(key);
    if (session == nullptr) {
        return;
    }
    if (rst && session->syn_acked) {
        inject_tcp(key.dst_ip, key.src_ip, key.dst_port, key.src_port, session->server_next_seq,
                   session->client_next_seq, kTcpRst | kTcpAck, nullptr, 0, 0, false);
    }
    if (session->sock_fd >= 0) {
        del_epoll(session->sock_fd);
        ::close(session->sock_fd);
    }
    sessions_.erase_tcp(key);
}

void Engine::close_udp_session(const FiveTuple& key) {
    UdpSession* session = sessions_.find_udp(key);
    if (session == nullptr) {
        return;
    }
    if (session->sock_fd >= 0) {
        del_epoll(session->sock_fd);
        ::close(session->sock_fd);
    }
    sessions_.erase_udp(key);
}

void Engine::expire_sessions() {
    const uint64_t now = now_ms();
    for (const auto& key : sessions_.expire_tcp(now, kTcpIdleMs)) {
        close_tcp_session(key, false);
    }
    for (const auto& key : sessions_.expire_udp(now, kUdpIdleMs)) {
        close_udp_session(key);
    }
}

void Engine::drop_all_sessions() {
    for (const auto& key : sessions_.all_tcp()) {
        close_tcp_session(key, true);
    }
    for (const auto& key : sessions_.all_udp()) {
        close_udp_session(key);
    }
    sessions_.clear();
}

void Engine::apply_strategy(ConnectionContext* ctx, uint32_t dst_ip) {
    if (ctx == nullptr) {
        return;
    }
    DomainRuleMatch match;
    if (!ctx->hostname.empty()) {
        match = matcher_.match_domain(ctx->hostname);
    } else {
        match = matcher_.match_ipv4(dst_ip);
        ctx->hostname = matcher_.domain_for_ipv4(dst_ip);
    }
    const RuleConfig* rule = nullptr;
    ctx->matched_rule = -1;
    if (match.rule_index >= 0 && match.rule_index < static_cast<int>(config_.rules.size())) {
        rule = &config_.rules[static_cast<size_t>(match.rule_index)];
        if (!rule->enabled) {
            rule = nullptr;
        } else {
            ctx->matched_rule = match.rule_index;
        }
    }
    ctx->flags = resolve_flags(config_, rule);
    if (rule != nullptr && !ctx->hostname.empty() && dst_ip != 0) {
        matcher_.remember_ipv4(dst_ip, ctx->hostname);
    }
}

bool Engine::has_active_rule_for_ip(uint32_t ip) const {
    return matcher_.match_ipv4(ip).rule_index >= 0;
}

bool Engine::has_active_rule_for_domain(const std::string& domain) const {
    return matcher_.match_domain(domain).rule_index >= 0;
}

bool Engine::lookup_host(const std::string& domain, uint32_t* ipv4) const {
    std::lock_guard<std::mutex> lock(hosts_mu_);
    return hosts_.lookup(domain, ipv4);
}

bool Engine::send_to_socket(TcpSession* session, const uint8_t* data, size_t len) {
    if (session == nullptr || data == nullptr || len == 0) {
        return true;
    }
    if (!session->syn_acked || session->sock_fd < 0) {
        if (session->pending_out.size() + len > kMaxPending) {
            return false;
        }
        session->pending_out.insert(session->pending_out.end(), data, data + len);
        return true;
    }
    size_t sent = 0;
    while (sent < len) {
        const ssize_t n = ::send(session->sock_fd, data + sent, len - sent, 0);
        if (n > 0) {
            sent += static_cast<size_t>(n);
            stats_.bytes_out.fetch_add(static_cast<uint64_t>(n));
            continue;
        }
        if (n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
            if (session->pending_out.size() + (len - sent) > kMaxPending) {
                return false;
            }
            session->pending_out.insert(session->pending_out.end(), data + sent, data + len);
            mod_epoll(session->sock_fd, EPOLLIN | EPOLLOUT | EPOLLERR | EPOLLHUP);
            return true;
        }
        return false;
    }
    return true;
}

bool Engine::send_dpi_chunks(TcpSession* session, const std::vector<DpiChunk>& chunks) {
    if (session == nullptr) {
        return false;
    }
    if (chunks.empty()) {
        return true;
    }
    if (!session->syn_acked || session->sock_fd < 0) {
        size_t total = 0;
        for (const auto& chunk : chunks) {
            total += chunk.bytes.size();
        }
        if (total > kMaxPending) {
            return false;
        }
        for (const auto& chunk : chunks) {
            session->pending_chunks.push_back(chunk.bytes);
        }
        return true;
    }
    int nodelay = 1;
    setsockopt(session->sock_fd, IPPROTO_TCP, TCP_NODELAY, &nodelay, sizeof(nodelay));
    for (size_t i = 0; i < chunks.size(); ++i) {
        if (!send_to_socket(session, chunks[i].bytes.data(), chunks[i].bytes.size())) {
            return false;
        }
        if (i + 1 < chunks.size()) {
            usleep(8000);
        }
    }
    return true;
}

void Engine::inject_icmp_port_unreach(const Ipv4Packet& ip) {
    if (ip.raw == nullptr || ip.ihl_bytes < 20) {
        return;
    }
    const size_t quoted = static_cast<size_t>(ip.ihl_bytes) + 8u;
    if (quoted > ip.raw_len) {
        return;
    }
    const size_t icmp_len = 8u + quoted;
    const size_t total = 20u + icmp_len;
    std::vector<uint8_t> pkt(total, 0);
    pkt[0] = 0x45;
    write_u16(pkt.data() + 2, static_cast<uint16_t>(total));
    pkt[8] = 64;
    pkt[9] = kProtoIcmp;
    write_u32(pkt.data() + 12, ip.dst);
    write_u32(pkt.data() + 16, ip.src);
    write_u16(pkt.data() + 10, ipv4_header_checksum(pkt.data(), 20));
    uint8_t* icmp = pkt.data() + 20;
    icmp[0] = 3;
    icmp[1] = 3;
    std::memcpy(icmp + 8, ip.raw, quoted);
    write_u16(icmp + 2, internet_checksum(icmp, icmp_len));
    std::lock_guard<std::mutex> lock(tun_write_mu_);
    tun_write(tun_fd_, pkt.data(), pkt.size());
}

void Engine::enqueue_dns(const Ipv4Packet& ip, const UdpDatagram& udp) {
    DnsPending job;
    job.src_ip = ip.src;
    job.dst_ip = ip.dst;
    job.src_port = udp.src_port;
    job.dst_port = udp.dst_port;
    job.payload.assign(udp.payload, udp.payload + udp.payload_len);
    {
        std::lock_guard<std::mutex> lock(dns_mu_);
        if (dns_jobs_.size() > 64) {
            stats_.packets_dropped.fetch_add(1);
            return;
        }
        dns_jobs_.push(std::move(job));
    }
    dns_cv_.notify_one();
}

void Engine::drain_dns_results() {
    while (true) {
        DnsPending result;
        {
            std::lock_guard<std::mutex> lock(dns_mu_);
            if (dns_results_.empty()) {
                return;
            }
            result = std::move(dns_results_.front());
            dns_results_.pop();
        }
        if (result.payload.empty()) {
            stats_.packets_dropped.fetch_add(1);
            stats_.set_error("Encrypted DNS lookup failed");
            continue;
        }
        remember_dns_answers(result.payload.data(), result.payload.size());
        inject_udp(result.dst_ip, result.src_ip, result.dst_port, result.src_port, result.payload.data(),
                   result.payload.size());
    }
}

void Engine::dns_worker() {
    while (!dns_stop_.load()) {
        DnsPending job;
        {
            std::unique_lock<std::mutex> lock(dns_mu_);
            dns_cv_.wait(lock, [this]() { return dns_stop_.load() || !dns_jobs_.empty(); });
            if (dns_stop_.load()) {
                return;
            }
            job = std::move(dns_jobs_.front());
            dns_jobs_.pop();
        }
        std::vector<uint8_t> response;
        bool ok = false;
        if (dns_resolve_ != nullptr) {
            uint8_t out[4096];
            int out_len = 0;
            if (dns_resolve_(dns_user_, job.payload.data(), static_cast<int>(job.payload.size()), out,
                             static_cast<int>(sizeof(out)), &out_len) &&
                out_len >= 12) {
                response.assign(out, out + out_len);
                ok = true;
            }
        }
        if (!ok) {
            ok = fallback_udp_dns(job.payload, &response);
        }
        DnsPending result = job;
        if (ok) {
            result.payload = std::move(response);
        } else {
            result.payload.clear();
        }
        {
            std::lock_guard<std::mutex> lock(dns_mu_);
            dns_results_.push(std::move(result));
        }
    }
}

bool Engine::fallback_udp_dns(const std::vector<uint8_t>& query, std::vector<uint8_t>* response) {
    if (response == nullptr || query.empty() || dns_servers_.empty()) {
        return false;
    }
    const int fd = ::socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) {
        return false;
    }
    if (!protect_fd(fd)) {
        ::close(fd);
        return false;
    }
    const int flags = fcntl(fd, F_GETFL, 0);
    fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    const uint16_t ports[2] = {1253, 53};
    for (uint32_t ip : dns_servers_) {
        for (uint16_t port : ports) {
            sockaddr_in addr{};
            addr.sin_family = AF_INET;
            addr.sin_port = htons(port);
            addr.sin_addr.s_addr = htonl(ip);
            ::sendto(fd, query.data(), query.size(), 0, reinterpret_cast<sockaddr*>(&addr), sizeof(addr));
        }
    }
    pollfd pfd{};
    pfd.fd = fd;
    pfd.events = POLLIN;
    const int pr = ::poll(&pfd, 1, 1500);
    if (pr > 0 && (pfd.revents & POLLIN) != 0) {
        uint8_t buf[4096];
        const ssize_t n = ::recvfrom(fd, buf, sizeof(buf), 0, nullptr, nullptr);
        ::close(fd);
        if (n >= 12) {
            response->assign(buf, buf + n);
            return true;
        }
        return false;
    }
    ::close(fd);
    return false;
}

void Engine::remember_dns_answers(const uint8_t* data, size_t len) {
    DnsMessage msg{};
    if (!parse_dns(data, len, &msg)) {
        return;
    }
    if (msg.questions.empty()) {
        return;
    }
    const std::string& qname = msg.questions[0].qname;
    if (matcher_.match_domain(qname).rule_index < 0) {
        return;
    }
    for (const auto& ans : msg.answers) {
        if (ans.type == kDnsTypeA && ans.ipv4 != 0) {
            matcher_.remember_ipv4(ans.ipv4, qname);
        }
    }
}

uint32_t Engine::pick_dns_server() const {
    return pick_dns_ipv4(dns_servers_, const_cast<uint32_t*>(&dns_rr_));
}

uint32_t Engine::pick_isp_dns_server() const {
    return pick_dns_ipv4(isp_dns_servers_, const_cast<uint32_t*>(&isp_dns_rr_));
}

uint64_t Engine::now_ms() const {
    return static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch())
            .count());
}

} // namespace luna
