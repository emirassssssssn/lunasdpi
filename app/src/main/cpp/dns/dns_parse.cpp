#include "dns/dns_parse.h"

#include "packet/checksum.h"

namespace luna {
namespace {

bool read_name(const uint8_t* data, size_t len, size_t* offset, std::string* name, int depth) {
    if (data == nullptr || offset == nullptr || name == nullptr) {
        return false;
    }
    if (depth > 10) {
        return false;
    }
    size_t pos = *offset;
    bool jumped = false;
    size_t jump_end = 0;
    std::string assembled;
    int labels = 0;
    while (pos < len) {
        const uint8_t label_len = data[pos];
        if (label_len == 0) {
            pos++;
            if (!jumped) {
                *offset = pos;
            } else {
                *offset = jump_end;
            }
            *name = assembled;
            return true;
        }
        if ((label_len & 0xC0u) == 0xC0u) {
            if (pos + 1 >= len) {
                return false;
            }
            const uint16_t ptr = static_cast<uint16_t>(((label_len & 0x3Fu) << 8) | data[pos + 1]);
            if (!jumped) {
                jump_end = pos + 2;
                jumped = true;
            }
            if (ptr >= len) {
                return false;
            }
            pos = ptr;
            return read_name(data, len, &pos, name, depth + 1) &&
                   ((*offset = jumped ? jump_end : pos), true);
        }
        if ((label_len & 0xC0u) != 0) {
            return false;
        }
        pos++;
        if (label_len == 0 || pos + label_len > len || labels > 127) {
            return false;
        }
        if (!assembled.empty()) {
            assembled.push_back('.');
        }
        assembled.append(reinterpret_cast<const char*>(data + pos), label_len);
        pos += label_len;
        labels++;
    }
    return false;
}

} // namespace

bool parse_dns(const uint8_t* data, size_t len, DnsMessage* out) {
    if (data == nullptr || out == nullptr || len < 12) {
        return false;
    }
    DnsMessage msg;
    msg.id = read_u16(data);
    const uint16_t flags = read_u16(data + 2);
    msg.query = (flags & 0x8000u) == 0;
    msg.response = (flags & 0x8000u) != 0;
    const uint16_t qd = read_u16(data + 4);
    const uint16_t an = read_u16(data + 6);
    if (qd > 64 || an > 64) {
        return false;
    }
    size_t offset = 12;
    for (uint16_t i = 0; i < qd; ++i) {
        DnsQuestion q;
        if (!read_name(data, len, &offset, &q.qname, 0)) {
            return false;
        }
        if (offset + 4 > len) {
            return false;
        }
        q.qtype = read_u16(data + offset);
        offset += 4;
        msg.questions.push_back(std::move(q));
    }
    for (uint16_t i = 0; i < an; ++i) {
        DnsRecord rec;
        if (!read_name(data, len, &offset, &rec.name, 0)) {
            return false;
        }
        if (offset + 10 > len) {
            return false;
        }
        rec.type = read_u16(data + offset);
        const uint16_t rdlength = read_u16(data + offset + 8);
        offset += 10;
        if (offset + rdlength > len) {
            return false;
        }
        if (rec.type == 1 && rdlength == 4) {
            rec.ipv4 = read_u32(data + offset);
        }
        offset += rdlength;
        msg.answers.push_back(std::move(rec));
    }
    *out = std::move(msg);
    return true;
}

bool dns_make_noerror(const uint8_t* query, size_t len, std::vector<uint8_t>* out) {
    if (query == nullptr || out == nullptr || len < 12 || len > 4096) {
        return false;
    }
    out->assign(query, query + len);
    (*out)[2] = static_cast<uint8_t>((*out)[2] | 0x80u);
    (*out)[3] = static_cast<uint8_t>(((*out)[3] & 0x0Fu) | 0x80u);
    write_u16(out->data() + 6, 0);
    write_u16(out->data() + 8, 0);
    return true;
}

bool dns_make_servfail(const uint8_t* query, size_t len, std::vector<uint8_t>* out) {
    if (query == nullptr || out == nullptr || len < 12 || len > 4096) {
        return false;
    }
    out->assign(query, query + len);
    (*out)[2] = static_cast<uint8_t>((*out)[2] | 0x80u);
    (*out)[3] = static_cast<uint8_t>(((*out)[3] & 0xF0u) | 0x02u);
    write_u16(out->data() + 6, 0);
    write_u16(out->data() + 8, 0);
    write_u16(out->data() + 10, 0);
    return true;
}

bool dns_make_a_reply(const uint8_t* query, size_t len, uint32_t ipv4, std::vector<uint8_t>* out) {
    if (query == nullptr || out == nullptr || len < 12 || len > 4096) {
        return false;
    }
    const uint16_t qd = read_u16(query + 4);
    if (qd == 0 || qd > 16) {
        return false;
    }
    size_t offset = 12;
    for (uint16_t i = 0; i < qd; ++i) {
        std::string name;
        if (!read_name(query, len, &offset, &name, 0) || offset + 4 > len) {
            return false;
        }
        offset += 4;
    }
    out->assign(query, query + offset);
    (*out)[2] = static_cast<uint8_t>((*out)[2] | 0x80u);
    (*out)[3] = static_cast<uint8_t>(((*out)[3] & 0x0Fu) | 0x80u);
    write_u16(out->data() + 6, 1);
    write_u16(out->data() + 8, 0);
    write_u16(out->data() + 10, 0);
    const size_t rec = out->size();
    out->resize(rec + 16);
    uint8_t* p = out->data() + rec;
    p[0] = 0xC0;
    p[1] = 0x0C;
    write_u16(p + 2, kDnsTypeA);
    write_u16(p + 4, 1);
    write_u32(p + 6, 30);
    write_u16(p + 10, 4);
    write_u32(p + 12, ipv4);
    return true;
}

} // namespace luna
