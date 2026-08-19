#include "core/engine.h"
#include "core/log.h"
#include "dns/dns_engine.h"
#include "tests/self_test.h"

#include <jni.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace {

std::mutex g_mu;
luna::Engine g_engine;
JavaVM* g_vm = nullptr;
jobject g_engine_obj = nullptr;
jmethodID g_protect_method = nullptr;
jmethodID g_dns_method = nullptr;

std::string jstring_to_std(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars ? chars : "";
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

std::vector<std::string> jstring_array(JNIEnv* env, jobjectArray array) {
    std::vector<std::string> out;
    if (array == nullptr) {
        return out;
    }
    const jsize n = env->GetArrayLength(array);
    out.reserve(static_cast<size_t>(n));
    for (jsize i = 0; i < n; ++i) {
        auto* item = reinterpret_cast<jstring>(env->GetObjectArrayElement(array, i));
        out.push_back(jstring_to_std(env, item));
        env->DeleteLocalRef(item);
    }
    return out;
}

std::vector<jint> jint_array(JNIEnv* env, jintArray array) {
    std::vector<jint> out;
    if (array == nullptr) {
        return out;
    }
    const jsize n = env->GetArrayLength(array);
    out.resize(static_cast<size_t>(n));
    env->GetIntArrayRegion(array, 0, n, out.data());
    return out;
}

std::vector<char> jbool_array(JNIEnv* env, jbooleanArray array) {
    std::vector<char> out;
    if (array == nullptr) {
        return out;
    }
    const jsize n = env->GetArrayLength(array);
    std::vector<jboolean> tmp(static_cast<size_t>(n));
    env->GetBooleanArrayRegion(array, 0, n, tmp.data());
    out.assign(tmp.begin(), tmp.end());
    return out;
}

bool protect_socket(void* /*user*/, int fd) {
    if (g_vm == nullptr || g_engine_obj == nullptr || g_protect_method == nullptr) {
        return false;
    }
    JNIEnv* env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != 0) {
            return false;
        }
        attached = true;
    }
    const jboolean ok = env->CallBooleanMethod(g_engine_obj, g_protect_method, fd);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        if (attached) {
            // Keep attached for the engine thread lifetime; only detach if we attached for an unexpected thread.
        }
        return false;
    }
    (void)attached;
    return ok == JNI_TRUE;
}

bool resolve_dns(void* /*user*/, const uint8_t* query, int query_len, uint8_t* out, int out_cap, int* out_len) {
    if (g_vm == nullptr || g_engine_obj == nullptr || g_dns_method == nullptr || query == nullptr ||
        query_len < 12 || out == nullptr || out_len == nullptr || out_cap < 12) {
        return false;
    }
    JNIEnv* env = nullptr;
    if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != 0) {
            return false;
        }
    }
    jbyteArray q = env->NewByteArray(query_len);
    if (q == nullptr) {
        return false;
    }
    env->SetByteArrayRegion(q, 0, query_len, reinterpret_cast<const jbyte*>(query));
    auto* result = reinterpret_cast<jbyteArray>(env->CallObjectMethod(g_engine_obj, g_dns_method, q));
    env->DeleteLocalRef(q);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }
    if (result == nullptr) {
        return false;
    }
    const jsize n = env->GetArrayLength(result);
    if (n < 12 || n > out_cap) {
        env->DeleteLocalRef(result);
        return false;
    }
    env->GetByteArrayRegion(result, 0, n, reinterpret_cast<jbyte*>(out));
    env->DeleteLocalRef(result);
    *out_len = static_cast<int>(n);
    return true;
}

luna::EngineConfig read_config(JNIEnv* env, jobject config) {
    luna::EngineConfig out;
    if (config == nullptr) {
        return out;
    }
    jclass cls = env->GetObjectClass(config);
    auto gi = [&](const char* name) {
        return env->GetIntField(config, env->GetFieldID(cls, name, "I"));
    };
    auto gb = [&](const char* name) {
        return env->GetBooleanField(config, env->GetFieldID(cls, name, "Z")) == JNI_TRUE;
    };
    auto gs = [&](const char* name) {
        auto* field = reinterpret_cast<jstring>(env->GetObjectField(config, env->GetFieldID(cls, name, "Ljava/lang/String;")));
        std::string value = jstring_to_std(env, field);
        env->DeleteLocalRef(field);
        return value;
    };
    auto ga = [&](const char* name) {
        auto* field = reinterpret_cast<jobjectArray>(
            env->GetObjectField(config, env->GetFieldID(cls, name, "[Ljava/lang/String;")));
        auto value = jstring_array(env, field);
        env->DeleteLocalRef(field);
        return value;
    };

    out.mode = static_cast<luna::DpiMode>(gi("mode"));
    out.custom.tcp_fragmentation = gb("tcpFragmentation");
    out.custom.fragment_size = gi("fragmentSize");
    out.custom.http_host_case = gb("httpHostCase");
    out.custom.http_spacing = gb("httpSpacing");
    out.custom.http_method_spacing = gb("httpMethodSpacing");
    out.custom.persistent_fragment = gb("persistentFragment");
    out.block_quic = gb("blockQuic");
    out.dns_mode = static_cast<luna::DnsMode>(gi("dnsMode"));
    out.custom_dns = ga("customDns");
    out.system_dns = ga("systemDns");
    out.ipv6_mode = static_cast<luna::Ipv6Mode>(gi("ipv6Mode"));
    out.log_level = gi("logLevel");
    out.mtu = gi("mtu");
    uint32_t tun_dns = 0;
    luna::parse_ipv4_string(gs("tunDnsIpv4"), &tun_dns);
    out.tun_dns_ipv4 = tun_dns;

    auto rule_names = ga("ruleNames");
    auto rule_domains = ga("ruleDomains");
    auto* enabled_arr =
        reinterpret_cast<jbooleanArray>(env->GetObjectField(config, env->GetFieldID(cls, "ruleEnabled", "[Z")));
    auto* strat_arr = reinterpret_cast<jintArray>(env->GetObjectField(config, env->GetFieldID(cls, "ruleStrategies", "[I")));
    auto* frag_arr = reinterpret_cast<jbooleanArray>(env->GetObjectField(config, env->GetFieldID(cls, "ruleFrag", "[Z")));
    auto* frag_size_arr =
        reinterpret_cast<jintArray>(env->GetObjectField(config, env->GetFieldID(cls, "ruleFragSize", "[I")));
    auto* host_arr =
        reinterpret_cast<jbooleanArray>(env->GetObjectField(config, env->GetFieldID(cls, "ruleHostCase", "[Z")));
    auto* space_arr =
        reinterpret_cast<jbooleanArray>(env->GetObjectField(config, env->GetFieldID(cls, "ruleSpacing", "[Z")));
    auto* method_arr =
        reinterpret_cast<jbooleanArray>(env->GetObjectField(config, env->GetFieldID(cls, "ruleMethodSpacing", "[Z")));
    auto enabled = jbool_array(env, enabled_arr);
    auto strat = jint_array(env, strat_arr);
    auto frag = jbool_array(env, frag_arr);
    auto frag_size = jint_array(env, frag_size_arr);
    auto host = jbool_array(env, host_arr);
    auto space = jbool_array(env, space_arr);
    auto method = jbool_array(env, method_arr);

    const size_t n = rule_names.size();
    out.rules.reserve(n);
    for (size_t i = 0; i < n; ++i) {
        luna::RuleConfig rule;
        rule.name = rule_names[i];
        rule.enabled = i < enabled.size() && enabled[i];
        rule.strategy = i < strat.size() ? static_cast<luna::DpiMode>(strat[i]) : luna::DpiMode::Automatic;
        if (i < rule_domains.size()) {
            std::string acc;
            for (char c : rule_domains[i]) {
                if (c == '\n') {
                    if (!acc.empty()) {
                        rule.domains.push_back(acc);
                        acc.clear();
                    }
                } else {
                    acc.push_back(c);
                }
            }
            if (!acc.empty()) {
                rule.domains.push_back(acc);
            }
        }
        rule.custom.tcp_fragmentation = i < frag.size() && frag[i];
        rule.custom.fragment_size = i < frag_size.size() ? frag_size[i] : 2;
        rule.custom.http_host_case = i < host.size() && host[i];
        rule.custom.http_spacing = i < space.size() && space[i];
        rule.custom.http_method_spacing = i < method.size() && method[i];
        out.rules.push_back(std::move(rule));
    }

    auto host_names = ga("hostNames");
    auto host_ips = ga("hostIps");
    const size_t host_n = host_names.size() < host_ips.size() ? host_names.size() : host_ips.size();
    out.hosts.reserve(host_n);
    for (size_t i = 0; i < host_n; ++i) {
        uint32_t ip = 0;
        if (!luna::parse_ipv4_string(host_ips[i], &ip) || ip == 0) {
            continue;
        }
        luna::HostMapping mapped;
        mapped.host = host_names[i];
        mapped.ipv4 = ip;
        out.hosts.push_back(std::move(mapped));
    }

    env->DeleteLocalRef(cls);
    return out;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lunasdev_lunasdpi_vpn_NativeEngine_nativeStart(JNIEnv* env, jobject thiz, jint tun_fd, jobject config) {
    std::lock_guard<std::mutex> lock(g_mu);
    if (g_engine_obj != nullptr) {
        env->DeleteGlobalRef(g_engine_obj);
        g_engine_obj = nullptr;
    }
    g_engine_obj = env->NewGlobalRef(thiz);
    jclass cls = env->GetObjectClass(thiz);
    g_protect_method = env->GetMethodID(cls, "protectSocket", "(I)Z");
    g_dns_method = env->GetMethodID(cls, "resolveDns", "([B)[B");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        g_dns_method = nullptr;
    }
    env->DeleteLocalRef(cls);
    if (g_protect_method == nullptr) {
        return JNI_FALSE;
    }
    luna::EngineConfig cfg = read_config(env, config);
    const bool ok = g_engine.start(tun_fd, std::move(cfg), protect_socket, nullptr, resolve_dns, nullptr);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_lunasdev_lunasdpi_vpn_NativeEngine_nativeUpdateRules(JNIEnv* env, jobject, jobject config) {
    luna::EngineConfig cfg = read_config(env, config);
    g_engine.update_rules(std::move(cfg.rules));
}

extern "C" JNIEXPORT void JNICALL
Java_com_lunasdev_lunasdpi_vpn_NativeEngine_nativeUpdateHosts(JNIEnv* env, jobject, jobjectArray names,
                                                              jobjectArray ips) {
    auto host_names = jstring_array(env, names);
    auto host_ips = jstring_array(env, ips);
    const size_t n = host_names.size() < host_ips.size() ? host_names.size() : host_ips.size();
    std::vector<luna::HostMapping> mappings;
    mappings.reserve(n);
    for (size_t i = 0; i < n; ++i) {
        uint32_t ip = 0;
        if (!luna::parse_ipv4_string(host_ips[i], &ip) || ip == 0) {
            continue;
        }
        luna::HostMapping mapped;
        mapped.host = host_names[i];
        mapped.ipv4 = ip;
        mappings.push_back(std::move(mapped));
    }
    g_engine.update_hosts(std::move(mappings));
}

extern "C" JNIEXPORT void JNICALL Java_com_lunasdev_lunasdpi_vpn_NativeEngine_nativeStop(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mu);
    g_engine.stop();
    if (g_engine_obj != nullptr) {
        env->DeleteGlobalRef(g_engine_obj);
        g_engine_obj = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_lunasdev_lunasdpi_vpn_NativeEngine_nativeNetworkChanged(JNIEnv*, jobject) {
    g_engine.on_network_changed();
}

extern "C" JNIEXPORT jobject JNICALL Java_com_lunasdev_lunasdpi_vpn_NativeEngine_nativeGetStats(JNIEnv* env, jobject) {
    uint64_t processed = 0;
    uint64_t modified = 0;
    uint64_t dropped = 0;
    uint64_t bytes_in = 0;
    uint64_t bytes_out = 0;
    uint64_t dns = 0;
    int tcp = 0;
    int udp = 0;
    int errors = 0;
    int alive = 0;
    char last_error[256];
    char strategy[64];
    last_error[0] = 0;
    strategy[0] = 0;
    g_engine.snapshot_stats(&processed, &modified, &dropped, &bytes_in, &bytes_out, &dns, &tcp, &udp, &errors,
                            &alive, last_error, 256, strategy, 64);
    jclass cls = env->FindClass("com/lunasdev/lunasdpi/vpn/EngineStats");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(JJJJJJIIILjava/lang/String;Ljava/lang/String;Z)V");
    jstring err = env->NewStringUTF(last_error);
    jstring strat = env->NewStringUTF(strategy);
    jobject obj = env->NewObject(cls, ctor, static_cast<jlong>(processed), static_cast<jlong>(modified),
                                 static_cast<jlong>(dropped), static_cast<jlong>(bytes_in),
                                 static_cast<jlong>(bytes_out), static_cast<jlong>(dns), tcp, udp, errors, err, strat,
                                 alive != 0);
    env->DeleteLocalRef(cls);
    return obj;
}

extern "C" JNIEXPORT jstring JNICALL Java_com_lunasdev_lunasdpi_vpn_NativeEngine_nativeSelfTest(JNIEnv* env, jclass) {
    const std::string result = luna::run_self_tests();
    return env->NewStringUTF(result.c_str());
}
