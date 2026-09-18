package com.pblock.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pblock.app.MainActivity
import com.pblock.app.R
import com.pblock.app.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles:
 * 1. FCM token refreshes — saves the new token to Firebase DB so
 *    the partner web dashboard can send push notifications to this device.
 * 2. Incoming push messages from the partner — displays them as Android
 *    system notifications.
 */
class PBlockFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        const val CHANNEL_ID = "pblock_alerts"
        const val DB_URL = "https://p-block-69-default-rtdb.firebaseio.com"
    }

    /**
     * Called whenever FCM rotates this device's push token.
     * We save it to the Firebase DB so the partner dashboard always has the latest one.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token refreshed — uploading to Firebase DB")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = PreferencesManager(applicationContext)
                val topicId = prefs.partnerTopicId.first() ?: return@launch
                Firebase.database(DB_URL)
                    .getReference("users/$topicId/device_token")
                    .setValue(token)
                Log.i(TAG, "FCM token saved under users/$topicId/device_token")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save FCM token", e)
            }
        }
    }

    /**
     * Called when a push notification arrives while the app is in the foreground
     * (background messages are handled automatically by FCM and shown in system tray).
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.i(TAG, "FCM message received: ${message.notification?.title}")

        val title = message.notification?.title ?: message.data["title"] ?: "P-BLOCK"
        val body = message.notification?.body ?: message.data["body"] ?: ""

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        createChannel()
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "P-BLOCK Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Accountability partner notifications" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
