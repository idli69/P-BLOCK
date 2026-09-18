package com.pblock.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader

data class BlocklistData(val domains: List<String>, val keywords: List<String>)

class BlocklistLoader(private val context: Context) {

    fun loadSampleBlocklist(): BlocklistData {
        val domains = mutableListOf<String>()
        val keywords = mutableListOf<String>()
        try {
            val stream = context.assets.open("sample_blocklist.json")
            val reader = InputStreamReader(stream)
            val jsonString = reader.readText()
            reader.close()
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
            e.printStackTrace()
        }
        return BlocklistData(domains, keywords)
    }

    suspend fun downloadOisdBlocklist(): List<String> {
        val newDomains = mutableListOf<String>()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Fetching a basic, well-maintained adult blocklist (StevenBlack alternates/porn)
                val url = java.net.URL("https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts")
                val connection = url.openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 20000
                
                val reader = InputStreamReader(connection.getInputStream()).buffered()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("0.0.0.0 ")) {
                        val domain = line!!.substring(8).trim()
                        if (domain != "0.0.0.0") {
                            newDomains.add(domain)
                        }
                    }
                }
                reader.close()
                android.util.Log.i("BlocklistLoader", "Successfully downloaded ${newDomains.size} domains OTA")
            } catch (e: Exception) {
                android.util.Log.e("BlocklistLoader", "Failed to download OTA blocklist", e)
            }
        }
        return newDomains
    }

    // Append-only local event log for requests/unlocks
    fun logEvent(event: String) {
        try {
            val file = File(context.filesDir, "events.log")
            file.appendText("${System.currentTimeMillis()}: $event\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getLogs(): String {
        return try {
            val file = File(context.filesDir, "events.log")
            if (file.exists()) file.readText() else "No events yet."
        } catch (e: Exception) {
            "Error reading logs."
        }
    }
    
    fun clearLogs() {
        val file = File(context.filesDir, "events.log")
        if (file.exists()) {
            file.delete()
        }
    }
}
