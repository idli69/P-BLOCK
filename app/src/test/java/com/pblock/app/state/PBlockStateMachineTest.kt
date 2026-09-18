package com.pblock.app.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PBlockStateMachineTest {

    @Test fun `SETUP_INCOMPLETE + SetupCompleted goes ACTIVE`() {
        val t = PBlockStateMachine.transition(PBlockState.SETUP_INCOMPLETE, StateEvent.SetupCompleted)
        assertTrue(t.changed)
        assertEquals(PBlockState.ACTIVE, t.next)
    }

    @Test fun `SETUP_INCOMPLETE with SetupCompleted + healthIssue still goes ACTIVE`() {
        val t = PBlockStateMachine.transition(
            PBlockState.SETUP_INCOMPLETE, StateEvent.SetupCompleted, hasHealthIssue = true
        )
        assertEquals(PBlockState.ACTIVE, t.next)
    }

    @Test fun `ACTIVE + TamperDetected goes DEGRADED`() {
        val t = PBlockStateMachine.transition(PBlockState.ACTIVE, StateEvent.TamperDetected)
        assertEquals(PBlockState.DEGRADED, t.next)
    }

    @Test fun `ACTIVE + PartnerLock goes PARTNER_LOCKED`() {
        val t = PBlockStateMachine.transition(PBlockState.ACTIVE, StateEvent.PartnerLock)
        assertEquals(PBlockState.PARTNER_LOCKED, t.next)
    }

    @Test fun `PARTNER_LOCKED + CooldownStarted goes COOLDOWN_PENDING`() {
        val t = PBlockStateMachine.transition(PBlockState.PARTNER_LOCKED, StateEvent.CooldownStarted)
        assertEquals(PBlockState.COOLDOWN_PENDING, t.next)
    }

    @Test fun `COOLDOWN_PENDING + CooldownExpired returns to PARTNER_LOCKED (auto-deny)`() {
        val t = PBlockStateMachine.transition(PBlockState.COOLDOWN_PENDING, StateEvent.CooldownExpired)
        assertTrue(t.changed)
        assertEquals(PBlockState.PARTNER_LOCKED, t.next)
    }

    @Test fun `PARTNER_LOCKED + PartnerUnlock without healthIssue goes ACTIVE`() {
        val t = PBlockStateMachine.transition(
            PBlockState.PARTNER_LOCKED, StateEvent.PartnerUnlock, hasHealthIssue = false
        )
        assertEquals(PBlockState.ACTIVE, t.next)
    }

    @Test fun `PARTNER_LOCKED + PartnerUnlock with healthIssue goes DEGRADED`() {
        val t = PBlockStateMachine.transition(
            PBlockState.PARTNER_LOCKED, StateEvent.PartnerUnlock, hasHealthIssue = true
        )
        assertEquals(PBlockState.DEGRADED, t.next)
    }

    @Test fun `COOLDOWN_PENDING + PartnerUnlock without healthIssue goes ACTIVE`() {
        val t = PBlockStateMachine.transition(
            PBlockState.COOLDOWN_PENDING, StateEvent.PartnerUnlock, hasHealthIssue = false
        )
        assertEquals(PBlockState.ACTIVE, t.next)
    }

    @Test fun `ACTIVE + PartnerUnlinked goes DISCONNECTED`() {
        val t = PBlockStateMachine.transition(PBlockState.ACTIVE, StateEvent.PartnerUnlinked)
        assertEquals(PBlockState.DISCONNECTED, t.next)
    }

    @Test fun `DISCONNECTED + Relinked goes ACTIVE`() {
        val t = PBlockStateMachine.transition(PBlockState.DISCONNECTED, StateEvent.Relinked)
        assertEquals(PBlockState.ACTIVE, t.next)
    }

    @Test fun `DEGRADED + PartnerLock goes PARTNER_LOCKED`() {
        val t = PBlockStateMachine.transition(PBlockState.DEGRADED, StateEvent.PartnerLock)
        assertEquals(PBlockState.PARTNER_LOCKED, t.next)
    }

    @Test fun `active and degraded are protected, disconnected is not`() {
        assertTrue(PBlockStateMachine.isProtected(PBlockState.ACTIVE))
        assertTrue(PBlockStateMachine.isProtected(PBlockState.DEGRADED))
        assertFalse(PBlockStateMachine.isProtected(PBlockState.DISCONNECTED))
        assertFalse(PBlockStateMachine.isProtected(PBlockState.SETUP_INCOMPLETE))
    }

    @Test fun `locked states are PARTNER_LOCKED and COOLDOWN_PENDING`() {
        assertTrue(PBlockStateMachine.isLocked(PBlockState.PARTNER_LOCKED))
        assertTrue(PBlockStateMachine.isLocked(PBlockState.COOLDOWN_PENDING))
        assertFalse(PBlockStateMachine.isLocked(PBlockState.ACTIVE))
        assertFalse(PBlockStateMachine.isLocked(PBlockState.DEGRADED))
    }
}