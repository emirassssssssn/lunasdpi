#include "packet/reassembly.h"

namespace luna {

bool Ipv4Reassembler::offer(const uint8_t* data, size_t len, uint64_t now_ms, std::vector<uint8_t>* out) {
    if (out == nullptr) {
        return false;
    }
    out->clear();
    Ipv4Packet p{};
    if (!parse_ipv4(data, len, &p)) {
        return false;
    }
    if (!p.fragmented) {
        out->assign(data, data + p.raw_len);
        return true;
    }
    if (bufs_.size() > 32) {
        expire(now_ms);
        if (bufs_.size() > 32) {
            bufs_.clear();
        }
    }
    Key key{p.src, p.dst, p.id, p.protocol};
    Buf& b = bufs_[key];
    if (b.header.empty()) {
        b.header.assign(p.raw, p.raw + p.ihl_bytes);
        b.deadline_ms = now_ms + 5000;
    }
    const uint32_t offset = static_cast<uint32_t>(p.frag_off & 0x1FFFu) * 8u;
    const bool more = (p.frag_off & 0x2000u) != 0;
    b.parts[offset] = std::vector<uint8_t>(p.payload, p.payload + p.payload_len);
    if (!more) {
        b.last = true;
        b.expected = static_cast<size_t>(offset) + p.payload_len;
    }
    if (!b.last) {
        return false;
    }
    std::vector<uint8_t> payload;
    payload.reserve(b.expected);
    uint32_t expect_off = 0;
    for (const auto& part : b.parts) {
        if (part.first != expect_off) {
            return false;
        }
        payload.insert(payload.end(), part.second.begin(), part.second.end());
        expect_off += static_cast<uint32_t>(part.second.size());
    }
    if (payload.size() != b.expected || payload.size() > 65515) {
        bufs_.erase(key);
        return false;
    }
    std::vector<uint8_t> full = b.header;
    if (full.size() < 20) {
        bufs_.erase(key);
        return false;
    }
    full[0] = static_cast<uint8_t>((full[0] & 0xF0u) | 5u); // force IHL 5 without options copy issues
    full.resize(20);
    full.insert(full.end(), payload.begin(), payload.end());
    const uint16_t total = static_cast<uint16_t>(full.size());
    write_u16(full.data() + 2, total);
    write_u16(full.data() + 6, 0); // clear fragment flags
    write_u16(full.data() + 10, 0);
    const uint16_t csum = ipv4_header_checksum(full.data(), 20);
    write_u16(full.data() + 10, csum);
    *out = std::move(full);
    bufs_.erase(key);
    return true;
}

void Ipv4Reassembler::expire(uint64_t now_ms) {
    for (auto it = bufs_.begin(); it != bufs_.end();) {
        if (now_ms > it->second.deadline_ms) {
            it = bufs_.erase(it);
        } else {
            ++it;
        }
    }
}

} // namespace luna
