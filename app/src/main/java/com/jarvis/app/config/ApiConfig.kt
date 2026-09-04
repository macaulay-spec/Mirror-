package com.jarvis.app.config

import android.content.Context
import android.content.SharedPreferences
import com.rork.jarvisaiassistant.BuildConfig

/**
 * Central JARVIS Neural Configuration - NVIDIA-focused.
 *
 * NVIDIA Multi-Model Integration (per NVIDIA_MULTI_MODEL_PROMPT.md):
 * - Primary: GLM-5.2 (1M token context, long-horizon agentic reasoning)
 * - Fallback 1: Nemotron-3-Super (NVIDIA's flagship)
 * - Fallback 2: Mistral Nemotron (purpose-built for agentic workflows)
 * - Fallback 3: Llama-4 Maverick (broad general reasoning)
 *
 * All providers use NVIDIA's OpenAI-compatible endpoint at integrate.api.nvidia.com/v1
 * API keys are injected from local.properties / CI secrets at compile time.
 * NO hardcoded keys are embedded in the source code.
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

    // ElevenLabs British voice presets
    data class VoicePreset(
        val id: String,
        val name: String,
        val accent: String,
        val gender: String,
        val description: String
    )

    val PRESET_VOICES = listOf(
        VoicePreset("JBFqnCBsd6RMkjVDRZzb", "George",    "British", "Male",   "Warm & Refined (Classic JARVIS)"),
        VoicePreset("21m00Tcm4TlvDq8ikWAM", "Rachel",    "US",      "Female", "Calm & Natural"),
        VoicePreset("pNInz6obpgDQGcFmaJgB", "Adam",      "US",      "Male",   "Deep & Authoritative"),
        VoicePreset("N2lVS1w4EtoT3dr4eOWO", "Callum",    "British", "Male",   "Intense & Articulate"),
        VoicePreset("Xb7hH8MSUJpSbSDYk0k2", "Alice",     "British", "Female", "Confident & Professional"),
        VoicePreset("XB0fDUnXU5ikbmg1I5A3", "Charlotte", "Swedish", "Female", "Expressive & Pleasant"),
        VoicePreset("nPczCjzI2devNBz1zQrb", "Brian",     "US",      "Male",   "Deep & Resonant Narrator"),
        VoicePreset("ErXwobaYiN019PkySvjV", "Antoni",    "US",      "Male",   "Energetic & Crisp")
    )

    // Runtime state
    var userName: String = "Macaulay"
        private set
    var personalityTone: String = "jarvis_protocol"
        private set
    var isOnboardingCompleted: Boolean = false
        private set
    var voiceEngineType: String = "elevenlabs"
        private set
    var selectedVoiceId: String = "JBFqnCBsd6RMkjVDRZzb"
        private set

    // Custom key entered by the user in Settings
    var customApiKey: String? = null
        private set
    var customProvider: String? = null
        private set

    // API Keys - injected from local.properties / CI secrets at compile time
    // NO hardcoded fallback keys - if not configured, provider is unavailable
    val NVIDIA_API_KEY: String
        get() = BuildConfig.NVIDIA_API_KEY

    val ELEVENLABS_API_KEY: String
        get() = BuildConfig.ELEVENLABS_API_KEY

    /**
     * Rork toolkit key — still used by the voice stack (ElevenLabs proxy TTS,
     * cloud STT via toolkit.rork.com). Build-time injected only; blank means
     * those proxy paths are skipped and the direct/Android fallbacks are used.
     */
    val rorkApiKey: String
        get() = BuildConfig.RORK_TOOLKIT_KEY

    // Provider/key resolution
    val activeProvider: String
        get() {
            // 1. User's custom key (entered in Settings - auto-detected provider)
            customProvider?.takeIf { it.isNotBlank() }?.let { return it }
            
            // 2. NVIDIA GLM-5.2 is the primary provider
            if (NVIDIA_API_KEY.isNotBlank()) {
                return "nvidia_glm"
            }
            
            // 3. No AI available
            return ""
        }

    val activeApiKey: String
        get() {
            // 1. User's custom key
            val custom = customApiKey?.trim()
            if (!custom.isNullOrBlank()) return custom
            
            // 2. NVIDIA API key
            if (activeProvider.startsWith("nvidia_")) {
                return NVIDIA_API_KEY
            }
            
            return ""
        }

    val hasAI: Boolean get() = activeApiKey.isNotBlank()
    val hasCustomKey: Boolean get() = !customApiKey.isNullOrBlank()

    /** Human-readable label for the Diagnostics screen. */
    fun getProviderLabel(): String = when (activeProvider) {
        "nvidia_glm" -> "NVIDIA GLM-5.2 (Primary)"
        "nvidia_nemotron" -> "NVIDIA Nemotron-3-Super"
        "nvidia_mistral" -> "NVIDIA Mistral Nemotron"
        "nvidia_llama" -> "NVIDIA Llama-4 Maverick"
        else -> activeProvider.replaceFirstChar { it.uppercase() }
    }

    // NVIDIA Multi-Model Integration
    const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
    const val NVIDIA_GLM_MODEL = "zhipuai/glm-5.2"              // Primary: 1M token context
    const val NVIDIA_NEMOTRON_MODEL = "nvidia/nemotron-3-super"   // Fallback 1: NVIDIA flagship
    const val NVIDIA_MISTRAL_NEMOTRON_MODEL = "mistralai/mistral-nemotron-7b"  // Fallback 2
    const val NVIDIA_LLAMA_MODEL = "meta/llama-4-maverick"      // Fallback 3

    // Provider fallback chain (per NVIDIA_MULTI_MODEL_PROMPT.md)
    val PROVIDER_FALLBACK_CHAIN = listOf(
        "nvidia_glm",
        "nvidia_nemotron",
        "nvidia_mistral",
        "nvidia_llama"
    )

    /** Get the next provider in the fallback chain. */
    fun getNextProvider(currentProvider: String): String? {
        val currentIndex = PROVIDER_FALLBACK_CHAIN.indexOf(currentProvider)
        if (currentIndex >= 0 && currentIndex < PROVIDER_FALLBACK_CHAIN.size - 1) {
            return PROVIDER_FALLBACK_CHAIN[currentIndex + 1]
        }
        return null
    }

    /** Resolve model ID for a given provider. */
    fun resolveModel(provider: String): String = when (provider) {
        "nvidia_glm" -> NVIDIA_GLM_MODEL
        "nvidia_nemotron" -> NVIDIA_NEMOTRON_MODEL
        "nvidia_mistral" -> NVIDIA_MISTRAL_NEMOTRON_MODEL
        "nvidia_llama" -> NVIDIA_LLAMA_MODEL
        else -> NVIDIA_GLM_MODEL
    }

    // Optional connectors
    const val GOOGLE_STT_API_KEY = ""
    const val GOOGLE_TTS_API_KEY = ""
    const val HOME_ASSISTANT_URL = ""
    const val HOME_ASSISTANT_TOKEN = ""
    const val LIVEKIT_URL = ""
    const val LIVEKIT_API_KEY = ""
    const val LIVEKIT_API_SECRET = ""
    val hasCloudSTT get() = GOOGLE_STT_API_KEY.isNotBlank()
    val hasCloudTTS get() = GOOGLE_TTS_API_KEY.isNotBlank()
    val hasHomeAssistant get() = HOME_ASSISTANT_URL.isNotBlank() && HOME_ASSISTANT_TOKEN.isNotBlank()
    val hasLiveKit get() = LIVEKIT_URL.isNotBlank() && LIVEKIT_API_KEY.isNotBlank()

    // Key auto-detection for custom keys
    fun autoDetectProvider(key: String): String = when {
        key.startsWith("sk_") -> "elevenlabs"
        else -> "nvidia_glm"
    }

    // Persistence helpers
    fun saveCustomApiKey(context: Context, key: String?, provider: String? = null) {
        val cleanKey = key?.trim()?.takeIf { it.isNotBlank() }
        val detectedProvider = if (cleanKey != null) (provider ?: autoDetectProvider(cleanKey)) else null
        customApiKey = cleanKey
        customProvider = detectedProvider
        prefs(context).edit()
            .putString(PREF_KEY_CUSTOM_API_KEY, cleanKey)
            .putString(PREF_KEY_CUSTOM_PROVIDER, detectedProvider)
            .apply()
    }

    fun saveVoicePreferences(context: Context, engineType: String, voiceId: String) {
        voiceEngineType = engineType
        selectedVoiceId = voiceId
        prefs(context).edit()
            .putString(PREF_KEY_VOICE_ENGINE, engineType)
            .putString(PREF_KEY_VOICE_ID, voiceId)
            .apply()
    }

    fun savePersonalityTone(context: Context, tone: String) {
        personalityTone = tone
        prefs(context).edit().putString(PREF_KEY_AI_TONE, tone).apply()
    }

    fun saveUserName(context: Context, name: String) {
        val clean = name.trim().takeIf { it.isNotBlank() } ?: "Macaulay"
        userName = clean
        prefs(context).edit().putString(PREF_KEY_USER_NAME, clean).apply()
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean = true) {
        isOnboardingCompleted = completed
        prefs(context).edit().putBoolean(PREF_KEY_ONBOARDING_DONE, completed).apply()
    }

    /** Call once on Application.onCreate() to restore persisted settings. */
    fun load(context: Context) {
        val p = prefs(context)
        userName = p.getString(PREF_KEY_USER_NAME, "Macaulay") ?: "Macaulay"
        personalityTone = p.getString(PREF_KEY_AI_TONE, "jarvis_protocol") ?: "jarvis_protocol"
        isOnboardingCompleted = p.getBoolean(PREF_KEY_ONBOARDING_DONE, false)
        voiceEngineType = p.getString(PREF_KEY_VOICE_ENGINE, "elevenlabs") ?: "elevenlabs"
        selectedVoiceId = p.getString(PREF_KEY_VOICE_ID, "JBFqnCBsd6RMkjVDRZzb") ?: "JBFqnCBsd6RMkjVDRZzb"
        customApiKey = p.getString(PREF_KEY_CUSTOM_API_KEY, null)?.takeIf { it.isNotBlank() }
        customProvider = p.getString(PREF_KEY_CUSTOM_PROVIDER, null)?.takeIf { it.isNotBlank() }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
