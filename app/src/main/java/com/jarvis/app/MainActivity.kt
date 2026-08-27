package com.jarvis.app

import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.jarvis.agent.orchestrator.AssistantOrchestrator
import com.jarvis.agent.tool.ToolRegistration
import com.jarvis.android.permissions.PermissionAndSetupHelper
import com.jarvis.android.voice.JarvisSoundManager
import com.jarvis.android.voice.JarvisVoiceEngine
import com.jarvis.android.voice.SoundEvent
import com.jarvis.android.voice.VoiceOrchestratorBridge
import com.jarvis.app.assistant.GeminiService
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.memory.AppDatabase
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisTheme
import com.jarvis.feature.home.DualModeHost
import com.jarvis.feature.onboarding.OnboardingScreen
import com.jarvis.feature.setup.SetupScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private var voiceEngine: JarvisVoiceEngine? = null
    private var voiceBridge: VoiceOrchestratorBridge? = null
    private lateinit var orchestrator: AssistantOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ApiConfig.load(applicationContext)

        try {
            ToolRegistration.registerAll(applicationContext)
        } catch (_: Exception) { }

        try {
            voiceEngine = JarvisVoiceEngine(applicationContext)
        } catch (_: Exception) { }

        orchestrator = AssistantOrchestrator(
            context = applicationContext,
            geminiService = GeminiService(),
            database = AppDatabase.get(applicationContext),
            voiceEngine = voiceEngine
        )

        setContent {
            val scope = rememberCoroutineScope()
            var isOnboarding by remember { mutableStateOf(!ApiConfig.isOnboardingCompleted) }
            var showSettings by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                voiceEngine?.let { vm ->
                    voiceBridge = VoiceOrchestratorBridge(vm, orchestrator, scope)
                }
                
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
                    SetupScreen(
                        onClose = { showSettings = false },
                        onRequestPermissions = { requestCorePermissions() },
                        onOpenAccessibility = { PermissionAndSetupHelper.openAccessibilitySettings(this@MainActivity) },
                        onOpenNotificationListener = { PermissionAndSetupHelper.openNotificationListenerSettings(this@MainActivity) }
                    )
                } else {
                    DualModeHost(
                        orchestrator = orchestrator,
                        onOpenSettings = { showSettings = true },
                        onToggleVoice = {
                            JarvisSoundManager.play(SoundEvent.ACTIVATE)
                            handleVoiceToggle()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("WAKE_WORD_ACTIVATED", false)) {
            com.jarvis.app.voice.VoiceBus.onWakeWord()
            intent.removeExtra("WAKE_WORD_ACTIVATED")
        }
    }

    private fun handleVoiceToggle() {
        if (!PermissionAndSetupHelper.hasMicrophone(this)) {
            orchestrator.postSystemMessage("Microphone permission is required for voice interaction.")
            requestCorePermissions()
            return
        }

        val vm = voiceEngine ?: return
        if (vm.engineState.value == com.jarvis.core.model.JarvisVisualState.LISTENING) {
            vm.stopListening()
            orchestrator.setVisualState(JarvisVisualState.IDLE)
        } else {
            orchestrator.setVisualState(JarvisVisualState.LISTENING)
            vm.startListening()
        }
    }

    private fun requestCorePermissions() {
        val needed = PermissionAndSetupHelper.REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onDestroy() {
        try {
            voiceEngine?.destroy()
        } catch (_: Exception) { }
        super.onDestroy()
    }
}
