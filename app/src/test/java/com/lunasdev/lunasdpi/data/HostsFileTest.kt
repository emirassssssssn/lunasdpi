package com.lunasdev.lunasdpi.data

import com.google.common.truth.Truth.assertThat
import com.lunasdev.lunasdpi.plugin.HostsStore
import com.lunasdev.lunasdpi.plugin.PluginPermission
import org.junit.Test

class HostsFileTest {
    @Test
    fun parsesClassicGrowtopiaLines() {
        val parsed = HostsFile.parse(
            """
            192.168.1.10 growtopia1.com
            192.168.1.10 growtopia2.com
            """.trimIndent(),
        )
        assertThat(parsed.errors).isEmpty()
        assertThat(parsed.entries).containsExactly(
            HostEntry("growtopia1.com", "192.168.1.10"),
            HostEntry("growtopia2.com", "192.168.1.10"),
        ).inOrder()
    }

    @Test
    fun skipsCommentsAndIpv6() {
        val parsed = HostsFile.parse(
            """
            # comment
            10.0.0.2 growtopia1.com
            ::1 growtopia2.com
            10.0.0.2 growtopia1.com alias.example.com
            """.trimIndent(),
        )
        assertThat(parsed.errors).isEmpty()
        assertThat(parsed.entries.map { it.host }).containsExactly("growtopia1.com", "alias.example.com").inOrder()
    }

    @Test
    fun rejectsTunMulticastAndReserved() {
        val parsed = HostsFile.parse(
            """
            10.7.0.1 growtopia1.com
            224.0.0.1 growtopia2.com
            0.0.0.0 blocked.example
            169.254.1.1 link.example.com
            """.trimIndent(),
        )
        assertThat(parsed.entries).isEmpty()
        assertThat(parsed.errors).hasSize(4)
    }

    @Test
    fun allowsLoopbackAndWildcard() {
        val parsed = HostsFile.parse("127.0.0.1 growtopia1.com\n10.0.0.5 *.priv.example")
        assertThat(parsed.errors).isEmpty()
        assertThat(parsed.entries).containsExactly(
            HostEntry("growtopia1.com", "127.0.0.1"),
            HostEntry("*.priv.example", "10.0.0.5"),
        ).inOrder()
    }

    @Test
    fun placeholderXxIsSkipped() {
        val parsed = HostsFile.parse(
            """
            xx.xx.xx.xx growtopia1.com
            xx.xx.xx.xx growtopia2.com
            """.trimIndent(),
        )
        assertThat(parsed.entries).isEmpty()
        assertThat(parsed.errors).hasSize(2)
    }

    @Test
    fun laterPluginWinsSameHostname() {
        val store = HostsStore()
        store.replacePlugin("a", listOf(HostEntry("growtopia1.com", "10.0.0.1")))
        store.replacePlugin("b", listOf(HostEntry("growtopia1.com", "10.0.0.2")))
        assertThat(store.current()).containsExactly(HostEntry("growtopia1.com", "10.0.0.2"))
        store.clearPlugin("b")
        assertThat(store.current()).containsExactly(HostEntry("growtopia1.com", "10.0.0.1"))
    }

    @Test
    fun hostsWritePermissionParses() {
        assertThat(PluginPermission.fromManifest("hosts.write")).isEqualTo(PluginPermission.HOSTS_WRITE)
        assertThat(PluginPermission.fromManifest("hosts")).isEqualTo(PluginPermission.HOSTS_WRITE)
        assertThat(PluginPermission.HOSTS_WRITE.manifestKey()).isEqualTo("hosts.write")
    }
}
