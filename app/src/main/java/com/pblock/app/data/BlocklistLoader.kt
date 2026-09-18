package com.pblock.app.data

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.io.InputStreamReader

class BlocklistLoader(private val context: Context) {

    fun loadSampleBlocklist(): List<String> {
        val domains = mutableListOf<String>()
        try {
            val stream = context.assets.open("sample_blocklist.json")
            val reader = InputStreamReader(stream)
            val jsonString = reader.readText()
            reader.close()
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                domains.add(array.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return domains
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
