package com.jarvis.agent.nlu

import android.content.Context
import com.jarvis.app.people.PeopleGraph

/**
 * Minimal fast-path intent router.
 *
 * Only handles:
 *  - Flashlight on/off (zero-latency hardware toggle)
 *  - Battery level query
 *  - Current time query
 *  - Call contact (with slot-filling for disambiguation)
 *  - SMS (with slot-filling for contact resolution)
 *  - Confirmation words (yes/no/cancel)
 *
 * Everything else goes to the LLM with real function calling.
 * The model decides what tool to call — no keyword matching.
 */
object IntentRouter {

    data class ParsedIntent(
        val id: String,
        val args: MutableMap<String, Any?> = mutableMapOf(),
        val confidence: Float = 1f
    ) {
        val isUnknown: Boolean get() = id == INTENT_UNKNOWN
    }

    const val INTENT_UNKNOWN = "unknown"
    const val INTENT_CALL = "call_contact"
    const val INTENT_SMS = "send_sms"
    const val INTENT_FLASHLIGHT = "device_flashlight"
    const val INTENT_BATTERY = "device_battery"
    const val INTENT_TIME = "device_time"
    const val INTENT_OPEN_APP = "open_app"
    const val INTENT_REPLY = "reply_to_notification"

    private val CANCEL_WORDS = setOf(
        "stop", "cancel", "nevermind", "never mind", "forget it", "abort", "enough", "quit"
    )
    private val YES_WORDS = setOf("yes", "yeah", "yep", "sure", "go ahead", "do it", "send it", "okay", "ok", "confirm", "fine")
    private val NO_WORDS = setOf("no", "nope", "nah", "don't", "dont", "cancel that", "not now")

    fun isCancel(input: String) = matches(input, CANCEL_WORDS)
    fun isYes(input: String) = matches(input, YES_WORDS)
    fun isNo(input: String) = matches(input, NO_WORDS)

    private fun matches(input: String, words: Set<String>): Boolean {
        val lower = input.lowercase().trim().trimEnd('.', '!')
        return lower in words || words.any { lower.startsWith("$it ") } || lower.length <= 6 && lower in words
    }

    /**
     * Parses a fresh utterance.
     * Only fast-paths truly zero-latency hardware commands and
     * communication intents that need slot-filling.
     */
    suspend fun parse(context: Context, input: String): ParsedIntent {
        val raw = input.trim()
        val lower = raw.lowercase().trim()
        if (raw.isBlank()) return ParsedIntent(INTENT_UNKNOWN)

        // ---- Communication (needs contact resolution, so it goes first) ----
        parseCall(context, raw, lower)?.let { return it }
        parseSms(context, raw, lower)?.let { return it }

        // ---- Fast-path: truly zero-latency hardware commands ----
        // Flashlight on/off — exact match, not keyword anywhere
        if (lower == "flashlight on" || lower == "torch on" ||
            lower == "turn on flashlight" || lower == "turn on torch") {
            return ParsedIntent(INTENT_FLASHLIGHT, mutableMapOf("enabled" to true))
        }
        if (lower == "flashlight off" || lower == "torch off" ||
            lower == "turn off flashlight" || lower == "turn off torch") {
            return ParsedIntent(INTENT_FLASHLIGHT, mutableMapOf("enabled" to false))
        }

        // Battery level — exact match
        if (lower == "battery" || lower == "battery level" ||
            lower == "how much battery" || lower == "what's my battery") {
            return ParsedIntent(INTENT_BATTERY)
        }

        // Current time — exact match
        if (lower == "time" || lower == "what time" ||
            lower == "what time is it" || lower == "what's the time") {
            return ParsedIntent(INTENT_TIME)
        }

        // Everything else → LLM (returns Unknown)
        return ParsedIntent(INTENT_UNKNOWN)
    }

    // ------------------------------------------------------------------ calls

    private suspend fun parseCall(context: Context, raw: String, lower: String): ParsedIntent? {
        val verb = listOf("call up", "phone", "ring", "dial", "call")
            .firstOrNull { lower.startsWith("$it ") || lower == it }
            ?: return null

        var rest = raw.substring(verb.length).trim()
        val redial = rest.contains("back", true) || rest.contains("again", true)

        var numberType: String? = null
        for (type in listOf("mobile", "home", "work")) {
            if (rest.endsWith(" on $type", true) || rest.contains(" $type number", true)) {
                numberType = type
                rest = rest.substringBefore(" on $type", rest).trim()
                break
            }
        }
        rest = rest.replace(Regex("(?i)\\b(please|back|again|up|right now|now)\\b"), "").trim()
        if (rest.isBlank()) return ParsedIntent(INTENT_CALL, mutableMapOf("redial" to true))

        val args = mutableMapOf<String, Any?>("contact" to rest)
        numberType?.let { args["number_type"] = it }
        args["redial"] = redial
        return ParsedIntent(INTENT_CALL, args)
    }

    // ------------------------------------------------------------------- sms

    private suspend fun parseSms(context: Context, raw: String, lower: String): ParsedIntent? {
        val verb = listOf("send a message to", "send message to", "send sms to", "text", "message", "sms", "tell")
            .firstOrNull { lower.startsWith("$it ") }
            ?: return null

        val rest = raw.substring(verb.length).trim()
        if (rest.isBlank()) return ParsedIntent(INTENT_SMS, mutableMapOf("contact" to "", "message" to ""))

        val words = rest.split(Regex("\\s+"))
        val maxNameWords = words.size.coerceAtMost(4)
        for (take in maxNameWords downTo 1) {
            val candidate = words.take(take).joinToString(" ")
            val resolved = try {
                PeopleGraph.resolve(context, candidate)
            } catch (_: Exception) {
                emptyList()
            }
            if (resolved.isNotEmpty()) {
                val body = words.drop(take).joinToString(" ").trim()
                return ParsedIntent(
                    INTENT_SMS,
                    mutableMapOf("contact" to candidate, "message" to body)
                )
            }
        }

        return ParsedIntent(
            INTENT_SMS,
            mutableMapOf("contact" to words.first(), "message" to words.drop(1).joinToString(" ").trim())
        )
    }
}
