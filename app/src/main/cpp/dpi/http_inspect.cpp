#include "dpi/http_inspect.h"

#include <cctype>
#include <cstring>

namespace luna {
namespace {

const char* kMethods[] = {"GET ", "POST ", "HEAD ", "PUT ", "DELETE ", "OPTIONS ", "PATCH ", "CONNECT "};

int find_ci(const std::vector<uint8_t>& p, const char* needle) {
    const size_t nlen = std::strlen(needle);
    if (nlen == 0 || p.size() < nlen) {
        return -1;
    }
    for (size_t i = 0; i + nlen <= p.size(); ++i) {
        bool ok = true;
        for (size_t j = 0; j < nlen; ++j) {
            const char a = static_cast<char>(std::tolower(p[i + j]));
            const char b = static_cast<char>(std::tolower(static_cast<unsigned char>(needle[j])));
            if (a != b) {
                ok = false;
                break;
            }
        }
        if (ok) {
            return static_cast<int>(i);
        }
    }
    return -1;
}

} // namespace

bool payload_looks_like_http(const uint8_t* data, size_t len) {
    if (data == nullptr || len < 4) {
        return false;
    }
    for (const char* method : kMethods) {
        const size_t n = std::strlen(method);
        if (len >= n && std::memcmp(data, method, n) == 0) {
            return true;
        }
    }
    return false;
}

bool extract_http_host(const uint8_t* data, size_t len, std::string* host) {
    if (host == nullptr || data == nullptr) {
        return false;
    }
    host->clear();
    std::vector<uint8_t> tmp(data, data + len);
    const int idx = find_ci(tmp, "\r\nhost:");
    if (idx < 0) {
        return false;
    }
    size_t pos = static_cast<size_t>(idx) + 7;
    while (pos < len && (tmp[pos] == ' ' || tmp[pos] == '\t')) {
        pos++;
    }
    size_t end = pos;
    while (end < len && tmp[end] != '\r' && tmp[end] != '\n') {
        end++;
    }
    if (end <= pos) {
        return false;
    }
    host->assign(reinterpret_cast<const char*>(data + pos), end - pos);
    while (!host->empty() && (host->back() == ' ' || host->back() == '\t')) {
        host->pop_back();
    }
    return !host->empty();
}

bool apply_http_host_case(std::vector<uint8_t>* payload) {
    if (payload == nullptr) {
        return false;
    }
    const int idx = find_ci(*payload, "\r\nhost:");
    if (idx < 0 || static_cast<size_t>(idx) + 6 >= payload->size()) {
        return false;
    }
    // Mix the header name: hOsT
    (*payload)[idx + 2] = 'h';
    (*payload)[idx + 3] = 'O';
    (*payload)[idx + 4] = 's';
    (*payload)[idx + 5] = 'T';
    return true;
}

bool apply_http_spacing(std::vector<uint8_t>* payload) {
    if (payload == nullptr) {
        return false;
    }
    const int idx = find_ci(*payload, "\r\nhost:");
    if (idx < 0) {
        return false;
    }
    const size_t colon = static_cast<size_t>(idx) + 6;
    if (colon >= payload->size() || (*payload)[colon] != ':') {
        return false;
    }
    payload->insert(payload->begin() + static_cast<std::ptrdiff_t>(colon + 1), ' ');
    return true;
}

bool apply_http_method_spacing(std::vector<uint8_t>* payload) {
    if (payload == nullptr || payload->size() < 5) {
        return false;
    }
    size_t space = 0;
    while (space < payload->size() && (*payload)[space] != ' ') {
        space++;
    }
    if (space == 0 || space >= payload->size()) {
        return false;
    }
    payload->insert(payload->begin() + static_cast<std::ptrdiff_t>(space), ' ');
    return true;
}

} // namespace luna
