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
        "memory_save" to "memory_remember"
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

        // 17. Call Contact / Phone Dial (Level 2)
        register(
            ToolDefinition(
                id = "call_contact",
                name = "Call Contact",
                description = "Places a call or opens the dialer for a given person or phone number.",
                category = "COMMUNICATION",
                riskLevel = RiskLevel.LEVEL_2
            ) { context, args ->
                val contactQuery = args["contact"]?.toString() ?: args["name"]?.toString() ?: args["number"]?.toString() ?: ""
                if (contactQuery.isBlank()) {
                    return@ToolDefinition ToolExecutionResult("call_contact", false, null, "Contact name or number not specified.")
                }

                // Check people graph first for relationships like mumsi / nicknames
                val matches = com.jarvis.app.people.PeopleGraph.resolve(context, contactQuery)
                val topMatch = matches.firstOrNull()
                val number = topMatch?.numbers?.firstOrNull()?.value ?: run {
                    val phoneContact = com.jarvis.app.tools.ContactsToolkit(context).search(contactQuery)
                    phoneContact?.phone
                } ?: contactQuery
                val displayName = topMatch?.person?.displayName ?: contactQuery

                val dialed = com.jarvis.app.tools.ContactsToolkit(context).dial(number)
                if (dialed) {
                    ToolExecutionResult(
                        toolId = "call_contact",
                        success = true,
                        data = mapOf("target" to displayName, "number" to number),
                        verificationDetails = "Initiating call to $displayName ($number)."
                    )
                } else {
                    ToolExecutionResult("call_contact", false, null, "Could not open dialer for $contactQuery.")
                }
            }
        )

        // 18. Send Message / SMS (Level 2)
        register(
            ToolDefinition(
                id = "send_message",
                name = "Send Direct Message",
                description = "Sends an SMS or direct message to a contact.",
                category = "COMMUNICATION",
                riskLevel = RiskLevel.LEVEL_2
            ) { context, args ->
                val contactName = args["contact"]?.toString() ?: args["recipient"]?.toString() ?: ""
                val body = args["body"]?.toString() ?: args["message"]?.toString() ?: ""
                if (contactName.isBlank() || body.isBlank()) {
                    return@ToolDefinition ToolExecutionResult("send_message", false, null, "Both recipient and message body are required.")
                }

                val matches = com.jarvis.app.people.PeopleGraph.resolve(context, contactName)
                val topMatch = matches.firstOrNull()
                val phone = topMatch?.numbers?.firstOrNull()?.value ?: run {
                    val phoneContact = com.jarvis.app.tools.ContactsToolkit(context).search(contactName)
                    phoneContact?.phone
                } ?: contactName
                val displayName = topMatch?.person?.displayName ?: contactName

                val result = com.jarvis.app.tools.DeviceToolkit(context).sendSms(phone, body)
                if (result.startsWith("Sent")) {
                    ToolExecutionResult(
                        toolId = "send_message",
                        success = true,
                        data = mapOf("recipient" to displayName, "body" to body),
                        verificationDetails = "Message sent to $displayName: \"$body\""
                    )
                } else {
                    // Fallback to drafting in SMS app
                    com.jarvis.app.tools.DeviceToolkit(context).openSmsApp(phone, body)
                    ToolExecutionResult(
                        toolId = "send_message",
                        success = true,
                        data = mapOf("recipient" to displayName, "body" to body),
                        verificationDetails = "Opened SMS composer for $displayName with draft: \"$body\""
                    )
                }
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

        // 20. Navigate To (Level 1)
        register(
            ToolDefinition(
                id = "navigate_to",
                name = "Navigate To Location",
                description = "Opens turn-by-turn navigation or Google Maps for a destination.",
                category = "NAVIGATION",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val destination = args["destination"]?.toString() ?: args["query"]?.toString() ?: ""
                if (destination.isBlank()) {
                    return@ToolDefinition ToolExecutionResult("navigate_to", false, null, "Destination not provided.")
                }
                val navUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
                val intent = Intent(Intent.ACTION_VIEW, navUri).apply {
                    setPackage("com.google.android.apps.maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    ToolExecutionResult(
                        toolId = "navigate_to",
                        success = true,
                        data = mapOf("destination" to destination),
                        verificationDetails = "Starting navigation to $destination."
                    )
                } catch (_: Exception) {
                    // Fallback to geo URI
                    val geoUri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                    ToolExecutionResult(
                        toolId = "navigate_to",
                        success = true,
                        data = mapOf("destination" to destination),
                        verificationDetails = "Opened map search for $destination."
                    )
                }
            }
        )

        // 21. Send WhatsApp (Level 2)
        register(
            ToolDefinition(
                id = "send_whatsapp",
                name = "Send WhatsApp Message",
                description = "Sends a message to a contact via WhatsApp. Requires 'contact' or 'number' and 'message'.",
                category = "MESSAGING",
                riskLevel = RiskLevel.LEVEL_2
            ) { context, args ->
                val contactQuery = (args["contact"] ?: args["name"] ?: args["number"] ?: args["recipient"])?.toString() ?: ""
                val message = (args["message"] ?: args["body"] ?: args["text"])?.toString() ?: ""
                if (contactQuery.isBlank() || message.isBlank()) {
                    return@ToolDefinition ToolExecutionResult("send_whatsapp", false, null, "Contact and message are required.")
                }
                val matches = com.jarvis.app.people.PeopleGraph.resolve(context, contactQuery)
                val topMatch = matches.firstOrNull()
                val rawNumber = topMatch?.numbers?.firstOrNull()?.value ?: run {
                    val phoneContact = com.jarvis.app.tools.ContactsToolkit(context).search(contactQuery)
                    phoneContact?.phone
                } ?: contactQuery
                val displayName = topMatch?.person?.displayName ?: contactQuery
                val cleanNumber = rawNumber.replace(Regex("[^0-9+]"), "")

                try {
                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolExecutionResult(
                        toolId = "send_whatsapp",
                        success = true,
                        data = mapOf("contact" to displayName, "number" to cleanNumber, "message" to message),
                        verificationDetails = "WhatsApp message dispatched to $displayName."
                    )
                } catch (_: Exception) {
                    try {
                        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}")
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallbackIntent)
                        ToolExecutionResult(
                            toolId = "send_whatsapp",
                            success = true,
                            data = mapOf("contact" to displayName, "number" to cleanNumber, "message" to message),
                            verificationDetails = "WhatsApp link opened for $displayName."
                        )
                    } catch (e2: Exception) {
                        ToolExecutionResult("send_whatsapp", false, null, "Could not launch WhatsApp: ${e2.localizedMessage}")
                    }
                }
            }
        )

        // 22. Set Alarm (Level 1)
        register(
            ToolDefinition(
                id = "set_alarm",
                name = "Set Alarm",
                description = "Sets an alarm on the device clock. Pass 'hour' (0-23), 'minute' (0-59), and optional 'message'.",
                category = "CALENDAR",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val hour = (args["hour"] as? Number)?.toInt() ?: 7
                val minute = (args["minute"] as? Number)?.toInt() ?: 0
                val message = (args["message"] ?: args["label"] ?: "Alarm")?.toString() ?: "Alarm"
                val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                    putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
                    putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    val timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                    ToolExecutionResult(
                        toolId = "set_alarm",
                        success = true,
                        data = mapOf("hour" to hour, "minute" to minute, "message" to message),
                        verificationDetails = "Alarm set for $timeStr ($message)."
                    )
                } catch (e: Exception) {
                    ToolExecutionResult("set_alarm", false, null, "Failed to set alarm: ${e.localizedMessage}")
                }
            }
        )

        // 23. Set Timer (Level 1)
        register(
            ToolDefinition(
                id = "set_timer",
                name = "Set Timer",
                description = "Sets a countdown timer. Pass 'seconds' or 'minutes' and optional 'message'.",
                category = "CALENDAR",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val secondsArg = (args["seconds"] as? Number)?.toInt()
                val minutesArg = (args["minutes"] as? Number)?.toInt()
                val totalSeconds = secondsArg ?: ((minutesArg ?: 1) * 60)
                val message = (args["message"] ?: args["label"] ?: "Timer")?.toString() ?: "Timer"
                val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(android.provider.AlarmClock.EXTRA_LENGTH, totalSeconds)
                    putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
                    putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    ToolExecutionResult(
                        toolId = "set_timer",
                        success = true,
                        data = mapOf("seconds" to totalSeconds, "message" to message),
                        verificationDetails = "Timer set for $totalSeconds seconds ($message)."
                    )
                } catch (e: Exception) {
                    ToolExecutionResult("set_timer", false, null, "Failed to set timer: ${e.localizedMessage}")
                }
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
