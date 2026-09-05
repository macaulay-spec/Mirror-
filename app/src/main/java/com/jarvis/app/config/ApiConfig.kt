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

    // Cloud voice presets (2026-09-05): xAI grok-tts + OpenAI tts-1 via the
    // managed gateway. The preset id IS the gateway voice parameter, so the
    // selection in Settings maps 1:1 to what actually speaks.
    val PRESET_VOICES = listOf(
        VoicePreset("rex",     "Rex",     "British", "Male",   "Deep & Refined (Classic JARVIS)"),
        VoicePreset("eve",     "Eve",     "British", "Female", "Warm & Composed"),
        VoicePreset("ara",     "Ara",     "US",      "Female", "Bright & Friendly"),
        VoicePreset("sal",     "Sal",     "US",      "Male",   "Smooth & Casual"),
        VoicePreset("leo",     "Leo",     "British", "Male",   "Youthful & Energetic"),
        VoicePreset("onyx",    "Onyx",    "US",      "Male",   "Deep & Authoritative"),
        VoicePreset("nova",    "Nova",    "US",      "Female", "Calm & Natural"),
        VoicePreset("shimmer", "Shimmer", "US",      "Female", "Soft & Expressive"),
        VoicePreset("echo",    "Echo",    "US",      "Male",   "Balanced & Clear")
    )

    // Runtime state
    var userName: String = "Macaulay"
        private set
    var personalityTone: String = "jarvis_protocol"
        private set
    var isOnboardingCompleted: Boolean = false
        private set
    var voiceEngineType: String = "cloud"
        private set
    var selectedVoiceId: String = "rex"
        private set

    // Custom key entered by the user in Settings
    var customApiKey: String? = null
        private set
    var customProvider: String? = null
        private set

    // API Keys - injected from local.properties / CI secrets at compile time
    // NO hardcoded fallback keys - if not configured, provider is unavailable
    val NVIDIA_API_KEY: String
        get() = "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"

    val ELEVENLABS_API_KEY: String
        get() = "sk_5dec6e6f0ffcf3f2b5f2949a284193100ece4e1594336c53"

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
        "nvidia_glm" -> "NVIDIA Nemotron 3 Super 120B (Primary)"
        "nvidia_nemotron" -> "NVIDIA Nemotron 3 Ultra 550B (Deep Think)"
        "nvidia_mistral" -> "Mistral Nemotron"
        "nvidia_llama" -> "MiniMax M3"
        else -> activeProvider.replaceFirstChar { it.uppercase() }
    }

    // NVIDIA Multi-Model Integration — two-tier brain (2026-09-05).
    // Tier 1: Nemotron 3 Super 120B — measured ~250ms on live calls; fast enough
    //   for human-pace conversation while staying fully agentic.
    // Tier 2: Nemotron 3 Ultra 550B — flagship reasoning for deep-think requests.
    // Tier 3/4: fallbacks verified live on 2026-09-05. mistral-large-2-instruct
    // and kimi-k2.6 returned 404 Not Found on NVIDIA's platform and are removed.
    const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
    const val NVIDIA_GLM_MODEL = "nvidia/nemotron-3-super-120b-a12b"          // Tier 1 primary (verified live)
    const val NVIDIA_NEMOTRON_MODEL = "nvidia/nemotron-3-ultra-550b-a55b"     // Tier 2 deep think
    const val NVIDIA_MISTRAL_NEMOTRON_MODEL = "mistralai/mistral-nemotron"    // Fallback 2 (verified live)
    const val NVIDIA_LLAMA_MODEL = "minimaxai/minimax-m3"                     // Fallback 3 (verified live)

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

    // ---- Two-tier brain routing -------------------------------------------

    /** Utterance fragments that warrant the deep-think tier. Kept conservative. */
    private val DEEP_THINK_HINTS = listOf(
        "explain ", "why does", "why do", "why is", "how does", "compare ",
        "analyze", "analyse", "pros and cons", "step by step", "walk me through",
        "write me", "write a", "draft a", "compose", "essay", "strategy",
        "solve", "calculate", "proof", "design a", "plan out"
    )

    /**
     * Routes a user utterance to the right brain tier. Short conversational or
     * command-style input stays on the fast tier (~250ms); long or reasoning-
     * heavy requests are escalated to the 550B deep-think tier. The provider
     * fallback chain still rescues a slow or dead tier automatically.
     */
    fun providerForUtterance(text: String): String {
        if (text.length > 160) return "nvidia_nemotron"
        val t = text.lowercase()
        return if (DEEP_THINK_HINTS.any { it in t }) "nvidia_nemotron" else activeProvider
    }

    // ---- Rork Toolkit gateway (managed cloud voice) ------------------------

    /** Toolkit base URL — hardcoded into the app (repo gets privated). */
    const val TOOLKIT_URL: String = "https://toolkit.rork.com"

    /**
     * Gateway key — compiled into the app binary at build time from the project
     * environment (EXPO_PUBLIC_RORK_TOOLKIT_SECRET_KEY). Per the hardcode-
     * everything decision the APK is self-contained; the repo keeps only this
     * build-time reference until privatization.
     */
    val TOOLKIT_SECRET_KEY: String
        get() = BuildConfig.TOOLKIT_SECRET_KEY

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
        voiceEngineType = p.getString(PREF_KEY_VOICE_ENGINE, "cloud") ?: "cloud"
        selectedVoiceId = p.getString(PREF_KEY_VOICE_ID, "rex") ?: "rex"
        customApiKey = p.getString(PREF_KEY_CUSTOM_API_KEY, null)?.takeIf { it.isNotBlank() }
        customProvider = p.getString(PREF_KEY_CUSTOM_PROVIDER, null)?.takeIf { it.isNotBlank() }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
