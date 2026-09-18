package com.pblock.app.accountability

import com.pblock.app.data.PreferencesManager
import com.pblock.app.data.SecureKeyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.pblock.app.data.BlocklistLoader
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class AccountabilityManager(
    private val prefs: PreferencesManager,
    private val secureKeyManager: SecureKeyManager,
    private val blocklistLoader: BlocklistLoader,
    private val authManager: AuthManager
) {
    suspend fun generateAndSetPartnerCode(pairingCode: String, offlinePin: String, onPaired: suspend () -> Unit) {
        val encryptedPin = secureKeyManager.encryptPartnerCode(offlinePin)
        
        // Write to pending_codes and listen for claim
        val uid = authManager.deviceUid.value
        if (uid != null) {
            val db = com.google.firebase.ktx.Firebase.database("https://p-block-69-default-rtdb.firebaseio.com")
            val pendingRef = db.getReference("pending_codes/${pairingCode.replace("-", "")}")
            val codeData = mapOf(
                "device_uid" to uid,
                "expires_at" to System.currentTimeMillis() + 10 * 60 * 1000L,
                "device_name" to android.os.Build.MODEL
            )
            pendingRef.setValue(codeData)
            
            // Listen for admin claim
            pendingRef.child("claimed_by").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val adminUid = snapshot.getValue(String::class.java)
                    if (adminUid != null) {
                        // Admin claimed it! Add admin to devices/$uid/admins
                        db.getReference("devices/$uid/admins/$adminUid").setValue(true)
                        // Also link device in admin's node
                        db.getReference("admins/$adminUid/devices/$uid").setValue(mapOf("name" to android.os.Build.MODEL))
                        // Delete pending code
                        pendingRef.removeValue()
                        // Proceed to active protection
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            prefs.setOfflinePin(encryptedPin)
                            prefs.setProtected(true)
                            blocklistLoader.logEvent("Protection enabled with admin: $adminUid")
                            onPaired()
                        }
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        }
    }

    /**
     * Verifies offline PIN and unlocks. This is a user-initiated unlock
     * so it RESETS the streak (resetStreak = true).
     */
    suspend fun verifyAndUnlock(inputPin: String): Boolean {
        val encryptedPin = prefs.offlinePinEncrypted.first() ?: return false

        if (secureKeyManager.verifyPartnerCode(encryptedPin, inputPin)) {
            prefs.setProtected(false, resetStreak = true)
            prefs.clearEmergencyUnlock()
            blocklistLoader.logEvent("Protection unlocked via offline PIN")
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

    suspend fun getRemainingCooldownMs(): Long {
        val startTime = prefs.emergencyUnlockStart.first()
        if (startTime == 0L) return 0L
        val duration = prefs.cooldownDuration.first()
        val elapsed = System.currentTimeMillis() - startTime
        return if (elapsed < duration) duration - elapsed else 0L
    }
}
