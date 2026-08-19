#pragma once

#include "packet/checksum.h"

#include <cstddef>
#include <cstdint>

namespace luna {

constexpr uint8_t kTcpFin = 0x01;
constexpr uint8_t kTcpSyn = 0x02;
constexpr uint8_t kTcpRst = 0x04;
constexpr uint8_t kTcpPsh = 0x08;
constexpr uint8_t kTcpAck = 0x10;
constexpr uint8_t kTcpUrg = 0x20;

struct TcpSegment {
    uint16_t src_port = 0;
    uint16_t dst_port = 0;
    uint32_t seq = 0;
    uint32_t ack = 0;
    uint8_t data_offset_bytes = 0;
    uint8_t flags = 0;
    uint16_t window = 0;
    const uint8_t* payload = nullptr;
    size_t payload_len = 0;
    const uint8_t* raw = nullptr;
    size_t raw_len = 0;
};

inline bool parse_tcp(const uint8_t* data, size_t len, TcpSegment* out) {
    if (data == nullptr || out == nullptr || len < 20) {
        return false;
    }
    const uint8_t data_offset = data[12] >> 4;
    if (data_offset < 5) {
        return false;
    }
    const size_t header_len = static_cast<size_t>(data_offset) * 4u;
    if (header_len > len || header_len < 20) {
        return false;
    }
    out->src_port = read_u16(data);
    out->dst_port = read_u16(data + 2);
    out->seq = read_u32(data + 4);
    out->ack = read_u32(data + 8);
    out->data_offset_bytes = static_cast<uint8_t>(header_len);
    out->flags = data[13];
    out->window = read_u16(data + 14);
    out->payload = data + header_len;
    out->payload_len = len - header_len;
    out->raw = data;
    out->raw_len = len;
    return true;
}

inline bool seq_before(uint32_t a, uint32_t b) {
    return static_cast<int32_t>(a - b) < 0;
}

} // namespace luna
