#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

#include "dns/hosts_table.h"

namespace luna {

enum class DpiMode : int {
    Automatic = 0,
    Basic = 1,
    Balanced = 2,
    Aggressive = 3,
    Custom = 4,
};

enum class DnsMode : int {
    Automatic = 0,
    System = 1,
    Custom = 2,
};

enum class Ipv6Mode : int {
    Off = 0,
    Block = 1,
};

struct StrategyFlags {
    bool tcp_fragmentation = false;
    int fragment_size = 2;
    bool http_host_case = false;
    bool http_spacing = false;
    bool http_method_spacing = false;
    bool persistent_fragment = false;
    bool block_quic = false;
};

inline StrategyFlags flags_for_mode(DpiMode mode, const StrategyFlags& custom) {
    StrategyFlags f;
    switch (mode) {
    case DpiMode::Basic:
        f.tcp_fragmentation = true;
        f.fragment_size = 2;
        f.http_host_case = false;
        f.http_spacing = false;
        f.http_method_spacing = false;
        f.persistent_fragment = false;
        return f;
    case DpiMode::Balanced:
    case DpiMode::Automatic:
        f.tcp_fragmentation = true;
        f.fragment_size = 2;
        f.http_host_case = true;
        f.http_spacing = false;
        f.http_method_spacing = false;
        f.persistent_fragment = false;
        return f;
    case DpiMode::Aggressive:
        f.tcp_fragmentation = true;
        f.fragment_size = 1;
        f.http_host_case = true;
        f.http_spacing = true;
        f.http_method_spacing = true;
        f.persistent_fragment = false;
        return f;
    case DpiMode::Custom:
        f = custom;
        if (f.fragment_size < 1) {
            f.fragment_size = 1;
        }
        if (f.fragment_size > 256) {
            f.fragment_size = 256;
        }
        return f;
    }
    return flags_for_mode(DpiMode::Balanced, custom);
}

inline StrategyFlags passthrough_flags() {
    StrategyFlags f;
    f.tcp_fragmentation = false;
    f.fragment_size = 2;
    f.http_host_case = false;
    f.http_spacing = false;
    f.http_method_spacing = false;
    f.persistent_fragment = false;
    f.block_quic = false;
    return f;
}

struct RuleConfig {
    std::string name;
    bool enabled = true;
    DpiMode strategy = DpiMode::Automatic;
    std::vector<std::string> domains;
    StrategyFlags custom;
};

struct EngineConfig {
    DpiMode mode = DpiMode::Automatic;
    StrategyFlags custom;
    DnsMode dns_mode = DnsMode::Automatic;
    std::vector<std::string> custom_dns;
    std::vector<std::string> system_dns;
    Ipv6Mode ipv6_mode = Ipv6Mode::Block;
    bool block_quic = true;
    int log_level = 2;
    int mtu = 1500;
    uint32_t tun_dns_ipv4 = 0;
    std::vector<RuleConfig> rules;
    std::vector<HostMapping> hosts;
};

inline int clamp_mtu(int mtu) {
    if (mtu < 576) {
        return 576;
    }
    if (mtu > 1500) {
        return 1500;
    }
    return mtu;
}

} // namespace luna
