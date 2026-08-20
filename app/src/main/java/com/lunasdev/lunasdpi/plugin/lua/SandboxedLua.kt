package com.lunasdev.lunasdpi.plugin.lua

import com.lunasdev.lunasdpi.plugin.PluginLimits
import com.lunasdev.lunasdpi.plugin.PluginSecurity
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.CoroutineLib
import org.luaj.vm2.lib.MathLib
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.ResourceFinder
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.VarArgFunction

internal object SandboxedLua {
    fun create(root: File, luna: LuaTable): Globals {
        val globals = Globals()
        globals.finder = ResourceFinder { null }
        globals.STDOUT = PrintStream(DiscardStream, false)
        globals.STDIN = null
        globals.load(BaseLib())
        globals.load(PackageLib())
        globals.load(Bit32Lib())
        globals.load(TableLib())
        globals.load(StringLib())
        globals.load(CoroutineLib())
        globals.load(MathLib())
        LuaC.install(globals)
        stripDangerous(globals)
        globals.set("luna", luna)
        globals.set("require", RequireFn(root, globals))
        globals.set("print", PrintFn(luna))
        return globals
    }

    fun loadFile(globals: Globals, root: File, relative: String): LuaValue {
        if (!PluginSecurity.validateRelativePath(relative) || !relative.endsWith(".lua")) {
            throw LuaError("Refusing to load $relative")
        }
        val file = File(root, relative).canonicalFile
        val base = root.canonicalFile
        if (file != base && !file.path.startsWith(base.path + File.separator)) {
            throw LuaError("Load path escapes plugin directory")
        }
        if (!file.isFile) {
            throw LuaError("Missing $relative")
        }
        val bytes = file.readBytes()
        if (bytes.size > PluginSecurity.MAX_LUA_BYTES || PluginSecurity.isLuaBytecode(bytes)) {
            throw LuaError("Lua source rejected")
        }
        return globals.load(bytes.decodeToString(), relative).call()
    }

    private fun stripDangerous(globals: Globals) {
        val banned = listOf(
            "dofile",
            "loadfile",
            "load",
            "loadstring",
            "collectgarbage",
            "module",
            "debug",
            "io",
            "os",
            "package",
            "java",
            "luajava",
            "newproxy",
        )
        banned.forEach { name -> globals.set(name, LuaValue.NIL) }
        val string = globals.get("string")
        if (string.istable()) {
            string.set("dump", LuaValue.NIL)
        }
        globals.set("_G", globals)
    }

    private object DiscardStream : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    private class PrintFn(private val luna: LuaTable) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val parts = ArrayList<String>(args.narg())
            for (i in 1..args.narg()) {
                parts.add(args.arg(i).tojstring())
            }
            val log = luna.get("log")
            if (log.istable()) {
                val info = log.get("info")
                if (!info.isnil()) {
                    info.call(LuaValue.valueOf(parts.joinToString("\t").take(500)))
                }
            }
            return LuaValue.NONE
        }
    }

    private class RequireFn(
        private val root: File,
        private val globals: Globals,
    ) : OneArgFunction() {
        private val loaded = LuaTable()
        private var count = 0

        override fun call(arg: LuaValue): LuaValue {
            val name = arg.checkjstring().trim()
            if (name.isEmpty() || name.contains("..") || name.contains('\\') || name.contains('/') || name.startsWith(".")) {
                throw LuaError("Invalid require: $name")
            }
            val cached = loaded.get(name)
            if (!cached.isnil()) {
                return cached
            }
            if (count >= PluginLimits.MAX_MODULES) {
                throw LuaError("Too many Lua modules.")
            }
            count += 1
            loaded.set(name, LuaValue.TRUE)
            val relative = "modules/" + name.replace('.', '/') + ".lua"
            val result = loadFile(globals, root, relative)
            loaded.set(name, result)
            return result
        }
    }
}
