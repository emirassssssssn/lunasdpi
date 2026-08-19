#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace luna {

bool payload_looks_like_http(const uint8_t* data, size_t len);
bool extract_http_host(const uint8_t* data, size_t len, std::string* host);
bool apply_http_host_case(std::vector<uint8_t>* payload);
bool apply_http_spacing(std::vector<uint8_t>* payload);
bool apply_http_method_spacing(std::vector<uint8_t>* payload);

} // namespace luna
