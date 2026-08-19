#include "tun/tun_io.h"

#include <cerrno>
#include <unistd.h>

namespace luna {

ssize_t tun_read(int fd, uint8_t* buf, size_t len) {
    if (fd < 0 || buf == nullptr || len == 0) {
        return -1;
    }
    while (true) {
        const ssize_t n = ::read(fd, buf, len);
        if (n < 0 && errno == EINTR) {
            continue;
        }
        return n;
    }
}

ssize_t tun_write(int fd, const uint8_t* buf, size_t len) {
    if (fd < 0 || buf == nullptr) {
        return -1;
    }
    size_t sent = 0;
    while (sent < len) {
        const ssize_t n = ::write(fd, buf + sent, len - sent);
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            return -1;
        }
        if (n == 0) {
            return static_cast<ssize_t>(sent);
        }
        sent += static_cast<size_t>(n);
    }
    return static_cast<ssize_t>(sent);
}

} // namespace luna
