package com.jarvis.agent.tool

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionRequest
import com.jarvis.core.model.ToolExecutionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Schema definition for an executable tool in the Assistant registry.
 */
data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val riskLevel: RiskLevel,
    val handler: suspend (Context, Map<String, Any?>) -> ToolExecutionResult
)

object ToolRegistry {

    private val tools = mutableMapOf<String, ToolDefinition>()

    init {
        registerDefaultTools()
    }

    fun register(tool: ToolDefinition) {
        tools[tool.id] = tool
    }

    fun getAllTools(): List<ToolDefinition> = tools.values.toList()

    fun getTool(id: String): ToolDefinition? = tools[id]

    suspend fun execute(context: Context, request: ToolExecutionRequest): ToolExecutionResult {
        val tool = tools[request.toolId] ?: return ToolExecutionResult(
            toolId = request.toolId,
            success = false,
            data = null,
            error = "Tool with ID '${request.toolId}' is not registered."
        )
        return try {
            tool.handler(context, request.arguments)
        } catch (e: Exception) {
            ToolExecutionResult(
                toolId = request.toolId,
                success = false,
                data = null,
                error = "Execution failed: ${e.localizedMessage}"
            )
        }
    }

    private fun registerDefaultTools() {
        // 1. Battery Status (Level 0)
        register(
            ToolDefinition(
                id = "device_battery",
                name = "Get Battery Status",
                description = "Checks current battery percentage and charging state.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, _ ->
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                val isCharging = bm?.isCharging ?: false
                ToolExecutionResult(
                    toolId = "device_battery",
                    success = true,
                    data = mapOf("level" to level, "charging" to isCharging),
                    verificationDetails = "Battery is at $level%, charging: $isCharging"
                )
            }
        )

        // 2. Current Time & Date (Level 0)
        register(
            ToolDefinition(
                id = "device_time",
                name = "Get Current Time",
                description = "Retrieves formatted system date and time.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, _ ->
                val now = SimpleDateFormat("EEEE, MMMM d, yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                ToolExecutionResult(
                    toolId = "device_time",
                    success = true,
                    data = mapOf("time" to now),
                    verificationDetails = "Current time is $now"
                )
            }
        )

        // 3. Open App (Level 1)
        register(
            ToolDefinition(
                id = "app_launch",
                name = "Launch Application",
                description = "Opens an application installed on the device.",
                category = "APPS",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val appName = args["app_name"]?.toString()?.lowercase()?.trim() ?: ""
                val pm = context.packageManager
                
                // Try specific standard intents
                var launchIntent: Intent? = when {
                    appName == "camera" || appName.contains("camera") -> Intent("android.media.action.IMAGE_CAPTURE")
                    appName == "settings" || appName.contains("setting") -> Intent(android.provider.Settings.ACTION_SETTINGS)
                    appName == "calendar" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
                    appName == "maps" || appName.contains("map") -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)
                    appName == "music" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
                    appName == "browser" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
                    appName == "calculator" || appName.contains("calculator") -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    else -> null
                }

                // If not found or if specific app requested, query installed packages
                if (launchIntent == null || launchIntent.resolveActivity(pm) == null) {
                    try {
                        val installedApps = pm.getInstalledApplications(0)
                        val match = installedApps.firstOrNull { appInfo ->
                            val label = appInfo.loadLabel(pm).toString().lowercase()
                            label == appName || label.contains(appName) || appInfo.packageName.lowercase().contains(appName)
                        }
                        if (match != null) {
                            launchIntent = pm.getLaunchIntentForPackage(match.packageName)
                        }
                    } catch (_: Exception) { }
                }

                // Fallback: Query launcher activities
                if (launchIntent == null) {
                    try {
                        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
                        val match = resolveInfos.firstOrNull {
                            val label = it.loadLabel(pm).toString().lowercase()
                            label == appName || label.contains(appName) || it.activityInfo.packageName.lowercase().contains(appName)
                        }
                        if (match != null) {
                            launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
                        }
                    } catch (_: Exception) { }
                }

                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(launchIntent)
                        ToolExecutionResult(
                            toolId = "app_launch",
                            success = true,
                            data = mapOf("launched" to appName),
                            verificationDetails = "Application '$appName' launched successfully."
                        )
                    } catch (e: Exception) {
                        ToolExecutionResult(
                            toolId = "app_launch",
                            success = false,
                            data = null,
                            error = "Could not start application '$appName': ${e.message}"
                        )
                    }
                } else {
                    ToolExecutionResult(
                        toolId = "app_launch",
                        success = false,
                        data = null,
                        error = "Could not find an installed application matching '$appName'."
                    )
                }
            }
        )

        // 4. Web Search (Level 1)
        register(
            ToolDefinition(
                id = "web_search",
                name = "Search the Web",
                description = "Performs a web search via browser.",
                category = "WEB",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val query = args["query"]?.toString() ?: ""
                val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult(
                    toolId = "web_search",
                    success = true,
                    data = mapOf("query" to query),
                    verificationDetails = "Web search opened for '$query'."
                )
            }
        )

        // 5. Send Message (Level 2 - Requires confirmation)
        register(
            ToolDefinition(
                id = "communication_send",
                name = "Send Message",
                description = "Sends a message via SMS or messenger.",
                category = "COMMUNICATION",
                riskLevel = RiskLevel.LEVEL_2
            ) { context, args ->
                val recipient = args["recipient"]?.toString() ?: "Unknown"
                val message = args["message"]?.toString() ?: ""
                val uri = Uri.parse("smsto:${Uri.encode(recipient)}")
                val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult(
                    toolId = "communication_send",
                    success = true,
                    data = mapOf("recipient" to recipient, "message" to message),
                    verificationDetails = "Message draft prepared for $recipient: '$message'"
                )
            }
        )

        // 6. Get Recent Notifications (Level 0)
        register(
            ToolDefinition(
                id = "get_recent_notifications",
                name = "Get Recent Notifications",
                description = "Retrieves recent active notifications. You can filter by app_name if provided.",
                category = "NOTIFICATIONS",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                if (com.jarvis.app.notifications.JarvisNotificationListener.instance == null) {
                    return@ToolDefinition ToolExecutionResult(
                        toolId = "get_recent_notifications",
                        success = false,
                        data = null,
                        error = "Notification access is not enabled. Please enable it in Settings."
                    )
                }
                
                val appName = args["app_name"]?.toString()
                val limit = (args["limit"] as? Number)?.toInt() ?: 5
                
                val notifs = if (!appName.isNullOrBlank()) {
                    com.jarvis.app.notifications.NotificationRepository.byApp(appName)
                } else {
                    com.jarvis.app.notifications.NotificationRepository.all.value
                }.sortedByDescending { it.timestamp }.take(limit)
                
                ToolExecutionResult(
                    toolId = "get_recent_notifications",
                    success = true,
                    data = mapOf("notifications" to notifs.map { mapOf("app" to it.appLabel, "title" to it.title, "text" to it.text, "key" to it.key, "hasReply" to it.hasReplyAction) }),
                    verificationDetails = "Found ${notifs.size} notifications."
                )
            }
        )
        
        // 7. Reply to Notification (Level 2)
        register(
            ToolDefinition(
                id = "reply_to_notification",
                name = "Reply to Notification",
                description = "Replies to a specific notification if it supports direct reply. Requires 'package_name' or 'app_name' and 'reply_text'.",
                category = "NOTIFICATIONS",
                riskLevel = RiskLevel.LEVEL_2
            ) { _, args ->
                val appName = args["app_name"]?.toString() ?: args["package_name"]?.toString() ?: ""
                val replyText = args["reply_text"]?.toString() ?: ""
                
                if (appName.isBlank() || replyText.isBlank()) {
                    return@ToolDefinition ToolExecutionResult(
                        toolId = "reply_to_notification",
                        success = false,
                        data = null,
                        error = "Both app_name and reply_text are required."
                    )
                }
                
                // Find matching notification to get exact package name
                val notif = com.jarvis.app.notifications.NotificationRepository.byApp(appName).firstOrNull { it.hasReplyAction }
                    ?: com.jarvis.app.notifications.NotificationRepository.byApp(appName).firstOrNull()
                
                val pkgName = notif?.packageName ?: appName
                val success = com.jarvis.app.notifications.JarvisNotificationListener.replyViaNotification(pkgName, replyText)
                
                if (success) {
                    ToolExecutionResult(
                        toolId = "reply_to_notification",
                        success = true,
                        data = mapOf("package" to pkgName, "reply" to replyText),
                        verificationDetails = "Sent reply: '$replyText'"
                    )
                } else {
                    ToolExecutionResult(
                        toolId = "reply_to_notification",
                        success = false,
                        data = null,
                        error = "Failed to send reply. The notification may not support direct replies or doesn't exist."
                    )
                }
            }
        )
        
        // 8. Dismiss Notification (Level 1)
        register(
            ToolDefinition(
                id = "dismiss_notification",
                name = "Dismiss Notification",
                description = "Dismisses a notification by its key.",
                category = "NOTIFICATIONS",
                riskLevel = RiskLevel.LEVEL_1
            ) { _, args ->
                val key = args["key"]?.toString() ?: ""
                if (key.isBlank()) {
                    return@ToolDefinition ToolExecutionResult("dismiss_notification", false, null, "Missing notification key.")
                }
                
                val success = com.jarvis.app.notifications.JarvisNotificationListener.dismissNotification(key)
                if (success) {
                    ToolExecutionResult("dismiss_notification", true, null, verificationDetails = "Notification dismissed.")
                } else {
                    ToolExecutionResult("dismiss_notification", false, null, "Failed to dismiss notification.")
                }
            }
        )

        // 9. Get Recent Apps (Level 0)
        register(
            ToolDefinition(
                id = "get_recent_apps",
                name = "Get Recent Apps",
                description = "Retrieves the most recently used applications.",
                category = "USAGE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                if (!com.jarvis.android.permissions.PermissionAndSetupHelper.hasUsageAccess(context)) {
                    return@ToolDefinition ToolExecutionResult("get_recent_apps", false, null, "Usage Access is not enabled.")
                }
                val limit = (args["limit"] as? Number)?.toInt() ?: 5
                val recentApps = com.jarvis.app.usage.JarvisUsageManager.getRecentApps(context, limit)
                val appNames = recentApps.map { it.appName }
                
                ToolExecutionResult(
                    toolId = "get_recent_apps",
                    success = true,
                    data = mapOf("recent_apps" to appNames),
                    verificationDetails = "Recent apps: ${appNames.joinToString(", ")}"
                )
            }
        )

        // 10. Get Daily Usage (Level 0)
        register(
            ToolDefinition(
                id = "get_daily_usage",
                name = "Get Daily Usage",
                description = "Retrieves today's usage statistics for all apps or a specific app (by passing 'app_name').",
                category = "USAGE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                if (!com.jarvis.android.permissions.PermissionAndSetupHelper.hasUsageAccess(context)) {
                    return@ToolDefinition ToolExecutionResult("get_daily_usage", false, null, "Usage Access is not enabled.")
                }
                
                val appName = args["app_name"]?.toString()
                
                if (!appName.isNullOrBlank()) {
                    val appUsage = com.jarvis.app.usage.JarvisUsageManager.getAppUsage(context, appName)
                    if (appUsage != null) {
                        val minutes = appUsage.totalTimeInForegroundMs / (1000 * 60)
                        ToolExecutionResult(
                            toolId = "get_daily_usage",
                            success = true,
                            data = mapOf("app" to appUsage.appName, "durationMinutes" to minutes),
                            verificationDetails = "Used ${appUsage.appName} for $minutes minutes today."
                        )
                    } else {
                        ToolExecutionResult(
                            toolId = "get_daily_usage",
                            success = false,
                            data = null,
                            error = "No usage data found for $appName today."
                        )
                    }
                } else {
                    val dailyUsage = com.jarvis.app.usage.JarvisUsageManager.getDailyUsage(context)
                        .filter { it.totalTimeInForegroundMs > 60 * 1000 } // more than a minute
                        .take(10)
                    val formatted = dailyUsage.map { 
                        mapOf("app" to it.appName, "durationMinutes" to it.totalTimeInForegroundMs / (1000 * 60))
                    }
                    ToolExecutionResult(
                        toolId = "get_daily_usage",
                        success = true,
                        data = mapOf("usage" to formatted),
                        verificationDetails = "Found usage data for ${formatted.size} apps today."
                    )
                }
            }
        )

        // 11. Calendar Event Creator (Level 1)
        register(
            ToolDefinition(
                id = "calendar_create",
                name = "Create Calendar Event",
                description = "Creates a calendar event. Pass 'title' and optional 'when' or 'notes'.",
                category = "CALENDAR",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val title = args["title"]?.toString() ?: args["event"]?.toString() ?: "Meeting"
                val resultMsg = com.jarvis.app.tools.CalendarToolkit(context).createEvent(title)
                ToolExecutionResult(
                    toolId = "calendar_create",
                    success = !resultMsg.contains("failed", ignoreCase = true) && !resultMsg.contains("need", ignoreCase = true),
                    data = mapOf("title" to title),
                    verificationDetails = resultMsg,
                    error = if (resultMsg.contains("failed", ignoreCase = true) || resultMsg.contains("need", ignoreCase = true)) resultMsg else null
                )
            }
        )

        // 12. Memory Remember (Level 0)
        register(
            ToolDefinition(
                id = "memory_remember",
                name = "Remember Information",
                description = "Saves facts, notes, or user preferences to local persistent memory.",
                category = "MEMORY",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val content = args["content"]?.toString() ?: args["text"]?.toString() ?: ""
                if (content.isBlank()) {
                    return@ToolDefinition ToolExecutionResult("memory_remember", false, null, "No content provided to remember.")
                }
                try {
                    val db = com.jarvis.app.memory.AppDatabase.get(context)
                    db.memoryDao().insert(com.jarvis.app.memory.MemoryEntity(content = content, type = "fact"))
                    ToolExecutionResult(
                        toolId = "memory_remember",
                        success = true,
                        data = mapOf("saved" to content),
                        verificationDetails = "I will remember that: \"$content\""
                    )
                } catch (e: Exception) {
                    ToolExecutionResult("memory_remember", false, null, "Memory save failed: ${e.message}")
                }
            }
        )

        // 13. Memory Recall (Level 0)
        register(
            ToolDefinition(
                id = "memory_recall",
                name = "Recall Information",
                description = "Recalls stored facts or preferences matching a query.",
                category = "MEMORY",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val query = args["query"]?.toString() ?: ""
                try {
                    val db = com.jarvis.app.memory.AppDatabase.get(context)
                    val allMemories = db.memoryDao().snapshot()
                    val memories = if (query.isNotBlank()) {
                        allMemories.filter { it.content.contains(query, ignoreCase = true) }
                    } else {
                        allMemories
                    }
                    val contents = memories.map { it.content }
                    ToolExecutionResult(
                        toolId = "memory_recall",
                        success = true,
                        data = mapOf("memories" to contents),
                        verificationDetails = if (contents.isNotEmpty()) "Recalled: ${contents.joinToString("; ")}" else "No matching memories found."
                    )
                } catch (e: Exception) {
                    ToolExecutionResult("memory_recall", false, null, "Memory recall failed: ${e.message}")
                }
            }
        )

        // 14. Device Connectivity (Level 0)
        register(
            ToolDefinition(
                id = "device_connectivity",
                name = "Check Network Connectivity",
                description = "Checks Wi-Fi, cellular, and overall network status.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, _ ->
                val status = com.jarvis.app.tools.DeviceToolkit(context).connectivity()
                ToolExecutionResult(
                    toolId = "device_connectivity",
                    success = true,
                    data = mapOf("status" to status),
                    verificationDetails = status
                )
            }
        )

        // 15. Device Storage (Level 0)
        register(
            ToolDefinition(
                id = "device_storage",
                name = "Check Device Storage",
                description = "Checks free and total internal storage space.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, _ ->
                val storageInfo = com.jarvis.app.tools.DeviceToolkit(context).storage()
                ToolExecutionResult(
                    toolId = "device_storage",
                    success = true,
                    data = mapOf("storage" to storageInfo),
                    verificationDetails = storageInfo
                )
            }
        )

        // 16. Device Location (Level 0)
        register(
            ToolDefinition(
                id = "device_location",
                name = "Get Current Location",
                description = "Retrieves last known GPS/Network location coordinates and address.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, _ ->
                val loc = com.jarvis.app.tools.LocationToolkit(context).lastKnown()
                ToolExecutionResult(
                    toolId = "device_location",
                    success = !loc.contains("failed", ignoreCase = true) && !loc.contains("need", ignoreCase = true),
                    data = mapOf("location" to loc),
                    verificationDetails = loc,
                    error = if (loc.contains("failed", ignoreCase = true) || loc.contains("need", ignoreCase = true)) loc else null
                )
            }
        )
    }
}
