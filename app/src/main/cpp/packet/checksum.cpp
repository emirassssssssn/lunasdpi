#include "packet/checksum.h"

namespace luna {

uint16_t ipv4_header_checksum(const uint8_t* header, size_t header_len) {
    if (header == nullptr || header_len < 20 || (header_len % 2) != 0) {
        return 0;
    }
    return internet_checksum(header, header_len, 0);
}

uint16_t transport_checksum(uint32_t src, uint32_t dst, uint8_t proto, const uint8_t* payload,
                            size_t payload_len) {
    uint8_t pseudo[12];
    write_u32(pseudo + 0, src);
    write_u32(pseudo + 4, dst);
    pseudo[8] = 0;
    pseudo[9] = proto;
    write_u16(pseudo + 10, static_cast<uint16_t>(payload_len));
    uint32_t sum = 0;
    size_t i = 0;
    for (; i + 1 < 12; i += 2) {
        sum += read_u16(pseudo + i);
    }
    return internet_checksum(payload, payload_len, sum);
}

} // namespace luna
