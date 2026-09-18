package com.pblock.app.state

/**
 * Persisted top-level state machine for P-BLOCK.
 *
 * State precedence: protection-gating states win over health warnings.
 * PARTNER_LOCKED / COOLDOWN_PENDING are "hard" states; DEGRADED rides as a
 * health banner that only downgrades ACTIVE (never overrides lock states).
 */
enum class PBlockState {
    SETUP_INCOMPLETE,  // no partner paired yet / onboarding not finished
    ACTIVE,            // protected, healthy
    DEGRADED,          // protected but a health issue exists (battery exemption removed, tamper)
    PARTNER_LOCKED,    // accountability partner locked the device
    COOLDOWN_PENDING,  // lock is in cooldown; timeout does NOT auto-return (auto-deny)
    DISCONNECTED       // partner unlinked
}

sealed class StateEvent {
    /** Full setup completed (pairing + VPN). */
    data object SetupCompleted : StateEvent()

    /** Tamper detected — notify + log only (no back-out / redirect). */
    data object TamperDetected : StateEvent()

    /** Battery exemption removed — warn in DEGRADED (recommended, not required). */
    data object BatteryExemptionRemoved : StateEvent()

    /** Partner issued a lock command. */
    data object PartnerLock : StateEvent()

    /** Cooldown started while locked. */
    data object CooldownStarted : StateEvent()

    /** Cooldown elapsed — auto-deny: the device STAYS locked. */
    data object CooldownExpired : StateEvent()

    /** Partner issued an unlock command. */
    data object PartnerUnlock : StateEvent()

    /** Partner unlinked this device. */
    data object PartnerUnlinked : StateEvent()

    /** Device re-paired with an (existing or new) partner. */
    data object Relinked : StateEvent()

    /** Required permissions were revoked. */
    data object PermissionsRevoked : StateEvent()

    /** Forced health re-evaluation (e.g., on foreground). */
    data object HealthCheck : StateEvent()
}

object PBlockStateMachine {

    /** True for states where the device is under protection. */
    fun isProtected(state: PBlockState): Boolean =
        state == PBlockState.ACTIVE || state == PBlockState.DEGRADED ||
        state == PBlockState.PARTNER_LOCKED || state == PBlockState.COOLDOWN_PENDING

    /** True when the device is hard-locked by the partner. */
    fun isLocked(state: PBlockState): Boolean =
        state == PBlockState.PARTNER_LOCKED || state == PBlockState.COOLDOWN_PENDING

    /**
     * Pure reducer. `hasHealthIssue` (e.g. battery exemption removed) keeps
     * ACTIVE at DEGRADED after leaving a lock state. Returns the next state
     * and whether a persisted change occurred.
     */
    fun transition(
        current: PBlockState,
        event: StateEvent,
        hasHealthIssue: Boolean = false
    ): Transition {
        val next = when (current) {
            PBlockState.SETUP_INCOMPLETE -> when (event) {
                is StateEvent.SetupCompleted -> PBlockState.ACTIVE
                is StateEvent.Relinked -> PBlockState.ACTIVE
                else -> current
            }

            PBlockState.ACTIVE -> when (event) {
                is StateEvent.TamperDetected -> PBlockState.DEGRADED
                is StateEvent.BatteryExemptionRemoved -> PBlockState.DEGRADED
                is StateEvent.PartnerLock -> PBlockState.PARTNER_LOCKED
                is StateEvent.PartnerUnlinked -> PBlockState.DISCONNECTED
                is StateEvent.PermissionsRevoked -> PBlockState.SETUP_INCOMPLETE
                is StateEvent.HealthCheck -> contrastHealth(hasHealthIssue)
                else -> current
            }

            PBlockState.DEGRADED -> when (event) {
                is StateEvent.PartnerLock -> PBlockState.PARTNER_LOCKED
                is StateEvent.PartnerUnlinked -> PBlockState.DISCONNECTED
                is StateEvent.SetupCompleted -> contrastHealth(hasHealthIssue)
                is StateEvent.Relinked -> contrastHealth(hasHealthIssue)
                is StateEvent.PermissionsRevoked -> PBlockState.SETUP_INCOMPLETE
                is StateEvent.HealthCheck -> contrastHealth(hasHealthIssue)
                else -> current
            }

            PBlockState.PARTNER_LOCKED -> when (event) {
                is StateEvent.CooldownStarted -> PBlockState.COOLDOWN_PENDING
                is StateEvent.PartnerUnlock -> contrastHealth(hasHealthIssue)
                is StateEvent.PartnerUnlinked -> PBlockState.DISCONNECTED
                // Do not allow bypassing a hard lock just by revoking permissions
                else -> current
            }

            PBlockState.COOLDOWN_PENDING -> when (event) {
                is StateEvent.CooldownExpired -> PBlockState.PARTNER_LOCKED
                is StateEvent.PartnerUnlock -> contrastHealth(hasHealthIssue)
                is StateEvent.PartnerUnlinked -> PBlockState.DISCONNECTED
                else -> current
            }

            PBlockState.DISCONNECTED -> when (event) {
                is StateEvent.Relinked -> contrastHealth(hasHealthIssue)
                is StateEvent.SetupCompleted -> contrastHealth(hasHealthIssue)
                is StateEvent.PermissionsRevoked -> PBlockState.SETUP_INCOMPLETE
                is StateEvent.HealthCheck -> contrastHealth(hasHealthIssue)
                else -> current
            }
        }
        return Transition(previous = current, next = next, changed = next != current)
    }

    private fun contrastHealth(hasHealthIssue: Boolean): PBlockState =
        if (hasHealthIssue) PBlockState.DEGRADED else PBlockState.ACTIVE
}

data class Transition(val previous: PBlockState, val next: PBlockState, val changed: Boolean)