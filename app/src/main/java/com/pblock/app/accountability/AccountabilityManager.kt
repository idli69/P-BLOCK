package com.pblock.app.accountability

import com.pblock.app.data.PreferencesManager
import com.pblock.app.data.SecureKeyManager
import kotlinx.coroutines.flow.first
import com.pblock.app.data.BlocklistLoader
import kotlin.math.abs

class AccountabilityManager(
    private val prefs: PreferencesManager,
    private val secureKeyManager: SecureKeyManager,
    private val blocklistLoader: BlocklistLoader
) {
    fun deriveTopicId(plainCode: String): String {
        val cleanCode = plainCode.replace("-", "").uppercase()
        return "pblock_$cleanCode"
    }

    suspend fun generateAndSetPartnerCode(code: String) {
        val encrypted = secureKeyManager.encryptPartnerCode(code)
        val topicId = deriveTopicId(code)
        prefs.setPartnerCode(encrypted, topicId)  // stores both encrypted code AND stable topicId
        prefs.setProtected(true)
        blocklistLoader.logEvent("Protection enabled with partner code. Topic: $topicId")
    }

    /**
     * Verifies partner code and unlocks. This is a user-initiated unlock
     * so it RESETS the streak (resetStreak = true).
     */
    suspend fun verifyAndUnlock(inputCode: String): Boolean {
        val encryptedCode = prefs.partnerCodeEncrypted.first() ?: return false

        if (secureKeyManager.verifyPartnerCode(encryptedCode, inputCode)) {
            prefs.setProtected(false, resetStreak = true)
            prefs.clearEmergencyUnlock()
            blocklistLoader.logEvent("Protection unlocked via partner code")
            return true
        }
        blocklistLoader.logEvent("Failed unlock attempt")
        return false
    }

    /**
     * Firebase-triggered remote unlock — does NOT reset the streak
     * because the partner is granting access, not the user giving up.
     */
    suspend fun remoteUnlock() {
        prefs.setProtected(false, resetStreak = false)
        prefs.clearEmergencyUnlock()
        blocklistLoader.logEvent("Protection disabled via remote Firebase unlock")
    }

    /**
     * Firebase-triggered remote lock from the accountability partner.
     * Keeps the device protected until the partner unlocks it.
     */
    suspend fun remoteLock() {
        prefs.setProtected(true)   // does not reset the streak
        prefs.clearEmergencyUnlock()
        blocklistLoader.logEvent("Protection enabled via remote Firebase lock")
    }

    suspend fun requestEmergencyUnlock() {
        val currentStart = prefs.emergencyUnlockStart.first()
        if (currentStart == 0L) {
            prefs.startEmergencyUnlock(System.currentTimeMillis())
            blocklistLoader.logEvent("Emergency unlock cooldown started")
        }
    }

    suspend fun checkEmergencyUnlockStatus(): Boolean {
        val startTime = prefs.emergencyUnlockStart.first()
        if (startTime == 0L) return false

        val duration = prefs.cooldownDuration.first()
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed >= duration) {
            // Emergency cooldown expired — this IS a user-initiated "give up" so streak resets
            prefs.setProtected(false, resetStreak = true)
            prefs.clearEmergencyUnlock()
            blocklistLoader.logEvent("Emergency unlock cooldown expired — protection disabled")
            return true
        }
        return false
    }

    suspend fun getRemainingCooldownMs(): Long {
        val startTime = prefs.emergencyUnlockStart.first()
        if (startTime == 0L) return 0L
        val duration = prefs.cooldownDuration.first()
        val elapsed = System.currentTimeMillis() - startTime
        return if (elapsed < duration) duration - elapsed else 0L
    }
}
