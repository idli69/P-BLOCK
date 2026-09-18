package com.pblock.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pblock.app.data.BlocklistLoader
import com.pblock.app.data.PreferencesManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    blocklistLoader: BlocklistLoader
) {
    val coroutineScope = rememberCoroutineScope()
    var logs by remember { mutableStateOf(blocklistLoader.getLogs()) }
    val cooldown by prefs.cooldownDuration.collectAsState(initial = 15 * 60 * 1000L)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        item {
            Text("Cooldown Duration", style = MaterialTheme.typography.titleMedium)
            val options = listOf(
                15 * 60 * 1000L to "15 Minutes",
                60 * 60 * 1000L to "1 Hour",
                6 * 60 * 60 * 1000L to "6 Hours",
                24 * 60 * 60 * 1000L to "24 Hours"
            )
            options.forEach { (duration, label) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(
                        selected = cooldown == duration,
                        onClick = {
                            coroutineScope.launch {
                                prefs.setCooldownDuration(duration)
                            }
                        }
                    )
                    Text(label)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text("Event Logs", style = MaterialTheme.typography.titleMedium)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp)
            ) {
                Text(logs, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
            }
            
            Button(onClick = {
                blocklistLoader.clearLogs()
                logs = blocklistLoader.getLogs()
            }) {
                Text("Clear Logs")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
