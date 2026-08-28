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

    @Volatile
    var alwaysListening: Boolean = true
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        alwaysListening = prefs.getBoolean(KEY_ALWAYS_LISTENING, true)
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
