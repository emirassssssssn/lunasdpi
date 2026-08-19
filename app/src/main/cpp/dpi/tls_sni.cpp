#include "dpi/tls_sni.h"

#include "packet/checksum.h"

namespace luna {
namespace {

bool read_u24(const uint8_t* p, size_t len, size_t off, uint32_t* out) {
    if (off + 3 > len || out == nullptr) {
        return false;
    }
    *out = (static_cast<uint32_t>(p[off]) << 16) | (static_cast<uint32_t>(p[off + 1]) << 8) |
           static_cast<uint32_t>(p[off + 2]);
    return true;
}

} // namespace

bool payload_looks_like_tls_client_hello(const uint8_t* data, size_t len) {
    if (data == nullptr || len < 6) {
        return false;
    }
    return data[0] == 0x16 && data[1] == 0x03 && data[5] == 0x01;
}

bool extract_sni(const uint8_t* data, size_t len, std::string* sni, size_t* sni_offset) {
    if (sni != nullptr) {
        sni->clear();
    }
    if (sni_offset != nullptr) {
        *sni_offset = 0;
    }
    if (!payload_looks_like_tls_client_hello(data, len) || len < 44) {
        return false;
    }
    size_t pos = 5;
    if (pos + 4 > len || data[pos] != 0x01) {
        return false;
    }
    uint32_t hs_len = 0;
    if (!read_u24(data, len, pos + 1, &hs_len)) {
        return false;
    }
    (void)hs_len;
    pos += 4;
    if (pos + 2 + 32 + 1 > len) {
        return false;
    }
    pos += 2;
    pos += 32;
    if (pos >= len) {
        return false;
    }
    const uint8_t session_id_len = data[pos];
    pos += 1u + session_id_len;
    if (pos + 2 > len) {
        return false;
    }
    const uint16_t cipher_len = read_u16(data + pos);
    pos += 2u + cipher_len;
    if (pos >= len) {
        return false;
    }
    const uint8_t comp_len = data[pos];
    pos += 1u + comp_len;
    if (pos + 2 > len) {
        return false;
    }
    const uint16_t ext_len = read_u16(data + pos);
    pos += 2;
    if (pos + ext_len > len) {
        return false;
    }
    const size_t ext_end = pos + ext_len;
    while (pos + 4 <= ext_end) {
        const uint16_t type = read_u16(data + pos);
        const uint16_t elen = read_u16(data + pos + 2);
        pos += 4;
        if (pos + elen > ext_end) {
            return false;
        }
        if (type == 0 && elen >= 5) {
            const uint16_t list_len = read_u16(data + pos);
            if (static_cast<size_t>(list_len) + 2u > elen) {
                return false;
            }
            size_t npos = pos + 2;
            const size_t list_end = pos + 2 + list_len;
            if (npos + 3 <= list_end) {
                const uint8_t name_type = data[npos];
                const uint16_t name_len = read_u16(data + npos + 1);
                npos += 3;
                if (name_type == 0 && npos + name_len <= list_end && name_len > 0 && name_len < 256) {
                    if (sni != nullptr) {
                        sni->assign(reinterpret_cast<const char*>(data + npos), name_len);
                    }
                    if (sni_offset != nullptr) {
                        *sni_offset = npos;
                    }
                    return true;
                }
            }
        }
        pos += elen;
    }
    return false;
}

} // namespace luna
