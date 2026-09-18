package com.pblock.app.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        recordTamperEvent("Attempted to disable Device Admin (Uninstall Protection)")
        return "Disabling this will notify your accountability partner."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        recordTamperEvent("Device Admin disabled (Uninstall Protection removed)")
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
