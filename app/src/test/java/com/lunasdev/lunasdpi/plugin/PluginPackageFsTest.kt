package com.lunasdev.lunasdpi.plugin

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Test
import org.luaj.vm2.LuaError

class PluginPackageFsTest {
    @Test
    fun readsTextAndListsReadableFiles() {
        val root = createTempDirectory("luna-fs").toFile()
        File(root, "lists").mkdirs()
        File(root, "lists/hosts.txt").writeText("127.0.0.1 example.com\n")
        File(root, "meta.json").writeText("""{"n":1}""")
        File(root, "icon.png").writeBytes(byteArrayOf(1, 2, 3))
        val fs = PluginPackageFs(root)
        assertThat(fs.read("lists/hosts.txt")).contains("example.com")
        assertThat(fs.exists("meta.json")).isTrue()
        assertThat(fs.exists("icon.png")).isFalse()
        assertThat(fs.list("lists")).containsExactly("lists/hosts.txt")
        assertThat(fs.list("")).containsAtLeast("lists/hosts.txt", "meta.json")
        assertThat(fs.list("")).doesNotContain("icon.png")
    }

    @Test
    fun rejectsPathEscape() {
        val root = createTempDirectory("luna-fs").toFile()
        val fs = PluginPackageFs(root)
        val error = runCatching { fs.read("../secret.txt") }.exceptionOrNull()
        assertThat(error).isInstanceOf(LuaError::class.java)
    }
}
