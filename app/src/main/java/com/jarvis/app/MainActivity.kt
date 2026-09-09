package com.jarvis.app

import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.jarvis.feature.awareness.ScreenAwarenessScreen
import com.jarvis.feature.chat.ChatScreen
import com.jarvis.feature.home.HomeScreen
import com.jarvis.feature.memory.MemoryPeopleScreen
import com.jarvis.feature.onboarding.OnboardingScreen
import com.jarvis.feature.search.WebSearchLiveScreen
import com.jarvis.feature.settings.SettingsHubScreen
import com.jarvis.feature.settings.SettingsScreen
import com.jarvis.feature.splash.SplashScreen
import com.jarvis.feature.tasks.TaskExecutionScreen
import com.jarvis.feature.voice.VoiceActiveScreen
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    // FIX (audit P1-B): this callback used to be an empty `{ }`, so the result of
    // every permission dialog was thrown away. On a first install the microphone is
    // granted HERE -- and nothing happened. The always-on wake-word service and the
    // contacts import only ran from the "everything was already granted" branch of
    // requestCorePermissions(), so hands-free listening never started until the user
    // killed and relaunched the app, and JARVIS knew no contacts for the whole first
    // session. That is the "the Orb is there but nothing happens" report.
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshDependentCapabilities() }

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
            var showSplash by rememberSaveable { mutableStateOf(true) }
            var isOnboarding by rememberSaveable { mutableStateOf(!ApiConfig.isOnboardingCompleted) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var showVoiceActive by rememberSaveable { mutableStateOf(false) }
            var currentDest by rememberSaveable { mutableStateOf("home") }
            val scope = rememberCoroutineScope()

            // REAL STATE WIRING: when the agent starts executing a multi-step
            // task, the dedicated Task Execution screen surfaces automatically
            // (system Back dismisses to Home). The timeline is driven by actual
            // AgentExecutor step updates — never a mock sequence.
            val isTaskExecuting by orchestrator.isTaskExecuting.collectAsState()
            val taskDescription by orchestrator.currentTaskDescription.collectAsState()
            LaunchedEffect(isTaskExecuting) {
                if (isTaskExecuting && !showSplash && !isOnboarding) currentDest = "tasks"
            }

            // System Back leaves a sub-screen instead of finishing the Activity.
            BackHandler(enabled = showVoiceActive || showSettings || currentDest != "home") {
                if (showVoiceActive) {
                    showVoiceActive = false
                    orchestrator.emergencyStop()
                } else if (showSettings) {
                    showSettings = false
                } else {
                    currentDest = "home"
                }
            }

            LaunchedEffect(Unit) {
                if (intent?.getBooleanExtra("WAKE_WORD_ACTIVATED", false) == true) {
                    showVoiceActive = true
                    handleVoiceToggle()
                    intent?.removeExtra("WAKE_WORD_ACTIVATED")
                }
            }

            JarvisTheme {
                if (showSplash) {
                    SplashScreen(onTimeout = { showSplash = false })
                } else if (isOnboarding) {
                    OnboardingScreen(
                        onFinishOnboarding = {
                            isOnboarding = false
                        },
                        onRequestMicrophone = { requestCorePermissions() },
                        onOpenAccessibility = { PermissionAndSetupHelper.openAccessibilitySettings(this@MainActivity) },
                        onOpenNotificationListener = { PermissionAndSetupHelper.openNotificationListenerSettings(this@MainActivity) }
                    )
                } else if (showVoiceActive) {
                    VoiceActiveScreen(
                        orchestrator = orchestrator,
                        onCancel = {
                            showVoiceActive = false
                            orchestrator.emergencyStop()
                        }
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
                        "chat" -> ChatScreen(
                            orchestrator = orchestrator,
                            onBack = { currentDest = "home" },
                            onNavigate = { currentDest = it },
                            onToggleVoice = {
                                showVoiceActive = true
                                handleVoiceToggle()
                            }
                        )
                        "memory" -> MemoryPeopleScreen(
                            onBack = { currentDest = "home" },
                            onNavigate = { currentDest = it }
                        )
                        "settings" -> SettingsScreen(
                            onBack = { currentDest = "home" },
                            onNavigate = { currentDest = it },
                            onVoiceSettings = { currentDest = "voice" },
                            onPermissions = { requestCorePermissions() },
                            onAbout = { showSettings = true }
                        )
                        "history" -> com.jarvis.feature.history.ChatHistoryScreen(
                            orchestrator = orchestrator,
                            onBack = { currentDest = "home" }
                        )
                        "device" -> com.jarvis.feature.control.DeviceControlScreen(
                            onBack = { currentDest = "home" }
                        )
                        "voice" -> com.jarvis.feature.voice.VoiceRoomScreen(
                            onDone = { currentDest = "home" }
                        )
                        "tasks" -> TaskExecutionScreen(
                            orchestrator = orchestrator,
                            taskDescription = taskDescription ?: "Working on it",
                            onDismiss = { currentDest = "home" }
                        )
                        "awareness" -> ScreenAwarenessScreen(
                            onDismiss = { currentDest = "home" }
                        )
                        "search" -> WebSearchLiveScreen(
                            onDismiss = { currentDest = "home" }
                        )
                        else -> HomeScreen(
                            orchestrator = orchestrator,
                            onNavigate = { currentDest = it },
                            onOpenSettings = { currentDest = "settings" },
                            onOpenDrawer = { showSettings = true },
                            onToggleVoice = {
                                showVoiceActive = true
                                handleVoiceToggle()
                            },
                            onQuickAction = { action ->
                                when (action) {
                                    "open_app" -> {
                                        currentDest = "chat"
                                        scope.launch {
                                            orchestrator.submitUserInput("Open WhatsApp")
                                        }
                                    }
                                    "volume" -> {
                                        currentDest = "chat"
                                        scope.launch {
                                            orchestrator.submitUserInput("Make the volume a bit louder")
                                        }
                                    }
                                    "flashlight" -> {
                                        currentDest = "chat"
                                        scope.launch {
                                            orchestrator.submitUserInput("Turn on the flashlight")
                                        }
                                    }
                                    "screenshot" -> {
                                        currentDest = "chat"
                                        scope.launch {
                                            orchestrator.submitUserInput("Analyze what is currently on my screen")
                                        }
                                    }
                                    "recent_task" -> {
                                        currentDest = "chat"
                                        scope.launch {
                                            orchestrator.submitUserInput("Check my recent notifications and messages")
                                        }
                                    }
                                }
                            }
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
            // The launcher callback above now starts whatever became usable; there is
            // no "all granted" precondition any more.
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            refreshDependentCapabilities()
        }
    }

    /**
     * Starts each capability on the permission IT actually needs.
     *
     * FIX (audit P1-C): this used to be gated behind `hasAllCorePermissions()`, so
     * declining the camera -- or SMS, calendar or location -- also blocked the
     * always-on wake-word service and the contacts import, even though RECORD_AUDIO
     * and READ_CONTACTS had been granted. Voice is the core feature; it must not be
     * hostage to an unrelated permission.
     *
     * Safe to call repeatedly: startWakeWordServiceIfAllowed() re-checks the
     * always-listening preference, the microphone permission and whether the service
     * is already running, and the contacts sync is idempotent (it upserts by
     * lookup key and preserves learned nicknames).
     */
    private fun refreshDependentCapabilities() {
        startWakeWordServiceIfAllowed()

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
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
