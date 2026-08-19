package com.lunasdev.lunasdpi.plugin

import com.lunasdev.lunasdpi.data.HostEntry
import com.lunasdev.lunasdpi.data.HostsFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HostsStore {
    private val lock = Any()
    private val byPlugin = LinkedHashMap<String, List<HostEntry>>()
    private val _mappings = MutableStateFlow<List<HostEntry>>(emptyList())
    val mappings: StateFlow<List<HostEntry>> = _mappings.asStateFlow()

    fun current(): List<HostEntry> = _mappings.value

    fun pluginEntries(pluginId: String): List<HostEntry> = synchronized(lock) {
        byPlugin[pluginId].orEmpty()
    }

    fun replacePlugin(pluginId: String, entries: List<HostEntry>) {
        synchronized(lock) {
            if (entries.isEmpty()) {
                byPlugin.remove(pluginId)
            } else {
                byPlugin.remove(pluginId)
                byPlugin[pluginId] = entries.take(HostsFile.MAX_PER_PLUGIN)
            }
            rebuildLocked()
        }
    }

    fun clearPlugin(pluginId: String) {
        replacePlugin(pluginId, emptyList())
    }

    private fun rebuildLocked() {
        val merged = LinkedHashMap<String, HostEntry>()
        byPlugin.values.forEach { list ->
            list.forEach { entry ->
                merged[entry.host] = entry
            }
        }
        val snapshot = if (merged.size <= HostsFile.MAX_MERGED) {
            merged.values.toList()
        } else {
            merged.entries.toList().takeLast(HostsFile.MAX_MERGED).map { it.value }
        }
        _mappings.value = snapshot
    }
}
