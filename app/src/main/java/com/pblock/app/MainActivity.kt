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
import com.pblock.app.accountability.RemoteSyncManager
import com.pblock.app.data.BlocklistLoader
import com.pblock.app.data.PreferencesManager
import com.pblock.app.data.SecureKeyManager
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
    private lateinit var remoteSyncManager: RemoteSyncManager

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
        remoteSyncManager = RemoteSyncManager(this, prefs, accountabilityManager)

        // Start Firebase listener immediately — it will attach as soon as a topicId exists
        remoteSyncManager.start()

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
        startForegroundService(Intent(this, BlockingVpnService::class.java))
    }

    private fun stopVpnService() {
        stopService(Intent(this, BlockingVpnService::class.java))
    }
}
