#pragma once

#include "core/config.h"
#include "session/connection.h"

#include <cstddef>
#include <cstdint>
#include <vector>

namespace luna {

struct DpiChunk {
    std::vector<uint8_t> bytes;
};

class DpiEngine {
public:
    std::vector<DpiChunk> process(ConnectionContext* ctx, const uint8_t* data, size_t len, bool* modified);
};

class DpiStrategy {
public:
    virtual ~DpiStrategy() = default;
    virtual bool process(std::vector<uint8_t>* payload, ConnectionContext* context) = 0;
};

} // namespace luna
