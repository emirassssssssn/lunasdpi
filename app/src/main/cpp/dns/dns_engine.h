#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace luna {

uint32_t pick_dns_ipv4(const std::vector<uint32_t>& servers, uint32_t* rr);
bool parse_ipv4_string(const std::string& text, uint32_t* out);

} // namespace luna
