package com.lunasdev.lunasdpi.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lunasdev.lunasdpi.LunaApplication
import com.lunasdev.lunasdpi.data.model.DpiConfig
import com.lunasdev.lunasdpi.data.model.DomainRule
import com.lunasdev.lunasdpi.data.model.EngineSnapshot
import com.lunasdev.lunasdpi.data.model.UserFacingError
import com.lunasdev.lunasdpi.data.model.VpnPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LunaApplication

    val phase: StateFlow<VpnPhase> = app.vpnState.phase
    val config: StateFlow<DpiConfig> = app.configState
    val error: StateFlow<UserFacingError?> = app.vpnState.error
    val snapshot: StateFlow<EngineSnapshot> = app.vpnState.snapshot

    fun prepareIntent(): Intent? = app.vpnController.prepareIntent()

    fun start() {
        viewModelScope.launch { app.vpnController.start() }
    }

    fun stop() = app.vpnController.stop()

    fun onPermissionDenied() = app.vpnController.onPermissionDenied()

    fun dismissError() = app.vpnState.clearError()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LunaApplication
    val config: StateFlow<DpiConfig> = app.configState

    fun update(transform: (DpiConfig) -> DpiConfig) {
        viewModelScope.launch { app.settings.update(transform) }
    }
}

class RulesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LunaApplication
    val rules: StateFlow<List<DomainRule>> = app.rulesState

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { app.rules.setEnabled(id, enabled) }
    }

    fun setAllEnabled(enabled: Boolean) {
        viewModelScope.launch { app.rules.setAllEnabled(enabled) }
    }

    fun delete(id: String) {
        viewModelScope.launch { app.rules.delete(id) }
    }

    fun upsert(rule: DomainRule) {
        viewModelScope.launch { app.rules.upsert(rule) }
    }

    fun duplicate(rule: DomainRule, copySuffix: String) {
        viewModelScope.launch {
            app.rules.upsert(
                rule.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    name = copySuffix,
                ),
            )
        }
    }

    fun addPreset(rule: DomainRule) {
        viewModelScope.launch {
            val current = app.rules.current()
            val existing = current.find { item ->
                item.id == rule.id || item.name.equals(rule.name, ignoreCase = true)
            }
            if (existing != null) {
                if (!existing.enabled) {
                    app.rules.setEnabled(existing.id, true)
                }
                return@launch
            }
            app.rules.upsert(rule)
        }
    }

    fun exportJson(): String = app.rules.exportJson(rules.value)

    fun importJson(raw: String): Result<Int> {
        val parsed = app.rules.importJson(raw)
        return parsed.map { imported ->
            viewModelScope.launch {
                imported.forEach { app.rules.upsert(it) }
            }
            imported.size
        }
    }
}

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LunaApplication
    val snapshot: StateFlow<EngineSnapshot> = app.vpnState.snapshot
    val phase: StateFlow<VpnPhase> = app.vpnState.phase
    val error: StateFlow<UserFacingError?> = app.vpnState.error
    val history: StateFlow<List<Long>> = snapshot
        .map { it.packetsProcessed }
        .scanHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selfTest(): String = com.lunasdev.lunasdpi.vpn.NativeEngine.selfTest()
}

private fun Flow<Long>.scanHistory(limit: Int = 24): Flow<List<Long>> = flow {
    val points = ArrayDeque<Long>(limit)
    collect { value ->
        if (points.size == limit) points.removeFirst()
        points.addLast(value)
        emit(points.toList())
    }
}

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LunaApplication
    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            app.settings.setOnboardingDone()
            onDone()
        }
    }
}
