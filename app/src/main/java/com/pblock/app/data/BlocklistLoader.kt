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
