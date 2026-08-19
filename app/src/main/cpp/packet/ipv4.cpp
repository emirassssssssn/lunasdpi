#include "packet/ipv4.h"

// Header-only IPv4 parser; this unit exists so CMake and host tests share a stable object list.
namespace luna {
int ipv4_parser_version() {
    return 1;
}
}
