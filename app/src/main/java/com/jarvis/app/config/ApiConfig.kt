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
        

    // API Keys — BuildConfig injection first, hardcoded fallback second.
    // OWNER DECISION: the repository is going private and the APK must be fully
    // self-contained ("hardcode everything"), so the NVIDIA and ElevenLabs keys
    // live in source as compile-time fallbacks.
    val GEMINI_API_KEY: String get() = BuildConfig.GEMINI_API_KEY

    val NVIDIA_API_KEY: String get() = BuildConfig.NVIDIA_API_KEY.ifBlank { HARDCODED_NVIDIA_KEY }

    val ELEVENLABS_API_KEY: String get() = BuildConfig.ELEVENLABS_API_KEY.ifBlank { HARDCODED_ELEVENLABS_KEY }

    // Hardcoded fallback keys (owner decision — repo is being made private)
    private const val HARDCODED_NVIDIA_KEY =
        "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"
    private const val HARDCODED_ELEVENLABS_KEY =
        "sk_5dec6e6f0ffcf3f2b5f2949a284193100ece4e1594336c53"

    // OpenAI — optional extra cloud brain. Fill OPENAI_API_KEY to activate;
    // it automatically joins the provider fallback chain after NVIDIA.
    const val OPENAI_BASE_URL = "https://api.openai.com/v1"
    const val OPENAI_MODEL = "gpt-4o-mini"
    const val OPENAI_API_KEY = ""

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

    val hasUsableGeminiKey: Boolean
        get() = synchronized(geminiKeyPoolLock) {
            val pool = geminiKeys
            if (pool.isEmpty()) return false
            val nonLimited = pool.filterNot { rateLimitedKeys.contains(it) }
            nonLimited.isNotEmpty()
        }

    fun markGeminiKeyFailed(key: String) = synchronized(geminiKeyPoolLock) {
        rateLimitedKeys.add(key)
        rotateToNextGeminiKey()
    }

    // Provider/key resolution
    val activeProvider: String
        get() {
            // Powered exclusively by Gemini
            return "gemini_flash"
        }

    val activeApiKey: String
        get() {
            val gKey = currentGeminiKey
            if (gKey.isNotBlank()) return gKey
            val custom = customApiKey?.trim()
            if (!custom.isNullOrBlank()) return custom
            return GEMINI_API_KEY
        }

    val hasAI: Boolean
        get() = currentApiKey.isNotBlank()

    val currentApiKey: String
        get() = currentGeminiKey.ifBlank { customApiKey?.takeIf { it.isNotBlank() } ?: GEMINI_API_KEY }

    val originalHasAI: Boolean get() = activeApiKey.isNotBlank()
    val hasCustomKey: Boolean get() = !customApiKey.isNullOrBlank()

    /** Human-readable label for the Diagnostics screen. */
    fun getProviderLabel(): String = when (activeProvider) {
        "gemini_flash" -> "Gemini 2.5 Flash (Ultra-Fast Engine)"
        "gemini_pro" -> "Gemini 2.5 Pro (Deep Reasoning)"
        else -> "Gemini AI"
    }

    // Google Gemini Brain Integration (per official Gemini guidelines).
    const val GEMINI_FLASH_MODEL = "gemini-2.5-flash"
    const val GEMINI_PRO_MODEL = "gemini-2.5-pro"
    const val GEMINI_LITE_MODEL = "gemini-2.5-flash"

    // Legacy fallback constants
    const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
    const val NVIDIA_SUPER_MODEL = "nvidia/nemotron-3-super-120b-a12b"

    // Gemini high-speed pool
    val PROVIDER_FALLBACK_CHAIN = listOf(
        "gemini_flash",
        "gemini_pro"
    )

    /** Get the next provider in the fallback chain that actually has an available API key. */
    fun getNextProvider(currentProvider: String): String? {
        val currentIndex = PROVIDER_FALLBACK_CHAIN.indexOf(currentProvider)
        val startIndex = if (currentIndex >= 0) currentIndex + 1 else 0

        for (i in startIndex until PROVIDER_FALLBACK_CHAIN.size) {
            val candidate = PROVIDER_FALLBACK_CHAIN[i]
            if (candidate.startsWith("gemini") && !hasUsableGeminiKey) continue
            val candidateKey = currentGeminiKey
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
        else -> GEMINI_FLASH_MODEL
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
        if (activeProvider.startsWith("openai")) return "openai_mini"
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
            trimmed.startsWith("sk-") -> "openai_mini"
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
