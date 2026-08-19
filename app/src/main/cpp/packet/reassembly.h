#pragma once

#include "packet/ipv4.h"

#include <cstdint>
#include <map>
#include <unordered_map>
#include <vector>

namespace luna {

class Ipv4Reassembler {
public:
    // Returns true when a complete datagram is available in `out`.
    bool offer(const uint8_t* data, size_t len, uint64_t now_ms, std::vector<uint8_t>* out);
    void expire(uint64_t now_ms);
    size_t size() const { return bufs_.size(); }

private:
    struct Key {
        uint32_t src = 0;
        uint32_t dst = 0;
        uint16_t id = 0;
        uint8_t proto = 0;
        bool operator==(const Key& o) const {
            return src == o.src && dst == o.dst && id == o.id && proto == o.proto;
        }
    };
    struct KeyHash {
        size_t operator()(const Key& k) const {
            return static_cast<size_t>(k.src ^ k.dst ^ (static_cast<uint32_t>(k.id) << 16) ^ k.proto);
        }
    };
    struct Buf {
        std::vector<uint8_t> header;
        std::map<uint32_t, std::vector<uint8_t>> parts;
        bool last = false;
        size_t expected = 0;
        uint64_t deadline_ms = 0;
    };

    std::unordered_map<Key, Buf, KeyHash> bufs_;
};

} // namespace luna
