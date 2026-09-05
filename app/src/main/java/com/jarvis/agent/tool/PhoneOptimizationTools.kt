package com.jarvis.agent.tool

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult

/**
 * Phone optimization: JARVIS keeps the device fast.
 *
 * - phone_boost: hibernates background apps that were recently active, drops the
 *   screen brightness to an efficient level, and reports what was freed.
 * - app_hog_report: honest per-app screen-time ranking for today.
 * - storage_report: how full the phone is, with the biggest pressure points.
 *
 * These are the tools behind "JARVIS, my phone is slow" and "boost my phone" —
 * something no stock assistant does at all.
 */
object PhoneOptimizationTools {

    private const val OWN_PACKAGE = "com.rork.jarvisaiassistant"

    fun registerAll() {
        registerBoost()
        registerAppHogReport()
        registerStorageReport()
    }

    private fun error(toolId: String, message: String) =
        ToolExecutionResult(toolId = toolId, success = false, data = null, error = message)

    // ---------------------------------------------------------------- boost

    private fun registerBoost() {
        ToolRegistry.register(
            ToolDefinition(
                id = "phone_boost",
                name = "Boost Phone Performance",
                description = "Speeds the phone up: hibernates recently-active background apps, lowers screen brightness to an efficient level, and reports the result. Use when the user says the phone is slow, hot, or asks to boost/clean/optimize it.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, _ ->
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return@ToolDefinition error("phone_boost", "Activity service unavailable.")

                val hibernated = mutableListOf<String>()
                val skipped = mutableSetOf<String>()

                // Recently used apps are the ones holding background services and RAM.
                for (pkg in recentlyUsedPackages(context, hours = 6)) {
                    if (pkg == OWN_PACKAGE || pkg in skipped) continue
                    val ran = runCatching { am.killBackgroundProcesses(pkg) }.isSuccess
                    if (ran) hibernated += pkg else skipped += pkg
                }

                var brightnessNote = ""
                if (Settings.System.canWrite(context)) {
                    runCatching {
                        val current = Settings.System.getInt(
                            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1
                        )
                        if (current > 102) {
                            Settings.System.putInt(
                                context.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS,
                                102 // ~40% on the standard 0-255 curve
                            )
                            brightnessNote = " Brightness lowered from $current to 102."
                        } else {
                            brightnessNote = " Brightness already efficient."
                        }
                    }
                }

                val freeGb = freeInternalGb()
                val detail = buildString {
                    append("Boost complete. Hibernated ${hibernated.size} background app${if (hibernated.size == 1) "" else "s"}")
                    if (hibernated.isNotEmpty()) {
                        append(" (${hibernated.take(5).joinToString(", ") { appLabel(it) }}")
                        if (hibernated.size > 5) append(" and ${hibernated.size - 5} more")
                        append(")")
                    }
                    append(".$brightnessNote Free storage: $freeGb.")
                }

                ToolExecutionResult(
                    toolId = "phone_boost",
                    success = true,
                    data = mapOf("hibernated" to hibernated, "storage_free_gb" to freeGb),
                    verificationDetails = detail
                )
            }
        )
    }

    // ---------------------------------------------------------- usage report

    private fun registerAppHogReport() {
        ToolRegistry.register(
            ToolDefinition(
                id = "app_hog_report",
                name = "Report App Hogs",
                description = "Reports which apps used the most screen time today, so the user can see what is slowing the phone or draining the battery. Use when the user asks about battery drain, data hogs, or where their time goes.",
                category = "USAGE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, _ ->
                val stats = com.jarvis.app.usage.JarvisUsageManager.getDailyUsage(context)
                    .filter { it.totalTimeInForegroundMs > 60_000L }
                    .sortedByDescending { it.totalTimeInForegroundMs }
                    .take(5)

                if (stats.isEmpty()) {
                    return@ToolDefinition ToolExecutionResult(
                        toolId = "app_hog_report",
                        success = true,
                        data = emptyMap<String, Any>(),
                        verificationDetails = "No significant app usage recorded today."
                    )
                }

                val detail = stats.joinToString("; ") {
                    val minutes = it.totalTimeInForegroundMs / 60_000
                    "${it.appName}: $minutes min"
                }
                ToolExecutionResult(
                    toolId = "app_hog_report",
                    success = true,
                    data = mapOf("hogs" to stats.map { s -> mapOf("app" to s.appName, "minutes" to s.totalTimeInForegroundMs / 60_000) }),
                    verificationDetails = "Today's heaviest apps — $detail."
                )
            }
        )
    }

    // -------------------------------------------------------------- storage

    private fun registerStorageReport() {
        ToolRegistry.register(
            ToolDefinition(
                id = "storage_report",
                name = "Check Storage Pressure",
                description = "Reports how full the phone's internal storage is and how much is free. Use when the user mentions storage, space, or cleanup.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, _ ->
                val dataDir = Environment.getDataDirectory()
                val stat = StatFs(dataDir.path)
                val total = stat.totalBytes / (1024L * 1024L * 1024L)
                val free = stat.availableBytes / (1024L * 1024L * 1024L)
                val used = (total - free).coerceAtLeast(0)

                ToolExecutionResult(
                    toolId = "storage_report",
                    success = true,
                    data = mapOf("total_gb" to total, "free_gb" to free, "used_gb" to used),
                    verificationDetails = "Storage: ${used}GB used of ${total}GB — ${free}GB free."
                )
            }
        )
    }

    // ---------------------------------------------------------------- utils

    /** Packages with foreground activity in the last [hours] hours. */
    private fun recentlyUsedPackages(context: Context, hours: Int): List<String> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val now = System.currentTimeMillis()
        val stats = runCatching {
            usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - hours * 3_600_000L,
                now
            )
        }.getOrNull() ?: return emptyList()

        return stats.asSequence()
            .filter { it.lastTimeUsed > now - hours * 3_600_000L }
            .filter { !it.packageName.startsWith("com.android.") && it.packageName != "com.android.systemui" }
            .map { it.packageName }
            .distinct()
            .toList()
    }

    private fun appLabel(packageName: String): String =
        packageName.substringAfterLast('.').ifBlank { packageName }

    private fun freeInternalGb(): String {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val gb = stat.availableBytes / (1024L * 1024L * 1024L)
            "${gb}GB free"
        }.getOrDefault("storage unavailable")
    }
}
