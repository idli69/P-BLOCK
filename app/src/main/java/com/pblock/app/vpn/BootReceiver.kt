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

/**
 * Restarts the VPN service automatically after device reboot, as long as
 * the user had protection enabled before shutdown.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i("BootReceiver", "Boot completed — checking if VPN should restart")

        CoroutineScope(Dispatchers.IO).launch {
            val prefs = PreferencesManager(context)
            val isProtected = prefs.isProtected.first()
            if (isProtected) {
                // Only start if the user has already granted VPN permission
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent == null) {
                    // Permission already granted — restart VPN
                    val serviceIntent = Intent(context, BlockingVpnService::class.java)
                    context.startForegroundService(serviceIntent)
                    Log.i("BootReceiver", "VPN service restarted after boot")
                } else {
                    Log.w("BootReceiver", "VPN permission not pre-granted — cannot auto-start")
                }
            }
        }
    }
}
