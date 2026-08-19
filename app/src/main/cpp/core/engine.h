#pragma once

#include "core/config.h"
#include "core/stats.h"
#include "domain/domain_matcher.h"
#include "dpi/dpi_engine.h"
#include "packet/ipv4.h"
#include "packet/reassembly.h"
#include "packet/udp.h"
#include "session/session_table.h"

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <memory>
#include <mutex>
#include <queue>
#include <thread>
#include <vector>

namespace luna {

struct DnsPending {
    uint32_t src_ip = 0;
    uint32_t dst_ip = 0;
    uint16_t src_port = 0;
    uint16_t dst_port = 0;
    std::vector<uint8_t> payload;
};

class Engine {
public:
    using ProtectFn = bool (*)(void* user, int fd);
    using DnsResolveFn = bool (*)(void* user, const uint8_t* query, int query_len, uint8_t* out, int out_cap,
                                  int* out_len);

    Engine();
    ~Engine();

    bool start(int tun_fd, EngineConfig config, ProtectFn protect, void* user, DnsResolveFn dns_resolve,
               void* dns_user);
    void stop();
    void update_rules(std::vector<RuleConfig> rules);
    void update_hosts(std::vector<HostMapping> mappings);
    void on_network_changed();
    void snapshot_stats(uint64_t* packets_processed, uint64_t* packets_modified, uint64_t* packets_dropped,
                        uint64_t* bytes_in, uint64_t* bytes_out, uint64_t* dns_queries, int* active_tcp,
                        int* active_udp, int* native_errors, int* alive, char* last_error, int last_error_cap,
                        char* strategy, int strategy_cap) const;

    EngineStats& stats() { return stats_; }

private:
    void thread_main();
    void handle_tun();
    void handle_socket(int fd, uint32_t events);
    void handle_ipv4(const uint8_t* data, size_t len);
    void handle_tcp(const Ipv4Packet& ip, const uint8_t* transport, size_t transport_len);
    void handle_udp(const Ipv4Packet& ip, const uint8_t* transport, size_t transport_len);
    void handle_dns(const Ipv4Packet& ip, const UdpDatagram& udp);

    bool protect_fd(int fd);
    bool add_epoll(int fd, uint32_t events);
    bool mod_epoll(int fd, uint32_t events);
    void del_epoll(int fd);

    bool inject_tcp(uint32_t src, uint32_t dst, uint16_t sport, uint16_t dport, uint32_t seq, uint32_t ack,
                    uint8_t flags, const uint8_t* payload, size_t payload_len, uint16_t window, bool mss);
    bool inject_udp(uint32_t src, uint32_t dst, uint16_t sport, uint16_t dport, const uint8_t* payload,
                    size_t payload_len);

    int open_tcp_socket(uint32_t dst_ip, uint16_t dst_port);
    int open_udp_socket();
    void close_tcp_session(const FiveTuple& key, bool rst);
    void close_udp_session(const FiveTuple& key);
    void expire_sessions();
    void drop_all_sessions();
    void apply_strategy(ConnectionContext* ctx, uint32_t dst_ip);
    void rebuild_matcher();
    void apply_pending_rules();
    bool has_active_rule_for_ip(uint32_t ip) const;
    bool has_active_rule_for_domain(const std::string& domain) const;
    bool lookup_host(const std::string& domain, uint32_t* ipv4) const;
    bool send_to_socket(TcpSession* session, const uint8_t* data, size_t len);
    bool send_dpi_chunks(TcpSession* session, const std::vector<DpiChunk>& chunks);
    void inject_icmp_port_unreach(const Ipv4Packet& ip);
    void enqueue_dns(const Ipv4Packet& ip, const UdpDatagram& udp);
    void drain_dns_results();
    void dns_worker();
    bool fallback_udp_dns(const std::vector<uint8_t>& query, std::vector<uint8_t>* response);
    void remember_dns_answers(const uint8_t* data, size_t len);
    void forward_dns_query(const Ipv4Packet& ip, const UdpDatagram& udp, uint32_t resolver);
    uint32_t pick_dns_server() const;
    uint32_t pick_isp_dns_server() const;
    uint64_t now_ms() const;

    int tun_fd_ = -1;
    int epoll_fd_ = -1;
    EngineConfig config_{};
    ProtectFn protect_ = nullptr;
    void* protect_user_ = nullptr;
    DnsResolveFn dns_resolve_ = nullptr;
    void* dns_user_ = nullptr;
    std::atomic<bool> running_{false};
    std::atomic<bool> network_changed_{false};
    std::atomic<bool> rules_dirty_{false};
    std::mutex rules_mu_;
    std::vector<RuleConfig> pending_rules_;
    mutable std::mutex hosts_mu_;
    HostsTable hosts_;
    std::thread thread_;
    SessionTable sessions_;
    DomainMatcher matcher_;
    DpiEngine dpi_;
    Ipv4Reassembler reassembler_;
    EngineStats stats_;
    std::vector<uint32_t> dns_servers_;
    std::vector<uint32_t> isp_dns_servers_;
    std::mutex tun_write_mu_;
    uint8_t tun_buf_[65535]{};
    uint32_t dns_rr_ = 0;
    uint32_t isp_dns_rr_ = 0;
    std::mutex dns_mu_;
    std::condition_variable dns_cv_;
    std::queue<DnsPending> dns_jobs_;
    std::queue<DnsPending> dns_results_;
    std::thread dns_thread_;
    std::atomic<bool> dns_stop_{true};
};

uint32_t random_u32();

} // namespace luna
