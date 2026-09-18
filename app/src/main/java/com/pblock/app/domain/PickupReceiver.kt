package com.pblock.app.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pblock.app.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PickupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            Log.i("PickupReceiver", "Screen unlocked (Pickup detected)")
            val prefs = PreferencesManager(context)
            CoroutineScope(Dispatchers.IO).launch {
                prefs.incrementPickups()
            }
        }
    }
}
