package com.pblock.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import com.pblock.app.admin.AdminReceiver
import com.pblock.app.data.PreferencesManager
import kotlinx.coroutines.delay
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(
    prefs: PreferencesManager,
    isProtected: Boolean,
    onNavigateToUnlock: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onStartVpn: () -> Unit
) {
    val emergencyStart by prefs.emergencyUnlockStart.collectAsState(initial = 0L)
    val cooldown by prefs.cooldownDuration.collectAsState(initial = 15 * 60 * 1000L)
    
    val totalQueries by prefs.totalQueries.collectAsState(initial = 0L)
    val blockedQueries by prefs.blockedQueries.collectAsState(initial = 0L)
    val streakStart by prefs.streakStartDate.collectAsState(initial = 0L)
    
    var timeRemaining by remember { mutableStateOf(0L) }
    
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("P-BLOCK") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Text("⚙️") // Simple settings icon
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isProtected) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Protected",
                    modifier = Modifier.size(80.dp).scale(pulseScale),
                    tint = Color(0xFF4CAF50) // Green for protected
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Protection is ACTIVE", style = MaterialTheme.typography.headlineMedium)
                
                val streakDays = if (streakStart > 0) ((System.currentTimeMillis() - streakStart) / (1000 * 60 * 60 * 24)) else 0
                Text("🔥 $streakDays Day Streak", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFF9800))
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp), 
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Total DNS Queries", style = MaterialTheme.typography.labelLarge)
                        Text("$totalQueries", style = MaterialTheme.typography.headlineLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Queries Blocked", style = MaterialTheme.typography.labelLarge)
                        Text("$blockedQueries", style = MaterialTheme.typography.headlineLarge, color = Color(0xFFE53935))
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                val partnerCode by prefs.partnerCodeEncrypted.collectAsState(initial = null)
                if (partnerCode != null) {
                    val topicId = "pblock_sync_" + Math.abs(partnerCode!!.hashCode())
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Firebase Remote Control", style = MaterialTheme.typography.titleMedium)
                            Text("Partner can unlock by writing 'UNLOCK' to:", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            Text("users/$topicId/status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                        }
                    }
                }
                
                if (emergencyStart > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Emergency unlock requested.", color = MaterialTheme.colorScheme.error)
                    val mins = (timeRemaining / 1000) / 60
                    val secs = (timeRemaining / 1000) % 60
                    Text("Cooldown remaining: ${mins}m ${secs}s", color = MaterialTheme.colorScheme.error)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onNavigateToUnlock,
                    modifier = Modifier.fillMaxWidth(0.7f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Request Unlock")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                val context = LocalContext.current
                val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val componentName = ComponentName(context, AdminReceiver::class.java)
                val isAdminActive = dpm.isAdminActive(componentName)
                
                if (!isAdminActive) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Activate this to prevent easy uninstallation of P-BLOCK.")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        Text("Enable Uninstall Protection")
                    }
                } else {
                    Text("Uninstall Protection is Active", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Unprotected",
                    modifier = Modifier.size(80.dp),
                    tint = Color(0xFFE53935) // Red for unlocked
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Protection is DISABLED", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "You are currently unprotected. You must start the VPN from the Settings menu.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onStartVpn,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("START PROTECTION")
                }
            }
        }
    }
}
