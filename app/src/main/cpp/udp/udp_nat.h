#pragma once

#include <cstddef>
#include <cstdint>

namespace luna {

bool inject_udp_packet(int tun_fd, uint32_t src, uint32_t dst, uint16_t sport, uint16_t dport,
                       const uint8_t* payload, size_t payload_len);

int create_protected_udp_socket(bool (*protect)(void*, int), void* user);

} // namespace luna
