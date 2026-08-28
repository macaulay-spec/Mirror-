package com.jarvis.agent.tool

import android.content.Context
import com.jarvis.app.config.AssistantPrefs
import com.jarvis.app.proactive.ProactiveScheduler
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult

/**
 * The switches JARVIS controls about itself.
 *
 * These are exposed as tools rather than only as rows in a settings screen because the
 * natural way to change them is to say so: "brief me at seven every morning", "stop
 * reading my codes out loud". Anything registered here is published to the model
 * automatically by ToolSchema, so no prompt needs editing.
 */
object ProactiveTools {

    fun registerAll() {
        registerBriefing()
        registerOtpSwitch()
        registerSensitiveSwitch()
        registerAlwaysListening()
    }

    private fun registerBriefing() {
        ToolRegistry.register(
            ToolDefinition(
                id = "set_briefing",
                name = "Morning Briefing",
                description = "Turns the daily spoken morning briefing on or off, and sets the hour and minute it fires.",
                category = "ASSISTANT",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val raw = args["enabled"]?.toString()
                val enabled = when {
                    raw == null -> null
                    raw.equals("true", true) || raw == "1" || raw.equals("on", true) -> true
                    raw.equals("false", true) || raw == "0" || raw.equals("off", true) -> false
                    else -> null
                }

                val hour = args["hour"]?.toString()?.toIntOrNull()
                val minute = args["minute"]?.toString()?.toIntOrNull()

                if (hour != null) {
                    val safeHour = hour.coerceIn(0, 23)
                    val safeMinute = (minute ?: 0).coerceIn(0, 59)
                    ProactiveScheduler.setTime(context, safeHour, safeMinute)
                }

                if (enabled != null) {
                    ProactiveScheduler.setEnabled(context, enabled)
                } else if (hour != null && !ProactiveScheduler.isEnabled(context)) {
                    // Setting a time implies wanting the briefing.
                    ProactiveScheduler.setEnabled(context, true)
                }

                val (currentHour, currentMinute) = ProactiveScheduler.briefingTime(context)
                val timeLabel = String.format("%02d:%02d", currentHour, currentMinute)
                ok(
                    "set_briefing",
                    mapOf(
                        "enabled" to ProactiveScheduler.isEnabled(context),
                        "time" to timeLabel
                    ),
                    if (ProactiveScheduler.isEnabled(context)) {
                        "Morning briefing is on, and I will speak to you at $timeLabel."
                    } else {
                        "Morning briefing is off."
                    }
                )
            }
        )
    }

    private fun registerOtpSwitch() {
        ToolRegistry.register(
            ToolDefinition(
                id = "set_read_otp",
                name = "Read Codes Aloud",
                description = "Turns reading verification and one-time codes aloud on or off.",
                category = "ASSISTANT",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val enabled = parseBoolean(args["enabled"])
                    ?: return@ToolDefinition error("set_read_otp", "I did not catch whether you want that on or off.")
                AssistantPrefs.setReadOtpAloud(context, enabled)
                ok(
                    "set_read_otp",
                    mapOf("enabled" to enabled),
                    if (enabled) "I will read your codes aloud." else "I will keep codes to myself from now on."
                )
            }
        )
    }

    private fun registerSensitiveSwitch() {
        ToolRegistry.register(
            ToolDefinition(
                id = "set_hide_sensitive",
                name = "Hide Sensitive Content",
                description = "Hides notification text that looks like a password or card detail. One-time codes stay visible either way.",
                category = "ASSISTANT",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val enabled = parseBoolean(args["enabled"])
                    ?: return@ToolDefinition error("set_hide_sensitive", "I did not catch whether you want that on or off.")
                AssistantPrefs.setHideSensitiveContent(context, enabled)
                ok(
                    "set_hide_sensitive",
                    mapOf("enabled" to enabled),
                    if (enabled) "Sensitive notification text will be hidden." else "I will show notification text in full."
                )
            }
        )
    }

    private fun registerAlwaysListening() {
        ToolRegistry.register(
            ToolDefinition(
                id = "set_always_listening",
                name = "Always Listening",
                description = "Turns the hands-free 'Hey JARVIS' background service on or off.",
                category = "ASSISTANT",
                riskLevel = RiskLevel.LEVEL_0
            ) { context, args ->
                val enabled = parseBoolean(args["enabled"])
                    ?: return@ToolDefinition error("set_always_listening", "I did not catch whether you want that on or off.")
                AssistantPrefs.setAlwaysListening(context, enabled)
                ok(
                    "set_always_listening",
                    mapOf("enabled" to enabled),
                    if (enabled) "I am listening for 'Hey JARVIS'." else "Background listening is off. Wake me from the app or the quick settings tile."
                )
            }
        )
    }

    private fun parseBoolean(value: Any?): Boolean? = when (
        value?.toString()?.lowercase()
    ) {
        "true", "1", "on", "yes" -> true
        "false", "0", "off", "no" -> false
        else -> null
    }

    private fun ok(toolId: String, data: Map<String, Any>, spoken: String) =
        ToolExecutionResult(
            toolId = toolId,
            success = true,
            data = data,
            verificationDetails = spoken
        )

    private fun error(toolId: String, message: String) =
        ToolExecutionResult(toolId = toolId, success = false, data = null, error = message)
}
