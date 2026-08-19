#pragma once

#include "dpi/dpi_engine.h"

#include <vector>

namespace luna {

class TcpFragmentationStrategy : public DpiStrategy {
public:
    bool process(std::vector<uint8_t>* payload, ConnectionContext* context) override;
    bool split(const std::vector<uint8_t>& payload, int fragment_size, std::vector<DpiChunk>* out);
    bool split_desync(const std::vector<uint8_t>& payload, int fragment_size, bool tls_hello,
                      std::vector<DpiChunk>* out);
};

class HttpHostCaseStrategy : public DpiStrategy {
public:
    bool process(std::vector<uint8_t>* payload, ConnectionContext* context) override;
};

class HttpSpacingStrategy : public DpiStrategy {
public:
    bool process(std::vector<uint8_t>* payload, ConnectionContext* context) override;
};

class HttpMethodUriStrategy : public DpiStrategy {
public:
    bool process(std::vector<uint8_t>* payload, ConnectionContext* context) override;
};

class DnsStrategy : public DpiStrategy {
public:
    bool process(std::vector<uint8_t>* payload, ConnectionContext* context) override;
};

class FakePacketStrategy : public DpiStrategy {
public:
    bool process(std::vector<uint8_t>* payload, ConnectionContext* context) override;
};

} // namespace luna
