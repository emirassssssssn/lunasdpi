#include "tcp/tcp_relay.h"

#include "packet/checksum.h"
#include "packet/tcp.h"
#include "tun/tun_io.h"

#include <arpa/inet.h>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/socket.h>
#include <unistd.h>
#include <vector>

namespace luna {

bool inject_tcp_packet(int tun_fd, int mtu, uint32_t src, uint32_t dst, uint16_t sport, uint16_t dport,
                       uint32_t seq, uint32_t ack, uint8_t flags, const uint8_t* payload, size_t payload_len,
                       uint16_t window, bool include_mss) {
    if (tun_fd < 0) {
        return false;
    }
    const int use_mtu = mtu >= 576 ? mtu : 1500;
    const size_t tcp_hdr = include_mss ? 24u : 20u;
    const size_t max_payload = static_cast<size_t>(use_mtu) > (20u + tcp_hdr) ? static_cast<size_t>(use_mtu) - 20u - tcp_hdr : 536u;
    size_t offset = 0;
    bool first = true;
    if (payload == nullptr) {
        payload_len = 0;
    }
    do {
        const size_t chunk = payload_len - offset > max_payload ? max_payload : payload_len - offset;
        const bool syn = first && include_mss;
        const size_t hdr_len = syn ? 24u : 20u;
        const size_t total = 20u + hdr_len + chunk;
        std::vector<uint8_t> pkt(total, 0);
        pkt[0] = 0x45;
        pkt[1] = 0;
        write_u16(pkt.data() + 2, static_cast<uint16_t>(total));
        write_u16(pkt.data() + 4, 0);
        write_u16(pkt.data() + 6, 0);
        pkt[8] = 64;
        pkt[9] = 6;
        write_u32(pkt.data() + 12, src);
        write_u32(pkt.data() + 16, dst);
        write_u16(pkt.data() + 10, 0);
        write_u16(pkt.data() + 10, ipv4_header_checksum(pkt.data(), 20));

        uint8_t* tcp = pkt.data() + 20;
        write_u16(tcp + 0, sport);
        write_u16(tcp + 2, dport);
        write_u32(tcp + 4, seq + static_cast<uint32_t>(offset));
        write_u32(tcp + 8, ack);
        tcp[12] = static_cast<uint8_t>((hdr_len / 4u) << 4);
        tcp[13] = flags;
        write_u16(tcp + 14, window);
        write_u16(tcp + 16, 0);
        write_u16(tcp + 18, 0);
        if (syn) {
            tcp[20] = 2;
            tcp[21] = 4;
            write_u16(tcp + 22, static_cast<uint16_t>(max_payload));
        }
        if (chunk > 0) {
            memcpy(tcp + hdr_len, payload + offset, chunk);
        }
        write_u16(tcp + 16, 0);
        const uint16_t tsum = transport_checksum(src, dst, 6, tcp, hdr_len + chunk);
        write_u16(tcp + 16, tsum);

        if (tun_write(tun_fd, pkt.data(), pkt.size()) != static_cast<ssize_t>(pkt.size())) {
            return false;
        }
        offset += chunk;
        first = false;
        if (payload_len == 0) {
            break;
        }
    } while (offset < payload_len);
    return true;
}

int create_protected_tcp_socket(uint32_t dst_ip, uint16_t dst_port, bool (*protect)(void*, int), void* user) {
    const int fd = ::socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        return -1;
    }
    const int one = 1;
    setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));
    setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, &one, sizeof(one));
    const int flags = fcntl(fd, F_GETFL, 0);
    fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    if (protect != nullptr && !protect(user, fd)) {
        ::close(fd);
        return -1;
    }
    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(dst_port);
    addr.sin_addr.s_addr = htonl(dst_ip);
    const int rc = ::connect(fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr));
    if (rc < 0 && errno != EINPROGRESS) {
        ::close(fd);
        return -1;
    }
    return fd;
}

} // namespace luna
