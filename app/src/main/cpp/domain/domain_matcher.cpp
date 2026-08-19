#include "domain/domain_matcher.h"

#include <cctype>

namespace luna {
namespace {

bool valid_label(const std::string& label) {
    if (label.empty() || label.size() > 63) {
        return false;
    }
    if (label.front() == '-' || label.back() == '-') {
        return false;
    }
    for (const char c : label) {
        if (!std::isalnum(static_cast<unsigned char>(c)) && c != '-') {
            return false;
        }
    }
    return true;
}

} // namespace

std::string DomainMatcher::normalize(const std::string& domain) {
    std::string out;
    out.reserve(domain.size());
    for (const char c : domain) {
        if (c == ' ') {
            continue;
        }
        out.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
    }
    while (!out.empty() && out.back() == '.') {
        out.pop_back();
    }
    return out;
}

bool DomainMatcher::is_valid_pattern(const std::string& domain) {
    const std::string n = normalize(domain);
    if (n.empty() || n.size() > 253) {
        return false;
    }
    if (n.find("://") != std::string::npos || n.find('/') != std::string::npos ||
        n.find(':') != std::string::npos || n.find('?') != std::string::npos) {
        return false;
    }
    std::string rest = n;
    if (rest.size() >= 2 && rest[0] == '*' && rest[1] == '.') {
        rest = rest.substr(2);
        if (rest.empty() || rest.find('*') != std::string::npos) {
            return false;
        }
    } else if (rest.find('*') != std::string::npos) {
        return false;
    }
    size_t start = 0;
    int labels = 0;
    while (start <= rest.size()) {
        const size_t dot = rest.find('.', start);
        const std::string label = rest.substr(start, dot == std::string::npos ? std::string::npos : dot - start);
        if (!valid_label(label)) {
            return false;
        }
        labels++;
        if (dot == std::string::npos) {
            break;
        }
        start = dot + 1;
    }
    return labels >= 2;
}

void DomainMatcher::clear() {
    exact_.clear();
    wildcard_suffix_.clear();
    ipv4_to_domain_.clear();
    names_.clear();
}

void DomainMatcher::add_rule(int index, const std::string& name, const std::vector<std::string>& domains) {
    names_[index] = name;
    for (const auto& raw : domains) {
        const std::string n = normalize(raw);
        if (n.size() >= 2 && n[0] == '*' && n[1] == '.') {
            wildcard_suffix_[n.substr(2)] = index;
        } else if (!n.empty()) {
            exact_[n] = index;
        }
    }
}

DomainRuleMatch DomainMatcher::match_domain(const std::string& domain) const {
    DomainRuleMatch result;
    const std::string n = normalize(domain);
    if (n.empty()) {
        return result;
    }
    const auto exact = exact_.find(n);
    if (exact != exact_.end()) {
        result.rule_index = exact->second;
        const auto name = names_.find(exact->second);
        if (name != names_.end()) {
            result.rule_name = name->second;
        }
        return result;
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
            result.rule_index = wild->second;
            const auto name = names_.find(wild->second);
            if (name != names_.end()) {
                result.rule_name = name->second;
            }
            return result;
        }
        start = dot + 1;
    }
    return result;
}

void DomainMatcher::remember_ipv4(uint32_t ip, const std::string& domain) {
    if (ip == 0) {
        return;
    }
    ipv4_to_domain_[ip] = normalize(domain);
}

DomainRuleMatch DomainMatcher::match_ipv4(uint32_t ip) const {
    const auto it = ipv4_to_domain_.find(ip);
    if (it == ipv4_to_domain_.end()) {
        return {};
    }
    return match_domain(it->second);
}

std::string DomainMatcher::domain_for_ipv4(uint32_t ip) const {
    const auto it = ipv4_to_domain_.find(ip);
    return it == ipv4_to_domain_.end() ? std::string() : it->second;
}

} // namespace luna
