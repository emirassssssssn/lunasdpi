cmake_minimum_required(VERSION 3.22.1)
project(luna_engine)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(luna_engine SHARED
        jni/native_engine_jni.cpp
        core/engine.cpp
        packet/checksum.cpp
        packet/ipv4.cpp
        packet/ipv6.cpp
        packet/tcp.cpp
        packet/udp.cpp
        packet/reassembly.cpp
        tun/tun_io.cpp
        session/session_table.cpp
        tcp/tcp_relay.cpp
        udp/udp_nat.cpp
        dns/dns_parse.cpp
        dns/dns_engine.cpp
        dns/hosts_table.cpp
        dns/public_resolver.cpp
        dpi/tls_sni.cpp
        dpi/http_inspect.cpp
        dpi/dpi_engine.cpp
        strategies/strategies.cpp
        domain/domain_matcher.cpp
        tests/self_test.cpp
)

target_include_directories(luna_engine PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)
target_link_libraries(luna_engine ${log-lib})
