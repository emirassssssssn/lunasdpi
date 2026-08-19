#include "dns/dns_engine.h"

#include <cstdio>

namespace luna {

bool parse_ipv4_string(const std::string& text, uint32_t* out) {
    if (out == nullptr || text.empty()) {
        return false;
    }
    unsigned a = 0;
    unsigned b = 0;
    unsigned c = 0;
    unsigned d = 0;
    if (std::sscanf(text.c_str(), "%u.%u.%u.%u", &a, &b, &c, &d) != 4) {
        return false;
    }
    if (a > 255 || b > 255 || c > 255 || d > 255) {
        return false;
    }
    *out = (a << 24) | (b << 16) | (c << 8) | d;
    return true;
}

uint32_t pick_dns_ipv4(const std::vector<uint32_t>& servers, uint32_t* rr) {
    if (servers.empty()) {
        return 0;
    }
    uint32_t index = 0;
    if (rr != nullptr) {
        index = (*rr) % static_cast<uint32_t>(servers.size());
        (*rr)++;
    }
    return servers[index];
}

} // namespace luna
