package com.pblock.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import com.pblock.app.accountability.AccountabilityManager
import com.pblock.app.accountability.AuthManager
import com.pblock.app.accountability.RemoteSyncManager
import com.pblock.app.data.BlocklistLoader
import com.pblock.app.data.PreferencesManager
import com.pblock.app.data.SecureKeyManager
import com.pblock.app.state.AppStateController
import com.pblock.app.state.StateEvent
import com.pblock.app.ui.NavGraph
import com.pblock.app.vpn.BlockingVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var secureKeyManager: SecureKeyManager
    private lateinit var blocklistLoader: BlocklistLoader
    private lateinit var accountabilityManager: AccountabilityManager
    private lateinit var authManager: AuthManager
    private lateinit var remoteSyncManager: RemoteSyncManager
    private lateinit var appStateController: AppStateController

    private val vpnRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferencesManager(this)
        secureKeyManager = SecureKeyManager()
        blocklistLoader = BlocklistLoader(this)
        accountabilityManager = AccountabilityManager(prefs, secureKeyManager, blocklistLoader)
        authManager = AuthManager()
        appStateController = AppStateController(
            prefs, authManager,
            onStateChanged = { state -> remoteSyncManager.publishState(state) }
        )
        remoteSyncManager = RemoteSyncManager(
            this, prefs, accountabilityManager, authManager,
            onLock = { appStateController.applyEvent(com.pblock.app.state.StateEvent.PartnerLock) },
            onUnlock = { appStateController.applyEvent(com.pblock.app.state.StateEvent.PartnerUnlock) }
        )

        // Start Firebase identity + sync immediately
        authManager.start()
        appStateController.start()
        remoteSyncManager.start()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(
                        prefs = prefs,
                        accountabilityManager = accountabilityManager,
                        blocklistLoader = blocklistLoader,
                        appStateController = appStateController,
                        onStartVpn = { requestVpnPermission() },
                        onStopVpn = { stopVpnService() }
                    )
                }
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnRequestLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        CoroutineScope(Dispatchers.IO).launch {
            prefs.setProtected(true)
        }
        val intent = Intent(this, BlockingVpnService::class.java)
            .putExtra(BlockingVpnService.EXTRA_MANUAL_START, true)
        startForegroundService(intent)
    }

    private fun stopVpnService() {
        stopService(Intent(this, BlockingVpnService::class.java))
    }
}
