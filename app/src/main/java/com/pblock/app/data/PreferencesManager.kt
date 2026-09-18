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
        val PARTNER_TOPIC_ID = stringPreferencesKey("partner_topic_id") // derived from plain code BEFORE encryption
        val COOLDOWN_DURATION = longPreferencesKey("cooldown_duration")
        val EMERGENCY_UNLOCK_START = longPreferencesKey("emergency_unlock_start")
        val TOTAL_QUERIES = longPreferencesKey("total_queries")
        val BLOCKED_QUERIES = longPreferencesKey("blocked_queries")
        val STREAK_START_DATE = longPreferencesKey("streak_start_date")
        val PICKUPS_COUNT = intPreferencesKey("pickups_count")
        val PICKUPS_LAST_DATE = longPreferencesKey("pickups_last_date")
    }

    val isProtected: Flow<Boolean> = context.dataStore.data.map { it[IS_PROTECTED] ?: false }
    val partnerCodeEncrypted: Flow<String?> = context.dataStore.data.map { it[PARTNER_CODE_ENCRYPTED] }
    val partnerTopicId: Flow<String?> = context.dataStore.data.map { it[PARTNER_TOPIC_ID] }
    val cooldownDuration: Flow<Long> = context.dataStore.data.map { it[COOLDOWN_DURATION] ?: (15 * 60 * 1000L) }
    val emergencyUnlockStart: Flow<Long> = context.dataStore.data.map { it[EMERGENCY_UNLOCK_START] ?: 0L }
    val totalQueries: Flow<Long> = context.dataStore.data.map { it[TOTAL_QUERIES] ?: 0L }
    val blockedQueries: Flow<Long> = context.dataStore.data.map { it[BLOCKED_QUERIES] ?: 0L }
    val streakStartDate: Flow<Long> = context.dataStore.data.map { it[STREAK_START_DATE] ?: 0L }

    suspend fun incrementQueries(blocked: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[TOTAL_QUERIES] = (prefs[TOTAL_QUERIES] ?: 0L) + 1
            if (blocked) {
                prefs[BLOCKED_QUERIES] = (prefs[BLOCKED_QUERIES] ?: 0L) + 1
            }
        }
    }

    /**
     * Sets protected state. `resetStreak` controls whether unlocking also resets the streak.
     * Partner-initiated remote unlocks should NOT reset the streak (that would be unfair on the user).
     * Only a user-initiated emergency unlock should reset it.
     */
    suspend fun setProtected(value: Boolean, resetStreak: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[IS_PROTECTED] = value
            if (value) {
                // Start streak if not already running
                if ((prefs[STREAK_START_DATE] ?: 0L) == 0L) {
                    prefs[STREAK_START_DATE] = System.currentTimeMillis()
                }
            } else if (resetStreak) {
                prefs[STREAK_START_DATE] = 0L
            }
        }
    }

    suspend fun setPartnerCode(encryptedCode: String, plainTopicId: String) {
        context.dataStore.edit {
            it[PARTNER_CODE_ENCRYPTED] = encryptedCode
            it[PARTNER_TOPIC_ID] = plainTopicId
        }
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

    val dailyPickups: Flow<Int> = context.dataStore.data.map { prefs ->
        val lastDate = prefs[PICKUPS_LAST_DATE] ?: 0L
        val todayStart = getStartOfDay()
        if (lastDate < todayStart) 0 else (prefs[PICKUPS_COUNT] ?: 0)
    }

    suspend fun incrementPickups() {
        context.dataStore.edit { prefs ->
            val lastDate = prefs[PICKUPS_LAST_DATE] ?: 0L
            val todayStart = getStartOfDay()
            
            if (lastDate < todayStart) {
                prefs[PICKUPS_COUNT] = 1
                prefs[PICKUPS_LAST_DATE] = System.currentTimeMillis()
            } else {
                val current = prefs[PICKUPS_COUNT] ?: 0
                prefs[PICKUPS_COUNT] = current + 1
            }
        }
    }

    private fun getStartOfDay(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
