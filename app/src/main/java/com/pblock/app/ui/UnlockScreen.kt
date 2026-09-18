package com.pblock.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pblock.app.accountability.AccountabilityManager
import kotlinx.coroutines.launch

@Composable
fun UnlockScreen(
    accountabilityManager: AccountabilityManager,
    onUnlockSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var inputCode by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    var showEmergencyConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Unlock Protection", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = inputCode,
            onValueChange = { inputCode = it },
            label = { Text("Partner Code") },
            modifier = Modifier.fillMaxWidth()
        )
        
        if (errorMsg != null) {
            Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                coroutineScope.launch {
                    val success = accountabilityManager.verifyAndUnlock(inputCode)
                    if (success) {
                        onUnlockSuccess()
                    } else {
                        errorMsg = "Invalid code"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock Immediately")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("Can't reach your partner?")
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = { showEmergencyConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Emergency Unlock")
        }
        
        if (showEmergencyConfirm) {
            AlertDialog(
                onDismissRequest = { showEmergencyConfirm = false },
                title = { Text("Emergency Unlock") },
                text = { Text("This will start the cooldown timer. The app remains protected until the timer expires. Continue?") },
                confirmButton = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            accountabilityManager.requestEmergencyUnlock()
                        }
                        showEmergencyConfirm = false
                    }) {
                        Text("Start Timer")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmergencyConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
