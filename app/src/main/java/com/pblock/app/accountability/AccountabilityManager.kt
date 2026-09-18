package com.pblock.app.accountability

import com.pblock.app.data.PreferencesManager
import com.pblock.app.data.SecureKeyManager
import kotlinx.coroutines.flow.first
import com.pblock.app.data.BlocklistLoader

class AccountabilityManager(
    private val prefs: PreferencesManager,
    private val secureKeyManager: SecureKeyManager,
    private val blocklistLoader: BlocklistLoader
) {
    suspend fun generateAndSetPartnerCode(code: String) {
        val encrypted = secureKeyManager.encryptPartnerCode(code)
        prefs.setPartnerCode(encrypted)
        prefs.setProtected(true)
        blocklistLoader.logEvent("Protection enabled with partner code")
    }

    suspend fun verifyAndUnlock(inputCode: String): Boolean {
        val encryptedCode = prefs.partnerCodeEncrypted.first()
        if (encryptedCode == null) return false
        
        if (secureKeyManager.verifyPartnerCode(encryptedCode, inputCode)) {
            prefs.setProtected(false)
            prefs.clearEmergencyUnlock()
            blocklistLoader.logEvent("Protection unlocked successfully using partner code")
            return true
        }
        blocklistLoader.logEvent("Failed unlock attempt with partner code")
        return false
    }

    suspend fun requestEmergencyUnlock() {
        val currentStart = prefs.emergencyUnlockStart.first()
        if (currentStart == 0L) {
            prefs.startEmergencyUnlock(System.currentTimeMillis())
            blocklistLoader.logEvent("Emergency unlock requested")
        }
    }

    suspend fun checkEmergencyUnlockStatus(): Boolean {
        val startTime = prefs.emergencyUnlockStart.first()
        if (startTime == 0L) return false

        val duration = prefs.cooldownDuration.first()
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed >= duration) {
            prefs.setProtected(false)
            prefs.clearEmergencyUnlock()
            blocklistLoader.logEvent("Emergency unlock cooldown expired, protection disabled")
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
