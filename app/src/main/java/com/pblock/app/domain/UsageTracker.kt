package com.pblock.app.domain

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import java.util.Calendar

class UsageTracker(private val context: Context) {
    
    fun getDailyAppUsage(): Map<String, Long> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        
        val appUsage = mutableMapOf<String, Long>()
        
        for ((packageName, stats) in usageStats) {
            if (stats.totalTimeInForeground > 0) {
                val appName = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    packageName
                }
                
                appUsage[appName] = (appUsage[appName] ?: 0L) + stats.totalTimeInForeground
            }
        }

        return appUsage.entries
            .sortedByDescending { it.value }
            .take(20)
            .associate { it.key to (it.value / 60000L) }
            .filter { it.value > 0 }
    }
}
