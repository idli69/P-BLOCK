package com.pblock.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pblock.app.accountability.AccountabilityManager
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun OnboardingScreen(
    accountabilityManager: AccountabilityManager,
    onStartVpn: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var generatedCode by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Removed confusing chars: I, 1, O, 0
        val code = (1..6).map { chars.random() }.joinToString("")
        val formattedCode = "${code.substring(0,3)}-${code.substring(3,6)}"
        generatedCode = formattedCode
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to P-BLOCK", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Share this 6-character code with your accountability partner. You will need it to unlock the app instantly.")
        Spacer(modifier = Modifier.height(24.dp))
        
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = generatedCode,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("Required Permissions:", fontWeight = FontWeight.Bold)
        
        Button(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, modifier = Modifier.fillMaxWidth().padding(vertical=4.dp)) {
            Text("1. Grant Usage Access (For App Stats)")
        }
        Button(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth().padding(vertical=4.dp)) {
            Text("2. Enable Accessibility Service (Anti-Uninstall)")
        }
        Button(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, modifier = Modifier.fillMaxWidth().padding(vertical=4.dp)) {
            Text("3. Disable Battery Optimization (Anti-Kill)")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                coroutineScope.launch {
                    accountabilityManager.generateAndSetPartnerCode(generatedCode)
                    onStartVpn()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("All Done! Start Protection")
        }
    }
}
