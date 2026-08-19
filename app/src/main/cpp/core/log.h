#pragma once

#include <cstdint>
#include <cstdio>

#ifdef __ANDROID__
#include <android/log.h>
#define LUNA_LOG_TAG "LunasDPI"
#define LOGD(...)                                                                \
    do {                                                                         \
        if (luna::g_log_level >= 3)                                              \
            __android_log_print(ANDROID_LOG_DEBUG, LUNA_LOG_TAG, __VA_ARGS__);   \
    } while (0)
#define LOGI(...)                                                                \
    do {                                                                         \
        if (luna::g_log_level >= 2)                                              \
            __android_log_print(ANDROID_LOG_INFO, LUNA_LOG_TAG, __VA_ARGS__);    \
    } while (0)
#define LOGW(...)                                                                \
    do {                                                                         \
        if (luna::g_log_level >= 1)                                              \
            __android_log_print(ANDROID_LOG_WARN, LUNA_LOG_TAG, __VA_ARGS__);    \
    } while (0)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LUNA_LOG_TAG, __VA_ARGS__)
#else
#define LOGD(...)                                                                \
    do {                                                                         \
        if (luna::g_log_level >= 3)                                              \
            std::fprintf(stderr, __VA_ARGS__);                                   \
    } while (0)
#define LOGI(...)                                                                \
    do {                                                                         \
        if (luna::g_log_level >= 2)                                              \
            std::fprintf(stderr, __VA_ARGS__);                                   \
    } while (0)
#define LOGW(...)                                                                \
    do {                                                                         \
        if (luna::g_log_level >= 1)                                               \
            std::fprintf(stderr, __VA_ARGS__);                                   \
    } while (0)
#define LOGE(...) std::fprintf(stderr, __VA_ARGS__)
#endif

namespace luna {
inline int g_log_level = 2;
}
