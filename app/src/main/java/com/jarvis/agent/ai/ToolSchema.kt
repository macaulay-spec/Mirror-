package com.jarvis.agent.ai

import com.jarvis.agent.tool.ToolDefinition
import com.jarvis.agent.tool.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns the ToolRegistry into real function-calling schemas.
 *
 * Before this the model only got a *text list* of tool names and was asked to invent JSON
 * that matched. That is why it hallucinated argument names and missed tools. Providers
 * that support function calling (Gemini, OpenAI) now receive the actual schema for every
 * registered action, and reply with a structured call — so any tool can be invoked by the
 * model, including ones that did not exist when the prompt was written.
 */
object ToolSchema {

    /** Argument names each tool actually reads, so the schema matches the handler. */
    private val ARG_HINTS: Map<String, List<String>> = mapOf(
        "call_contact" to listOf("contact", "number", "number_type"),
        "send_sms" to listOf("contact", "number", "message"),
        "contact_lookup" to listOf("contact", "name", "query"),
        "read_call_log" to listOf("limit"),
        "reply_to_notification" to listOf("app_name", "package_name", "reply_text"),
        "reply_notification" to listOf("package", "app", "message"),
        "read_notifications" to listOf("app", "package", "limit"),
        "get_recent_notifications" to listOf("app_name", "limit"),
        "dismiss_notification" to listOf("key"),
        "open_app" to listOf("app", "name"),
        "app_launch" to listOf("app_name", "app"),
        "app_search" to listOf("app", "query"),
        "web_search" to listOf("query"),
        "set_brightness" to listOf("percent", "auto"),
        "set_dnd" to listOf("on"),
        "set_ringer_mode" to listOf("mode"),
        "toggle_wifi" to listOf("on"),
        "toggle_bluetooth" to listOf("on"),
        "device_volume" to listOf("direction", "level"),
        "device_flashlight" to listOf("enabled", "on"),
        "device_media_control" to listOf("action"),
        "device_vibrate" to listOf("duration_ms"),
        "device_navigate_global" to listOf("action"),
        "smart_tv_control" to listOf("action", "url"),
        "find_text" to listOf("query"),
        "click_element" to listOf("text", "target"),
        "type_text" to listOf("text", "marker"),
        "scroll" to listOf("direction"),
        "tap" to listOf("x", "y"),
        "swipe" to listOf("fromX", "fromY", "toX", "toY"),
        "calendar_create" to listOf("title", "when", "notes"),
        "memory_remember" to listOf("content"),
        "memory_recall" to listOf("query"),
        "communication_send" to listOf("recipient", "message"),
        "get_daily_usage" to listOf("app_name"),
        "get_recent_apps" to listOf("limit")
    )

    private val INTEGER_ARGS = setOf("percent", "level", "limit", "duration_ms")
    private val NUMBER_ARGS = setOf("x", "y", "fromX", "fromY", "toX", "toY")
    private val BOOLEAN_ARGS = setOf("on", "auto", "enabled")

    /**
     * Tools worth exposing to the model. Read-only informational tools are left out of the
     * schema because the intent router already answers those instantly and offline.
     */
    // Keep this in step with the categories tools are registered under. A tool in a
    // category that is missing here is invisible to the model — it exists, it just never
    // gets called, which is the worst kind of bug to chase.
    private val EXPOSED_CATEGORIES = setOf(
        "PHONE", "MESSAGING", "APPS", "DEVICE", "SCREEN", "NOTIFICATIONS", "WEB",
        "CALENDAR", "MEMORY", "MEDIA", "COMMUNICATION", "INTEGRATION", "USAGE",
        "LOCATION", "ASSISTANT"
    )

    fun exportedTools(): List<ToolDefinition> =
        ToolRegistry.getAllTools().filter { it.category in EXPOSED_CATEGORIES }

    /** Gemini: `tools: [{ functionDeclarations: [...] }]` */
    fun forGemini(): JSONArray {
        val declarations = JSONArray()
        for (tool in exportedTools()) {
            declarations.put(
                JSONObject()
                    .put("name", tool.id)
                    .put("description", tool.description.take(400))
                    .put("parameters", parametersFor(tool.id))
            )
        }
        return JSONArray().put(JSONObject().put("functionDeclarations", declarations))
    }

    /** OpenAI: `tools: [{ type: "function", function: { ... } }]` */
    fun forOpenAI(): JSONArray {
        val tools = JSONArray()
        for (tool in exportedTools()) {
            tools.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function", JSONObject()
                            .put("name", tool.id)
                            .put("description", tool.description.take(400))
                            .put("parameters", parametersFor(tool.id))
                    )
            )
        }
        return tools
    }

    private fun parametersFor(toolId: String): JSONObject {
        val names = ARG_HINTS[toolId] ?: defaultArgs(toolId)
        val properties = JSONObject()
        for (name in names) {
            properties.put(
                name, JSONObject().put(
                    "type", when {
                        name in INTEGER_ARGS -> "integer"
                        name in NUMBER_ARGS -> "number"
                        name in BOOLEAN_ARGS -> "boolean"
                        else -> "string"
                    }
                )
            )
        }
        return JSONObject()
            .put("type", "object")
            .put("properties", properties)
    }

    /** Best-effort argument names for a tool we have no explicit hint for. */
    private fun defaultArgs(toolId: String): List<String> = when {
        toolId.startsWith("device_") || toolId.startsWith("get_") -> emptyList()
        toolId.contains("search") -> listOf("query")
        toolId.contains("click") || toolId.contains("tap") -> listOf("text", "target")
        toolId.contains("type") -> listOf("text")
        toolId.contains("send") || toolId.contains("reply") -> listOf("message")
        toolId.contains("open") || toolId.contains("launch") -> listOf("app")
        else -> listOf("query", "text")
    }
}
