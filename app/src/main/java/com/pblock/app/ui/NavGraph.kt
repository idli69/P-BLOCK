package com.pblock.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pblock.app.accountability.AccountabilityManager
import com.pblock.app.data.BlocklistLoader
import com.pblock.app.data.PreferencesManager

@Composable
fun NavGraph(
    prefs: PreferencesManager,
    accountabilityManager: AccountabilityManager,
    blocklistLoader: BlocklistLoader,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit
) {
    val navController = rememberNavController()
    val isProtected by prefs.isProtected.collectAsState(initial = false)
    val partnerCode by prefs.partnerCodeEncrypted.collectAsState(initial = null)

    val startDestination = if (partnerCode == null) "onboarding" else "dashboard"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreen(
                accountabilityManager = accountabilityManager,
                onStartVpn = {
                    onStartVpn()
                    navController.navigate("dashboard") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("dashboard") {
            Dashboard(
                isProtected = isProtected,
                prefs = prefs,
                onNavigateToUnlock = { navController.navigate("unlock") },
                onNavigateToSettings = { navController.navigate("settings") },
                onStartVpn = onStartVpn
            )
        }
        composable("unlock") {
            UnlockScreen(
                accountabilityManager = accountabilityManager,
                onUnlockSuccess = {
                    onStopVpn()
                    navController.popBackStack()
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                prefs = prefs,
                blocklistLoader = blocklistLoader
            )
        }
    }
}
