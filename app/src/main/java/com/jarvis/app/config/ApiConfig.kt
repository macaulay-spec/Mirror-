package com.jarvis.app.config

import android.content.Context
import android.content.SharedPreferences
import com.jarvis.app.BuildConfig

/**
 * CENTRAL JARVIS NEURAL CONFIGURATION.
 *
 * Supports:
 *   - Zero-setup developer key pool with automatic quota rotation
 *   - User custom API keys with multi-provider auto-detection (Gemini, OpenAI, Claude, Groq, OpenRouter)
 *   - Persistent storage in SharedPreferences
 *   - Masked UI access
 */
object ApiConfig {

    private const val PREFS_NAME = "jarvis_neural_prefs"
    private const val PREF_KEY_CUSTOM_API_KEY = "custom_neural_api_key"
    private const val PREF_KEY_CUSTOM_PROVIDER = "custom_neural_provider"
    private const val PREF_KEY_VOICE_ENGINE = "voice_engine_type"
    private const val PREF_KEY_VOICE_ID = "elevenlabs_voice_id"
    private const val PREF_KEY_USER_NAME = "user_address_name"
    private const val PREF_KEY_AI_TONE = "ai_personality_tone"
    private const val PREF_KEY_ONBOARDING_DONE = "onboarding_completed"

    data class VoicePreset(
        val id: String,
        val name: String,
        val accent: String,
        val gender: String,
        val description: String
    )

    val PRESET_VOICES = listOf(
        VoicePreset("JBFqnCBsd6RMkjVDRZzb", "George", "British", "Male", "Warm & Refined (Classic JARVIS)"),
        VoicePreset("21m00Tcm4TlvDq8ikWAM", "Rachel", "US", "Female", "Calm & Natural"),
        VoicePreset("pNInz6obpgDQGcFmaJgB", "Adam", "US", "Male", "Deep & Authoritative"),
        VoicePreset("N2lVS1w4EtoT3dr4eOWO", "Callum", "British", "Male", "Intense & Articulate"),
        VoicePreset("Xb7hH8MSUJpSbSDYk0k2", "Alice", "British", "Female", "Confident & Professional"),
        VoicePreset("XB0fDUnXU5ikbmg1I5A3", "Charlotte", "Swedish/EU", "Female", "Expressive & Pleasant"),
        VoicePreset("nPczCjzI2devNBz1zQrb", "Brian", "US", "Male", "Deep & Resonant Narrator"),
        VoicePreset("ErXwobaYiN019PkySvjV", "Antoni", "US", "Male", "Energetic & Crisp")
    )

    var userName: String = "Macaulay"
        private set
    var personalityTone: String = "jarvis_protocol" // "jarvis_protocol", "conversational", "executive"
        private set
    var isOnboardingCompleted: Boolean = false
        private set
    var voiceEngineType: String = "elevenlabs" // "elevenlabs" or "native"
        private set
    var selectedVoiceId: String = "JBFqnCBsd6RMkjVDRZzb"
        private set

    fun saveVoicePreferences(context: Context, engineType: String, voiceId: String) {
        voiceEngineType = engineType
        selectedVoiceId = voiceId
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_VOICE_ENGINE, engineType)
            .putString(PREF_KEY_VOICE_ID, voiceId)
            .apply()
    }

    fun savePersonalityTone(context: Context, tone: String) {
        personalityTone = tone
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_AI_TONE, tone)
            .apply()
    }

    // Bundled Zero-Config Multi-Model AI Key Vault
    const val BUNDLED_CEREBRAS_KEY = ""
    const val BUNDLED_OPENAI_KEY = ""
    const val BUNDLED_GROK_KEY = ""
    const val BUNDLED_MISTRAL_KEY = ""
    const val BUNDLED_COHERE_KEY = ""

    // Voice & Audio Infrastructure Keys

    // LiveKit Cloud WebRTC Configuration
    const val LIVEKIT_URL = ""
    const val LIVEKIT_API_KEY = ""
    const val LIVEKIT_API_SECRET = ""
    val hasLiveKit get() = LIVEKIT_URL.isNotBlank() && LIVEKIT_API_KEY.isNotBlank()

    // JARVIS BACKEND CONNECTION
    const val JARVIS_BACKEND_URL = "https://jarvis-backend.example.com" // Update with real backend URL

    val GEMINI_API_KEY: String get() = BuildConfig.GEMINI_API_KEY
    val ELEVENLABS_API_KEY: String get() = BuildConfig.ELEVENLABS_API_KEY

    // Custom user key and provider (Deprecated logic)
    var customApiKey: String? = null
        private set
    var customProvider: String? = null
        private set

    val activeProvider: String
        get() {
            return customProvider ?: "gemini"
        }

    val activeApiKey: String
        get() = if (customApiKey?.isNotBlank() == true) customApiKey!! else GEMINI_API_KEY

    fun getProviderLabel(): String = when (activeProvider) {
        "gemini" -> "Google Gemini"
        "openai" -> "OpenAI"
        "cerebras" -> "Cerebras"
        "groq" -> "Groq"
        "anthropic" -> "Anthropic Claude"
        "mistral" -> "Mistral AI"
        "cohere" -> "Cohere"
        "openrouter" -> "OpenRouter"
        else -> activeProvider.replaceFirstChar { it.uppercase() }
    }

    const val GEMINI_MODEL = "gemini-2.5-flash"
    const val CEREBRAS_MODEL = "llama3.1-70b"
    const val OPENAI_MODEL = "gpt-4o-mini"
    const val GROK_MODEL = "grok-2"
    const val MISTRAL_MODEL = "mistral-small-latest"
    const val COHERE_MODEL = "command-r-plus"
    const val ANTHROPIC_MODEL = "claude-3-5-sonnet-latest"
    const val GROQ_MODEL = "llama-3.3-70b-versatile"
    const val OPENROUTER_MODEL = "openai/gpt-4o-mini"

    val hasCustomKey: Boolean get() = !customApiKey.isNullOrBlank()
    val hasAI: Boolean = true

    // ===== Cloud STT (optional) =====
    const val GOOGLE_STT_API_KEY = ""
    val hasCloudSTT get() = GOOGLE_STT_API_KEY.isNotBlank()

    // ===== Cloud TTS (optional) =====
    const val GOOGLE_TTS_API_KEY = ""
    val hasCloudTTS get() = GOOGLE_TTS_API_KEY.isNotBlank()

    // ===== ElevenLabs (optional) =====

    // ===== Connectors (optional, later) =====
    const val HOME_ASSISTANT_URL = ""
    const val HOME_ASSISTANT_TOKEN = ""
    val hasHomeAssistant get() = HOME_ASSISTANT_URL.isNotBlank() && HOME_ASSISTANT_TOKEN.isNotBlank()

    /**
     * Automatically detects the AI provider based on API key prefix.
     */

    /**
     * Loads persisted settings on app startup.
     */
    fun load(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        userName = prefs.getString(PREF_KEY_USER_NAME, "Macaulay") ?: "Macaulay"
        personalityTone = prefs.getString(PREF_KEY_AI_TONE, "jarvis_protocol") ?: "jarvis_protocol"
        isOnboardingCompleted = prefs.getBoolean(PREF_KEY_ONBOARDING_DONE, false)
        voiceEngineType = prefs.getString(PREF_KEY_VOICE_ENGINE, "elevenlabs") ?: "elevenlabs"
        selectedVoiceId = prefs.getString(PREF_KEY_VOICE_ID, "JBFqnCBsd6RMkjVDRZzb") ?: "JBFqnCBsd6RMkjVDRZzb"
    }

    fun saveUserName(context: Context, name: String) {
        val clean = name.trim().takeIf { it.isNotBlank() } ?: "Macaulay"
        userName = clean
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_USER_NAME, clean)
            .apply()
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean = true) {
        isOnboardingCompleted = completed
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_KEY_ONBOARDING_DONE, completed)
            .apply()
    }

}
