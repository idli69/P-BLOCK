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
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.pblock.app.MainActivity
import com.pblock.app.data.PreferencesManager
import com.pblock.app.state.PBlockState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * RemoteSyncManager: auth'd Firebase integration.
 *
 * All device data lives under `devices/{anonymousUid}/...`, matching the
 * deny-by-default database rules:
 *   1. Pushes this device's FCM token (partner dashboard push target).
 *   2. Heartbeat every 5 minutes (liveness monitor).
 *   3. Syncs live state + blocked-query counters.
 *   4. Listens for LOCK / UNLOCK commands under `commands/` from the partner.
 *   5. Applies remote lock/unlock and shows a local notification.
 */
class RemoteSyncManager(
    private val context: Context,
    private val prefs: PreferencesManager,
    private val accountabilityManager: AccountabilityManager,
    private val authManager: AuthManager,
    private val onLock: suspend () -> Unit,
    private val onUnlock: suspend () -> Unit
) {
    private val TAG = "RemoteSyncManager"
    private val DB_URL = "https://p-block-69-default-rtdb.firebaseio.com"
    private val CHANNEL_ID = "pblock_alerts"
    private val scope = CoroutineScope(Dispatchers.IO)

    enum class SyncStatus { OFFLINE, SYNCING, SYNCED }
    private val _syncStatus = kotlinx.coroutines.flow.MutableStateFlow(SyncStatus.OFFLINE)
    val syncStatus: kotlinx.coroutines.flow.StateFlow<SyncStatus> = _syncStatus

    private val _lastSyncTime = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val lastSyncTime: kotlinx.coroutines.flow.StateFlow<Long> = _lastSyncTime

    fun start() {
        scope.launch {
            setupFirebase()
        }
    }

    /** Mirror the persisted app state to this device's RTDB node. */
    suspend fun publishState(state: PBlockState) {
        val uid = authManager.deviceUid.value ?: return
        try {
            Firebase.database(DB_URL)
                .getReference("devices/$uid/state")
                .setValue(state.name)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish state", e)
        }
    }

    private suspend fun setupFirebase() {
        val uid = authManager.deviceUid.first { it != null } ?: return
        if (authManager.auth.currentUser == null) return

        val db = Firebase.database(DB_URL)
        val deviceRoot = db.getReference("devices/$uid")
        val activeRef  = deviceRoot.child("last_active")
        val blockedRef = deviceRoot.child("blocked_count")
        val tokenRef   = deviceRoot.child("fcm_token")

        val topAllowedRef = deviceRoot.child("top_allowed")
        val topBlockedRef = deviceRoot.child("top_blocked")
        val customBlocksRef = deviceRoot.child("custom_blocks")
        val usageRef = deviceRoot.child("daily_usage")
        val pickupsRef = deviceRoot.child("daily_pickups")
        val commandsRef = deviceRoot.child("commands")

        Log.i(TAG, "Firebase connected at devices/$uid")

        // 1. Upload FCM push token so the partner dashboard can notify this device
        try {
            val token = Firebase.messaging.token.await()
            tokenRef.setValue(token)
            Log.i(TAG, "FCM token uploaded")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload FCM token", e)
        }

        val usageTracker = com.pblock.app.domain.UsageTracker(context)

        // 2. Heartbeat loop — runs forever while the app is alive
        scope.launch {
            while (true) {
                try {
                    _syncStatus.value = SyncStatus.SYNCING
                    prefs.flushPendingQueries()
                    
                    activeRef.setValue(System.currentTimeMillis()).await()
                    blockedRef.setValue(prefs.blockedQueries.first())
                    topAllowedRef.setValue(com.pblock.app.domain.StatsTracker.getTopAllowed())
                    topBlockedRef.setValue(com.pblock.app.domain.StatsTracker.getTopBlocked())

                    // Analytics
                    pickupsRef.setValue(prefs.dailyPickups.first())
                    try {
                        usageRef.setValue(usageTracker.getDailyAppUsage())
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get usage stats (permission missing?)", e)
                    }
                    
                    _syncStatus.value = SyncStatus.SYNCED
                    _lastSyncTime.value = System.currentTimeMillis()
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat failed", e)
                    _syncStatus.value = SyncStatus.OFFLINE
                }
                delay(60 * 1000L) // every 60 seconds
            }
        }

        // 3. Listen for partner commands (LOCK / UNLOCK). Full command lifecycle
        //    (PENDING -> DELIVERED -> ACKNOWLEDGED) arrives in Phase 4.
        commandsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val commands = snapshot.getValue<Map<String, Any>>() ?: return
                for ((commandId, value) in commands) {
                    scope.launch {
                        handleCommand(commandId, value, commandsRef)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Commands listener cancelled", error.toException())
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

        // Listen for admin removals (unlink flow)
        val adminsRef = deviceRoot.child("admins")
        adminsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // If there are no admins, and we were protected, it means we got unlinked.
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    scope.launch {
                        val isProtected = prefs.isProtected.first()
                        if (isProtected) {
                            Log.w(TAG, "No admins left. Device was unlinked.")
                            prefs.setProtected(false, resetStreak = true)
                            prefs.clearOfflinePin()
                            com.pblock.app.state.PBlockStateMachine // To access the object if needed
                            // We don't have direct access to appStateController here easily,
                            // but we can publish a state change or wait for UI.
                            // Actually, MainActivity passes `onUnlinked` callback or we can use `AppForeground` later.
                            // To make it robust without callback changes, we can rely on `isProtected` 
                            // turning false which modifies `uiState` in AppStateController if it drops.
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private suspend fun handleCommand(
        commandId: String,
        value: Any,
        commandsRef: com.google.firebase.database.DatabaseReference
    ) {
        val map = value as? Map<*, *> ?: return
        val command = map["type"]?.toString() ?: return
        val status = map["status"]?.toString() ?: "PENDING"
        
        if (status == "ACKNOWLEDGED") return // Already done

        if (status == "PENDING") {
            // Mark DELIVERED
            commandsRef.child(commandId).child("status").setValue("DELIVERED")
        }
        
        Log.d(TAG, "Command $commandId -> $command ($status)")
        when (command) {
            "UNLOCK" -> {
                accountabilityManager.remoteUnlock()
                onUnlock()
                commandsRef.child(commandId).child("status").setValue("ACKNOWLEDGED")
                showLocalNotification(
                    title = "Protection Disabled",
                    body = "Your accountability partner has unlocked P-BLOCK."
                )
            }
            "LOCK" -> {
                accountabilityManager.remoteLock()
                onLock()
                commandsRef.child(commandId).child("status").setValue("ACKNOWLEDGED")
                showLocalNotification(
                    title = "Protection Locked",
                    body = "Your accountability partner has locked P-BLOCK."
                )
            }
        }
    }

    /**
     * Called when the user taps "Emergency Unlock". Writes a flag to Firebase
     * so the partner web dashboard shows a badge. (Push to the partner is
     * dashboard-open-only by design and is a backlog item for a Blaze-triggered
     * Cloud Function.)
     */
    fun notifyPartnerEmergencyRequest() {
        scope.launch {
            val uid = authManager.deviceUid.value ?: return@launch
            try {
                Firebase.database(DB_URL)
                    .getReference("devices/$uid/emergency_request")
                    .setValue(System.currentTimeMillis())
                Log.i(TAG, "Emergency unlock request written to Firebase")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write emergency request", e)
            }
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