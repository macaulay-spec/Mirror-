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
        const val CHANNEL_BRIEFING = "jarvis_briefing"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Load persisted custom API key and provider
        ApiConfig.load(this)
        AssistantPrefs.load(this)

        // Register all core device, accessibility, and system tools
        ToolRegistration.registerAll(this)

        // Import the address book so JARVIS already knows who your people are.
        // It only ever asks about a person it genuinely cannot resolve.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { PeopleGraph.syncFromContacts(this@JarvisApp) }
        }

        createNotificationChannels()

        // Re-arm the daily briefing after a reboot or an update.
        if (ProactiveScheduler.isEnabled(this@JarvisApp)) {
            ProactiveScheduler.schedule(this@JarvisApp)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val listening = NotificationChannel(
            CHANNEL_LISTENING,
            "JARVIS assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while JARVIS is listening or running in the background" }

        val briefing = NotificationChannel(
            CHANNEL_BRIEFING,
            "JARVIS briefings",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Morning briefings and proactive suggestions" }

        manager.createNotificationChannel(listening)
        manager.createNotificationChannel(briefing)
    }
}
