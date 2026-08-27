package com.jarvis.app.voice

import com.jarvis.core.model.JarvisVisualState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

object VoiceBus {
    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private val _command = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val command: SharedFlow<String> = _command

    private val _wakeWordDetected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val wakeWordDetected: SharedFlow<Unit> = _wakeWordDetected
    
    private val _engineState = MutableStateFlow(JarvisVisualState.IDLE)
    val engineState: StateFlow<JarvisVisualState> = _engineState

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    private val _stopped = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val stopped: SharedFlow<Unit> = _stopped

    fun onPartial(text: String) { _transcript.value = text }
    fun onCommand(text: String) { _command.tryEmit(text) }
    fun onWakeWord() { _wakeWordDetected.tryEmit(Unit) }
    fun setEngineState(state: JarvisVisualState) { _engineState.value = state }
    fun setAudioLevel(level: Float) { _audioLevel.value = level }
    fun onStopped() { _stopped.tryEmit(Unit) }
    fun clearTranscript() { _transcript.value = "" }
}
