package com.pblock.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.net.URL

data class BlocklistData(val domains: List<String>, val keywords: List<String>)

class BlocklistLoader(private val context: Context) {

    private val TAG = "BlocklistLoader"
    private val CACHE_FILE = "blocklist_cache.txt"
    private val CACHE_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    private val OTA_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts"

    /** Loads the bundled sample blocklist from assets. Always succeeds. */
    fun loadSampleBlocklist(): BlocklistData {
        val domains = mutableListOf<String>()
        val keywords = mutableListOf<String>()
        try {
            val stream = context.assets.open("sample_blocklist.json")
            val jsonString = InputStreamReader(stream).use { it.readText() }
            val obj = JSONObject(jsonString)

            val domainsArray = obj.optJSONArray("domains")
            if (domainsArray != null) {
                for (i in 0 until domainsArray.length()) {
                    domains.add(domainsArray.getString(i))
                }
            }
            val keywordsArray = obj.optJSONArray("keywords")
            if (keywordsArray != null) {
                for (i in 0 until keywordsArray.length()) {
                    keywords.add(keywordsArray.getString(i))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sample blocklist", e)
        }
        return BlocklistData(domains, keywords)
    }

    /**
     * Fetches extra blocked domains and keywords from Firebase Remote Config.
     * This allows adding new blocks instantly from the Firebase Console
     * without needing an app update.
     *
     * Setup in Firebase Console → Remote Config:
     *   - Parameter key: `blocked_keywords`  Value: ["gambling","betting","casino"]
     *   - Parameter key: `blocked_domains`   Value: ["example-bad-site.com","other.com"]
     *
     * Returns empty BlocklistData if fetch fails (graceful degradation).
     */
    suspend fun fetchRemoteConfigBlocklist(): BlocklistData {
        val extraDomains = mutableListOf<String>()
        val extraKeywords = mutableListOf<String>()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val config = Firebase.remoteConfig
                config.setConfigSettingsAsync(remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 3600 // fetch at most once per hour
                }).await()
                // Set safe defaults (empty — everything else from local list)
                config.setDefaultsAsync(mapOf(
                    "blocked_keywords" to "[]",
                    "blocked_domains"  to "[]"
                )).await()

                config.fetchAndActivate().await()

                val kwJson = config.getString("blocked_keywords")
                val dmJson = config.getString("blocked_domains")

                JSONArray(kwJson).let { arr ->
                    for (i in 0 until arr.length()) extraKeywords.add(arr.getString(i))
                }
                JSONArray(dmJson).let { arr ->
                    for (i in 0 until arr.length()) extraDomains.add(arr.getString(i))
                }
                Log.i(TAG, "Remote Config: +${extraKeywords.size} keywords, +${extraDomains.size} domains")
            } catch (e: Exception) {
                Log.e(TAG, "Remote Config fetch failed", e)
            }
        }
        return BlocklistData(extraDomains, extraKeywords)
    }

    /**
     * Loads cached OTA blocklist if available and not too stale.
     * Returns empty list if no valid cache exists (caller should then call downloadOisdBlocklist).
     */
    fun loadCachedBlocklist(): List<String> {
        val cacheFile = File(context.filesDir, CACHE_FILE)
        if (!cacheFile.exists()) return emptyList()

        val ageMs = System.currentTimeMillis() - cacheFile.lastModified()
        if (ageMs > CACHE_MAX_AGE_MS) {
            Log.i(TAG, "Cache is stale (${ageMs / 86400000}d old), will re-download")
            return emptyList()
        }

        return try {
            val lines = cacheFile.readLines()
            Log.i(TAG, "Loaded ${lines.size} domains from cache")
            lines
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cache", e)
            emptyList()
        }
    }

    /**
     * Downloads the StevenBlack adult hosts file and caches it locally.
     * Only performs a network request if the local cache is missing or >7 days old.
     *
     * IMPORTANT: This should only be called after the VPN is set up with a `protect()`
     * call on the socket, OR it should be called before the VPN starts so the DNS
     * lookup goes via the normal system resolver.
     */
    suspend fun downloadOisdBlocklist(): List<String> {
        val cached = loadCachedBlocklist()
        if (cached.isNotEmpty()) return cached

        val newDomains = mutableListOf<String>()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.i(TAG, "Downloading OTA blocklist from StevenBlack...")
                val connection = URL(OTA_URL).openConnection()
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000

                InputStreamReader(connection.getInputStream()).buffered().use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.startsWith("0.0.0.0 ")) {
                            val domain = line.substring(8).trim().split("#")[0].trim()
                            if (domain.isNotEmpty() && domain != "0.0.0.0") {
                                newDomains.add(domain)
                            }
                        }
                    }
                }

                // Save to cache
                val cacheFile = File(context.filesDir, CACHE_FILE)
                cacheFile.writeText(newDomains.joinToString("\n"))
                Log.i(TAG, "Downloaded & cached ${newDomains.size} domains")
            } catch (e: Exception) {
                Log.e(TAG, "OTA download failed — falling back to sample list", e)
            }
        }
        return newDomains
    }

    // ── Event log ──────────────────────────────────────────────────────────────

    fun logEvent(event: String) {
        try {
            val file = File(context.filesDir, "events.log")
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            file.appendText("$timestamp: $event\n")
        } catch (e: Exception) {
            Log.e(TAG, "logEvent failed", e)
        }
    }

    fun getLogs(): String {
        return try {
            val file = File(context.filesDir, "events.log")
            if (file.exists()) file.readText().takeLast(3000) // Keep last 3000 chars to avoid OOM
            else "No events yet."
        } catch (e: Exception) {
            "Error reading logs."
        }
    }

    fun clearLogs() {
        File(context.filesDir, "events.log").delete()
    }
}
