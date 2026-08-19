package com.lunasdev.lunasdpi.plugin.lua

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

internal object LunaCollection {
    fun type(): LuaTable {
        val table = LuaTable()
        table.set("new", LuaFn.z { instance() })
        table.set("from", LuaFn.o { source ->
            val col = instance()
            if (source.istable()) {
                val src = source.checktable()
                var i = 1
                while (i <= 256) {
                    val row = src.get(i)
                    if (row.isnil()) break
                    if (row.istable()) {
                        val pair = row.checktable()
                        val key = pair.get("key").optjstring(pair.get(1).optjstring(""))
                        val value = if (!pair.get("value").isnil()) pair.get("value") else pair.get(2)
                        if (key.isNotEmpty()) col.get("set").call(col, LuaValue.valueOf(key), value)
                    }
                    i += 1
                }
            }
            col
        })
        return table
    }

    private fun instance(): LuaTable {
        val data = LinkedHashMap<String, LuaValue>()
        val col = LuaTable()
        fun size(): Int = data.size
        col.set(
            "set",
            LuaFn.r { self, key, value ->
                if (data.size >= 256 && key.tojstring() !in data) return@r self
                data[key.tojstring().take(80)] = value
                self
            },
        )
        col.set("get", LuaFn.t { _, key -> data[key.tojstring()] ?: LuaValue.NIL })
        col.set("has", LuaFn.t { _, key -> LuaValue.valueOf(key.tojstring() in data) })
        col.set(
            "delete",
            LuaFn.t { self, key ->
                data.remove(key.tojstring())
                self
            },
        )
        col.set(
            "clear",
            LuaFn.o { self ->
                data.clear()
                self
            },
        )
        col.set("size", LuaFn.o { LuaValue.valueOf(size()) })
        col.set("count", LuaFn.o { LuaValue.valueOf(size()) })
        col.set("keys", LuaFn.o { LuaFn.fromJava(data.keys.toList()) })
        col.set("values", LuaFn.o { LuaFn.fromJava(data.values.toList()) })
        col.set("first", LuaFn.o { data.values.firstOrNull() ?: LuaValue.NIL })
        col.set("last", LuaFn.o { data.values.lastOrNull() ?: LuaValue.NIL })
        col.set("firstKey", LuaFn.o { data.keys.firstOrNull()?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL })
        col.set("lastKey", LuaFn.o { data.keys.lastOrNull()?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL })
        col.set("at", LuaFn.t { _, index ->
            data.values.toList().getOrNull(index.toint() - 1) ?: LuaValue.NIL
        })
        col.set("random", LuaFn.o {
            if (data.isEmpty()) LuaValue.NIL else data.values.random()
        })
        col.set("clone", LuaFn.o {
            val copy = instance()
            data.forEach { (key, value) -> copy.get("set").call(copy, LuaValue.valueOf(key), value) }
            copy
        })
        col.set("toJSON", LuaFn.o {
            LuaFn.fromJava(data.mapValues { it.value.tojstring() })
        })
        col.set("find", LuaFn.t { _, fn ->
            val pred = fn.checkfunction()
            data.entries.firstOrNull { pred.call(it.value, LuaValue.valueOf(it.key)).toboolean() }?.value
                ?: LuaValue.NIL
        })
        col.set("some", LuaFn.t { _, fn ->
            val pred = fn.checkfunction()
            LuaValue.valueOf(data.any { pred.call(it.value, LuaValue.valueOf(it.key)).toboolean() })
        })
        col.set("every", LuaFn.t { _, fn ->
            val pred = fn.checkfunction()
            LuaValue.valueOf(data.isNotEmpty() && data.all { pred.call(it.value, LuaValue.valueOf(it.key)).toboolean() })
        })
        col.set("filter", LuaFn.t { _, fn ->
            val pred = fn.checkfunction()
            val copy = instance()
            data.forEach { (key, value) ->
                if (pred.call(value, LuaValue.valueOf(key)).toboolean()) {
                    copy.get("set").call(copy, LuaValue.valueOf(key), value)
                }
            }
            copy
        })
        col.set("map", LuaFn.t { _, fn ->
            val mapper = fn.checkfunction()
            val out = LuaTable()
            var i = 1
            data.forEach { (key, value) ->
                out.set(i, mapper.call(value, LuaValue.valueOf(key)))
                i += 1
            }
            out
        })
        col.set("forEach", LuaFn.t { self, fn ->
            val each = fn.checkfunction()
            data.forEach { (key, value) -> each.call(value, LuaValue.valueOf(key)) }
            self
        })
        col.set("sweep", LuaFn.t { self, fn ->
            val pred = fn.checkfunction()
            data.entries.removeAll { pred.call(it.value, LuaValue.valueOf(it.key)).toboolean() }
            self
        })
        col.set("concat", LuaFn.t { self, other ->
            if (other.istable()) {
                val src = other.checktable()
                src.get("keys").takeIf { it.isfunction() }?.let {
                    val keys = it.call(src)
                    if (keys.istable()) {
                        var i = 1
                        val list = keys.checktable()
                        while (i <= 256) {
                            val key = list.get(i)
                            if (key.isnil()) break
                            val value = src.get("get").call(src, key)
                            self.get("set").call(self, key, value)
                            i += 1
                        }
                    }
                }
            }
            self
        })
        col.set("equals", LuaFn.t { _, other ->
            if (!other.istable()) return@t LuaValue.FALSE
            val keys = other.checktable().get("keys")
            if (!keys.isfunction()) return@t LuaValue.FALSE
            LuaValue.valueOf(size() == keys.call(other).checktable().let { seq ->
                var n = 1
                while (!seq.get(n).isnil()) n++
                n - 1
            })
        })
        col.set("each", col.get("forEach"))
        col.set("keyArray", col.get("keys"))
        col.set("array", col.get("values"))
        col.set(
            "findKey",
            LuaFn.t { _, fn ->
                val pred = fn.checkfunction()
                data.entries.firstOrNull { pred.call(it.value, LuaValue.valueOf(it.key)).toboolean() }
                    ?.key?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            },
        )
        col.set(
            "ensure",
            LuaFn.r { _, key, value ->
                if (key.tojstring() !in data) data[key.tojstring().take(80)] = value
                data[key.tojstring()] ?: value
            },
        )
        col.set(
            "hasAll",
            LuaFn.t { _, keys ->
                LuaValue.valueOf(LuaFn.stringList(keys, 64).all { it in data })
            },
        )
        col.set(
            "hasAny",
            LuaFn.t { _, keys ->
                LuaValue.valueOf(LuaFn.stringList(keys, 64).any { it in data })
            },
        )
        col.set(
            "reduce",
            LuaFn.r { _, fn, start ->
                val reducer = fn.checkfunction()
                var acc = start
                data.forEach { (key, value) ->
                    acc = reducer.call(acc, value, LuaValue.valueOf(key))
                }
                acc
            },
        )
        col.set(
            "tap",
            LuaFn.t { self, fn ->
                fn.checkfunction().call(self)
                self
            },
        )
        col.set(
            "reverse",
            LuaFn.o {
                val copy = instance()
                data.keys.reversed().forEach { key ->
                    copy.get("set").call(copy, LuaValue.valueOf(key), data.getValue(key))
                }
                copy
            },
        )
        col.set(
            "sort",
            LuaFn.o {
                val copy = instance()
                data.toList().sortedBy { it.first }.forEach { (key, value) ->
                    copy.get("set").call(copy, LuaValue.valueOf(key), value)
                }
                copy
            },
        )
        return col
    }
}
