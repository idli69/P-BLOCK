package com.pblock.app.vpn

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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
            Log.w(TAG, "Anti-tamper triggered! Redirecting to YouTube Music.")
            performGlobalAction(GLOBAL_ACTION_HOME)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch redirect intent", e)
            }
        }
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
