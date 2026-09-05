package com.jarvis.app

import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.jarvis.agent.orchestrator.AssistantOrchestrator
import com.jarvis.agent.tool.ToolRegistration
import com.jarvis.android.permissions.PermissionAndSetupHelper
import com.jarvis.android.voice.JarvisVoiceEngine
import com.jarvis.app.config.ApiConfig
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisTheme
import com.jarvis.feature.home.DualModeHost
import com.jarvis.feature.onboarding.OnboardingScreen
import com.jarvis.feature.settings.SettingsHubScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    // CHANGED (real-device report — "the Orb shows but nothing happens in the
    // background"): orchestrator/voiceEngine used to be created fresh in this
    // Activity's onCreate(), so they were destroyed along with it -- which
    // Android does routinely for a backgrounded Activity, not just on rare
    // low-memory kills. WakeWordForegroundService and JarvisFloatingOrbService
    // are separate components that keep running on their own, so the Orb could
    // stay visible and the wake word could still fire with nothing left alive
    // to actually process a command or speak a reply. Both now live on the
    // Application (JarvisApp), which survives independent of this Activity;
    // this Activity just reads them. Everything below that referenced
    // `orchestrator` / `voiceEngine` by name needs no other changes.
    private val app: JarvisApp get() = application as JarvisApp
    private val orchestrator: AssistantOrchestrator get() = app.orchestrator
    private val voiceEngine: JarvisVoiceEngine? get() = app.voiceEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // CHANGED: ApiConfig.load() and ToolRegistration.registerAll() moved to
        // JarvisApp.onCreate(). Running them here too re-registered every tool a
        // second time each time this Activity was created (the "overwriting
        // existing tool" warnings in logcat), and re-read the config on every
        // screen rotation. Application startup owns one-time init; Activities
        // must not repeat it.

        // The always-on "Hey JARVIS" service existed but nothing ever started it,
        // so hands-free never worked. Start it here when the mic is available.
        startWakeWordServiceIfAllowed()

        setContent {
            // rememberSaveable: rotation / process death no longer dumps the
            // user from Settings or a sub-screen back to the home deck.
            var isOnboarding by rememberSaveable { mutableStateOf(!ApiConfig.isOnboardingCompleted) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var currentDest by rememberSaveable { mutableStateOf("home") }

            // System Back leaves a sub-screen instead of finishing the Activity.
            BackHandler(enabled = showSettings || currentDest != "home") {
                showSettings = false
                currentDest = "home"
            }

            LaunchedEffect(Unit) {
                if (intent?.getBooleanExtra("WAKE_WORD_ACTIVATED", false) == true) {
                    handleVoiceToggle()
                    intent?.removeExtra("WAKE_WORD_ACTIVATED")
                }
            }

            JarvisTheme {
                if (isOnboarding) {
                    OnboardingScreen(
                        onFinishOnboarding = {
                            isOnboarding = false
                        },
                        onRequestMicrophone = { requestCorePermissions() },
                        onOpenAccessibility = { PermissionAndSetupHelper.openAccessibilitySettings(this@MainActivity) },
                        onOpenNotificationListener = { PermissionAndSetupHelper.openNotificationListenerSettings(this@MainActivity) }
                    )
                } else if (showSettings) {
                    SettingsHubScreen(
                        onClose = { showSettings = false },
                        onRequestPermissions = { requestCorePermissions() },
                        onOpenAccessibility = { PermissionAndSetupHelper.openAccessibilitySettings(this@MainActivity) },
                        onOpenNotificationListener = { PermissionAndSetupHelper.openNotificationListenerSettings(this@MainActivity) }
                    )
                } else {
                    when (currentDest) {
                        "history" -> com.jarvis.feature.history.ChatHistoryScreen(
                            orchestrator = orchestrator,
                            onBack = { currentDest = "home" }
                        )
                        "device" -> com.jarvis.feature.control.DeviceControlScreen(
                            onBack = { currentDest = "home" }
                        )
                        "memory" -> com.jarvis.feature.memory.MemoryPeopleScreen(
                            onBack = { currentDest = "home" }
                        )
                        "voice" -> com.jarvis.feature.voice.VoiceRoomScreen(
                            onDone = { currentDest = "home" }
                        )
                        else -> DualModeHost(
                            orchestrator = orchestrator,
                            onOpenSettings = { showSettings = true },
                            onToggleVoice = {
                                handleVoiceToggle()
                            },
                            onNavigate = { currentDest = it }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("WAKE_WORD_ACTIVATED", false)) {
            // FIX: Delegate to voiceBridge which properly handles wake word detection
            // and connects it to the voice pipeline
            // If the bridge failed to create, fall back to the direct engine
            // path — a warm-start wake-word tap must never be silently dropped.
            app.voiceBridge?.onWakeWordDetected() ?: handleVoiceToggle()
            intent.removeExtra("WAKE_WORD_ACTIVATED")
        }
    }

    private fun handleVoiceToggle() {
        if (!PermissionAndSetupHelper.hasMicrophone(this)) {
            orchestrator.postSystemMessage("Microphone permission is required for voice interaction.")
            requestCorePermissions()
            return
        }

        // FIX: Use voiceBridge if available, otherwise fall back to direct voiceEngine
        // The VoiceOrchestratorBridge properly manages state and connects
        // voice input to the orchestrator
        val bridge = app.voiceBridge
        if (bridge != null) {
            bridge.toggleVoiceInput()
        } else {
            // Fallback to direct voice engine control
            val vm = voiceEngine ?: return
            if (vm.engineState.value == com.jarvis.core.model.JarvisVisualState.LISTENING) {
                vm.stopListening()
                orchestrator.setVisualState(JarvisVisualState.IDLE)
            } else {
                orchestrator.setVisualState(JarvisVisualState.LISTENING)
                vm.startListening()
            }
        }
    }

    private fun startWakeWordServiceIfAllowed() {
        if (!com.jarvis.app.config.AssistantPrefs.alwaysListening) return
        if (!PermissionAndSetupHelper.hasMicrophone(this)) return
        if (com.jarvis.app.voice.WakeWordForegroundService.running) return
        runCatching {
            val intent = android.content.Intent(this, com.jarvis.app.voice.WakeWordForegroundService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun stopWakeWordService() {
        runCatching {
            val intent = android.content.Intent(this, com.jarvis.app.voice.WakeWordForegroundService::class.java)
            intent.action = "stop"
            startService(intent)
        }
    }

    private fun requestCorePermissions() {
        val needed = PermissionAndSetupHelper.REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            // Everything is granted — the always-on listener can safely start.
            startWakeWordServiceIfAllowed()
            CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { com.jarvis.app.people.PeopleGraph.syncFromContacts(applicationContext) }
            }
        }
    }

    // CHANGED: voiceEngine is no longer this Activity's to destroy -- it
    // belongs to JarvisApp now and should keep running after this Activity
    // goes away (that's the entire point of the fix above). onDestroy() used
    // to tear it down here, which would have undone the fix by destroying the
    // engine the moment you left the screen, wake word or not.
}
