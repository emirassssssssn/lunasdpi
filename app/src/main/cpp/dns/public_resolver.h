#pragma once

#include <cstdint>
#include <string>

namespace luna {

bool is_public_doh_host(const std::string& host);
bool is_public_doh_ipv4(uint32_t ip);
bool is_public_recursive_ipv4(uint32_t ip);

} // namespace luna
