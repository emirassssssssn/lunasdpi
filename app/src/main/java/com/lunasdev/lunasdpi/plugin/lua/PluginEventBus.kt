package com.lunasdev.lunasdpi.plugin.lua

import java.util.concurrent.CopyOnWriteArrayList
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue

class PluginEventBus {
    private val listeners = LinkedHashMap<String, CopyOnWriteArrayList<Listener>>()

    fun on(name: String, fn: LuaValue, once: Boolean = false) {
        val key = sanitize(name)
        val list = listeners.getOrPut(key) { CopyOnWriteArrayList() }
        if (list.size >= 8) throw LuaError("Too many listeners for $key")
        list.add(Listener(fn, once))
    }

    fun once(name: String, fn: LuaValue) = on(name, fn, once = true)

    fun off(name: String, fn: LuaValue?) {
        val key = sanitize(name)
        if (fn == null || fn.isnil()) {
            listeners.remove(key)
            return
        }
        listeners[key]?.removeAll { it.fn == fn }
    }

    fun emit(name: String, vararg args: LuaValue) {
        val key = sanitize(name)
        val list = listeners[key] ?: return
        val snapshot = list.toList()
        snapshot.forEach { listener ->
            when (args.size) {
                0 -> listener.fn.call()
                1 -> listener.fn.call(args[0])
                else -> listener.fn.call(args[0], args[1])
            }
            if (listener.once) list.remove(listener)
        }
    }

    fun listenerCount(name: String): Int = listeners[sanitize(name)]?.size ?: 0

    fun names(): List<String> = listeners.keys.toList()

    fun clear() {
        listeners.clear()
    }

    private fun sanitize(name: String): String = name.trim().take(40).ifBlank { "event" }

    private data class Listener(val fn: LuaValue, val once: Boolean)
}
