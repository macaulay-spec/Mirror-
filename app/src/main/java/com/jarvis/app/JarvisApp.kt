package com.jarvis.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.jarvis.agent.tool.ToolRegistration
import com.jarvis.app.proactive.ProactiveScheduler
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.config.AssistantPrefs
import com.jarvis.app.people.PeopleGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JarvisApp : Application() {

    companion object {
        const val CHANNEL_LISTENING = "jarvis_listening"
        const val CHANNEL_BRIEFING  = "jarvis_briefing"
    }

    override fun onCreate() {
        super.onCreate()

        // Restore persisted settings (API key, username, voice prefs, etc.)
        ApiConfig.load(this)
        AssistantPrefs.load(this)

        // Register all device, accessibility and system tools into ToolRegistry
        ToolRegistration.registerAll(this)

        // Import contacts so JARVIS knows your people from the first message
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { PeopleGraph.syncFromContacts(this@JarvisApp) }
        }

        // Create notification channels (required on Android 8+)
        createNotificationChannels()

        // Re-arm the daily briefing after a reboot or app update
        if (ProactiveScheduler.isEnabled(this)) {
            ProactiveScheduler.schedule(this)
        }

        // Start the floating Orb if overlay permission is granted
        startOrbIfAllowed()
    }

    private fun startOrbIfAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            android.provider.Settings.canDrawOverlays(this)
        ) {
            runCatching {
                startService(
                    android.content.Intent(this, com.jarvis.android.overlay.JarvisFloatingOrbService::class.java)
                )
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val listening = NotificationChannel(
            CHANNEL_LISTENING,
            "JARVIS Assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while JARVIS is listening or running in the background" }

        val briefing = NotificationChannel(
            CHANNEL_BRIEFING,
            "JARVIS Briefings",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Morning briefings and proactive suggestions" }

        manager.createNotificationChannel(listening)
        manager.createNotificationChannel(briefing)
    }
}
