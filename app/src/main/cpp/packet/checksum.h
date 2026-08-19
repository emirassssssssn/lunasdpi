#pragma once

#include <cstddef>
#include <cstdint>

namespace luna {

inline uint16_t read_u16(const uint8_t* p) {
    return static_cast<uint16_t>((static_cast<uint16_t>(p[0]) << 8) | p[1]);
}

inline uint32_t read_u32(const uint8_t* p) {
    return (static_cast<uint32_t>(p[0]) << 24) | (static_cast<uint32_t>(p[1]) << 16) |
           (static_cast<uint32_t>(p[2]) << 8) | static_cast<uint32_t>(p[3]);
}

inline void write_u16(uint8_t* p, uint16_t v) {
    p[0] = static_cast<uint8_t>(v >> 8);
    p[1] = static_cast<uint8_t>(v);
}

inline void write_u32(uint8_t* p, uint32_t v) {
    p[0] = static_cast<uint8_t>(v >> 24);
    p[1] = static_cast<uint8_t>(v >> 16);
    p[2] = static_cast<uint8_t>(v >> 8);
    p[3] = static_cast<uint8_t>(v);
}

inline uint16_t internet_checksum(const uint8_t* data, size_t len, uint32_t sum = 0) {
    size_t i = 0;
    for (; i + 1 < len; i += 2) {
        sum += static_cast<uint32_t>(read_u16(data + i));
    }
    if (i < len) {
        sum += static_cast<uint32_t>(data[i]) << 8;
    }
    while (sum >> 16) {
        sum = (sum & 0xFFFFu) + (sum >> 16);
    }
    return static_cast<uint16_t>(~sum);
}

uint16_t ipv4_header_checksum(const uint8_t* header, size_t header_len);
uint16_t transport_checksum(uint32_t src, uint32_t dst, uint8_t proto, const uint8_t* payload,
                            size_t payload_len);

} // namespace luna
