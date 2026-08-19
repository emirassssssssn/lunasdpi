#pragma once

#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

namespace luna {

struct HostMapping {
    std::string host;
    uint32_t ipv4 = 0;
};

class HostsTable {
public:
    void clear();
    void replace(std::vector<HostMapping> mappings);
    bool lookup(const std::string& qname, uint32_t* ipv4) const;
    size_t size() const;

private:
    std::unordered_map<std::string, uint32_t> exact_;
    std::unordered_map<std::string, uint32_t> wildcard_suffix_;
};

} // namespace luna
