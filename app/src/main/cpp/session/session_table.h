#pragma once

#include "session/connection.h"

#include <cstdint>
#include <memory>
#include <unordered_map>
#include <vector>

namespace luna {

class SessionTable {
public:
    TcpSession* find_tcp(const FiveTuple& key);
    TcpSession* insert_tcp(const FiveTuple& key, TcpSession session);
    void erase_tcp(const FiveTuple& key);

    TcpSession* find_tcp_by_fd(int fd);
    void bind_tcp_fd(int fd, const FiveTuple& key);
    void unbind_tcp_fd(int fd);

    UdpSession* find_udp(const FiveTuple& key);
    UdpSession* insert_udp(const FiveTuple& key, UdpSession session);
    void erase_udp(const FiveTuple& key);

    UdpSession* find_udp_by_fd(int fd);
    void bind_udp_fd(int fd, const FiveTuple& key);
    void unbind_udp_fd(int fd);

    std::vector<FiveTuple> expire_tcp(uint64_t now_ms, uint64_t idle_ms);
    std::vector<FiveTuple> expire_udp(uint64_t now_ms, uint64_t idle_ms);

    std::vector<FiveTuple> all_tcp() const;
    std::vector<FiveTuple> all_udp() const;
    int tcp_count() const;
    int udp_count() const;
    void clear();

private:
    std::unordered_map<FiveTuple, std::unique_ptr<TcpSession>, FiveTupleHash> tcp_;
    std::unordered_map<FiveTuple, std::unique_ptr<UdpSession>, FiveTupleHash> udp_;
    std::unordered_map<int, FiveTuple> tcp_fd_;
    std::unordered_map<int, FiveTuple> udp_fd_;
};

} // namespace luna
