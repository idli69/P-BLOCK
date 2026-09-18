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
