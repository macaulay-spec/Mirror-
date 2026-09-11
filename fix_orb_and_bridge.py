import re

with open("app/src/main/java/com/jarvis/android/voice/VoiceOrchestratorBridge.kt", "r") as f:
    content = f.read()

content = content.replace(
    """    fun toggleVoiceInput() {
        if (voiceEngine.engineState.value == JarvisVisualState.LISTENING) {
            voiceEngine.stopListening()
            orchestrator.setVisualState(JarvisVisualState.IDLE)
        } else {
            voiceEngine.continuousMode = true
            orchestrator.setVisualState(JarvisVisualState.LISTENING)
            voiceEngine.startListening()
        }
    }""",
    """    fun toggleVoiceInput() {
        if (voiceEngine.engineState.value == JarvisVisualState.LISTENING) {
            voiceEngine.stopListening()
            orchestrator.setVisualState(JarvisVisualState.IDLE)
        } else {
            VoiceBus.clearTranscript()
            voiceEngine.continuousMode = true
            orchestrator.setVisualState(JarvisVisualState.LISTENING)
            voiceEngine.startListening()
        }
    }"""
)

with open("app/src/main/java/com/jarvis/android/voice/VoiceOrchestratorBridge.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/com/jarvis/android/overlay/JarvisFloatingOrbService.kt", "r") as f:
    content = f.read()

content = content.replace(
    "val isUserSpeaking = state == JarvisVisualState.LISTENING && !userTranscript.isNullOrBlank()",
    "val isUserSpeaking = state == JarvisVisualState.LISTENING"
)

with open("app/src/main/java/com/jarvis/android/overlay/JarvisFloatingOrbService.kt", "w") as f:
    f.write(content)

print("done")
