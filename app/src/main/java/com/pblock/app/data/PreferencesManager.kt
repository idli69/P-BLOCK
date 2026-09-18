package com.pblock.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    companion object {
        val IS_PROTECTED = booleanPreferencesKey("is_protected")
        val PARTNER_CODE_ENCRYPTED = stringPreferencesKey("partner_code_encrypted")
        val COOLDOWN_DURATION = longPreferencesKey("cooldown_duration")
        val EMERGENCY_UNLOCK_START = longPreferencesKey("emergency_unlock_start")
        val TOTAL_QUERIES = longPreferencesKey("total_queries")
        val BLOCKED_QUERIES = longPreferencesKey("blocked_queries")
        val STREAK_START_DATE = longPreferencesKey("streak_start_date")
    }

    val isProtected: Flow<Boolean> = context.dataStore.data.map { it[IS_PROTECTED] ?: false }
    val partnerCodeEncrypted: Flow<String?> = context.dataStore.data.map { it[PARTNER_CODE_ENCRYPTED] }
    val cooldownDuration: Flow<Long> = context.dataStore.data.map { it[COOLDOWN_DURATION] ?: (15 * 60 * 1000L) }
    val emergencyUnlockStart: Flow<Long> = context.dataStore.data.map { it[EMERGENCY_UNLOCK_START] ?: 0L }
    val totalQueries: Flow<Long> = context.dataStore.data.map { it[TOTAL_QUERIES] ?: 0L }
    val blockedQueries: Flow<Long> = context.dataStore.data.map { it[BLOCKED_QUERIES] ?: 0L }
    val streakStartDate: Flow<Long> = context.dataStore.data.map { it[STREAK_START_DATE] ?: 0L }

    suspend fun incrementQueries(blocked: Boolean) {
        context.dataStore.edit { prefs ->
            val currentTotal = prefs[TOTAL_QUERIES] ?: 0L
            prefs[TOTAL_QUERIES] = currentTotal + 1
            if (blocked) {
                val currentBlocked = prefs[BLOCKED_QUERIES] ?: 0L
                prefs[BLOCKED_QUERIES] = currentBlocked + 1
            }
        }
    }

    suspend fun setProtected(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_PROTECTED] = value
            if (value) {
                if ((prefs[STREAK_START_DATE] ?: 0L) == 0L) {
                    prefs[STREAK_START_DATE] = System.currentTimeMillis()
                }
            } else {
                prefs[STREAK_START_DATE] = 0L // Reset streak on unlock
            }
        }
    }

    suspend fun setPartnerCode(encryptedCode: String) {
        context.dataStore.edit { it[PARTNER_CODE_ENCRYPTED] = encryptedCode }
    }

    suspend fun setCooldownDuration(durationMs: Long) {
        context.dataStore.edit { it[COOLDOWN_DURATION] = durationMs }
    }

    suspend fun startEmergencyUnlock(startTimeMs: Long) {
        context.dataStore.edit { it[EMERGENCY_UNLOCK_START] = startTimeMs }
    }

    suspend fun clearEmergencyUnlock() {
        context.dataStore.edit { it[EMERGENCY_UNLOCK_START] = 0L }
    }
}
