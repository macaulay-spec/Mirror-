package com.jarvis.app.voice

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/**
 * The JARVIS voice: how it sounds, and which ElevenLabs voice delivers it.
 *
 * You asked for a British JARVIS rather than the American default the app shipped with.
 * Two honest caveats:
 *
 *  1. Voice IDs are per-account. If a listed voice is not on your ElevenLabs account the
 *     API answers 404 and [fallbackFor] quietly moves to the next one, so the app always
 *     produces a voice rather than silence. Confirm the exact IDs from your own
 *     Voices screen and paste the one you like into Settings → Voice.
 *  2. When ElevenLabs is unreachable, the phone's own text-to-speech takes over — and it
 *     is now pinned to [LOCALE], so the fallback is British too.
 */
object JarvisVoice {

    /** The accent JARVIS speaks with when falling back to the device's own engine. */
    val LOCALE: Locale = Locale.UK

    private const val PREFS = "jarvis_voice"
    private const val KEY_WORKING_VOICE = "working_voice_id"

    data class Candidate(val id: String, val label: String, val note: String)

    /**
     * British voices worth trying, in order of how JARVIS-like they sound.
     * Daniel is the newsreader-calm one; George is warmer; Arthur is deeper and darker.
     * Rachel is American and is the last resort only — she is the one voice ElevenLabs
     * guarantees on every account.
     */
    val BRITISH_CANDIDATES = listOf(
        Candidate("onwK4e9ZLuTAKqWW03F9", "Daniel", "British, calm and precise — the closest to JARVIS"),
        Candidate("JBFqnCBsd6RMkjVDRZzb", "George", "British, warm, narration"),
        Candidate("ODq5zmih8GrVes37Dizd", "Arthur", "British, deep and authoritative"),
        Candidate("IKne3meq5aSn9XLyUdCD", "Charlie", "Australian-British, lighter and younger"),
        Candidate("21m00Tcm4TlvDq8ikWAM", "Rachel", "American — fallback present on every account")
    )

    /** Default until the user picks one. Daniel, not Rachel. */
    const val DEFAULT_VOICE_ID = "onwK4e9ZLuTAKqWW03F9"

    /**
     * Delivery settings. Lower stability gives more expression, higher similarity keeps the
     * timbre locked, and a little style stops it sounding like a sat-nav.
     */
    const val STABILITY = 0.35
    const val SIMILARITY_BOOST = 0.80
    const val STYLE = 0.30
    const val SPEAKER_BOOST = true

    /** ElevenLabs model. Turbo keeps replies snappy enough to feel like conversation. */
    const val MODEL = "eleven_turbo_v2_5"

    /** How fast JARVIS talks. 0.92 is measured without sounding slow. */
    const val RATE = 0.92f

    fun labelFor(voiceId: String): String =
        BRITISH_CANDIDATES.firstOrNull { it.id == voiceId }?.label ?: "Custom voice"

    /**
     * The next voice to try after [failedId] was rejected. Returns null when there is
     * nothing left to try.
     */
    fun fallbackFor(failedId: String): String? {
        val index = BRITISH_CANDIDATES.indexOfFirst { it.id == failedId }
        return BRITISH_CANDIDATES.getOrNull(index + 1)?.id
    }

    fun rememberWorking(context: Context, voiceId: String) {
        prefs(context).edit().putString(KEY_WORKING_VOICE, voiceId).apply()
    }

    /** The last voice that actually worked, or null when nothing has worked yet. */
    fun lastWorking(context: Context): String? =
        prefs(context).getString(KEY_WORKING_VOICE, null)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
