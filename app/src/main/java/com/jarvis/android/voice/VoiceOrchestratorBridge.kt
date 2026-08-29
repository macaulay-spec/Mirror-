package com.jarvis.android.voice

import com.jarvis.agent.orchestrator.AssistantOrchestrator
import com.jarvis.core.model.JarvisVisualState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bridges the Voice Engine lifecycle and Speech events with the Assistant Orchestrator.
 */
class VoiceOrchestratorBridge(
    private val voiceEngine: JarvisVoiceEngine,
    private val orchestrator: AssistantOrchestrator,
    private val scope: CoroutineScope
) {
    init {
        voiceEngine.onSpeechResult = { recognizedSpeech ->
            scope.launch(Dispatchers.Main) {
                orchestrator.submitUserInput(recognizedSpeech)
            }
        }
        scope.launch(Dispatchers.Main) {
            com.jarvis.app.voice.VoiceBus.wakeWordDetected.collect {
                if (voiceEngine.engineState.value == JarvisVisualState.IDLE) {
                    orchestrator.setVisualState(JarvisVisualState.LISTENING)
                    voiceEngine.startListening()
                    JarvisSoundManager.play(SoundEvent.ACTIVATE)
                }
            }
        }
    }

    fun toggleVoiceInput() {
        if (voiceEngine.engineState.value == JarvisVisualState.LISTENING) {
            voiceEngine.stopListening()
        } else {
            voiceEngine.startListening()
        }
    }
}
