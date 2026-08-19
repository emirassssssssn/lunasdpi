#include "dns/public_resolver.h"

#include "domain/domain_matcher.h"

#include <cstring>

namespace luna {
namespace {

bool host_is_or_under(const std::string& host, const char* domain) {
    const size_t n = host.size();
    const size_t m = std::strlen(domain);
    if (n == m) {
        return host == domain;
    }
    if (n > m + 1 && host[n - m - 1] == '.' && host.compare(n - m, m, domain) == 0) {
        return true;
    }
    return false;
}

} // namespace

bool is_public_doh_host(const std::string& host) {
    const std::string n = DomainMatcher::normalize(host);
    if (n.empty()) {
        return false;
    }
    return host_is_or_under(n, "dns.google") || host_is_or_under(n, "dns.google.com") ||
           host_is_or_under(n, "cloudflare-dns.com") || host_is_or_under(n, "one.one.one.one") ||
           host_is_or_under(n, "dns.quad9.net") || host_is_or_under(n, "dns9.quad9.net") ||
           host_is_or_under(n, "dns.adguard.com") || host_is_or_under(n, "adguard-dns.com") ||
           host_is_or_under(n, "dns.nextdns.io") || host_is_or_under(n, "dns.alidns.com") ||
           host_is_or_under(n, "opendns.com") || host_is_or_under(n, "cleanbrowsing.org") ||
           host_is_or_under(n, "dns.sb") || host_is_or_under(n, "doh.dns.sb");
}

bool is_public_recursive_ipv4(uint32_t ip) {
    switch (ip) {
    case 0x08080808u: // 8.8.8.8
    case 0x08080404u: // 8.8.4.4
    case 0x01010101u: // 1.1.1.1
    case 0x01000001u: // 1.0.0.1
    case 0x01010102u: // 1.1.1.2
    case 0x01000002u: // 1.0.0.2
    case 0x01010103u: // 1.1.1.3
    case 0x01000003u: // 1.0.0.3
    case 0x09090909u: // 9.9.9.9
    case 0x0909090Au: // 9.9.9.10
    case 0x0909090Bu: // 9.9.9.11
    case 0x95707070u: // 149.112.112.112
    case 0xD043DEDEu: // 208.67.222.222
    case 0xD043DCDCu: // 208.67.220.220
    case 0x5E8C0E0Eu: // 94.140.14.14
    case 0x5E8C0F0Fu: // 94.140.15.15
        return true;
    default:
        return false;
    }
}

bool is_public_doh_ipv4(uint32_t ip) {
    return is_public_recursive_ipv4(ip);
}

} // namespace luna
