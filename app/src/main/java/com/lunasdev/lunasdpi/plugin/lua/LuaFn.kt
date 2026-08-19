package com.lunasdev.lunasdpi.plugin.lua

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction

internal object LuaFn {
    fun z(block: () -> LuaValue) = object : ZeroArgFunction() {
        override fun call(): LuaValue = block()
    }

    fun o(block: (LuaValue) -> LuaValue) = object : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue = block(arg)
    }

    fun t(block: (LuaValue, LuaValue) -> LuaValue) = object : TwoArgFunction() {
        override fun call(left: LuaValue, right: LuaValue): LuaValue = block(left, right)
    }

    fun r(block: (LuaValue, LuaValue, LuaValue) -> LuaValue) = object : ThreeArgFunction() {
        override fun call(a: LuaValue, b: LuaValue, c: LuaValue): LuaValue = block(a, b, c)
    }

    fun v(block: (Varargs) -> LuaValue) = object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = block(args)
    }

    /** `obj.fn(x)` and `obj:fn(x)` both work. */
    fun m1(self: LuaTable, block: (LuaValue) -> LuaValue) = object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val first = args.arg(1)
            val payload = if (first.istable() && first.raweq(self)) args.arg(2) else first
            return block(payload)
        }
    }

    fun m2(self: LuaTable, block: (LuaValue, LuaValue) -> LuaValue) = object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val offset = if (args.arg(1).istable() && args.arg(1).raweq(self)) 1 else 0
            return block(args.arg(1 + offset), args.arg(2 + offset))
        }
    }

    fun m3(self: LuaTable, block: (LuaValue, LuaValue, LuaValue) -> LuaValue) = object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val offset = if (args.arg(1).istable() && args.arg(1).raweq(self)) 1 else 0
            return block(args.arg(1 + offset), args.arg(2 + offset), args.arg(3 + offset))
        }
    }

    fun invoke(mod: LuaValue, name: String, vararg args: LuaValue): LuaValue {
        val fn = mod.get(name)
        if (!fn.isfunction()) {
            throw org.luaj.vm2.LuaError("Unknown method: $name")
        }
        return when (args.size) {
            0 -> fn.call()
            1 -> fn.call(args[0])
            2 -> fn.call(args[0], args[1])
            3 -> fn.call(args[0], args[1], args[2])
            else -> fn.invoke(LuaValue.varargsOf(args)).arg1()
        }
    }

    fun module(vararg pairs: Pair<String, LuaValue>): LuaTable {
        val table = LuaTable()
        pairs.forEach { (name, fn) -> table.set(name, fn) }
        return table
    }

    fun fromJava(value: Any?): LuaValue = when (value) {
        null -> LuaValue.NIL
        is LuaValue -> value
        is String -> LuaValue.valueOf(value)
        is Boolean -> LuaValue.valueOf(value)
        is Int -> LuaValue.valueOf(value)
        is Long -> LuaValue.valueOf(value.toDouble())
        is Float -> LuaValue.valueOf(value.toDouble())
        is Double -> LuaValue.valueOf(value)
        is Map<*, *> -> {
            val table = LuaTable()
            value.forEach { (key, item) ->
                if (key != null && item != null) {
                    table.set(key.toString(), fromJava(item))
                }
            }
            table
        }
        is Iterable<*> -> {
            val table = LuaTable()
            value.forEachIndexed { index, item ->
                if (item != null) table.set(index + 1, fromJava(item))
            }
            table
        }
        else -> LuaValue.valueOf(value.toString())
    }

    fun stringList(value: LuaValue, max: Int = 64): List<String> {
        if (!value.istable()) return emptyList()
        val out = ArrayList<String>()
        var i = 1
        while (i <= max) {
            val item = value.get(i)
            if (item.isnil()) break
            val text = item.tojstring()
            if (text.isNotEmpty()) out.add(text)
            i += 1
        }
        return out
    }
}
