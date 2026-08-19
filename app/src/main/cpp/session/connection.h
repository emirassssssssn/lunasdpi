#pragma once

#include "core/config.h"

#include <cstdint>
#include <string>
#include <vector>

namespace luna {

enum class TcpState {
    Connecting,
    Established,
    Closing,
};

enum class AppProto {
    Unknown,
    Http,
    Tls,
    Other,
};

struct FiveTuple {
    uint32_t src_ip = 0;
    uint32_t dst_ip = 0;
    uint16_t src_port = 0;
    uint16_t dst_port = 0;
    uint8_t proto = 0;
};

inline bool operator==(const FiveTuple& a, const FiveTuple& b) {
    return a.src_ip == b.src_ip && a.dst_ip == b.dst_ip && a.src_port == b.src_port &&
           a.dst_port == b.dst_port && a.proto == b.proto;
}

struct FiveTupleHash {
    size_t operator()(const FiveTuple& t) const {
        size_t h = t.src_ip;
        h ^= static_cast<size_t>(t.dst_ip) + 0x9e3779b9u + (h << 6) + (h >> 2);
        h ^= static_cast<size_t>(t.src_port) + 0x9e3779b9u + (h << 6) + (h >> 2);
        h ^= static_cast<size_t>(t.dst_port) + 0x9e3779b9u + (h << 6) + (h >> 2);
        h ^= static_cast<size_t>(t.proto) + 0x9e3779b9u + (h << 6) + (h >> 2);
        return h;
    }
};

struct ConnectionContext {
    AppProto proto = AppProto::Unknown;
    bool first_data_done = false;
    bool identified = false;
    std::string hostname;
    int matched_rule = -1;
    StrategyFlags flags;
    uint32_t fragments_sent = 0;
};

struct TcpSession {
    FiveTuple tuple;
    int sock_fd = -1;
    TcpState state = TcpState::Connecting;
    uint32_t client_next_seq = 0;
    uint32_t server_next_seq = 0;
    uint32_t server_isn = 0;
    uint16_t client_window = 65535;
    uint16_t advertised_window = 65535;
    std::vector<uint8_t> pending_out;
    std::vector<std::vector<uint8_t>> pending_chunks;
    ConnectionContext dpi;
    uint64_t last_activity_ms = 0;
    bool syn_acked = false;
    bool fin_from_client = false;
    bool fin_from_socket = false;
};

struct UdpSession {
    FiveTuple tuple;
    int sock_fd = -1;
    bool is_dns = false;
    uint16_t original_dns_id = 0;
    uint64_t last_activity_ms = 0;
};

} // namespace luna
