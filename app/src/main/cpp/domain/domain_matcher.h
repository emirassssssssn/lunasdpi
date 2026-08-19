#pragma once

#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

namespace luna {

struct DomainRuleMatch {
    int rule_index = -1;
    std::string rule_name;
};

class DomainMatcher {
public:
    void clear();
    void add_rule(int index, const std::string& name, const std::vector<std::string>& domains);
    DomainRuleMatch match_domain(const std::string& domain) const;
    void remember_ipv4(uint32_t ip, const std::string& domain);
    DomainRuleMatch match_ipv4(uint32_t ip) const;
    std::string domain_for_ipv4(uint32_t ip) const;

    static std::string normalize(const std::string& domain);
    static bool is_valid_pattern(const std::string& domain);

private:
    std::unordered_map<std::string, int> exact_;
    std::unordered_map<std::string, int> wildcard_suffix_;
    std::unordered_map<uint32_t, std::string> ipv4_to_domain_;
    std::unordered_map<int, std::string> names_;
};

} // namespace luna
