package com.jarvis.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.jarvis.agent.orchestrator.AssistantOrchestrator
import com.jarvis.agent.tool.ToolRegistration
import com.jarvis.android.voice.JarvisVoiceEngine
import com.jarvis.android.voice.VoiceOrchestratorBridge
import com.jarvis.app.proactive.ProactiveScheduler
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.config.AssistantPrefs
import com.jarvis.app.memory.AppDatabase
import com.jarvis.app.people.PeopleGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JarvisApp : Application() {

    companion object {
        const val CHANNEL_LISTENING = "jarvis_listening"
        const val CHANNEL_BRIEFING  = "jarvis_briefing"
    }

    // CHANGED (real-device report): orchestrator/voiceEngine/voiceBridge used to
    // be created inside MainActivity.onCreate(), which means they died with the
    // Activity -- including whenever Android reclaims a backgrounded Activity,
    // which is routine, not an edge case. WakeWordForegroundService and
    // JarvisFloatingOrbService are separate components with their own
    // lifecycles, so the wake-word listener could still fire and the Orb could
    // still be drawn on screen with NOTHING behind them to actually process a
    // command or speak a reply -- which matches "the Orb is there, but it
    // doesn't answer, and the voice doesn't even work" exactly. Owning these
    // here means they live as long as the process does, which the foreground
    // wake-word service already protects.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val voiceEngine: JarvisVoiceEngine? by lazy {
        runCatching { JarvisVoiceEngine(applicationContext) }.getOrNull()
    }

    val orchestrator: AssistantOrchestrator by lazy {
        AssistantOrchestrator(
            context = applicationContext,
            database = AppDatabase.get(applicationContext),
            voiceEngine = voiceEngine
        )
    }

    var voiceBridge: VoiceOrchestratorBridge? = null

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

        // FIX: Wire the wake-word bus to the orchestrator via VoiceOrchestratorBridge
        // This is the CRITICAL connection that was missing - voiceEngine and orchestrator
        // existed but were never properly connected, so voice input went nowhere.
        // The VoiceOrchestratorBridge now:
        // 1. Wires voiceEngine.onSpeechResult to orchestrator.submitUserInput()
        // 2. Connects VoiceBus.wakeWordDetected to startListening()
        // 3. Manages state synchronization between components
        voiceEngine?.let { engine ->
            voiceBridge = VoiceOrchestratorBridge.create(
                context = applicationContext,
                voiceEngine = engine,
                orchestrator = orchestrator,
                scope = appScope
            )
        }
    }

    private fun startOrbIfAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            android.provider.Settings.canDrawOverlays(this)
        ) {
            runCatching {
                // CHANGED (item 10): foreground service requires
                // startForegroundService on API 26+.
                androidx.core.content.ContextCompat.startForegroundService(
                    this,
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
