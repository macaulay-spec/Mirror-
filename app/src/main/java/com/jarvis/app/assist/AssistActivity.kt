package com.jarvis.app.assist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Entry point for the system assist gesture.
 *
 * When JARVIS is the default assistant, long-pressing home (or the corner swipe) fires
 * `ACTION_ASSIST`, which lands here. We hand straight off to MainActivity already
 * listening, and pass along whatever the user was looking at so JARVIS can answer
 * "what am I looking at?" or "summarize this page".
 */
class AssistActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sourceApp = intent?.getStringExtra(Intent.EXTRA_ASSIST_PACKAGE)
            ?: referrer?.host

        val forward = Intent(this, com.jarvis.app.MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra("WAKE_WORD_ACTIVATED", true)
            putExtra("ASSIST_LAUNCHED", true)
            if (!sourceApp.isNullOrBlank()) putExtra("ASSIST_PACKAGE", sourceApp)
        }
        startActivity(forward)
        finish()
    }
}
