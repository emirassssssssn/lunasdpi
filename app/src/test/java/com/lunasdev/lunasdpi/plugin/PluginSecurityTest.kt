package com.lunasdev.lunasdpi.plugin

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class PluginSecurityTest {
    @Test
    fun rejectsZipSlip() {
        val zip = zipOf("../evil.lua" to "print(1)")
        assertThrows(IllegalStateException::class.java) {
            PluginSecurity.zipEntries(zip)
        }
    }

    @Test
    fun rejectsLuaBytecode() {
        val payload = byteArrayOf(0x1b, 'L'.code.toByte(), 'u'.code.toByte(), 'a'.code.toByte(), 0x53)
        val zip = zipOf("main.lua" to payload)
        assertThrows(IllegalStateException::class.java) {
            PluginSecurity.zipEntries(zip)
        }
    }

    @Test
    fun acceptsHostsWritePermission() {
        val files = setOf("manifest.json", "main.lua")
        val raw = """
            {"id":"community.hosts.file","name":"Hosts","author":"A","version":"1.0.0","main":"main.lua","permissions":["hosts.write"]}
        """.trimIndent()
        val manifest = PluginManifestParser.parse(raw, files)
        assertThat(manifest.permissions).contains(PluginPermission.HOSTS_WRITE)
    }

    @Test
    fun acceptsApiLevelOneAndTwo() {
        val files = setOf("manifest.json", "main.lua")
        val one = PluginManifestParser.parse(
            """{"id":"community.demo.one","name":"Demo","author":"A","version":"1.0.0","api_level":1,"main":"main.lua"}""",
            files,
        )
        val two = PluginManifestParser.parse(
            """{"id":"community.demo.two","name":"Demo","author":"A","version":"1.0.0","api_level":2,"main":"main.lua"}""",
            files,
        )
        assertThat(one.apiLevel).isEqualTo(1)
        assertThat(two.apiLevel).isEqualTo(2)
        assertThrows(IllegalStateException::class.java) {
            PluginManifestParser.parse(
                """{"id":"community.demo.three","name":"Demo","author":"A","version":"1.0.0","api_level":3,"main":"main.lua"}""",
                files,
            )
        }
    }

    @Test
    fun rejectsUnknownPermission() {
        val files = setOf("manifest.json", "main.lua")
        val raw = """
            {"id":"community.demo.one","name":"Demo","author":"A","version":"1.0.0","main":"main.lua","permissions":["shell"]}
        """.trimIndent()
        assertThrows(IllegalStateException::class.java) {
            PluginManifestParser.parse(raw, files)
        }
    }

    @Test
    fun acceptsGithubHomepageOnly() {
        assertThat(PluginSecurity.validateHomepage("https://github.com/acme/plugin")).isNull()
        assertThat(PluginSecurity.validateHomepage("https://evil.example/x")).isNotNull()
        assertThat(PluginSecurity.validateHomepage("https://github.com/acme/plugin@evil")).isNotNull()
    }

    @Test
    fun reservedIdsAreBlocked() {
        assertThat(PluginSecurity.validateId("com.lunasdev.spy")).isNotNull()
        assertThat(PluginSecurity.validateId("community.focus.list")).isNull()
        assertThat(PluginSecurity.validateId("community.hosts.file")).isNull()
    }

    @Test
    fun svgBanList() {
        assertThat(PluginSecurity.svgIsSafe("<svg xmlns='http://www.w3.org/2000/svg'></svg>")).isTrue()
        assertThat(PluginSecurity.svgIsSafe("<svg><script>alert(1)</script></svg>")).isFalse()
        assertThat(PluginSecurity.svgIsSafe("<svg><use href='#x'/></svg>")).isFalse()
    }

    @Test
    fun pluginRulesArePrefixed() {
        assertThat(PluginRuleIds.owns("community.focus.list", "p:community.focus.list:focus")).isTrue()
        assertThat(PluginRuleIds.owns("community.focus.list", "p:evil.plugin:focus")).isFalse()
        assertThat(PluginRuleIds.isPluginOwned("abc")).isFalse()
    }

    @Test
    fun unpackStripsSingleRootFolder() {
        val zip = zipOf(
            "my-plugin/manifest.json" to """{"id":"community.demo.one","name":"Demo","author":"A","version":"1.0.0","main":"main.lua"}""",
            "my-plugin/main.lua" to "function on_enable() end",
        )
        val unpacked = PluginPackageImporter.unpack(zip)
        assertThat(unpacked.manifest.id).isEqualTo("community.demo.one")
        assertThat(unpacked.files.keys).contains("main.lua")
    }

    private fun zipOf(vararg files: Pair<String, Any>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                when (content) {
                    is String -> zip.write(content.toByteArray())
                    is ByteArray -> zip.write(content)
                    else -> error("unsupported")
                }
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
