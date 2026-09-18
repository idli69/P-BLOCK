package com.pblock.app.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.pblock.app.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

/**
 * Restarts the VPN service automatically after device reboot, as long as
 * the user had protection enabled before shutdown.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        Log.i("BootReceiver", "$action received — checking if VPN should restart")

        CoroutineScope(Dispatchers.IO).launch {
            val prefs = PreferencesManager(context)
            val isProtected = prefs.isProtected.first()
            if (isProtected) {
                // Log reboot tamper event
                recordTamperEvent("Device was rebooted while protection was active")

                // Only start if the user has already granted VPN permission
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent == null) {
                    // Permission already granted — restart VPN
                    val serviceIntent = Intent(context, BlockingVpnService::class.java)
                    try {
                        context.startForegroundService(serviceIntent)
                        Log.i("BootReceiver", "VPN service restarted after $action")
                    } catch (e: Exception) {
                        Log.w("BootReceiver", "Could not start VPN foreground service from $action", e)
                    }
                } else {
                    Log.w("BootReceiver", "VPN permission not pre-granted — cannot auto-start")
                }
            }
        }
    }

    private fun recordTamperEvent(actionType: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val db = Firebase.database("https://p-block-69-default-rtdb.firebaseio.com")
        val feedRef = db.getReference("devices/$uid/activity_feed")
        
        val event = mapOf(
            "type" to "TAMPER",
            "action" to actionType,
            "device_name" to android.os.Build.MODEL,
            "timestamp" to System.currentTimeMillis()
        )
        feedRef.push().setValue(event)
    }
}
