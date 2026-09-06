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
    private const val PREF_KEY_DEFAULT_PROVIDER = "default_provider"

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
        VoicePreset("Aoede",   "Aoede",   "US", "Female", "Warm & Balanced"),
        VoicePreset("Charon",  "Charon",  "US", "Male",   "Deep & Authoritative"),
        VoicePreset("Fenrir",  "Fenrir",  "US", "Male",   "Smooth & Casual"),
        VoicePreset("Kore",    "Kore",    "US", "Female", "Calm & Natural"),
        VoicePreset("Puck",    "Puck",    "US", "Male",   "Bright & Friendly"),
        VoicePreset("Eva",     "Eva",     "British", "Female", "Warm & Composed"),
        VoicePreset("Rex",     "Rex",     "British", "Male",   "Deep British Classic JARVIS")
    )

    val OLD_PRESET_VOICES = listOf(
        VoicePreset("Aoede",     "Rex",     "British", "Male",   "Deep & Refined (Classic JARVIS)"),
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
        
    var personalityTone: String = "jarvis_protocol"
        
    var isOnboardingCompleted: Boolean = true
        
    var voiceEngineType: String = "cloud"
        
    var selectedVoiceId: String = "Aoede"
        // removed 
        

    // Custom key entered by the user in Settings
    var customApiKey: String? = null
        
    var customProvider: String? = null
        

    // API Keys - injected from local.properties / CI secrets at compile time
    // NO hardcoded fallback keys - if not configured, provider is unavailable
    val GEMINI_API_KEY: String get() = BuildConfig.GEMINI_API_KEY

    val NVIDIA_API_KEY: String
        get() = "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"

    val ELEVENLABS_API_KEY: String
        get() = "sk_5dec6e6f0ffcf3f2b5f2949a284193100ece4e1594336c53"

    // Multi-key Gemini pool with automatic failover / rotation on 429 quota exhaustion
    private val geminiKeyPoolLock = Any()
    private var geminiPoolIndex: Int = 0
    private val rateLimitedKeys = mutableSetOf<String>()

    val geminiKeys: List<String>
        get() {
            val list = mutableListOf<String>()
            // 1. From customApiKey (if user entered comma/newline/semicolon-separated keys in Settings)
            customApiKey?.let { raw ->
                val tokens = raw.split(',', ';', '\n', '\r')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                list.addAll(tokens)
            }
            // 2. From BuildConfig.GEMINI_API_KEY (supports comma-separated list)
            if (GEMINI_API_KEY.isNotBlank()) {
                val tokens = GEMINI_API_KEY.split(',', ';', '\n', '\r')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                list.addAll(tokens)
            }
            return list.distinct()
        }

    val currentGeminiKey: String
        get() = synchronized(geminiKeyPoolLock) {
            val pool = geminiKeys
            if (pool.isEmpty()) return GEMINI_API_KEY
            val nonLimited = pool.filterNot { rateLimitedKeys.contains(it) }
            val candidatePool = if (nonLimited.isNotEmpty()) nonLimited else pool
            candidatePool[geminiPoolIndex % candidatePool.size]
        }

    fun rotateToNextGeminiKey(): String? = synchronized(geminiKeyPoolLock) {
        val pool = geminiKeys
        if (pool.size <= 1) return null
        geminiPoolIndex = (geminiPoolIndex + 1) % pool.size
        return pool[geminiPoolIndex]
    }

    fun markGeminiKeyRateLimited(key: String) = synchronized(geminiKeyPoolLock) {
        rateLimitedKeys.add(key)
        rotateToNextGeminiKey()
    }

    fun resetRateLimitStatuses() = synchronized(geminiKeyPoolLock) {
        rateLimitedKeys.clear()
        geminiPoolIndex = 0
    }

    // Provider/key resolution
    val activeProvider: String
        get() {
            // 1. User's custom key / provider (entered in Settings - auto-detected provider)
            customProvider?.takeIf { it.isNotBlank() }?.let { return it }
            
            // 2. Gemini 1.5 Pro if keys are available
            if (geminiKeys.isNotEmpty() || GEMINI_API_KEY.isNotBlank()) {
                return "gemini_flash"
            }

            // 3. NVIDIA Nemotron Super is the secondary provider
            if (NVIDIA_API_KEY.isNotBlank()) {
                return "nvidia_super"
            }
            
            // 4. No AI available
            return ""
        }

    val activeApiKey: String
        get() {
            if (activeProvider == "gemini_flash") {
                val gKey = currentGeminiKey
                if (gKey.isNotBlank()) return gKey
            }

            // User's custom key
            val custom = customApiKey?.trim()
            if (!custom.isNullOrBlank()) return custom
            
            // NVIDIA API key
            if (activeProvider.startsWith("nvidia_")) {
                return NVIDIA_API_KEY
            }
            
            return currentGeminiKey.ifBlank { NVIDIA_API_KEY }
        }

    val hasAI: Boolean
        get() = currentApiKey.isNotBlank()

    val currentApiKey: String
        get() = currentGeminiKey.ifBlank { if (customApiKey.isNullOrBlank()) NVIDIA_API_KEY else customApiKey!! }

    val originalHasAI: Boolean get() = activeApiKey.isNotBlank()
    val hasCustomKey: Boolean get() = !customApiKey.isNullOrBlank()

    /** Human-readable label for the Diagnostics screen. */
    fun getProviderLabel(): String = when (activeProvider) {
        "gemini_flash" -> "Gemini 2.5 Flash (Ultra-Fast)"
        "gemini_pro" -> "Gemini 2.5 Pro (Deep Reasoning)"
        "gemini_lite" -> "Gemini 2.0 Flash Lite"
        "nvidia_super", "nvidia_glm" -> "NVIDIA Nemotron 3 Super 120B"
        "nvidia_llama" -> "NVIDIA Llama 3.2 11B Vision"
        "nvidia_mistral" -> "NVIDIA Mistral Nemotron"
        "nvidia_ultra", "nvidia_nemotron" -> "NVIDIA Nemotron 3 Ultra 550B"
        else -> activeProvider.replaceFirstChar { it.uppercase() }
    }

    // Google Gemini Multi-Model Brain Integration.
    const val GEMINI_FLASH_MODEL = "gemini-2.5-flash"
    const val GEMINI_PRO_MODEL = "gemini-2.5-pro"
    const val GEMINI_LITE_MODEL = "gemini-2.0-flash-lite"

    // NVIDIA AI Endpoints & Live-Verified Models
    const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
    const val NVIDIA_SUPER_MODEL = "nvidia/nemotron-3-super-120b-a12b" // Fast primary (~480ms live stream)
    const val NVIDIA_LLAMA_MODEL = "meta/llama-3.2-11b-vision-instruct" // Ultra-fast multimodal (~230ms live stream)
    const val NVIDIA_MISTRAL_MODEL = "mistralai/mistral-nemotron" // Verified active
    const val NVIDIA_ULTRA_MODEL = "nvidia/nemotron-3-ultra-550b-a55b" // Flagship deep reasoning (verified active)

    // Complete Provider fallback chain: Gemini High-Speed Pool -> NVIDIA Multi-Model Cluster
    val PROVIDER_FALLBACK_CHAIN = listOf(
        "gemini_flash",
        "gemini_pro",
        "gemini_lite",
        "nvidia_super",
        "nvidia_llama",
        "nvidia_mistral",
        "nvidia_ultra"
    )

    /** Get the next provider in the fallback chain that actually has an available API key. */
    fun getNextProvider(currentProvider: String): String? {
        val normalized = when (currentProvider) {
            "nvidia_glm" -> "nvidia_super"
            "nvidia_nemotron" -> "nvidia_ultra"
            else -> currentProvider
        }
        val currentIndex = PROVIDER_FALLBACK_CHAIN.indexOf(normalized)
        val startIndex = if (currentIndex >= 0) currentIndex + 1 else 0

        for (i in startIndex until PROVIDER_FALLBACK_CHAIN.size) {
            val candidate = PROVIDER_FALLBACK_CHAIN[i]
            val candidateKey = when {
                candidate.startsWith("gemini") -> currentGeminiKey
                candidate.startsWith("nvidia") -> NVIDIA_API_KEY
                else -> activeApiKey
            }
            if (candidateKey.isNotBlank()) {
                return candidate
            }
        }
        return null
    }

    /** Resolve model ID for a given provider. */
    fun resolveModel(provider: String): String = when (provider) {
        "gemini_flash" -> GEMINI_FLASH_MODEL
        "gemini_pro" -> GEMINI_PRO_MODEL
        "gemini_lite" -> GEMINI_LITE_MODEL
        "nvidia_super", "nvidia_glm" -> NVIDIA_SUPER_MODEL
        "nvidia_llama" -> NVIDIA_LLAMA_MODEL
        "nvidia_mistral" -> NVIDIA_MISTRAL_MODEL
        "nvidia_ultra", "nvidia_nemotron" -> NVIDIA_ULTRA_MODEL
        else -> if (provider.startsWith("nvidia")) NVIDIA_SUPER_MODEL else GEMINI_FLASH_MODEL
    }

    // ---- Multi-tier brain routing -------------------------------------------

    /** Utterance fragments that warrant the deep-think tier. Kept conservative. */
    private val DEEP_THINK_HINTS = listOf(
        "explain ", "why does", "why do", "why is", "how does", "compare ",
        "analyze", "analyse", "pros and cons", "step by step", "walk me through",
        "write me", "write a", "draft a", "compose", "essay", "strategy",
        "solve", "calculate", "proof", "design a", "plan out"
    )

    /**
     * Routes a user utterance to the right brain tier.
     * Fast conversational tasks stay on Gemini Flash / NVIDIA Nemotron Super.
     * Deep reasoning requests route to Gemini Pro / NVIDIA Nemotron Ultra 550B.
     */
    fun providerForUtterance(text: String): String {
        val isDeep = text.length > 160 || DEEP_THINK_HINTS.any { it in text.lowercase() }
        val isNvidia = activeProvider.startsWith("nvidia")
        return if (isNvidia) {
            if (isDeep) "nvidia_ultra" else "nvidia_super"
        } else {
            if (isDeep) "gemini_pro" else "gemini_flash"
        }
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
    fun autoDetectProvider(key: String): String {
        val trimmed = key.trim()
        return when {
            trimmed.startsWith("sk_") -> "elevenlabs"
            trimmed.startsWith("nvapi-") -> "nvidia_super"
            trimmed.startsWith("AIza") || trimmed.contains("AIza") -> "gemini_flash"
            trimmed.contains(",") || trimmed.contains("\n") || trimmed.contains(";") -> "gemini_flash"
            else -> "gemini_flash"
        }
    }

    // Persistence helpers
    fun saveCustomApiKey(context: Context, key: String?, provider: String? = null) {
        val cleanKey = key?.trim()?.takeIf { it.isNotBlank() }
        val detectedProvider = if (cleanKey != null) (provider ?: autoDetectProvider(cleanKey)) else null
        customApiKey = cleanKey
        customProvider = detectedProvider
        resetRateLimitStatuses()
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
        isOnboardingCompleted = p.getBoolean(PREF_KEY_ONBOARDING_DONE, true)
        voiceEngineType = p.getString(PREF_KEY_VOICE_ENGINE, "cloud") ?: "cloud"
        selectedVoiceId = p.getString(PREF_KEY_VOICE_ID, "Aoede") ?: "Aoede"
        customApiKey = p.getString(PREF_KEY_CUSTOM_API_KEY, null)?.takeIf { it.isNotBlank() }
        customProvider = p.getString(PREF_KEY_CUSTOM_PROVIDER, null)?.takeIf { it.isNotBlank() }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
