#pragma once

#include "packet/checksum.h"

#include <cstddef>
#include <cstdint>

namespace luna {

struct Ipv6Packet {
    uint8_t next_header = 0;
    uint8_t hop_limit = 0;
    uint8_t src[16]{};
    uint8_t dst[16]{};
    const uint8_t* payload = nullptr;
    size_t payload_len = 0;
};

inline bool parse_ipv6(const uint8_t* data, size_t len, Ipv6Packet* out) {
    if (data == nullptr || out == nullptr || len < 40) {
        return false;
    }
    const uint8_t version = data[0] >> 4;
    if (version != 6) {
        return false;
    }
    const uint16_t payload_len = read_u16(data + 4);
    if (static_cast<size_t>(payload_len) + 40u > len) {
        return false;
    }
    out->next_header = data[6];
    out->hop_limit = data[7];
    for (int i = 0; i < 16; ++i) {
        out->src[i] = data[8 + i];
        out->dst[i] = data[24 + i];
    }
    out->payload = data + 40;
    out->payload_len = payload_len;
    return true;
}

} // namespace luna
