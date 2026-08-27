package com.jarvis.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.agent.orchestrator.AssistantOrchestrator
import com.jarvis.app.assistant.GeminiService
import com.jarvis.app.memory.AppDatabase
import com.jarvis.app.memory.MemoryEntity
import com.jarvis.app.memory.MemoryRepository
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.app.voice.SpeechOutput
import com.jarvis.app.voice.VoiceBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatLine(val role: String, val text: String)

class JarvisViewModel(app: Application) : AndroidViewModel(app) {

    val orchestrator = AssistantOrchestrator(
        context = app,
        geminiService = GeminiService(),
        database = AppDatabase.get(app),
        voiceEngine = null
    )
    private val memoryRepo = MemoryRepository(AppDatabase.get(app))
    private val speech = SpeechOutput(app)

    val state: StateFlow<JarvisVisualState> = orchestrator.visualState

    private val _lines = MutableStateFlow<List<ChatLine>>(emptyList())
    val lines = _lines.asStateFlow()

    private val _input = MutableStateFlow("")
    val input = _input.asStateFlow()

    private val _memories = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val memories = _memories.asStateFlow()

    init {
        viewModelScope.launch {
            VoiceBus.command.collect { cmd -> handle(cmd) }
        }
        viewModelScope.launch {
            memoryRepo.all().collect { _memories.value = it }
        }
        viewModelScope.launch {
            orchestrator.messages.collect { msgs ->
                _lines.value = msgs.map { ChatLine(if (it.role == com.jarvis.core.model.MessageRole.USER) "user" else "jarvis", it.text) }
            }
        }
    }

    fun setInput(v: String) { _input.value = v }

    fun send() {
        val t = _input.value.trim()
        if (t.isEmpty()) return
        _input.value = ""
        viewModelScope.launch { handle(t) }
    }

    fun toggleListening() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val svc = android.content.Intent(ctx, com.jarvis.app.voice.WakeWordForegroundService::class.java)
            if (com.jarvis.app.voice.WakeWordForegroundService.running) {
                svc.action = "stop"
                ctx.startForegroundService(svc)
                orchestrator.setVisualState(JarvisVisualState.IDLE)
            } else {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        ctx, android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    ctx.startForegroundService(svc)
                    orchestrator.setVisualState(JarvisVisualState.LISTENING)
                }
            }
        }
    }

    private suspend fun handle(text: String) {
        orchestrator.submitUserInput(text)
    }

    fun speak(text: String) { speech.speak(text) }
    fun stopSpeaking() { speech.stop() }

    fun wipeMemory() {
        viewModelScope.launch { orchestrator.clearHistory() }
    }

    private val _runtimeRequestTrigger = MutableStateFlow(0L)
    val runtimeRequestTrigger = _runtimeRequestTrigger.asStateFlow()

    private val _captureIntent = MutableStateFlow(0L)
    val captureIntent = _captureIntent.asStateFlow()

    private val _pickIntent = MutableStateFlow(0L)
    val pickIntent = _pickIntent.asStateFlow()

    fun requestAllRuntime() {
        _runtimeRequestTrigger.value = System.currentTimeMillis()
    }

    fun launchCamera() { _captureIntent.value = System.currentTimeMillis() }
    fun launchFilePicker() { _pickIntent.value = System.currentTimeMillis() }

    fun onCameraImage(uri: android.net.Uri?) {
        viewModelScope.launch {
            if (uri != null) {
                val bmp = com.jarvis.app.tools.ImageUtils.fromUri(getApplication(), uri)
                val analysis = com.jarvis.app.tools.ImageAnalyzer.analyze(bmp ?: return@launch)
                val desc = com.jarvis.app.tools.ImageAnalyzer.describe(analysis)
                orchestrator.postSystemMessage(desc)
                speech.speak(desc)
            }
        }
    }

    override fun onCleared() {
        speech.shutdown()
        super.onCleared()
    }
}
