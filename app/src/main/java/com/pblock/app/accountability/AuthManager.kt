package com.pblock.app.accountability

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Owns anonymous Firebase Auth. The device's anonymous UID is the key of its
 * node under `devices/{uid}` in the Realtime Database, and is the identity
 * the new deny-by-default rules trust for self-writes.
 */
class AuthManager {
    val auth: FirebaseAuth = Firebase.auth

    private val _deviceUid = MutableStateFlow<String?>(auth.currentUser?.uid)
    val deviceUid: StateFlow<String?> = _deviceUid.asStateFlow()

    fun start() {
        auth.addAuthStateListener { state ->
            val uid = state.currentUser?.uid
            if (_deviceUid.value != uid) {
                Log.i(TAG, "Auth state changed -> uid=${uid ?: "null"}")
                _deviceUid.value = uid
            }
        }
        // Kick off a sign-in immediately if none exists.
        ensureSignedIn()
    }

    /**
     * Guarantees an authenticated session, retrying with exponential backoff.
     * Returns the signed-in user, or null if all retries failed (callers must
     * degrade gracefully — local blocking keeps working, sync is skipped).
     */
    suspend fun ensureSignedIn(): FirebaseUser? {
        auth.currentUser?.let { return it }

        var attempt = 0
        while (attempt < MAX_SIGN_IN_ATTEMPTS) {
            attempt++
            try {
                val result = auth.signInAnonymously().await()
                Log.i(TAG, "Anonymous sign-in succeeded on attempt $attempt")
                return result.user
            } catch (e: Exception) {
                Log.e(TAG, "Anonymous sign-in failed on attempt $attempt", e)
                if (attempt < MAX_SIGN_IN_ATTEMPTS) {
                    delay(RETRY_DELAY_MS * (1L shl (attempt - 1)))
                }
            }
        }
        return auth.currentUser
    }

    suspend fun signOut() {
        auth.signOut()
        _deviceUid.value = auth.currentUser?.uid
    }

    companion object {
        private const val TAG = "AuthManager"
        private const val MAX_SIGN_IN_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
    }
}