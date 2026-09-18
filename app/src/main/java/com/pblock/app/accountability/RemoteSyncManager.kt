package com.pblock.app.accountability

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.pblock.app.MainActivity
import com.pblock.app.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * RemoteSyncManager: Full Firebase integration.
 *
 * 1. Uploads this device's FCM token so the partner web dashboard can
 *    send direct push notifications.
 * 2. Writes a heartbeat timestamp every 5 minutes (liveness monitor).
 * 3. Syncs live blocked-query count for partner dashboard.
 * 4. Listens for UNLOCK / LOCK commands from the partner.
 * 5. Shows a local notification to the user when partner remotely unlocks.
 */
class RemoteSyncManager(
    private val context: Context,
    private val prefs: PreferencesManager,
    private val accountabilityManager: AccountabilityManager
) {
    private val TAG = "RemoteSyncManager"
    private val DB_URL = "https://p-block-69-default-rtdb.firebaseio.com"
    private val CHANNEL_ID = "pblock_alerts"

    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            setupFirebase()
        }
    }

    private suspend fun setupFirebase() {
        val topicId = prefs.partnerTopicId.first()
        if (topicId == null) {
            Log.w(TAG, "No topic ID yet — will not connect to Firebase")
            return
        }

        val db = Firebase.database(DB_URL)
        val statusRef  = db.getReference("users/$topicId/status")
        val activeRef  = db.getReference("users/$topicId/last_active")
        val blockedRef = db.getReference("users/$topicId/blocked_count")
        val tokenRef   = db.getReference("users/$topicId/device_token")

        val topAllowedRef = db.getReference("users/$topicId/top_allowed")
        val topBlockedRef = db.getReference("users/$topicId/top_blocked")
        val customBlocksRef = db.getReference("users/$topicId/custom_blocks")

        Log.i(TAG, "Firebase connected at users/$topicId")

        // 1. Upload FCM push token so partner dashboard can notify this device
        try {
            val token = Firebase.messaging.token.await()
            tokenRef.setValue(token)
            Log.i(TAG, "FCM token uploaded")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload FCM token", e)
        }

        // 2. Heartbeat loop — runs forever while the app is alive
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    activeRef.setValue(System.currentTimeMillis())
                    blockedRef.setValue(prefs.blockedQueries.first())
                    topAllowedRef.setValue(com.pblock.app.domain.StatsTracker.getTopAllowed())
                    topBlockedRef.setValue(com.pblock.app.domain.StatsTracker.getTopBlocked())
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat failed", e)
                }
                delay(5 * 60 * 1000L) // every 5 minutes
            }
        }

        // 3. Listen for remote commands from partner
        statusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java) ?: return
                Log.d(TAG, "Status update from Firebase: $status")
                when (status) {
                    "UNLOCK" -> {
                        Log.i(TAG, "Remote UNLOCK received!")
                        CoroutineScope(Dispatchers.IO).launch {
                            accountabilityManager.remoteUnlock()
                            statusRef.setValue("LOCKED") // reset so it doesn't fire again
                            showLocalNotification(
                                title = "Protection Disabled",
                                body = "Your accountability partner has unlocked P-BLOCK."
                            )
                        }
                    }
                    "EMERGENCY_ACK" -> {
                        // Partner acknowledged the emergency unlock request notification
                        showLocalNotification(
                            title = "Partner Notified",
                            body = "Your partner has seen your emergency unlock request."
                        )
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase listener cancelled: ${error.message}")
            }
        })

        // 4. Listen for custom blocks from partner dashboard
        customBlocksRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val customBlocks = mutableListOf<String>()
                for (child in snapshot.children) {
                    val domain = child.key
                    val isBlocked = child.getValue(Boolean::class.java) ?: false
                    if (domain != null && isBlocked) {
                        customBlocks.add(domain.replace("_", ".")) // Firebase keys can't have '.', dashboard replaces them with '_'
                    }
                }
                com.pblock.app.vpn.BlockingVpnService.activeFilterEngine?.setCustomBlocks(customBlocks)
                Log.i(TAG, "Loaded ${customBlocks.size} custom blocks from Firebase")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Custom blocks listener cancelled", error.toException())
            }
        })
    }

    /**
     * Called when the user taps "Emergency Unlock". Writes a flag to Firebase
     * so the partner web dashboard shows a badge + sends a push to the partner.
     * (The web dashboard reads this flag and uses the stored FCM token to push notify.)
     */
    fun notifyPartnerEmergencyRequest() {
        CoroutineScope(Dispatchers.IO).launch {
            val topicId = prefs.partnerTopicId.first() ?: return@launch
            Firebase.database(DB_URL)
                .getReference("users/$topicId/emergency_request")
                .setValue(System.currentTimeMillis())
            Log.i(TAG, "Emergency unlock request written to Firebase")
        }
    }

    private fun showLocalNotification(title: String, body: String) {
        createChannel()
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "P-BLOCK Alerts", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Accountability partner alerts" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
