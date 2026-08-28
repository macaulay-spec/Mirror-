package com.jarvis.app.config

import android.content.Context

/**
 * Small runtime switches that must survive a restart.
 *
 * [alwaysListening] controls whether the always-on "Hey JARVIS" foreground service is
 * running. It defaults to ON because the whole point of the assistant is being reachable
 * without unlocking and hunting for the app — and the service shows a persistent
 * notification with a Stop button, so it is always one tap from being turned off.
 */
object AssistantPrefs {

    private const val PREFS = "jarvis_assistant_prefs"
    private const val KEY_ALWAYS_LISTENING = "always_listening"
    private const val KEY_WAKE_WORD = "wake_word"
    private const val KEY_ONBOARDING_V2 = "onboarding_v2_done"
    private const val KEY_READ_OTP = "read_otp_aloud"
    private const val KEY_HIDE_SENSITIVE = "hide_sensitive_content"

    @Volatile
    var alwaysListening: Boolean = true
        private set

    /**
     * Reading one-time codes aloud is the single most useful thing a hands-free
     * assistant does, so it is ON by default. JARVIS only hides notification text when
     * [hideSensitiveContent] is explicitly switched on.
     */
    @Volatile
    var readOtpAloud: Boolean = true
        private set

    @Volatile
    var hideSensitiveContent: Boolean = false
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        alwaysListening = prefs.getBoolean(KEY_ALWAYS_LISTENING, true)
        readOtpAloud = prefs.getBoolean(KEY_READ_OTP, true)
        hideSensitiveContent = prefs.getBoolean(KEY_HIDE_SENSITIVE, false)
    }

    fun setReadOtpAloud(context: Context, enabled: Boolean) {
        readOtpAloud = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_READ_OTP, enabled)
            .apply()
    }

    fun setHideSensitiveContent(context: Context, enabled: Boolean) {
        hideSensitiveContent = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIDE_SENSITIVE, enabled)
            .apply()
    }

    fun setAlwaysListening(context: Context, enabled: Boolean) {
        alwaysListening = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALWAYS_LISTENING, enabled)
            .apply()
    }

    var wakeWord: String = "jarvis"
}
