#include "dns/hosts_table.h"

#include "domain/domain_matcher.h"

namespace luna {

void HostsTable::clear() {
    exact_.clear();
    wildcard_suffix_.clear();
}

void HostsTable::replace(std::vector<HostMapping> mappings) {
    clear();
    for (auto& item : mappings) {
        const std::string n = DomainMatcher::normalize(item.host);
        if (!DomainMatcher::is_valid_pattern(n)) {
            continue;
        }
        if (n.size() >= 2 && n[0] == '*' && n[1] == '.') {
            wildcard_suffix_[n.substr(2)] = item.ipv4;
        } else {
            exact_[n] = item.ipv4;
        }
    }
}

bool HostsTable::lookup(const std::string& qname, uint32_t* ipv4) const {
    if (ipv4 == nullptr) {
        return false;
    }
    const std::string n = DomainMatcher::normalize(qname);
    if (n.empty()) {
        return false;
    }
    const auto exact = exact_.find(n);
    if (exact != exact_.end()) {
        *ipv4 = exact->second;
        return true;
    }
    size_t start = 0;
    while (true) {
        const size_t dot = n.find('.', start);
        if (dot == std::string::npos) {
            break;
        }
        const std::string suffix = n.substr(dot + 1);
        const auto wild = wildcard_suffix_.find(suffix);
        if (wild != wildcard_suffix_.end()) {
            *ipv4 = wild->second;
            return true;
        }
        start = dot + 1;
    }
    return false;
}

size_t HostsTable::size() const {
    return exact_.size() + wildcard_suffix_.size();
}

} // namespace luna
