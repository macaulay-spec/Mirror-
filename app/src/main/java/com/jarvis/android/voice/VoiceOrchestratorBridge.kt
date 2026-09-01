package com.jarvis.android.voice

import android.content.Context
import com.jarvis.agent.orchestrator.AssistantOrchestrator
import com.jarvis.app.voice.JarvisSoundManager
import com.jarvis.app.voice.SoundEvent
import com.jarvis.core.model.JarvisVisualState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bridges the Voice Engine lifecycle and Speech events with the Assistant Orchestrator.
 *
 * This is the CRITICAL connection component that wires:
 *   WakeWordForegroundService -> VoiceEngine -> Orchestrator
 *
 * Without this bridge, voice input is completely disconnected from the AI system.
 *
 * FIXES:
 * - Properly wires onSpeechResult callback to orchestrator.submitUserInput()
 * - Connects VoiceBus.wakeWordDetected to startListening()
 * - Adds error handling for voice pipeline
 * - Manages state synchronization between voice engine and orchestrator
 */
class VoiceOrchestratorBridge(
    private val voiceEngine: JarvisVoiceEngine,
    private val orchestrator: AssistantOrchestrator,
    private val scope: CoroutineScope
) {
    init {
        // FIX: Wire the speech result callback to the orchestrator
        // This was the critical missing connection - voice engine had onSpeechResult
        // but it was never assigned, so recognized speech went nowhere
        voiceEngine.onSpeechResult = { recognizedSpeech ->
            scope.launch(Dispatchers.Main) {
                try {
                    orchestrator.setVisualState(JarvisVisualState.THINKING)
                    orchestrator.submitUserInput(recognizedSpeech)
                } catch (e: Exception) {
                    orchestrator.setVisualState(JarvisVisualState.ERROR)
                    orchestrator.postSystemMessage("Voice recognition error: ${e.localizedMessage}")
                }
            }
        }

        // FIX: Connect wake word detection to the voice pipeline
        // VoiceBus.wakeWordDetected was firing into void - now it triggers listening
        scope.launch(Dispatchers.Main) {
            VoiceBus.wakeWordDetected.collect {
                onWakeWordDetected()
            }
        }

        // FIX: Connect audio level for Orb visualization
        scope.launch(Dispatchers.Main) {
            voiceEngine.audioRms.collect { level ->
                VoiceBus.setAudioLevel(level)
            }
        }
    }

    /**
     * Handles wake word detection from the foreground service or UI.
     * This is the entry point for hands-free voice interaction.
     */
    fun onWakeWordDetected() {
        // Stop any current audio playback
        voiceEngine.stopSpeaking()
        voiceEngine.stopListening()
        
        // Clear previous transcript
        VoiceBus.clearTranscript()
        
        // Only start listening if we're in a valid state
        if (voiceEngine.engineState.value == JarvisVisualState.IDLE) {
            JarvisSoundManager.play(SoundEvent.ACTIVATE)
            orchestrator.setVisualState(JarvisVisualState.LISTENING)
            voiceEngine.startListening()
        }
    }

    /**
     * Toggles voice input on/off.
     * Called by UI components (mic button, Orb tap).
     */
    fun toggleVoiceInput() {
        if (voiceEngine.engineState.value == JarvisVisualState.LISTENING) {
            voiceEngine.stopListening()
            orchestrator.setVisualState(JarvisVisualState.IDLE)
        } else {
            orchestrator.setVisualState(JarvisVisualState.LISTENING)
            voiceEngine.startListening()
        }
    }

    /**
     * Starts listening for voice input.
     */
    fun startListening() {
        voiceEngine.startListening()
        orchestrator.setVisualState(JarvisVisualState.LISTENING)
    }

    /**
     * Stops listening for voice input.
     */
    fun stopListening() {
        voiceEngine.stopListening()
        orchestrator.setVisualState(JarvisVisualState.IDLE)
    }

    /**
     * Speaks text using the voice engine.
     * Called by orchestrator when it needs to speak a response.
     */
    fun speak(text: String) {
        voiceEngine.speak(text)
    }

    /**
     * Emergency stop - stops all voice operations immediately.
     */
    fun emergencyStop() {
        voiceEngine.stopListening()
        voiceEngine.stopSpeaking()
        orchestrator.setVisualState(JarvisVisualState.IDLE)
        VoiceBus.clearTranscript()
    }

    /**
     * Returns the current engine state for UI binding.
     */
    fun getEngineState() = voiceEngine.engineState

    companion object {
        /**
         * Factory method for creating the bridge with proper lifecycle management.
         */
        fun create(
            context: Context,
            voiceEngine: JarvisVoiceEngine,
            orchestrator: AssistantOrchestrator,
            scope: CoroutineScope
        ): VoiceOrchestratorBridge {
            return VoiceOrchestratorBridge(voiceEngine, orchestrator, scope)
        }
    }
}
