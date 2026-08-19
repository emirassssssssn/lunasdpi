#include "session/session_table.h"

namespace luna {

TcpSession* SessionTable::find_tcp(const FiveTuple& key) {
    const auto it = tcp_.find(key);
    return it == tcp_.end() ? nullptr : it->second.get();
}

TcpSession* SessionTable::insert_tcp(const FiveTuple& key, TcpSession session) {
    auto owned = std::unique_ptr<TcpSession>(new TcpSession(std::move(session)));
    TcpSession* ptr = owned.get();
    tcp_[key] = std::move(owned);
    return ptr;
}

void SessionTable::erase_tcp(const FiveTuple& key) {
    const auto it = tcp_.find(key);
    if (it == tcp_.end()) {
        return;
    }
    if (it->second && it->second->sock_fd >= 0) {
        tcp_fd_.erase(it->second->sock_fd);
    }
    tcp_.erase(it);
}

TcpSession* SessionTable::find_tcp_by_fd(int fd) {
    const auto it = tcp_fd_.find(fd);
    if (it == tcp_fd_.end()) {
        return nullptr;
    }
    return find_tcp(it->second);
}

void SessionTable::bind_tcp_fd(int fd, const FiveTuple& key) {
    tcp_fd_[fd] = key;
}

void SessionTable::unbind_tcp_fd(int fd) {
    tcp_fd_.erase(fd);
}

UdpSession* SessionTable::find_udp(const FiveTuple& key) {
    const auto it = udp_.find(key);
    return it == udp_.end() ? nullptr : it->second.get();
}

UdpSession* SessionTable::insert_udp(const FiveTuple& key, UdpSession session) {
    auto owned = std::unique_ptr<UdpSession>(new UdpSession(std::move(session)));
    UdpSession* ptr = owned.get();
    udp_[key] = std::move(owned);
    return ptr;
}

void SessionTable::erase_udp(const FiveTuple& key) {
    const auto it = udp_.find(key);
    if (it == udp_.end()) {
        return;
    }
    if (it->second && it->second->sock_fd >= 0) {
        udp_fd_.erase(it->second->sock_fd);
    }
    udp_.erase(it);
}

UdpSession* SessionTable::find_udp_by_fd(int fd) {
    const auto it = udp_fd_.find(fd);
    if (it == udp_fd_.end()) {
        return nullptr;
    }
    return find_udp(it->second);
}

void SessionTable::bind_udp_fd(int fd, const FiveTuple& key) {
    udp_fd_[fd] = key;
}

void SessionTable::unbind_udp_fd(int fd) {
    udp_fd_.erase(fd);
}

std::vector<FiveTuple> SessionTable::expire_tcp(uint64_t now_ms, uint64_t idle_ms) {
    std::vector<FiveTuple> dead;
    for (const auto& entry : tcp_) {
        if (entry.second && now_ms - entry.second->last_activity_ms > idle_ms) {
            dead.push_back(entry.first);
        }
    }
    return dead;
}

std::vector<FiveTuple> SessionTable::expire_udp(uint64_t now_ms, uint64_t idle_ms) {
    std::vector<FiveTuple> dead;
    for (const auto& entry : udp_) {
        if (entry.second && now_ms - entry.second->last_activity_ms > idle_ms) {
            dead.push_back(entry.first);
        }
    }
    return dead;
}

std::vector<FiveTuple> SessionTable::all_tcp() const {
    std::vector<FiveTuple> keys;
    keys.reserve(tcp_.size());
    for (const auto& entry : tcp_) {
        keys.push_back(entry.first);
    }
    return keys;
}

std::vector<FiveTuple> SessionTable::all_udp() const {
    std::vector<FiveTuple> keys;
    keys.reserve(udp_.size());
    for (const auto& entry : udp_) {
        keys.push_back(entry.first);
    }
    return keys;
}

int SessionTable::tcp_count() const {
    return static_cast<int>(tcp_.size());
}

int SessionTable::udp_count() const {
    return static_cast<int>(udp_.size());
}

void SessionTable::clear() {
    tcp_.clear();
    udp_.clear();
    tcp_fd_.clear();
    udp_fd_.clear();
}

} // namespace luna
