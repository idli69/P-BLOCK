package com.pblock.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import com.pblock.app.admin.AdminReceiver
import com.pblock.app.data.PreferencesManager
import com.pblock.app.state.PBlockState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(
    prefs: PreferencesManager,
    isProtected: Boolean,
    appState: PBlockState,
    onNavigateToUnlock: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onStartVpn: () -> Unit
) {
    val emergencyStart by prefs.emergencyUnlockStart.collectAsState(initial = 0L)
    val cooldown by prefs.cooldownDuration.collectAsState(initial = 15 * 60 * 1000L)
    val totalQueries by prefs.totalQueries.collectAsState(initial = 0L)
    val blockedQueries by prefs.blockedQueries.collectAsState(initial = 0L)
    val streakStart by prefs.streakStartDate.collectAsState(initial = 0L)
    val topicId by prefs.partnerTopicId.collectAsState(initial = null)
    val vpnStatus by prefs.vpnStatus.collectAsState(initial = PreferencesManager.VPN_STATUS_NOT_RUNNING)

    var timeRemaining by remember { mutableStateOf(0L) }

    val infiniteTransition = rememberInfiniteTransition(label = "shield_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(emergencyStart, cooldown) {
        if (emergencyStart > 0) {
            while (true) {
                val elapsed = System.currentTimeMillis() - emergencyStart
                val rem = cooldown - elapsed
                if (rem <= 0) { timeRemaining = 0L; break }
                timeRemaining = rem
                delay(1000)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("P-BLOCK", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Persisted state machine chip
            val stateTint = when (appState) {
                PBlockState.ACTIVE -> Color(0xFF4CAF50)
                PBlockState.DEGRADED -> Color(0xFFFF9800)
                PBlockState.PARTNER_LOCKED, PBlockState.COOLDOWN_PENDING -> Color(0xFFE53935)
                PBlockState.SETUP_INCOMPLETE, PBlockState.DISCONNECTED -> Color(0xFFB0BEC5)
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = stateTint.copy(alpha = 0.18f)
            ) {
                Text(
                    text = appState.name.replace("_", " "),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = stateTint
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isProtected) {
                // Shield icon with pulsing animation
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Protected",
                    modifier = Modifier.size(96.dp).scale(pulseScale),
                    tint = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Protection ACTIVE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF4CAF50)
                )

                // Streak counter
                val streakDays = if (streakStart > 0)
                    ((System.currentTimeMillis() - streakStart) / (1000L * 60 * 60 * 24)).toInt()
                else 0
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Day Streak: $streakDays",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFFF9800)
                )

                if (vpnStatus == PreferencesManager.VPN_STATUS_FAILED) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            "VPN failed to start. Protection is NOT active — please try again.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (vpnStatus != PreferencesManager.VPN_STATUS_RUNNING) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            "VPN is not confirmed running — blocking may not be enforced.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Statistics card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$totalQueries", style = MaterialTheme.typography.headlineMedium)
                            Text("Total Queries", style = MaterialTheme.typography.labelMedium)
                        }
                        HorizontalDivider(modifier = Modifier.height(48.dp).width(1.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$blockedQueries",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color(0xFFE53935)
                            )
                            Text("Blocked", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Firebase sync info card
                if (topicId != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Partner Topic ID", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                topicId!!,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Share this with your accountability partner",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Cooldown timer banner
                if (emergencyStart > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Emergency Unlock Requested", color = MaterialTheme.colorScheme.onErrorContainer)
                            val mins = (timeRemaining / 1000) / 60
                            val secs = (timeRemaining / 1000) % 60
                            Text(
                                "Unlocks in ${mins}m ${secs}s",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Request Unlock button
                Button(
                    onClick = onNavigateToUnlock,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Request Unlock")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Device admin / uninstall protection
                val context = LocalContext.current
                val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val componentName = ComponentName(context, AdminReceiver::class.java)
                val isAdminActive = dpm.isAdminActive(componentName)

                if (!isAdminActive) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "Enables uninstall protection — your accountability partner will be notified if removed.")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable Uninstall Protection")
                    }
                } else {
                    Text(
                        "Uninstall Protection: Active",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

            } else {
                // Unprotected state
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Unprotected",
                    modifier = Modifier.size(96.dp),
                    tint = Color(0xFFE53935)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Protection DISABLED",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFE53935)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "You are unprotected. Start the VPN to re-enable filtering.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onStartVpn,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("START PROTECTION")
                }
            }
        }
    }
}
