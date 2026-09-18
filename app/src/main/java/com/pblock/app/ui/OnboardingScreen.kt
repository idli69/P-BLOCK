package com.pblock.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pblock.app.accountability.AccountabilityManager
import com.pblock.app.vpn.PermissionChecker
import com.pblock.app.vpn.PBlockAccessibilityService
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    accountabilityManager: AccountabilityManager,
    onStartVpn: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var generatedCode by remember { mutableStateOf("") }
    var offlinePin by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasUsage by remember { mutableStateOf(PermissionChecker.hasUsageStatsPermission(context)) }
    var hasAccess by remember { mutableStateOf(PermissionChecker.hasAccessibilityPermission(context, PBlockAccessibilityService::class.java)) }
    var hasNotif by remember { mutableStateOf(PermissionChecker.hasNotificationPermission(context)) }
    var hasVpn by remember { mutableStateOf(PermissionChecker.hasVpnPermission(context)) }
    var hasBattery by remember { mutableStateOf(PermissionChecker.isBatteryOptimizationIgnored(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsage = PermissionChecker.hasUsageStatsPermission(context)
                hasAccess = PermissionChecker.hasAccessibilityPermission(context, PBlockAccessibilityService::class.java)
                hasNotif = PermissionChecker.hasNotificationPermission(context)
                hasVpn = PermissionChecker.hasVpnPermission(context)
                hasBattery = PermissionChecker.isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..6).map { chars.random() }.joinToString("")
        val formattedCode = "${code.substring(0,3)}-${code.substring(3,6)}"
        generatedCode = formattedCode
        
        val digits = "0123456789"
        offlinePin = (1..6).map { digits.random() }.joinToString("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to P-BLOCK", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Share this 6-character pairing code with your accountability partner to link your device.")
        Spacer(modifier = Modifier.height(8.dp))
        
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
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Offline Unlock PIN (Save this somewhere safe!)")
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = offlinePin,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("Required Permissions:", fontWeight = FontWeight.Bold)
        
        Button(
            onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            modifier = Modifier.fillMaxWidth().padding(vertical=4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (hasUsage) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
        ) {
            Text(if (hasUsage) "✓ Usage Access Granted" else "1. Grant Usage Access (App Stats)")
        }
        
        Button(
            onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth().padding(vertical=4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (hasAccess) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
        ) {
            Text(if (hasAccess) "✓ Accessibility Enabled" else "2. Enable Accessibility (Anti-Uninstall)")
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Button(
                onClick = { 
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical=4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (hasNotif) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
            ) {
                Text(if (hasNotif) "✓ Notifications Enabled" else "3. Enable Notifications")
            }
        }
        
        Button(
            onClick = { 
                val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().padding(vertical=4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (hasBattery) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondary)
        ) {
            Text(if (hasBattery) "✓ Battery Optimization Disabled" else "4. Disable Battery Optimization (Optional)")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        var isWaitingForAdmin by remember { mutableStateOf(false) }

        val allRequired = hasUsage && hasAccess && hasNotif
        if (isWaitingForAdmin) {
            CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Waiting for admin to claim code...", textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else {
            Button(
                onClick = {
                    if (allRequired) {
                        isWaitingForAdmin = true
                        coroutineScope.launch {
                            accountabilityManager.generateAndSetPartnerCode(generatedCode, offlinePin) {
                                onStartVpn()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = allRequired
            ) {
                Text(if (allRequired) "All Done! Generate Code & Wait" else "Complete Required Permissions")
            }
        }
    }
}
