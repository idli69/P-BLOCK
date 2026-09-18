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
        
        Text("Important Steps:", fontWeight = FontWeight.Bold)
        Text("1. Write down or share the code above.")
        Text("2. Enable the VPN in the next step.")
        Text("3. Go to Android Settings -> Network & internet -> VPN -> P-BLOCK -> Enable 'Always-on VPN' and 'Block connections without VPN' to prevent bypassing.")
        
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
            Text("I have saved the code, Enable Protection")
        }
    }
}
