package com.lunasdev.lunasdpi.data

import android.os.SystemClock
import com.lunasdev.lunasdpi.data.model.EngineSnapshot
import com.lunasdev.lunasdpi.data.model.UserFacingError
import com.lunasdev.lunasdpi.data.model.VpnPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VpnStateRepository {
    private val _phase = MutableStateFlow(VpnPhase.DISCONNECTED)
    val phase: StateFlow<VpnPhase> = _phase.asStateFlow()

    private val _snapshot = MutableStateFlow(EngineSnapshot())
    val snapshot: StateFlow<EngineSnapshot> = _snapshot.asStateFlow()

    private val _error = MutableStateFlow<UserFacingError?>(null)
    val error: StateFlow<UserFacingError?> = _error.asStateFlow()

    @Volatile
    private var lastSnapshotAt = 0L

    fun setPhase(phase: VpnPhase) {
        _phase.value = phase
    }

    fun setSnapshot(snapshot: EngineSnapshot) {
        val now = SystemClock.elapsedRealtime()
        val previous = _snapshot.value
        val significant = snapshot.engineAlive != previous.engineAlive ||
            snapshot.tunActive != previous.tunActive ||
            snapshot.lastError != previous.lastError ||
            snapshot.currentStrategy != previous.currentStrategy
        if (!significant && now - lastSnapshotAt < 250L) {
            return
        }
        lastSnapshotAt = now
        _snapshot.value = snapshot
    }

    fun setError(error: UserFacingError?) {
        _error.value = error
        if (error != null) {
            _phase.value = VpnPhase.ERROR
        }
    }

    fun clearError() {
        _error.value = null
    }
}
