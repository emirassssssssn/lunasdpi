#include "dpi/dpi_engine.h"

#include "dpi/http_inspect.h"
#include "dpi/tls_sni.h"
#include "strategies/strategies.h"

namespace luna {

std::vector<DpiChunk> DpiEngine::process(ConnectionContext* ctx, const uint8_t* data, size_t len,
                                         bool* modified) {
    std::vector<DpiChunk> out;
    if (modified != nullptr) {
        *modified = false;
    }
    if (ctx == nullptr || data == nullptr || len == 0) {
        return out;
    }

    std::vector<uint8_t> payload(data, data + len);
    bool changed = false;

    if (!ctx->identified) {
        if (payload_looks_like_tls_client_hello(data, len)) {
            ctx->proto = AppProto::Tls;
            std::string sni;
            size_t sni_off = 0;
            if (extract_sni(data, len, &sni, &sni_off) && !sni.empty()) {
                ctx->hostname = sni;
            }
        } else if (payload_looks_like_http(data, len)) {
            ctx->proto = AppProto::Http;
            std::string host;
            if (extract_http_host(data, len, &host)) {
                ctx->hostname = host;
            }
        } else {
            ctx->proto = AppProto::Other;
        }
        ctx->identified = true;
    }

    if (ctx->proto == AppProto::Http && !ctx->first_data_done) {
        HttpHostCaseStrategy host_case;
        HttpSpacingStrategy spacing;
        HttpMethodUriStrategy method;
        if (ctx->flags.http_host_case && host_case.process(&payload, ctx)) {
            changed = true;
        }
        if (ctx->flags.http_spacing && spacing.process(&payload, ctx)) {
            changed = true;
        }
        if (ctx->flags.http_method_spacing && method.process(&payload, ctx)) {
            changed = true;
        }
    }

    const bool should_fragment =
        ctx->flags.tcp_fragmentation && (!ctx->first_data_done || ctx->flags.persistent_fragment);
    TcpFragmentationStrategy frag;
    FakePacketStrategy fake;
    fake.process(&payload, ctx);

    if (should_fragment && payload.size() > 1) {
        int split_at = ctx->flags.fragment_size;
        if (split_at < 1) {
            split_at = 1;
        }
        if (static_cast<size_t>(split_at) >= payload.size()) {
            split_at = 1;
        }
        std::vector<uint8_t> copy = payload;
        const bool tls_hello = ctx->proto == AppProto::Tls && !ctx->first_data_done;
        if (frag.split_desync(copy, split_at, tls_hello, &out)) {
            changed = true;
            ctx->fragments_sent++;
        } else {
            DpiChunk chunk;
            chunk.bytes = std::move(payload);
            out.push_back(std::move(chunk));
        }
    } else {
        DpiChunk chunk;
        chunk.bytes = std::move(payload);
        out.push_back(std::move(chunk));
    }

    ctx->first_data_done = true;
    if (modified != nullptr) {
        *modified = changed;
    }
    return out;
}

} // namespace luna
