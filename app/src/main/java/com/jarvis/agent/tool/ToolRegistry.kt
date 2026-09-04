package com.jarvis.agent.tool

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
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
        // HARD FAIL on duplicate tool IDs — two different files registering the
        // same id was the root cause of silent overwrites (battery, volume,
        // flashlight, call_contact, calendar, alarm, timer, navigate_to all had
        // duplicate registrations). A hard crash here is better than a silent
        // wrong answer in production.
        require(!tools.containsKey(tool.id)) {
            "Duplicate tool id '${tool.id}' — this is a bug: two different files " +
                "registered the same id. The previous registration must be removed."
        }
        tools[tool.id] = tool
    }

    fun getAllTools(): List<ToolDefinition> = tools.values.toList()

    fun getTool(id: String): ToolDefinition? = tools[id]

    /**
     * Backwards-compatible tool aliases.
     *
     * `open_app`, `battery_info`, `read_notifications` and `reply_notification` used to be
     * redirected here. That silently discarded the newer implementations — and because
     * `app_launch` reads `app_name` while callers send `app`, every "open X" command
     * arrived with an empty name and launched whatever app PackageManager returned first.
     * Those redirects are gone; the canonical tools now run.
     */
    private val aliases = mapOf(
        "memory_save" to "memory_remember",
        "read_screen" to "screen_read",
        "see_screen" to "screen_read",
        "get_screen" to "screen_read",
        "find_on_screen" to "find_text",
        "search_screen" to "find_text",
        "tap_on_screen" to "click_element",
        "click" to "click_element",
        "click_screen" to "click_element",
        "click_text" to "click_element",
        "click_button" to "click_element",
        "tap_button" to "click_element",
        "type" to "type_text",
        "enter_text" to "type_text",
        "tap_screen" to "tap"
    )

    suspend fun execute(context: Context, request: ToolExecutionRequest): ToolExecutionResult {
        val targetId = aliases[request.toolId] ?: request.toolId
        val tool = tools[targetId] ?: return ToolExecutionResult(
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
        // CHANGED (mirror fix pass, item 9): the duplicate registrations of
        // "calendar_create", "call_contact", "navigate_to", "set_alarm" and
        // "set_timer" that used to live here have been removed. The canonical
        // versions are registered by LifeTools.registerAll() and
        // PhoneTools.registerAll() (called from ToolRegistration.registerAll()
        // AFTER this init ran, so these older copies were always silently
        // overwritten at runtime anyway -- dead weight that only existed to
        // be picked up if registerAll() somehow didn't run, and to confuse
        // anyone reading this file). LifeTools/PhoneTools' versions are the
        // better implementations: real time parsing for calendar/alarm/timer,
        // contact disambiguation for calls, proper permission checks.
        WebTools.register(this)
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
                // CHANGED (forensic audit): merged in from the duplicate
                // "battery_info" tool in DeviceToolExecutors.kt, which reported
                // the same thing under a different id with near-identical
                // wording -- both were "DEVICE" category and visible to the AI
                // at once, so the model had two indistinguishable tools to pick
                // between for the same request. This is now the one canonical
                // battery tool; the duplicate registration has been removed.
                val tempFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
                val tempStatus = context.registerReceiver(null, tempFilter)
                val tempC = (tempStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
                ToolExecutionResult(
                    toolId = "device_battery",
                    success = true,
                    data = mapOf("level" to level, "charging" to isCharging, "temperature_c" to tempC),
                    verificationDetails = "Battery is at $level% (${if (isCharging) "charging" else "discharging"}), ${tempC}°C"
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
                val raw = (args["app"] ?: args["app_name"] ?: args["name"] ?: args["query"] ?: args["target"])
                    ?.toString()?.trim() ?: ""
                val result = com.jarvis.app.tools.AppLauncher.launch(context, raw)
                ToolExecutionResult(
                    toolId = "app_launch",
                    success = result.success,
                    data = mapOf("app" to raw, "package" to (result.packageName ?: "")),
                    verificationDetails = if (result.success) result.message else null,
                    error = if (result.success) null else result.message
                )
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
                val uri = "https://www.google.com/search?q=${Uri.encode(query)}".toUri()
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

        // 5. REMOVED duplicate "communication_send" tool.
        // It duplicated send_message (#18) and only performed a shallow SMS
        // composer launch (no contact resolution, no Accessibility-driven
        // verification). send_message now owns all SMS sending via
        // MessagingAutomation. Keeping it would also crash ToolRegistry because
        // require() hard-fails on duplicate ids if anything ever reused this id.

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
                
                val appName = (args["app_name"] ?: args["app"] ?: args["package"])?.toString()
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
                val appName = (args["app_name"] ?: args["app"] ?: args["package_name"] ?: args["package"])?.toString() ?: ""
                val replyText = (args["reply_text"] ?: args["message"] ?: args["text"] ?: args["body"])?.toString() ?: ""
                
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
                
                val appName = (args["app_name"] ?: args["app"] ?: args["package"])?.toString()
                
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
                        verificationDetails = if (contents.isNotEmpty()) "Recalled: ${contents.joinToString("; ")}" else "Memory check complete. No specific memories found."
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

        // 18. Send Message / SMS (Level 2) \u2014 Accessibility-driven via MessagingAutomation
        // Per the JARVIS vision (HOW_JARVIS_IS_SUPPOSED_TO_WORK): open the real
        // messaging app, resolve the contact, type the message, VERIFY it landed,
        // then STOP and ask the user "send now?" (pendingSendApp). The existing
        // LEVEL_2 confirmation system calls confirmSendSms() when the user says yes.
        register(
            ToolDefinition(
                id = "send_message",
                name = "Send Direct Message",
                description = "Sends an SMS or direct message to a contact. Resolves the contact from the address book, opens the messaging app, types the message, and asks for confirmation before sending.",
                category = "COMMUNICATION",
                riskLevel = RiskLevel.LEVEL_2
            ) { context, args ->
                val contactName = args["contact"]?.toString() ?: args["recipient"]?.toString() ?: ""
                val body = args["body"]?.toString() ?: args["message"]?.toString() ?: ""
                if (contactName.isBlank() || body.isBlank()) {
                    return@ToolDefinition ToolExecutionResult("send_message", false, null, "Both recipient and message body are required.")
                }

                // Stateful: first call types & verifies the draft and returns
                // pending confirmation; the orchestrator re-invokes this same
                // tool on "yes", which then performs the real send tap.
                val result = com.jarvis.app.tools.MessagingAutomation.executeSend(
                    context,
                    com.jarvis.app.tools.MessagingAutomation.TargetApp.SMS,
                    contactName,
                    body
                )
                if (!result.success) {
                    return@ToolDefinition ToolExecutionResult("send_message", false, null, result.verificationDetails.ifBlank { result.error ?: "Failed to prepare the message." })
                }

                if (result.preparedOnly) {
                    return@ToolDefinition ToolExecutionResult(
                        toolId = "send_message",
                        success = true,
                        data = mapOf("recipient" to contactName, "body" to body, "preparedOnly" to true),
                        verificationDetails = result.verificationDetails
                    )
                }

                ToolExecutionResult(
                    toolId = "send_message",
                    success = true,
                    data = mapOf(
                        "recipient" to contactName,
                        "body" to body,
                        "pendingSendApp" to (result.pendingSendApp ?: "your messaging app")
                    ),
                    verificationDetails = result.verificationDetails
                )
            }
        )

        // 19. Toggle System Setting (Level 1)
        register(
            ToolDefinition(
                id = "toggle_setting",
                name = "Toggle Setting",
                description = "Toggles Wi-Fi, Bluetooth, Flashlight, or Do Not Disturb.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val setting = args["setting"]?.toString()?.lowercase() ?: ""
                val state = args["state"] as? Boolean ?: true
                val dtk = com.jarvis.app.tools.DeviceToolkit(context)
                val response = when (setting) {
                    "wifi", "wi-fi" -> dtk.toggleWifi(state)
                    "flashlight", "torch" -> dtk.flashlight(state)
                    "dnd", "do_not_disturb" -> dtk.dnd(state)
                    else -> "Setting '$setting' toggle not supported directly."
                }
                ToolExecutionResult(
                    toolId = "toggle_setting",
                    success = !response.contains("failed", ignoreCase = true) && !response.contains("unavailable", ignoreCase = true),
                    data = mapOf("setting" to setting, "state" to state),
                    verificationDetails = response
                )
            }
        )

        // 21. Send WhatsApp (Level 2) \u2014 Accessibility-driven via MessagingAutomation
        // Per the JARVIS vision worked example: open WhatsApp, resolve the
        // contact, open the conversation, TYPE the message, VERIFY it landed in
        // the input field, then STOP and ask "send now?". On confirmation the
        // orchestrator calls confirmSendWhatsApp() to tap the real send button.
        register(
            ToolDefinition(
                id = "send_whatsapp",
                name = "Send WhatsApp Message",
                description = "Opens WhatsApp, resolves the contact, opens the conversation, types the message and verifies it, then asks for confirmation before sending. Requires 'contact' or 'number' and 'message'.",
                category = "MESSAGING",
                riskLevel = RiskLevel.LEVEL_2
            ) { context, args ->
                val contactQuery = (args["contact"] ?: args["name"] ?: args["number"] ?: args["recipient"])?.toString() ?: ""
                val message = (args["message"] ?: args["body"] ?: args["text"])?.toString() ?: ""
                if (contactQuery.isBlank() || message.isBlank()) {
                    return@ToolDefinition ToolExecutionResult("send_whatsapp", false, null, "Contact and message are required.")
                }

                // Stateful: first call types & verifies the draft and returns
                // pending confirmation; the orchestrator re-invokes this same
                // tool on "yes", which then taps the real send button.
                val result = com.jarvis.app.tools.MessagingAutomation.executeSend(
                    context,
                    com.jarvis.app.tools.MessagingAutomation.TargetApp.WHATSAPP,
                    contactQuery,
                    message
                )
                if (!result.success) {
                    return@ToolDefinition ToolExecutionResult("send_whatsapp", false, null, result.verificationDetails.ifBlank { result.error ?: "Failed to prepare the WhatsApp message." })
                }

                if (result.preparedOnly) {
                    return@ToolDefinition ToolExecutionResult(
                        toolId = "send_whatsapp",
                        success = true,
                        data = mapOf("contact" to contactQuery, "message" to message, "preparedOnly" to true),
                        verificationDetails = result.verificationDetails
                    )
                }

                ToolExecutionResult(
                    toolId = "send_whatsapp",
                    success = true,
                    data = mapOf(
                        "contact" to contactQuery,
                        "message" to message,
                        "pendingSendApp" to (result.pendingSendApp ?: "WhatsApp")
                    ),
                    verificationDetails = result.verificationDetails
                )
            }
        )

        // 24. Volume Adjustment (Level 1)
        register(
            ToolDefinition(
                id = "device_volume",
                name = "Adjust Volume",
                description = "Controls media volume. Pass 'action' ('up', 'down', 'mute', 'unmute', 'max') or 'level' (0-100).",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                if (am == null) {
                    return@ToolDefinition ToolExecutionResult("device_volume", false, null, "Audio service unavailable.")
                }
                val action = (args["action"] ?: args["direction"])?.toString()?.lowercase() ?: "up"
                val level = (args["level"] as? Number)?.toInt()
                val maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)

                val result = when {
                    level != null -> {
                        val target = (level.coerceIn(0, 100) * maxVol) / 100
                        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, android.media.AudioManager.FLAG_SHOW_UI)
                        "Volume set to $level%"
                    }
                    action == "up" || action == "raise" || action == "increase" -> {
                        am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                        "Volume increased."
                    }
                    action == "down" || action == "lower" || action == "decrease" -> {
                        am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
                        "Volume decreased."
                    }
                    action == "mute" || action == "silence" -> {
                        am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_MUTE, android.media.AudioManager.FLAG_SHOW_UI)
                        "Volume muted."
                    }
                    action == "unmute" -> {
                        am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, android.media.AudioManager.FLAG_SHOW_UI)
                        "Volume unmuted."
                    }
                    action == "max" -> {
                        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxVol, android.media.AudioManager.FLAG_SHOW_UI)
                        "Volume set to maximum."
                    }
                    else -> {
                        am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                        "Volume adjusted."
                    }
                }
                ToolExecutionResult(
                    toolId = "device_volume",
                    success = true,
                    data = mapOf("action" to action),
                    verificationDetails = result
                )
            }
        )

        // 25. Flashlight (Level 1)
        register(
            ToolDefinition(
                id = "device_flashlight",
                name = "Toggle Flashlight",
                description = "Turns the flashlight / torch on or off. Pass 'enabled' or 'on' as boolean.",
                category = "DEVICE",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val enable = (args["enabled"] ?: args["on"] ?: args["state"] ?: true) as? Boolean ?: true
                val response = com.jarvis.app.tools.DeviceToolkit(context).flashlight(enable)
                ToolExecutionResult(
                    toolId = "device_flashlight",
                    success = !response.contains("failed", ignoreCase = true),
                    data = mapOf("enabled" to enable),
                    verificationDetails = response
                )
            }
        )
    }
}
