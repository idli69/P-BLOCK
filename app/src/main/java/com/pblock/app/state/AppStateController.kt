package com.pblock.app.state

import android.util.Log
import com.pblock.app.accountability.AuthManager
import com.pblock.app.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Runtime owner of the persisted state machine.
 *
 * Feeds the pure reducer with events (from UI/commands/tamper), persists the
 * result, and mirrors every change to the device's RTDB node via a callback
 * supplied by the sync layer.
 */
class AppStateController(
    private val prefs: PreferencesManager,
    private val authManager: AuthManager,
    private val onStateChanged: suspend (PBlockState) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _state = MutableStateFlow(PBlockState.SETUP_INCOMPLETE)
    val state: StateFlow<PBlockState> = _state.asStateFlow()

    /** UI-facing binding of persisted state + startup bootstrap. */
    val uiState: StateFlow<PBlockState> = combine(
        prefs.appState,
        prefs.partnerCodeEncrypted,
        prefs.isProtected
    ) { persisted, partnerCode, protected ->
        bootstrap(persisted, partnerCode, protected)
    }.stateIn(scope, SharingStarted.Eagerly, PBlockState.SETUP_INCOMPLETE)

    fun start() {
        scope.launch {
            _state.value = uiState.value
            if (_state.value != PBlockState.SETUP_INCOMPLETE) {
                persistAndMirror(_state.value)
            }
        }
        scope.launch {
            uiState.collect { next ->
                if (next != _state.value) {
                    _state.value = next
                    persistAndMirror(next)
                }
            }
        }
    }

    suspend fun applyEvent(event: StateEvent) {
        val transition = PBlockStateMachine.transition(_state.value, event, hasHealthIssue())
        if (transition.changed) {
            _state.value = transition.next
            persistAndMirror(transition.next)
            Log.i(TAG, "${transition.previous} -> ${transition.next} via $event")
        } else {
            Log.d(TAG, "No state change on $event (state=${_state.value})")
        }
    }

    private suspend fun persistAndMirror(state: PBlockState) {
        prefs.setAppState(state)
        try {
            onStateChanged(state)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mirror state to RTDB", e)
        }
    }

    /**
     * Health issues ride as a banner and only downgrade ACTIVE while leaving
     * hard lock states untouched. For now the signal is a VPN that isn't
     * running while protection is claimed; a battery-exemption toggle event
     * will be added when that optional setting lands (Phase 7).
     */
    private suspend fun hasHealthIssue(): Boolean {
        val protected = prefs.isProtected.first()
        return protected && prefs.vpnStatus.first() != PreferencesManager.VPN_STATUS_RUNNING
    }

    /**
     * Maps persisted state + current facts into the effective state at
     * startup. Keeps DISCONNECTED after an unlink, drives SETUP_INCOMPLETE ->
     * ACTIVE once pairing + protection are both present.
     */
    private fun bootstrap(
        persisted: PBlockState,
        partnerCode: String?,
        protected: Boolean
    ): PBlockState = when {
        partnerCode == null && persisted == PBlockState.SETUP_INCOMPLETE ->
            PBlockState.SETUP_INCOMPLETE
        partnerCode == null -> persisted // e.g. DISCONNECTED after an unlink
        persisted == PBlockState.SETUP_INCOMPLETE && protected -> PBlockState.ACTIVE
        else -> persisted
    }

    companion object {
        private const val TAG = "AppStateController"
    }
}