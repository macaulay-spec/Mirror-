package com.jarvis.agent.nlu

import android.content.Context
import com.jarvis.app.people.PeopleGraph

/**
 * Local, instant, offline understanding of the ~40 things people actually say.
 *
 * This is the SiriKit / Bixby-Capsule layer: typed intents with slots, resolved on the
 * device in milliseconds, with no model in the loop. Anything it cannot parse comes back
 * as [ParsedIntent.Unknown] and is handed to the LLM.
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
    const val INTENT_OPEN_APP = "open_app"
    const val INTENT_APP_SEARCH = "app_search"
    const val INTENT_BRIGHTNESS = "set_brightness"
    const val INTENT_DND = "set_dnd"
    const val INTENT_RINGER = "set_ringer_mode"
    const val INTENT_WIFI = "toggle_wifi"
    const val INTENT_BLUETOOTH = "toggle_bluetooth"
    const val INTENT_VOLUME = "device_volume"
    const val INTENT_FLASHLIGHT = "device_flashlight"
    const val INTENT_MEDIA = "device_media_control"
    const val INTENT_LOCK = "device_lock"
    const val INTENT_BATTERY = "battery_info"
    const val INTENT_TIME = "device_time"
    const val INTENT_STORAGE = "device_storage"
    const val INTENT_CONNECTIVITY = "device_connectivity"
    const val INTENT_LOCATION = "device_location"
    const val INTENT_SCREEN_READ = "screen_read"
    const val INTENT_FIND_TEXT = "find_text"
    const val INTENT_CLICK = "click_element"
    const val INTENT_TYPE = "type_text"
    const val INTENT_SCROLL = "scroll"
    const val INTENT_BACK = "press_back"
    const val INTENT_HOME = "press_home"
    const val INTENT_RECENTS = "open_recents"
    const val INTENT_REMEMBER = "memory_remember"
    const val INTENT_RECALL = "memory_recall"
    const val INTENT_CONTACT_LOOKUP = "contact_lookup"
    const val INTENT_CALL_LOG = "read_call_log"
    const val INTENT_NOTIFICATIONS = "read_notifications"
    const val INTENT_REPLY = "reply_to_notification"
    const val INTENT_CALENDAR = "calendar_create"
    const val INTENT_SMART_TV = "smart_tv_control"
    const val INTENT_WEB_SEARCH = "web_search"

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
     * Parses a fresh utterance. [context] is needed to resolve contact names against the
     * people graph when splitting "text tunde i'm on my way" into recipient + body.
     */
    suspend fun parse(context: Context, input: String): ParsedIntent {
        val raw = input.trim()
        val lower = raw.lowercase().trim()
        if (raw.isBlank()) return ParsedIntent(INTENT_UNKNOWN)

        // ---- communication: needs contact resolution, so it goes first
        parseCall(context, raw, lower)?.let { return it }
        parseSms(context, raw, lower)?.let { return it }

        // ---- apps
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ")) {
            val app = raw.substringAfter(' ').trim()
            return ParsedIntent(INTENT_OPEN_APP, mutableMapOf("app" to app))
        }
        if ((lower.contains("search for ") || lower.startsWith("search ")) && lower.contains(" on youtube")) {
            val q = lower.substringAfter("search").substringBefore(" on youtube").trim().removePrefix("for").trim()
            return ParsedIntent(INTENT_APP_SEARCH, mutableMapOf("app" to "youtube", "query" to q))
        }
        if ((lower.contains("search for ") || lower.startsWith("search ")) && lower.contains(" on spotify")) {
            val q = lower.substringAfter("search").substringBefore(" on spotify").trim().removePrefix("for").trim()
            return ParsedIntent(INTENT_APP_SEARCH, mutableMapOf("app" to "spotify", "query" to q))
        }
        if (lower.startsWith("google ") || lower.startsWith("search for ") || lower.startsWith("search ")) {
            val q = lower.removePrefix("google ").removePrefix("search for ").removePrefix("search ").trim()
            return ParsedIntent(INTENT_APP_SEARCH, mutableMapOf("app" to "web", "query" to q))
        }

        // ---- device settings
        if (lower.contains("bright")) {
            val percent = Regex("(\\d{1,3})\\s*%?").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            return ParsedIntent(INTENT_BRIGHTNESS, mutableMapOf("percent" to (percent ?: 50)))
        }
        if (lower.contains("do not disturb") || lower.contains("dnd")) {
            val on = !lower.contains("off") && !lower.contains("disable")
            return ParsedIntent(INTENT_DND, mutableMapOf("on" to on))
        }
        if (lower.contains("vibrat") || lower.contains("silent") || lower.contains("ringer")) {
            val mode = when {
                lower.contains("vibrat") -> "vibrate"
                lower.contains("silent") -> "silent"
                else -> "normal"
            }
            return ParsedIntent(INTENT_RINGER, mutableMapOf("mode" to mode))
        }
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            val on = !lower.contains("off") && !lower.contains("disconnect")
            return ParsedIntent(INTENT_WIFI, mutableMapOf("on" to on))
        }
        if (lower.contains("bluetooth")) {
            val on = !lower.contains("off")
            return ParsedIntent(INTENT_BLUETOOTH, mutableMapOf("on" to on))
        }
        if (lower.contains("volume") || lower.contains("louder") || lower.contains("quieter")) {
            val direction = when {
                lower.contains("up") || lower.contains("louder") || lower.contains("increase") -> "up"
                lower.contains("down") || lower.contains("quieter") || lower.contains("lower") -> "down"
                lower.contains("mute") -> "mute"
                else -> "up"
            }
            return ParsedIntent(INTENT_VOLUME, mutableMapOf("direction" to direction))
        }
        if (lower.contains("flashlight") || lower.contains("torch")) {
            val on = !lower.contains("off")
            return ParsedIntent(INTENT_FLASHLIGHT, mutableMapOf("enabled" to on))
        }
        if (lower.contains("lock") && (lower.contains("phone") || lower.contains("screen") || lower == "lock")) {
            return ParsedIntent(INTENT_LOCK)
        }
        if (lower.contains("battery")) return ParsedIntent(INTENT_BATTERY)
        if (lower.contains("storage") || lower.contains("space")) return ParsedIntent(INTENT_STORAGE)
        if (lower.contains("internet") || lower.contains("network") || lower.contains("connected")) {
            return ParsedIntent(INTENT_CONNECTIVITY)
        }
        if (lower.startsWith("time") || lower.contains("what time") || lower.contains("the time")) {
            return ParsedIntent(INTENT_TIME)
        }
        if (lower.contains("where am i") || lower.contains("my location") || lower.contains("where are we")) {
            return ParsedIntent(INTENT_LOCATION)
        }

        // ---- media
        if (lower.startsWith("play") || lower.startsWith("pause") || lower.contains("next song") ||
            lower.contains("next track") || lower.contains("previous song") || lower == "skip" ||
            lower == "next" || lower.contains("stop music")
        ) {
            val action = when {
                lower.startsWith("pause") || lower.contains("stop music") -> "pause"
                lower.contains("next") || lower == "skip" -> "next"
                lower.contains("previous") || lower.contains("prev") -> "previous"
                else -> "play"
            }
            return ParsedIntent(INTENT_MEDIA, mutableMapOf("action" to action))
        }

        // ---- screen control
        if (lower.contains("read screen") || lower.contains("what's on screen") || lower.contains("read display")) {
            return ParsedIntent(INTENT_SCREEN_READ)
        }
        if (lower.startsWith("find ")) {
            return ParsedIntent(INTENT_FIND_TEXT, mutableMapOf("query" to raw.substring(5).trim()))
        }
        if (lower.startsWith("click ") || lower.startsWith("tap on ")) {
            val target = if (lower.startsWith("click ")) raw.substring(6) else raw.substring(7)
            return ParsedIntent(INTENT_CLICK, mutableMapOf("text" to target.trim()))
        }
        if (lower.startsWith("type ")) {
            return ParsedIntent(INTENT_TYPE, mutableMapOf("text" to raw.substring(5).trim(), "marker" to ""))
        }
        if (lower.contains("scroll")) {
            val direction = if (lower.contains("up") || lower.contains("back")) "backward" else "forward"
            return ParsedIntent(INTENT_SCROLL, mutableMapOf("direction" to direction))
        }
        if (lower == "back" || lower == "go back" || lower == "press back") return ParsedIntent(INTENT_BACK)
        if (lower == "home" || lower == "go home" || lower == "press home") return ParsedIntent(INTENT_HOME)
        if (lower == "recents" || lower == "recent apps" || lower == "overview") return ParsedIntent(INTENT_RECENTS)

        // ---- notifications
        if (lower.contains("notification") || lower.contains("read my messages") || lower.contains("any messages")) {
            return ParsedIntent(INTENT_NOTIFICATIONS)
        }

        // ---- calls / contacts
        if (lower.contains("who called") || lower.contains("missed call") || lower.contains("call log")) {
            return ParsedIntent(INTENT_CALL_LOG)
        }
        if (lower.startsWith("who is ")) {
            return ParsedIntent(INTENT_CONTACT_LOOKUP, mutableMapOf("contact" to raw.substring(7).trim()))
        }

        // ---- memory
        if (lower.startsWith("remember ") || lower.startsWith("remember that ")) {
            val fact = lower.removePrefix("remember that ").removePrefix("remember ").trim()
            return ParsedIntent(INTENT_REMEMBER, mutableMapOf("content" to fact))
        }
        if (lower.startsWith("recall ") || lower.contains("what do you remember") || lower.contains("my memories")) {
            val q = if (lower.startsWith("recall ")) raw.substring(7).trim() else ""
            return ParsedIntent(INTENT_RECALL, mutableMapOf("query" to q))
        }

        // ---- calendar & tv
        if (lower.contains("remind me") || lower.contains("add event") || lower.contains("schedule ")) {
            return ParsedIntent(INTENT_CALENDAR, mutableMapOf("title" to raw))
        }
        if (lower.contains("tv") || lower.contains("cast")) {
            return ParsedIntent(INTENT_SMART_TV, mutableMapOf("action" to "open_cast"))
        }

        return ParsedIntent(INTENT_UNKNOWN)
    }

    // ------------------------------------------------------------------ calls

    private fun parseCall(context: Context, raw: String, lower: String): ParsedIntent? {
        val verb = listOf("call up", "phone", "ring", "dial", "call")
            .firstOrNull { lower.startsWith("$it ") || lower == it }
            ?: return null

        var rest = raw.substring(verb.length).trim()
        val redial = rest.contains("back", true) || rest.contains("again", true)

        // "call mumsi on mobile" -> number type
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
        redial.let { args["redial"] = it }
        return ParsedIntent(INTENT_CALL, args)
    }

    // ------------------------------------------------------------------- sms

    private fun parseSms(context: Context, raw: String, lower: String): ParsedIntent? {
        val verb = listOf("send a message to", "send message to", "send sms to", "text", "message", "sms", "tell")
            .firstOrNull { lower.startsWith("$it ") }
            ?: return null

        val rest = raw.substring(verb.length).trim()
        if (rest.isBlank()) return ParsedIntent(INTENT_SMS, mutableMapOf("contact" to "", "message" to ""))

        // Split "tunde i'm on my way" into the longest prefix that names a person
        // and the rest as the message body.
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

        // No known person matched — treat the first token(s) as the recipient anyway
        return ParsedIntent(
            INTENT_SMS,
            mutableMapOf("contact" to words.first(), "message" to words.drop(1).joinToString(" ").trim())
        )
    }
}
