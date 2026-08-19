#pragma once

#include <cstddef>
#include <cstdint>

namespace luna {

bool inject_tcp_packet(int tun_fd, int mtu, uint32_t src, uint32_t dst, uint16_t sport, uint16_t dport,
                       uint32_t seq, uint32_t ack, uint8_t flags, const uint8_t* payload, size_t payload_len,
                       uint16_t window, bool include_mss);

int create_protected_tcp_socket(uint32_t dst_ip, uint16_t dst_port, bool (*protect)(void*, int), void* user);

} // namespace luna
