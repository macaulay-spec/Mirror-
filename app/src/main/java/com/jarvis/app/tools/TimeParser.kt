package com.jarvis.app.tools

import java.util.Calendar
import java.util.Locale

/**
 * Understands everyday time phrases so the calendar and alarms work the way people speak.
 *
 * Handles: "tomorrow", "tonight", "next monday", "thursday at 3pm", "in 20 minutes",
 * "in 2 hours", "3:30 pm", "15:00", "noon", "midnight", "this evening".
 * When only a day is given, 9am is assumed.
 */
object TimeParser {

    private val DAYS = mapOf(
        "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY, "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY
    )

    private val DAY_PART_HOUR = mapOf(
        "morning" to 9, "afternoon" to 14, "evening" to 19,
        "night" to 21, "tonight" to 20, "noon" to 12, "midnight" to 0
    )

    /** Returns epoch millis, or null when nothing time-like was found. */
    fun parse(text: String, now: Calendar = Calendar.getInstance()): Long? {
        val lower = text.lowercase(Locale.US)
        val cal = now.clone() as Calendar

        // ---- relative offsets: "in 20 minutes", "in 2 hours", "in 3 days"
        Regex("in\\s+(\\d+)\\s*(minute|min|hour|hr|day|week)s?").find(lower)?.let { m ->
            val amount = m.groupValues[1].toIntOrNull() ?: return@let
            when (m.groupValues[2]) {
                "minute", "min" -> cal.add(Calendar.MINUTE, amount)
                "hour", "hr" -> cal.add(Calendar.HOUR_OF_DAY, amount)
                "day" -> cal.add(Calendar.DAY_OF_YEAR, amount)
                "week" -> cal.add(Calendar.WEEK_OF_YEAR, amount)
            }
            return cal.timeInMillis
        }

        // ---- day of week, optional "next"
        val dayMatch = DAYS.entries.firstOrNull { (name, _) ->
            lower.contains("next $name") || lower.contains("this $name") || lower.contains(name)
        }
        val nextWeek = dayMatch != null && lower.contains("next ")

        when {
            lower.contains("day after tomorrow") -> cal.add(Calendar.DAY_OF_YEAR, 2)
            lower.contains("tomorrow") -> cal.add(Calendar.DAY_OF_YEAR, 1)
            lower.contains("yesterday") -> cal.add(Calendar.DAY_OF_YEAR, -1)
            dayMatch != null -> {
                val target = dayMatch.value
                var delta = (target - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                if (delta == 0) delta = 7          // "monday" means next Monday, not now
                if (nextWeek && delta < 7) delta += 7
                cal.add(Calendar.DAY_OF_YEAR, delta)
            }
            // no day given: keep today
        }

        // ---- time of day
        val explicit = explicitTime(lower)
        if (explicit != null) {
            cal.set(Calendar.HOUR_OF_DAY, explicit.first)
            cal.set(Calendar.MINUTE, explicit.second)
        } else {
            val part = DAY_PART_HOUR.entries.firstOrNull { lower.contains(it.key) }?.value
            cal.set(Calendar.HOUR_OF_DAY, part ?: 9)
            cal.set(Calendar.MINUTE, 0)
        }
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // If the moment has already passed and the user did not say a day, move to tomorrow.
        if (!lower.contains("today") && cal.timeInMillis <= System.currentTimeMillis() &&
            !lower.contains("in ")
        ) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /** "3pm", "3:30 pm", "15:00", "at 7" */
    private fun explicitTime(lower: String): Pair<Int, Int>? {
        Regex("\\b(\\d{1,2}):(\\d{2})\\s*(am|pm)?").find(lower)?.let { m ->
            var hour = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            if (m.groupValues[3] == "pm" && hour < 12) hour += 12
            if (m.groupValues[3] == "am" && hour == 12) hour = 0
            return hour to minute
        }
        Regex("\\b(\\d{1,2})\\s*(am|pm)\\b").find(lower)?.let { m ->
            var hour = m.groupValues[1].toIntOrNull() ?: return@let
            if (m.groupValues[2] == "pm" && hour < 12) hour += 12
            if (m.groupValues[2] == "am" && hour == 12) hour = 0
            return hour to 0
        }
        Regex("\\bat\\s+(\\d{1,2})\\b").find(lower)?.let { m ->
            var hour = m.groupValues[1].toIntOrNull() ?: return@let
            if (hour <= 7) hour += 12                 // "at 7" means 7pm
            return hour to 0
        }
        return null
    }

    /** "tomorrow at 3:30 PM" for reading back before JARVIS acts. */
    fun describe(millis: Long): String =
        java.text.SimpleDateFormat("EEEE d MMMM 'at' h:mm a", Locale.getDefault())
            .format(java.util.Date(millis))
}
