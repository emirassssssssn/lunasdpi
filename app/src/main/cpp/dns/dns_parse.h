#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace luna {

struct DnsQuestion {
    std::string qname;
    uint16_t qtype = 0;
};

struct DnsRecord {
    std::string name;
    uint16_t type = 0;
    uint32_t ipv4 = 0;
};

struct DnsMessage {
    uint16_t id = 0;
    bool query = true;
    bool response = false;
    std::vector<DnsQuestion> questions;
    std::vector<DnsRecord> answers;
};

bool parse_dns(const uint8_t* data, size_t len, DnsMessage* out);
bool dns_make_noerror(const uint8_t* query, size_t len, std::vector<uint8_t>* out);
bool dns_make_servfail(const uint8_t* query, size_t len, std::vector<uint8_t>* out);
bool dns_make_a_reply(const uint8_t* query, size_t len, uint32_t ipv4, std::vector<uint8_t>* out);

constexpr uint16_t kDnsTypeA = 1;
constexpr uint16_t kDnsTypeAaaa = 28;
constexpr uint16_t kDnsTypeSvcb = 64;
constexpr uint16_t kDnsTypeHttps = 65;

} // namespace luna
