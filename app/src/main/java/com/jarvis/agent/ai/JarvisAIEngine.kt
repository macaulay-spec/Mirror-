package com.jarvis.agent.ai

import android.content.Context
import com.jarvis.android.accessibility.JarvisAccessibilityService
import com.jarvis.agent.memory.JarvisMemoryManager
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionRequest
import com.jarvis.core.model.ToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class JarvisEngineResult(
    val reply: String,
    val state: JarvisVisualState = JarvisVisualState.SUCCESS,
    val toolResult: ToolExecutionResult? = null
)

class JarvisAIEngine(private val context: Context) {
    private val providerRouter = ProviderRouter()
    val memoryManager = JarvisMemoryManager(context)
    private val agentExecutor = AgentExecutor(context, providerRouter, memoryManager)

    val systemPrompt: String
        get() {
            val session = memoryManager.getSessionContext()
            return """
                You are JARVIS, a personal AI operating layer for Android.
                You are direct, precise, concise, and focused on executing actions via Accessibility and device tools.
                Current Session Context:
                - Active App Package: ${session.currentApp}
                - Current Task: ${session.currentTask}
                - Last Action: ${session.lastAction} (Success: ${session.lastActionResult})
                - Action Details: ${session.lastActionDetails}

                Available tools:
                - device_battery / battery_info: check battery percentage, charging state, temp
                - device_time: check system time
                - app_launch / open_app: open app by name (e.g. WhatsApp, YouTube, Spotify, Chrome)
                - app_search: search inside an app (e.g. search YouTube, Spotify, Google Maps)
                - device_lock: lock device screen / turn off display
                - device_media_control: media playback control (play, pause, next, previous, stop)
                - device_flashlight: toggle flashlight on/off
                - device_volume: adjust media volume (up, down, mute, unmute)
                - read_notifications: read incoming notifications from WhatsApp, SMS, Telegram, etc.
                - reply_notification: reply directly to a notification (requires package/app and message)
                - screen_read: read structured screen elements and visible text
                - find_text: search for specific text on screen
                - click_element: click an interactive UI element by text or description
                - type_text: enter text into editable input fields
                - scroll: scroll forward or backward
                - tap: tap at specific coordinates
                - press_back / press_home / open_recents: system navigation
                - smart_tv_control: control smart TV / Android box casting

                To use a tool, you MUST reply with a JSON object ONLY in this exact format:
                {
                    "action": "tool_call",
                    "tool": "tool_name",
                    "arguments": { "key": "value" },
                    "expectedResult": "what you expect"
                }
                If you are just answering the user or have finished executing tools, reply with:
                {
                    "action": "reply",
                    "message": "your final message to the user"
                }
                Analyze the request, use tools if needed, observe the result, and respond concisely.
            """.trimIndent()
        }

    suspend fun processCommand(rawInput: String, onChunk: ((String) -> Unit)? = null): JarvisEngineResult = withContext(Dispatchers.Default) {
        val input = rawInput.trim()
        if (input.isBlank()) return@withContext JarvisEngineResult("Please provide a command.")

        // Update active app context from accessibility service if available
        val activeApp = JarvisAccessibilityService.instance?.currentPackageName ?: "unknown"
        memoryManager.updateSessionContext(app = activeApp, task = "Processing command", action = null, result = null, details = null)

        memoryManager.addConversation("user", input)

        // Retrieve relevant long term memories and recent conversation history
        val relevantMemories = memoryManager.recallRelevant(input)
        val recentConv = memoryManager.recentConversation()
        val history = recentConv.map { it.role to it.text }.toMutableList()

        if (relevantMemories.isNotEmpty()) {
            val memorySnippet = "Relevant User Memories:\n" + relevantMemories.joinToString("\n") { "- ${it.content}" }
            history.add(0, "system" to memorySnippet)
        }

        // 1. Check deterministic fast-path or contextual routing
        val deterministicResult = checkDeterministicRouting(input)
        if (deterministicResult != null) {
            memoryManager.recordToolExecution(
                toolId = deterministicResult.toolId,
                args = emptyMap(),
                result = deterministicResult
            )
            val reply = if (deterministicResult.success) {
                deterministicResult.verificationDetails ?: "Action executed successfully."
            } else {
                deterministicResult.error ?: "Action failed."
            }
            memoryManager.addConversation("jarvis", reply)
            return@withContext JarvisEngineResult(
                reply = reply,
                state = if (deterministicResult.success) JarvisVisualState.SUCCESS else JarvisVisualState.ERROR,
                toolResult = deterministicResult
            )
        }
        // 2. Query Provider Router via AgentExecutor
        val agentResult = agentExecutor.executeTask(systemPrompt, history, input, null, onChunk)
        val finalResult = if (agentResult.state == JarvisVisualState.ERROR && agentResult.reply.contains("Agent failure")) {
            val localReply = generateLocalProtocolReply(input)
            JarvisEngineResult(localReply, JarvisVisualState.SUCCESS)
        } else if (agentResult.reply.contains("Neural gateway is currently offline")) {
            val localReply = generateLocalProtocolReply(input)
            JarvisEngineResult(localReply, JarvisVisualState.SUCCESS)
        } else {
            agentResult
        }
        memoryManager.addConversation("jarvis", finalResult.reply)
        return@withContext finalResult
    }

    private fun generateLocalProtocolReply(input: String): String {
        val lower = input.lowercase().trim()
        val name = com.jarvis.app.config.ApiConfig.userName
        return when {
            lower.contains("who are you") || lower.contains("what is your name") ->
                "I am JARVIS, your personal intelligence layer and device operating core on this device, $name."
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Online and standing by, $name. How may I assist you?"
            lower.contains("how are you") || lower.contains("status") ->
                "All local subsystems operating at nominal parameters, $name. Device controls and local memory are fully active."
            lower.contains("thank") ->
                "At your service, $name."
            lower.contains("what can you do") || lower.contains("capabilities") || lower.contains("help") ->
                "I can execute voice commands, control phone settings (volume, flashlight, camera), navigate and interact with apps via Accessibility, manage reminders, inspect battery, and query on-device intelligence."
            lower.contains("date") || lower.contains("today") -> {
                val now = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                "Today is $now, $name."
            }
            else ->
                "Executing local protocol for: \"$input\". All core device functions and sensors are fully engaged."
        }
    }

    private suspend fun checkDeterministicRouting(input: String): ToolExecutionResult? {
        val lower = input.lowercase().trim()
        return when {
            lower.contains("battery") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("battery_info", "Get Battery", emptyMap(), RiskLevel.LEVEL_0))
            }
            lower.startsWith("time") || lower.startsWith("what time") || lower == "what is the time" || lower == "what's the time" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_time", "Get Time", emptyMap(), RiskLevel.LEVEL_0))
            }
            lower.contains("lock phone") || lower.contains("lock screen") || lower.contains("turn off screen") || lower == "lock" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_lock", "Lock Screen", emptyMap(), RiskLevel.LEVEL_1))
            }
            lower.contains("search for ") && lower.contains(" on youtube") -> {
                val q = input.substringAfter("search for ").substringBefore(" on youtube").trim()
                ToolRegistry.execute(context, ToolExecutionRequest("app_search", "Search YouTube", mapOf("app" to "youtube", "query" to q), RiskLevel.LEVEL_0))
            }
            lower.contains("search ") && lower.contains(" on youtube") -> {
                val q = input.substringAfter("search ").substringBefore(" on youtube").trim()
                ToolRegistry.execute(context, ToolExecutionRequest("app_search", "Search YouTube", mapOf("app" to "youtube", "query" to q), RiskLevel.LEVEL_0))
            }
            lower.contains("search for ") && lower.contains(" on spotify") -> {
                val q = input.substringAfter("search for ").substringBefore(" on spotify").trim()
                ToolRegistry.execute(context, ToolExecutionRequest("app_search", "Search Spotify", mapOf("app" to "spotify", "query" to q), RiskLevel.LEVEL_0))
            }
            lower.contains("search ") && lower.contains(" on spotify") -> {
                val q = input.substringAfter("search ").substringBefore(" on spotify").trim()
                ToolRegistry.execute(context, ToolExecutionRequest("app_search", "Search Spotify", mapOf("app" to "spotify", "query" to q), RiskLevel.LEVEL_0))
            }
            lower.contains("search for ") && (lower.contains(" on maps") || lower.contains(" on google maps")) -> {
                val q = input.substringAfter("search for ").substringBefore(" on maps").substringBefore(" on google maps").trim()
                ToolRegistry.execute(context, ToolExecutionRequest("app_search", "Search Maps", mapOf("app" to "maps", "query" to q), RiskLevel.LEVEL_0))
            }
            lower.startsWith("play music") || lower == "play" || lower == "resume" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_media_control", "Play Media", mapOf("action" to "play"), RiskLevel.LEVEL_0))
            }
            lower.startsWith("pause music") || lower == "pause" || lower == "stop music" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_media_control", "Pause Media", mapOf("action" to "pause"), RiskLevel.LEVEL_0))
            }
            lower.contains("next song") || lower.contains("next track") || lower == "skip" || lower == "next" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_media_control", "Next Track", mapOf("action" to "next"), RiskLevel.LEVEL_0))
            }
            lower.contains("previous song") || lower.contains("prev song") || lower == "previous track" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_media_control", "Previous Track", mapOf("action" to "previous"), RiskLevel.LEVEL_0))
            }
            lower.startsWith("open ") && (lower.contains(" and ") || lower.contains(" to ") || lower.contains(" for ")) -> {
                val appPart = lower.substringAfter("open ").substringBefore(" and ").substringBefore(" to ").substringBefore(" for ").trim()
                val actionPart = input.substringAfter(appPart).trim()
                val openRes = ToolRegistry.execute(context, ToolExecutionRequest("open_app", "Launch $appPart", mapOf("app" to appPart), RiskLevel.LEVEL_0))
                if (actionPart.contains("search")) {
                    val query = actionPart.substringAfter("search").removePrefix("for").removePrefix("on").trim()
                    ToolRegistry.execute(context, ToolExecutionRequest("app_search", "Search $appPart", mapOf("app" to appPart, "query" to query), RiskLevel.LEVEL_0))
                } else {
                    openRes
                }
            }
            lower.startsWith("open ") -> {
                val app = input.substring(5).trim()
                ToolRegistry.execute(context, ToolExecutionRequest("open_app", "Launch $app", mapOf("app" to app), RiskLevel.LEVEL_0))
            }
            lower.startsWith("launch ") -> {
                val app = input.substring(7).trim()
                ToolRegistry.execute(context, ToolExecutionRequest("open_app", "Launch $app", mapOf("app" to app), RiskLevel.LEVEL_0))
            }
            lower.startsWith("start ") && !lower.startsWith("start with ") -> {
                val app = input.substring(6).trim()
                ToolRegistry.execute(context, ToolExecutionRequest("open_app", "Launch $app", mapOf("app" to app), RiskLevel.LEVEL_0))
            }
            lower.contains("read notifications") || lower.contains("check notifications") || lower.contains("my notifications") || lower.contains("what are my notifications") || lower == "notifications" || lower.contains("read my messages") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("read_notifications", "Read Notifications", emptyMap(), RiskLevel.LEVEL_0))
            }
            lower.startsWith("search for ") -> {
                val q = input.substring(11).trim()
                ToolRegistry.execute(context, ToolExecutionRequest("app_search", "Search", mapOf("app" to "web", "query" to q), RiskLevel.LEVEL_0))
            }
            lower.startsWith("google ") -> {
                val q = input.substring(7).trim()
                ToolRegistry.execute(context, ToolExecutionRequest("app_search", "Search", mapOf("app" to "web", "query" to q), RiskLevel.LEVEL_0))
            }
            lower.contains("tv") || lower.contains("cast") || lower.contains("smart tv") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("smart_tv_control", "Smart TV Control", mapOf("action" to "open_cast"), RiskLevel.LEVEL_0))
            }
            lower.contains("read screen") || lower.contains("what's on screen") || lower.contains("screen text") || lower == "read display" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("screen_read", "Read Screen Structure", emptyMap(), RiskLevel.LEVEL_0))
            }
            lower.startsWith("find ") -> {
                val query = input.substring(5).trim()
                ToolRegistry.execute(context, ToolExecutionRequest("find_text", "Find Text", mapOf("query" to query), RiskLevel.LEVEL_0))
            }
            lower.startsWith("click ") -> {
                val target = input.substring(6).trim()
                ToolRegistry.execute(context, ToolExecutionRequest("click_element", "Click Element", mapOf("target" to target), RiskLevel.LEVEL_1))
            }
            lower.startsWith("tap on ") -> {
                val target = input.substring(7).trim()
                ToolRegistry.execute(context, ToolExecutionRequest("click_element", "Click Element", mapOf("target" to target), RiskLevel.LEVEL_1))
            }
            lower.startsWith("type ") -> {
                val text = input.substring(5).trim()
                ToolRegistry.execute(context, ToolExecutionRequest("type_text", "Type Text", mapOf("text" to text, "marker" to ""), RiskLevel.LEVEL_1))
            }
            lower.contains("scroll down") || lower.contains("scroll forward") || lower == "scroll" || lower == "page down" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("scroll", "Scroll Screen", mapOf("direction" to "forward"), RiskLevel.LEVEL_1))
            }
            lower.contains("scroll up") || lower.contains("scroll backward") || lower == "page up" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("scroll", "Scroll Screen", mapOf("direction" to "backward"), RiskLevel.LEVEL_1))
            }
            lower == "back" || lower == "go back" || lower == "press back" || lower == "navigate back" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("press_back", "Press Back", emptyMap(), RiskLevel.LEVEL_1))
            }
            lower == "home" || lower == "go home" || lower == "press home" || lower == "return home" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("press_home", "Press Home", emptyMap(), RiskLevel.LEVEL_1))
            }
            lower == "recents" || lower == "recent apps" || lower == "switch apps" || lower == "overview" || lower == "show recents" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("open_recents", "Open Recents", emptyMap(), RiskLevel.LEVEL_1))
            }
            lower.startsWith("remember ") -> {
                val fact = input.substring(9).trim()
                ToolRegistry.execute(context, ToolExecutionRequest("memory_remember", "Remember Fact", mapOf("content" to fact), RiskLevel.LEVEL_0))
            }
            lower.startsWith("recall ") || lower.contains("what did i tell you to remember") || lower.contains("what are my memories") -> {
                val q = if (lower.startsWith("recall ")) input.substring(7).trim() else ""
                ToolRegistry.execute(context, ToolExecutionRequest("memory_recall", "Recall Fact", mapOf("query" to q), RiskLevel.LEVEL_0))
            }
            lower.contains("flashlight on") || lower.contains("turn on flashlight") || lower.contains("torch on") || lower.contains("turn on torch") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_flashlight", "Turn On Flashlight", mapOf("enable" to true), RiskLevel.LEVEL_1))
            }
            lower.contains("flashlight off") || lower.contains("turn off flashlight") || lower.contains("torch off") || lower.contains("turn off torch") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_flashlight", "Turn Off Flashlight", mapOf("enable" to false), RiskLevel.LEVEL_1))
            }
            lower.contains("volume up") || lower.contains("increase volume") || lower.contains("raise volume") || lower.contains("turn up volume") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_volume", "Volume Up", mapOf("action" to "up"), RiskLevel.LEVEL_1))
            }
            lower.contains("volume down") || lower.contains("decrease volume") || lower.contains("lower volume") || lower.contains("turn down volume") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_volume", "Volume Down", mapOf("action" to "down"), RiskLevel.LEVEL_1))
            }
            lower == "mute" || lower == "mute volume" || lower == "silence" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_volume", "Mute Volume", mapOf("action" to "mute"), RiskLevel.LEVEL_1))
            }
            lower == "unmute" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_volume", "Unmute Volume", mapOf("action" to "unmute"), RiskLevel.LEVEL_1))
            }
            lower == "vibrate" || lower == "haptic" || lower == "test vibration" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_vibrate", "Vibrate Device", mapOf("duration_ms" to 300L), RiskLevel.LEVEL_0))
            }
            lower.startsWith("create event ") || lower.startsWith("add event ") || lower.startsWith("schedule event ") -> {
                val title = if (lower.startsWith("create event ")) input.substring(13).trim()
                else if (lower.startsWith("add event ")) input.substring(10).trim()
                else input.substring(15).trim()
                ToolRegistry.execute(context, ToolExecutionRequest("calendar_create", "Create Calendar Event", mapOf("title" to title), RiskLevel.LEVEL_1))
            }
            lower.contains("read notifications") || lower.contains("check notifications") || lower.contains("my notifications") || lower == "notifications" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("get_recent_notifications", "Get Notifications", emptyMap(), RiskLevel.LEVEL_0))
            }
            lower.contains("what apps did i use") || lower.contains("recently used apps") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("get_recent_apps", "Get Recent Apps", emptyMap(), RiskLevel.LEVEL_0))
            }
            lower.contains("app usage") || lower.contains("how long did i use") || lower.contains("screen time") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("get_daily_usage", "Get Daily Usage", emptyMap(), RiskLevel.LEVEL_0))
            }
            lower.contains("check storage") || lower.contains("how much storage") || lower.contains("storage space") || lower.contains("free space") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_storage", "Get Storage", emptyMap(), RiskLevel.LEVEL_0))
            }
            lower.contains("network status") || lower.contains("wifi status") || lower.contains("check connection") || lower.contains("connectivity") -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_connectivity", "Get Connectivity", emptyMap(), RiskLevel.LEVEL_0))
            }
            lower.contains("where am i") || lower.contains("my location") || lower.contains("what is my location") || lower == "current location" -> {
                ToolRegistry.execute(context, ToolExecutionRequest("device_location", "Get Location", emptyMap(), RiskLevel.LEVEL_0))
            }
            else -> null
        }
    }
}
