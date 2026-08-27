package com.jarvis.app.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Calendar

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeInForegroundMs: Long,
    val lastTimeUsedMs: Long
)

object JarvisUsageManager {

    fun getUsageStatsManager(context: Context): UsageStatsManager? {
        return context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }

    private fun getAppName(context: Context, packageName: String): String {
        val pm = context.packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun getDailyUsage(context: Context): List<AppUsageInfo> {
        val usm = getUsageStatsManager(context) ?: return emptyList()

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime) ?: emptyList()
        
        return stats.filter { it.totalTimeInForeground > 0 }
            .map {
                AppUsageInfo(
                    packageName = it.packageName,
                    appName = getAppName(context, it.packageName),
                    totalTimeInForegroundMs = it.totalTimeInForeground,
                    lastTimeUsedMs = it.lastTimeUsed
                )
            }.sortedByDescending { it.totalTimeInForegroundMs }
    }
    
    fun getRecentApps(context: Context, limit: Int = 5): List<AppUsageInfo> {
        val usm = getUsageStatsManager(context) ?: return emptyList()
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 60 * 24 // last 24 hours
        
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime) ?: emptyList()
        
        return stats.filter { it.lastTimeUsed > 0 && it.packageName != context.packageName }
            .map {
                AppUsageInfo(
                    packageName = it.packageName,
                    appName = getAppName(context, it.packageName),
                    totalTimeInForegroundMs = it.totalTimeInForeground,
                    lastTimeUsedMs = it.lastTimeUsed
                )
            }
            .sortedByDescending { it.lastTimeUsedMs }
            .take(limit)
    }

    fun getAppUsage(context: Context, appName: String): AppUsageInfo? {
        val dailyUsage = getDailyUsage(context)
        return dailyUsage.firstOrNull { 
            it.appName.equals(appName, ignoreCase = true) || it.packageName.contains(appName, ignoreCase = true) 
        }
    }
}
