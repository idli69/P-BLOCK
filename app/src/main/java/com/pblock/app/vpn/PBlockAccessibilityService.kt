package com.pblock.app.vpn

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class PBlockAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "PBlockAccess"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // We only care about events in Settings
        if (event.packageName != "com.android.settings") return

        val rootNode = rootInActiveWindow ?: return
        
        val textOnScreen = extractAllText(rootNode).lowercase()
        
        val mentionsAppOrVpn = textOnScreen.contains("p-block") || 
                               textOnScreen.contains("pblock") || 
                               textOnScreen.contains("vpn")
                               
        val mentionsDanger = textOnScreen.contains("uninstall") || 
                             textOnScreen.contains("force stop") || 
                             textOnScreen.contains("clear data") ||
                             textOnScreen.contains("forget vpn") ||
                             textOnScreen.contains("disconnect")

        if (mentionsAppOrVpn && mentionsDanger) {
            Log.w(TAG, "Anti-tamper triggered!")
            recordTamperEvent("Attempted uninstall or force stop in Settings")
            
            // Note: We don't have access to AppStateController here easily without DI,
            // but we can update a shared preference or emit an event.
            // For now, writing to the RTDB activity feed fulfills the spec.
        }
    }

    private fun recordTamperEvent(actionType: String) {
        val uid = com.google.firebase.ktx.Firebase.auth.currentUser?.uid ?: return
        val db = com.google.firebase.ktx.Firebase.database("https://p-block-69-default-rtdb.firebaseio.com")
        val feedRef = db.getReference("devices/$uid/activity_feed")
        
        val event = mapOf(
            "type" to "TAMPER",
            "action" to actionType,
            "device_name" to android.os.Build.MODEL,
            "timestamp" to System.currentTimeMillis()
        )
        feedRef.push().setValue(event)
    }

    private fun extractAllText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        if (node.text != null) sb.append(node.text).append(" ")
        if (node.contentDescription != null) sb.append(node.contentDescription).append(" ")
        
        for (i in 0 until node.childCount) {
            sb.append(extractAllText(node.getChild(i)))
        }
        return sb.toString()
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
    }
}
