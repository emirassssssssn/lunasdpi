#pragma once

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>

namespace luna {

struct EngineStats {
    std::atomic<uint64_t> packets_processed{0};
    std::atomic<uint64_t> packets_modified{0};
    std::atomic<uint64_t> packets_dropped{0};
    std::atomic<uint64_t> bytes_in{0};
    std::atomic<uint64_t> bytes_out{0};
    std::atomic<uint64_t> dns_queries{0};
    std::atomic<int> active_tcp{0};
    std::atomic<int> active_udp{0};
    std::atomic<int> native_errors{0};
    std::atomic<int> engine_alive{0};

    mutable std::mutex error_mu;
    std::string last_error;
    std::string current_strategy = "automatic";

    void set_error(const std::string& message) {
        native_errors.fetch_add(1, std::memory_order_relaxed);
        std::lock_guard<std::mutex> lock(error_mu);
        last_error = message;
    }

    std::string copy_error() const {
        std::lock_guard<std::mutex> lock(error_mu);
        return last_error;
    }
};

} // namespace luna
