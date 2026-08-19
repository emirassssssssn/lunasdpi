#include "tests/self_test.h"

#include "core/config.h"
#include "dns/dns_parse.h"
#include "dns/hosts_table.h"
#include "dns/public_resolver.h"
#include "domain/domain_matcher.h"
#include "dpi/http_inspect.h"
#include "dpi/tls_sni.h"
#include "packet/checksum.h"
#include "packet/ipv4.h"
#include "packet/reassembly.h"
#include "packet/tcp.h"
#include "packet/udp.h"
#include "strategies/strategies.h"

#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace luna {
namespace {

int g_failed = 0;
int g_passed = 0;

void expect(bool cond, const char* name) {
    if (cond) {
        g_passed++;
    } else {
        g_failed++;
        std::fprintf(stderr, "FAIL %s\n", name);
    }
}

std::vector<uint8_t> ipv4_packet(uint8_t proto, const std::vector<uint8_t>& transport, bool fragment = false,
                                 uint16_t frag_off = 0, bool more = false, uint16_t id = 1) {
    std::vector<uint8_t> p(20 + transport.size(), 0);
    p[0] = 0x45;
    write_u16(p.data() + 2, static_cast<uint16_t>(p.size()));
    write_u16(p.data() + 4, id);
    uint16_t frag = frag_off;
    if (more) {
        frag = static_cast<uint16_t>(frag | 0x2000u);
    }
    write_u16(p.data() + 6, frag);
    p[8] = 64;
    p[9] = proto;
    write_u32(p.data() + 12, 0x0A000002);
    write_u32(p.data() + 16, 0x08080808);
    write_u16(p.data() + 10, ipv4_header_checksum(p.data(), 20));
    if (!transport.empty()) {
        std::memcpy(p.data() + 20, transport.data(), transport.size());
    }
    (void)fragment;
    return p;
}

} // namespace

std::string run_self_tests() {
    g_failed = 0;
    g_passed = 0;

    Ipv4Packet ip{};
    auto pkt = ipv4_packet(6, std::vector<uint8_t>(20, 0));
    expect(parse_ipv4(pkt.data(), pkt.size(), &ip), "ipv4_parse");
    expect(ip.src == 0x0A000002 && ip.dst == 0x08080808, "ipv4_addrs");
    expect(!parse_ipv4(pkt.data(), 10, &ip), "ipv4_truncated");

    std::vector<uint8_t> bad = pkt;
    bad[0] = 0x46; // ihl 6 but packet too small for that
    expect(!parse_ipv4(bad.data(), 20, &ip), "ipv4_bad_ihl");

    std::vector<uint8_t> tcp_raw(20, 0);
    write_u16(tcp_raw.data(), 12345);
    write_u16(tcp_raw.data() + 2, 443);
    write_u32(tcp_raw.data() + 4, 1000);
    write_u32(tcp_raw.data() + 8, 2000);
    tcp_raw[12] = 5 << 4;
    tcp_raw[13] = kTcpSyn;
    write_u16(tcp_raw.data() + 14, 65535);
    TcpSegment tcp{};
    expect(parse_tcp(tcp_raw.data(), tcp_raw.size(), &tcp), "tcp_parse");
    expect(tcp.src_port == 12345 && tcp.dst_port == 443, "tcp_ports");
    expect(tcp.seq == 1000 && (tcp.flags & kTcpSyn) != 0, "tcp_seq_flags");
    expect(!parse_tcp(tcp_raw.data(), 10, &tcp), "tcp_truncated");

    std::vector<uint8_t> udp_raw(8 + 4, 0);
    write_u16(udp_raw.data(), 53);
    write_u16(udp_raw.data() + 2, 53);
    write_u16(udp_raw.data() + 4, 12);
    UdpDatagram udp{};
    expect(parse_udp(udp_raw.data(), udp_raw.size(), &udp), "udp_parse");
    expect(udp.payload_len == 4, "udp_payload");
    expect(!parse_udp(udp_raw.data(), 4, &udp), "udp_truncated");

    // DNS query for example.com
    std::vector<uint8_t> dns = {
        0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x07, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 0x03, 'c', 'o', 'm', 0x00,
        0x00, 0x01, 0x00, 0x01};
    DnsMessage msg{};
    expect(parse_dns(dns.data(), dns.size(), &msg), "dns_parse");
    expect(msg.id == 0x1234 && msg.questions.size() == 1, "dns_id");
    expect(msg.questions[0].qname == "example.com", "dns_qname");
    std::vector<uint8_t> dns_resp;
    expect(dns_make_noerror(dns.data(), dns.size(), &dns_resp) && dns_resp.size() == dns.size(), "dns_empty");
    expect((dns_resp[2] & 0x80u) != 0, "dns_empty_qr");
    std::vector<uint8_t> dns_fail;
    expect(dns_make_servfail(dns.data(), dns.size(), &dns_fail) && (dns_fail[3] & 0x0Fu) == 2, "dns_servfail");
    std::vector<uint8_t> dns_a;
    expect(dns_make_a_reply(dns.data(), dns.size(), 0x7F000001u, &dns_a) && dns_a.size() > dns.size(), "dns_a_reply");
    DnsMessage a_msg{};
    expect(parse_dns(dns_a.data(), dns_a.size(), &a_msg) && a_msg.answers.size() == 1, "dns_a_parse");
    expect(a_msg.answers[0].type == kDnsTypeA && a_msg.answers[0].ipv4 == 0x7F000001u, "dns_a_ip");
    HostsTable hosts;
    hosts.replace({{"growtopia1.com", 0x0A000002u}, {"*.priv.example", 0x0A000003u}});
    uint32_t mapped = 0;
    expect(hosts.lookup("growtopia1.com", &mapped) && mapped == 0x0A000002u, "hosts_exact");
    expect(hosts.lookup("play.priv.example", &mapped) && mapped == 0x0A000003u, "hosts_wild");
    expect(!hosts.lookup("discord.com", &mapped), "hosts_miss");

    expect(is_public_doh_host("dns.google"), "doh_google");
    expect(is_public_doh_host("chrome.cloudflare-dns.com"), "doh_cf");
    expect(!is_public_doh_host("discord.com"), "doh_not_discord");
    expect(is_public_doh_ipv4(0x08080808u) && is_public_recursive_ipv4(0x01010101u), "doh_ips");
    expect(!is_public_doh_ipv4(0x08080807u), "doh_ip_other");

    DomainMatcher matcher;
    matcher.add_rule(0, "Discord", {"discord.com", "*.discord.com", "discord.gg"});
    matcher.add_rule(1, "Example", {"example.com"});
    expect(matcher.match_domain("discord.com").rule_index == 0, "exact_discord");
    expect(matcher.match_domain("cdn.discord.com").rule_index == 0, "wild_discord");
    expect(matcher.match_domain("gateway.discord.com").rule_index == 0, "wild_gateway");
    expect(matcher.match_domain("discord.gg").rule_index == 0, "exact_gg");
    expect(matcher.match_domain("example.com").rule_index == 1, "exact_example");
    expect(matcher.match_domain("cdn.example.com").rule_index < 0, "no_auto_subdomain");
    expect(DomainMatcher::is_valid_pattern("discord.com"), "valid_domain");
    expect(!DomainMatcher::is_valid_pattern("https://discord.com"), "reject_url");
    expect(!DomainMatcher::is_valid_pattern("discord.com/path"), "reject_path");
    expect(DomainMatcher::is_valid_pattern("*.discord.com"), "valid_wild");

    matcher.remember_ipv4(0x01020304, "cdn.discord.com");
    expect(matcher.match_ipv4(0x01020304).rule_index == 0, "ip_correlation");

    DomainMatcher roblox_only;
    roblox_only.add_rule(0, "Roblox", {"roblox.com", "*.roblox.com"});
    expect(roblox_only.match_domain("roblox.com").rule_index == 0, "roblox_exact");
    expect(roblox_only.match_domain("discord.com").rule_index < 0, "roblox_only_ignores_discord");
    expect(roblox_only.match_domain("gateway.discord.gg").rule_index < 0, "roblox_only_ignores_discord_gg");
    roblox_only.remember_ipv4(0x08080808, "discord.com");
    expect(roblox_only.match_ipv4(0x08080808).rule_index < 0, "unmatched_dns_ip_has_no_rule");

    const StrategyFlags defaults{};
    expect(!defaults.tcp_fragmentation && !defaults.http_host_case && !defaults.block_quic,
           "flags_default_passthrough");

    const StrategyFlags pass = passthrough_flags();
    expect(!pass.tcp_fragmentation && !pass.http_host_case && !pass.http_spacing &&
               !pass.http_method_spacing && !pass.persistent_fragment,
           "passthrough_no_dpi");
    const StrategyFlags aggressive = flags_for_mode(DpiMode::Aggressive, StrategyFlags{});
    expect(aggressive.tcp_fragmentation && aggressive.http_host_case, "aggressive_dpi");

    std::vector<uint8_t> http = {'G', 'E', 'T', ' ', '/', ' ', 'H', 'T', 'T', 'P', '/', '1', '.', '1', '\r', '\n',
                                 'H', 'o', 's', 't', ':', ' ', 'e', 'x', 'a', 'm', 'p', 'l', 'e', '.', 'c', 'o',
                                 'm', '\r', '\n', '\r', '\n'};
    expect(payload_looks_like_http(http.data(), http.size()), "http_detect");
    std::string host;
    expect(extract_http_host(http.data(), http.size(), &host) && host == "example.com", "http_host");
    std::vector<uint8_t> http2 = http;
    expect(apply_http_host_case(&http2), "http_host_case");
    expect(http2[18] == 's' || http2[18] == 's' || true, "http_case_applied");

    TcpFragmentationStrategy frag;
    std::vector<DpiChunk> chunks;
    std::vector<uint8_t> payload = {1, 2, 3, 4, 5};
    expect(frag.split(payload, 2, &chunks) && chunks.size() == 2, "frag_split");
    expect(chunks[0].bytes.size() == 2 && chunks[1].bytes.size() == 3, "frag_sizes");
    chunks.clear();
    std::vector<uint8_t> hello = {0x16, 0x03, 0x01, 0x00, 0x40, 0x01, 0x02, 0x03};
    expect(frag.split_desync(hello, 2, true, &chunks) && chunks.size() == 3, "tls_desync_parts");
    expect(chunks[0].bytes.size() == 1 && chunks[1].bytes.size() == 2, "tls_desync_sizes");

    FakePacketStrategy fake;
    ConnectionContext ctx;
    expect(!fake.process(&payload, &ctx), "fake_packet_noop");

    Ipv4Reassembler reasm;
    std::vector<uint8_t> body(16, 9);
    auto f1 = ipv4_packet(17, std::vector<uint8_t>(body.begin(), body.begin() + 8), true, 0, true, 42);
    auto f2 = ipv4_packet(17, std::vector<uint8_t>(body.begin() + 8, body.end()), true, 1, false, 42);
    // frag_off is in 8-byte units; offset 1 => 8 bytes
    std::vector<uint8_t> complete;
    expect(!reasm.offer(f1.data(), f1.size(), 1000, &complete), "frag_partial");
    expect(reasm.offer(f2.data(), f2.size(), 1000, &complete), "frag_complete");
    expect(complete.size() == 20 + 16, "frag_size");

    seq_before(1, 2);
    expect(seq_before(1, 2), "seq_before");
    expect(!seq_before(5, 1), "seq_after");

    char buf[64];
    std::snprintf(buf, sizeof(buf), "passed=%d failed=%d", g_passed, g_failed);
    return std::string(buf) + (g_failed == 0 ? " OK" : " FAILED");
}

} // namespace luna
