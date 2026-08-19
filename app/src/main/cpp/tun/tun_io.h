#pragma once

#include <cstddef>
#include <cstdint>
#include <sys/types.h>
#include <unistd.h>

namespace luna {

ssize_t tun_read(int fd, uint8_t* buf, size_t len);
ssize_t tun_write(int fd, const uint8_t* buf, size_t len);

} // namespace luna
