#pragma once

#include "packet/checksum.h"

#include <cstddef>
#include <cstdint>

namespace luna {

constexpr uint8_t kProtoIcmp = 1;
constexpr uint8_t kProtoTcp = 6;
constexpr uint8_t kProtoUdp = 17;

struct Ipv4Packet {
    const uint8_t* raw = nullptr;
    size_t raw_len = 0;
    uint8_t ihl_bytes = 0;
    uint8_t tos = 0;
    uint16_t total_length = 0;
    uint16_t id = 0;
    uint16_t frag_off = 0;
    uint8_t ttl = 0;
    uint8_t protocol = 0;
    uint32_t src = 0;
    uint32_t dst = 0;
    const uint8_t* payload = nullptr;
    size_t payload_len = 0;
    bool fragmented = false;
};

inline bool parse_ipv4(const uint8_t* data, size_t len, Ipv4Packet* out) {
    if (data == nullptr || out == nullptr || len < 20) {
        return false;
    }
    const uint8_t version = data[0] >> 4;
    const uint8_t ihl = data[0] & 0x0Fu;
    if (version != 4 || ihl < 5) {
        return false;
    }
    const size_t header_len = static_cast<size_t>(ihl) * 4u;
    if (header_len > len || header_len < 20) {
        return false;
    }
    const uint16_t total = read_u16(data + 2);
    if (total < header_len || total > len) {
        return false;
    }
    const uint16_t frag = read_u16(data + 6);
    const uint16_t offset = static_cast<uint16_t>(frag & 0x1FFFu);
    const bool mf = (frag & 0x2000u) != 0;
    out->raw = data;
    out->raw_len = total;
    out->ihl_bytes = static_cast<uint8_t>(header_len);
    out->tos = data[1];
    out->total_length = total;
    out->id = read_u16(data + 4);
    out->frag_off = frag;
    out->ttl = data[8];
    out->protocol = data[9];
    out->src = read_u32(data + 12);
    out->dst = read_u32(data + 16);
    out->payload = data + header_len;
    out->payload_len = static_cast<size_t>(total) - header_len;
    out->fragmented = mf || offset != 0;
    return true;
}

inline bool ipv4_is_fragment(const Ipv4Packet& p) {
    return p.fragmented;
}

} // namespace luna
