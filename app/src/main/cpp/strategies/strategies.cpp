#include "strategies/strategies.h"

#include "dpi/http_inspect.h"

namespace luna {

bool TcpFragmentationStrategy::process(std::vector<uint8_t>* payload, ConnectionContext* context) {
    (void)payload;
    (void)context;
    return false;
}

bool TcpFragmentationStrategy::split(const std::vector<uint8_t>& payload, int fragment_size,
                                     std::vector<DpiChunk>* out) {
    if (out == nullptr || payload.size() < 2 || fragment_size < 1) {
        return false;
    }
    const size_t cut = static_cast<size_t>(fragment_size);
    if (cut >= payload.size()) {
        return false;
    }
    DpiChunk a;
    a.bytes.assign(payload.begin(), payload.begin() + static_cast<std::ptrdiff_t>(cut));
    DpiChunk b;
    b.bytes.assign(payload.begin() + static_cast<std::ptrdiff_t>(cut), payload.end());
    out->push_back(std::move(a));
    out->push_back(std::move(b));
    return true;
}

bool TcpFragmentationStrategy::split_desync(const std::vector<uint8_t>& payload, int fragment_size,
                                            bool tls_hello, std::vector<DpiChunk>* out) {
    if (out == nullptr) {
        return false;
    }
    if (!tls_hello || payload.size() < 4) {
        return split(payload, fragment_size, out);
    }
    const size_t first = 1;
    size_t second = static_cast<size_t>(fragment_size < 1 ? 1 : fragment_size);
    if (second < 1) {
        second = 1;
    }
    if (first + second >= payload.size()) {
        return split(payload, 1, out);
    }
    DpiChunk a;
    a.bytes.assign(payload.begin(), payload.begin() + static_cast<std::ptrdiff_t>(first));
    DpiChunk b;
    b.bytes.assign(payload.begin() + static_cast<std::ptrdiff_t>(first),
                   payload.begin() + static_cast<std::ptrdiff_t>(first + second));
    DpiChunk c;
    c.bytes.assign(payload.begin() + static_cast<std::ptrdiff_t>(first + second), payload.end());
    out->push_back(std::move(a));
    out->push_back(std::move(b));
    out->push_back(std::move(c));
    return true;
}

bool HttpHostCaseStrategy::process(std::vector<uint8_t>* payload, ConnectionContext* context) {
    (void)context;
    return apply_http_host_case(payload);
}

bool HttpSpacingStrategy::process(std::vector<uint8_t>* payload, ConnectionContext* context) {
    (void)context;
    return apply_http_spacing(payload);
}

bool HttpMethodUriStrategy::process(std::vector<uint8_t>* payload, ConnectionContext* context) {
    (void)context;
    return apply_http_method_spacing(payload);
}

bool DnsStrategy::process(std::vector<uint8_t>* payload, ConnectionContext* context) {
    (void)payload;
    (void)context;
    return false;
}

bool FakePacketStrategy::process(std::vector<uint8_t>* payload, ConnectionContext* context) {
    (void)payload;
    (void)context;
    // Windows GoodbyeDPI fake packets rely on invalid checksums / TTL tricks via WinDivert.
    // Android userspace sockets cannot inject such packets without root. This strategy is a
    // documented no-op; TLS/HTTP splitting is the supported equivalent.
    return false;
}

} // namespace luna
