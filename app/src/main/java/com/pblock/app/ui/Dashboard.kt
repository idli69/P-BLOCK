package com.pblock.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.pblock.app.admin.AdminReceiver
import com.pblock.app.data.PreferencesManager
import kotlinx.coroutines.delay

@Composable
fun Dashboard(
    isProtected: Boolean,
    prefs: PreferencesManager,
    onNavigateToUnlock: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onStartVpn: () -> Unit
) {
    val emergencyStart by prefs.emergencyUnlockStart.collectAsState(initial = 0L)
    val cooldown by prefs.cooldownDuration.collectAsState(initial = 15 * 60 * 1000L)
    
    val totalQueries by prefs.totalQueries.collectAsState(initial = 0L)
    val blockedQueries by prefs.blockedQueries.collectAsState(initial = 0L)
    
    var timeRemaining by remember { mutableStateOf(0L) }
    
    LaunchedEffect(emergencyStart, cooldown) {
        if (emergencyStart > 0) {
            while (true) {
                val elapsed = System.currentTimeMillis() - emergencyStart
                val rem = cooldown - elapsed
                if (rem <= 0) {
                    timeRemaining = 0L
                    break
                }
                timeRemaining = rem
                delay(1000)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isProtected) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Protected",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Protection is ACTIVE", style = MaterialTheme.typography.headlineSmall)
            
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total DNS Queries: $totalQueries", style = MaterialTheme.typography.titleMedium)
                    Text("Queries Blocked: $blockedQueries", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            val partnerCode by prefs.partnerCodeEncrypted.collectAsState(initial = null)
            if (partnerCode != null) {
                val topicId = "pblock_sync_" + Math.abs(partnerCode!!.hashCode())
                Text("Remote Unlock Link (For Partner):", style = MaterialTheme.typography.labelMedium)
                Text("https://ntfy.sh/$topicId", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text("(Partner sends message 'UNLOCK')", style = MaterialTheme.typography.labelSmall)
            }
            
            if (emergencyStart > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Emergency unlock requested.")
                val mins = (timeRemaining / 1000) / 60
                val secs = (timeRemaining / 1000) % 60
                Text("Cooldown remaining: ${mins}m ${secs}s")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onNavigateToUnlock) {
                Text("Request Unlock")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            val context = LocalContext.current
            val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(context, AdminReceiver::class.java)
            val isAdminActive = dpm.isAdminActive(componentName)
            
            if (!isAdminActive) {
                OutlinedButton(onClick = {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Activate this to prevent easy uninstallation of P-BLOCK.")
                    }
                    context.startActivity(intent)
                }) {
                    Text("Enable Uninstall Protection")
                }
            } else {
                Text("Uninstall Protection is Active", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Unprotected",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Protection is INACTIVE", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onStartVpn) {
                Text("Enable Protection")
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(onClick = onNavigateToSettings) {
            Text("Settings & Logs")
        }
    }
}
