package com.jarvis.app.notifications

/**
 * Pulls one-time codes out of notification and SMS text.
 *
 * Deliberately permissive: a missed code is far more annoying than a wrong guess, and
 * anything this returns goes straight back to the user, never to the network.
 */
object OtpExtractor {

    private val LABELLED = Regex(
        """(?i)(?:\b(?:otp|code|pin|verification|verify|one[- ]?time|login|sign ?in|2fa|auth(?:entication)?)\b[^0-9]{0,40})([0-9]{4,8})\b"""
    )
    private val STANDALONE = Regex("""(?<!\d)(\d{4,8})(?!\d)""")

    /** The most likely code in [text], or null when there isn't one. */
    fun find(text: String): String? {
        if (text.isBlank()) return null

        LABELLED.findAll(text).map { it.groupValues[1] }.firstOrNull()?.let { return it }

        // A bare number is only treated as a code when nothing else in the text looks
        // like a real amount, date or phone number.
        val candidates = STANDALONE.findAll(text)
            .map { it.groupValues[1] }
            .filter { it.length in 4..8 }
            .filter { !looksLikeYear(it) }
            .toList()

        return when {
            candidates.isEmpty() -> null
            candidates.size == 1 -> candidates.first()
            else -> candidates.maxByOrNull { it.length }
        }
    }

    /** A sentence for JARVIS to say, or null when there is no code. */
    fun spoken(text: String, source: String = ""): String? {
        val code = find(text) ?: return null
        val spaced = code.toCharArray().joinToString(" ")
        val from = if (source.isBlank()) "" else " from $source"
        return "Your code$from is $spaced. I repeat, $spaced."
    }

    private fun looksLikeYear(value: String): Boolean =
        value.length == 4 && (value.startsWith("19") || value.startsWith("20"))
}
