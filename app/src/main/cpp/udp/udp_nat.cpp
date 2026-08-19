#include "udp/udp_nat.h"

#include "packet/checksum.h"
#include "tun/tun_io.h"

#include <arpa/inet.h>
#include <cstring>
#include <fcntl.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>
#include <vector>

namespace luna {

bool inject_udp_packet(int tun_fd, uint32_t src, uint32_t dst, uint16_t sport, uint16_t dport,
                       const uint8_t* payload, size_t payload_len) {
    if (tun_fd < 0) {
        return false;
    }
    if (payload == nullptr) {
        payload_len = 0;
    }
    const size_t total = 20u + 8u + payload_len;
    if (total > 65535u) {
        return false;
    }
    std::vector<uint8_t> pkt(total, 0);
    pkt[0] = 0x45;
    write_u16(pkt.data() + 2, static_cast<uint16_t>(total));
    pkt[8] = 64;
    pkt[9] = 17;
    write_u32(pkt.data() + 12, src);
    write_u32(pkt.data() + 16, dst);
    write_u16(pkt.data() + 10, ipv4_header_checksum(pkt.data(), 20));

    uint8_t* udp = pkt.data() + 20;
    write_u16(udp + 0, sport);
    write_u16(udp + 2, dport);
    write_u16(udp + 4, static_cast<uint16_t>(8u + payload_len));
    write_u16(udp + 6, 0);
    if (payload_len > 0) {
        memcpy(udp + 8, payload, payload_len);
    }
    const uint16_t csum = transport_checksum(src, dst, 17, udp, 8u + payload_len);
    write_u16(udp + 6, csum == 0 ? 0xFFFFu : csum);
    return tun_write(tun_fd, pkt.data(), pkt.size()) == static_cast<ssize_t>(pkt.size());
}

int create_protected_udp_socket(bool (*protect)(void*, int), void* user) {
    const int fd = ::socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) {
        return -1;
    }
    const int flags = fcntl(fd, F_GETFL, 0);
    fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    if (protect != nullptr && !protect(user, fd)) {
        ::close(fd);
        return -1;
    }
    return fd;
}

} // namespace luna
