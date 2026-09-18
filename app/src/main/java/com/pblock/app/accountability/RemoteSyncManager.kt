package com.pblock.app.accountability

import android.util.Log
import com.pblock.app.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class RemoteSyncManager(
    private val prefs: PreferencesManager,
    private val accountabilityManager: AccountabilityManager
) {
    private val TAG = "RemoteSyncManager"

    suspend fun startPolling() {
        withContext(Dispatchers.IO) {
            while (true) {
                try {
                    val isProtected = prefs.isProtected.first()
                    if (isProtected) {
                        val partnerCodeEncrypted = prefs.partnerCodeEncrypted.first()
                        if (partnerCodeEncrypted != null) {
                            // Derive a topic ID from the encrypted partner code hash (to keep it somewhat obscure but deterministic)
                            val topicId = "pblock_sync_" + Math.abs(partnerCodeEncrypted.hashCode())
                            
                            val url = URL("https://ntfy.sh/$topicId/json?poll=1")
                            val connection = url.openConnection() as HttpURLConnection
                            connection.requestMethod = "GET"
                            connection.connectTimeout = 5000
                            connection.readTimeout = 5000

                            if (connection.responseCode == 200) {
                                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    val obj = JSONObject(line!!)
                                    if (obj.optString("event") == "message" && obj.optString("message").trim() == "UNLOCK") {
                                        Log.i(TAG, "Remote UNLOCK command received!")
                                        // Acknowledge by unlocking (in a real app we'd verify a signed payload or password)
                                        prefs.setProtected(false)
                                        prefs.clearEmergencyUnlock()
                                        break
                                    }
                                }
                                reader.close()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error polling remote sync", e)
                }
                
                // Poll every 30 seconds
                delay(30000)
            }
        }
    }
}
