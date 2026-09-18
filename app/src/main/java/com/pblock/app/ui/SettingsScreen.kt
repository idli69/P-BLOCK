package com.pblock.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pblock.app.data.BlocklistLoader
import com.pblock.app.data.PreferencesManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    blocklistLoader: BlocklistLoader,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var logs by remember { mutableStateOf(blocklistLoader.getLogs()) }
    val cooldown by prefs.cooldownDuration.collectAsState(initial = 15 * 60 * 1000L)
    val topicId by prefs.partnerTopicId.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Partner Topic ID section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Accountability Partner", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (topicId != null) {
                            Text("Topic ID:", style = MaterialTheme.typography.labelMedium)
                            SelectionContainer {
                                Text(
                                    topicId!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Share this ID with your partner so they can open the web dashboard and monitor you remotely.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("No partner code set. Complete onboarding first.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Cooldown duration section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Emergency Unlock Cooldown", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "How long you must wait after requesting an emergency unlock before the VPN disables.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val options = listOf(
                            15 * 60 * 1000L to "15 Minutes",
                            60 * 60 * 1000L to "1 Hour",
                            6 * 60 * 60 * 1000L to "6 Hours",
                            24 * 60 * 60 * 1000L to "24 Hours"
                        )
                        options.forEach { (duration, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = cooldown == duration,
                                    onClick = {
                                        coroutineScope.launch { prefs.setCooldownDuration(duration) }
                                    }
                                )
                                Text(label)
                            }
                        }
                    }
                }
            }

            // Event logs section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Event Logs", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = {
                                blocklistLoader.clearLogs()
                                logs = blocklistLoader.getLogs()
                            }) { Text("Clear") }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        ) {
                            Text(
                                logs,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            // Crashlytics Test Section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Developer Tools", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { throw RuntimeException("Test Crash for Firebase Crashlytics") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Force Crash (Test Crashlytics)")
                        }
                    }
                }
            }
        }
    }
}


