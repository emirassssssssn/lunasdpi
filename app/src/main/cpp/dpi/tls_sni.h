#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

namespace luna {

bool payload_looks_like_tls_client_hello(const uint8_t* data, size_t len);
bool extract_sni(const uint8_t* data, size_t len, std::string* sni, size_t* sni_offset);

} // namespace luna
