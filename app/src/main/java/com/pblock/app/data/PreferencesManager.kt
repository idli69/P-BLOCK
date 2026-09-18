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
    }

    val isProtected: Flow<Boolean> = context.dataStore.data.map { it[IS_PROTECTED] ?: false }
    val partnerCodeEncrypted: Flow<String?> = context.dataStore.data.map { it[PARTNER_CODE_ENCRYPTED] }
    val cooldownDuration: Flow<Long> = context.dataStore.data.map { it[COOLDOWN_DURATION] ?: (15 * 60 * 1000L) }
    val emergencyUnlockStart: Flow<Long> = context.dataStore.data.map { it[EMERGENCY_UNLOCK_START] ?: 0L }

    suspend fun setProtected(value: Boolean) {
        context.dataStore.edit { it[IS_PROTECTED] = value }
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
