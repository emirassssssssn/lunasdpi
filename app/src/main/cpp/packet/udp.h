#pragma once

#include "packet/checksum.h"

#include <cstddef>
#include <cstdint>

namespace luna {

struct UdpDatagram {
    uint16_t src_port = 0;
    uint16_t dst_port = 0;
    uint16_t length = 0;
    const uint8_t* payload = nullptr;
    size_t payload_len = 0;
};

inline bool parse_udp(const uint8_t* data, size_t len, UdpDatagram* out) {
    if (data == nullptr || out == nullptr || len < 8) {
        return false;
    }
    const uint16_t length = read_u16(data + 4);
    if (length < 8 || length > len) {
        return false;
    }
    out->src_port = read_u16(data);
    out->dst_port = read_u16(data + 2);
    out->length = length;
    out->payload = data + 8;
    out->payload_len = static_cast<size_t>(length) - 8u;
    return true;
}

} // namespace luna
