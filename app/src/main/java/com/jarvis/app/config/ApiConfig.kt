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
    // NOTE: the stored string is a legacy SharedPreferences key from when this slot
    // held an ElevenLabs voice id. The VALUE is deliberately not renamed -- changing
    // it would orphan the voice choice of every existing install on upgrade.
    private const val PREF_KEY_VOICE_ID = "elevenlabs_voice_id"
    private const val PREF_KEY_USER_NAME = "user_address_name"
    private const val PREF_KEY_AI_TONE = "ai_personality_tone"
    private const val PREF_KEY_ONBOARDING_DONE = "onboarding_completed"

    /**
     * The voices JARVIS can speak with.
     *
     * Each preset id is a real Gemini TTS prebuilt voice name, so the selection in
     * Settings maps 1:1 to what actually speaks (see GeminiVoicePlayer.resolveVoice).
     */
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

    // REMOVED (owner decision, 2026-09-07): OLD_PRESET_VOICES listed ElevenLabs and
    // OpenAI voice ids that no integration in this app can play. It had zero
    // references. PRESET_VOICES above is the single source of truth.

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

    /**
     * FIX (audit P0-A): these two getters previously returned hardcoded credential
     * literals, directly contradicting the comment above them and shipping live API
     * keys inside every APK and in the public git history. They now come from
     * BuildConfig, which is populated from CI secrets or local.properties (see
     * app/build.gradle.kts). A blank value means "this provider is unavailable",
     * which ApiConfig.hasAI and the Settings screen already handle.
     */
    val NVIDIA_API_KEY: String
        get() = BuildConfig.NVIDIA_API_KEY

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

    /**
     * True for providers whose keys can actually serve chat completions.
     *
     * Guards provider selection so a key pasted in Settings can only become the
     * reasoning provider when it belongs to an endpoint that speaks the OpenAI chat
     * protocol. Without this, an unrecognised key was promoted to activeProvider and
     * sent somewhere it could only produce a 401 before the fallback chain started.
     */
    private fun isLlmProvider(provider: String?): Boolean =
        !provider.isNullOrBlank() &&
            (provider.startsWith("gemini") || provider.startsWith("nvidia"))

    // Provider/key resolution
    val activeProvider: String
        get() {
            // 1. User's custom key / provider, when it can serve chat completions.
            customProvider?.takeIf { isLlmProvider(it) }?.let { return it }
            
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

            // User's custom key, only when it belongs to an LLM-capable provider.
            val custom = customApiKey?.trim()
            if (!custom.isNullOrBlank() && isLlmProvider(customProvider)) return custom
            
            // NVIDIA API key
            if (activeProvider.startsWith("nvidia_")) {
                return NVIDIA_API_KEY
            }
            
            return currentGeminiKey.ifBlank { NVIDIA_API_KEY }
        }

    val hasAI: Boolean
        get() = currentApiKey.isNotBlank()

    val currentApiKey: String
        get() = currentGeminiKey.ifBlank {
            customApiKey?.takeIf { isLlmProvider(customProvider) } ?: NVIDIA_API_KEY
        }

    val originalHasAI: Boolean get() = activeApiKey.isNotBlank()
    val hasCustomKey: Boolean get() = !customApiKey.isNullOrBlank()

    /** Human-readable label for the Diagnostics screen. */
    fun getProviderLabel(): String = when (activeProvider) {
        "gemini_flash" -> "Gemini 2.5 Flash (Ultra-Fast)"
        "gemini_pro" -> "Gemini 2.5 Pro (Deep Reasoning)"
        "gemini_lite" -> "Gemini 2.0 Flash Lite"
        "nvidia_super", "nvidia_glm" -> "NVIDIA Nemotron 3 Super 120B"
        "nvidia_nano" -> "NVIDIA Nemotron 3 Nano 30B"
        "nvidia_ultra", "nvidia_nemotron" -> "NVIDIA Nemotron 3 Ultra 550B"
        "nvidia_llama", "nvidia_mistral" -> "NVIDIA Nemotron 3 Super 120B"
        else -> activeProvider.replaceFirstChar { it.uppercase() }
    }

    // Google Gemini Multi-Model Brain Integration.
    const val GEMINI_FLASH_MODEL = "gemini-2.5-flash"
    const val GEMINI_PRO_MODEL = "gemini-2.5-pro"
    const val GEMINI_LITE_MODEL = "gemini-2.0-flash-lite"

    // NVIDIA AI Endpoints. Model IDs verified against the live NIM catalogue.
    const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"

    /** Fast primary. 120B total / 12B active, 1M context, built for agentic tool use. */
    const val NVIDIA_SUPER_MODEL = "nvidia/nemotron-3-super-120b-a12b"

    /** Flagship deep reasoning. 550B total / 55B active. Used only for the deep tier. */
    const val NVIDIA_ULTRA_MODEL = "nvidia/nemotron-3-ultra-550b-a55b"

    /**
     * Low-latency fallback tier: 30B total / 3B active, 1M context. Entered
     * automatically by the fallback chain when Super is unavailable.
     */
    const val NVIDIA_NANO_MODEL = "nvidia/nemotron-3-nano-30b-a3b"

    // REMOVED (audit section 4.5): "mistralai/mistral-nemotron" and
    // "meta/llama-3.2-11b-vision-instruct" were stale catalogue entries sitting in
    // the fallback chain that every failed request walks. There is also no vision
    // call path in JarvisApiClient, so the Llama vision model could never be used
    // for its stated purpose. Keeping unverifiable model IDs in a fallback chain
    // turns one provider outage into a cascade of guaranteed 404s.

    // Complete Provider fallback chain: Gemini High-Speed Pool -> NVIDIA Multi-Model Cluster
    val PROVIDER_FALLBACK_CHAIN = listOf(
        "gemini_flash",
        "gemini_pro",
        "gemini_lite",
        "nvidia_super",
        "nvidia_nano",
        "nvidia_ultra"
    )

    /**
     * True for the providers that should reason before answering.
     *
     * Nemotron 3 ships with configurable reasoning and it defaults to ON, which
     * costs seconds per turn: independent measurements put Nemotron 3 Super around
     * 16s with reasoning enabled, versus roughly 4s for Nano. For a voice assistant
     * that is the difference between a conversation and a wait. JarvisApiClient uses
     * this to send chat_template_kwargs.enable_thinking explicitly instead of
     * inheriting the server default -- OFF for the conversational tier, ON for the
     * deep tier only.
     */
    fun isDeepTier(provider: String): Boolean = when (provider) {
        "nvidia_ultra", "nvidia_nemotron", "gemini_pro" -> true
        else -> false
    }

    /**
     * True when [model] is served by an endpoint that accepts chat_template_kwargs.
     * Gated deliberately: the Gemini OpenAI-compatibility endpoint does not accept
     * that field and would reject the whole request.
     */
    fun supportsReasoningToggle(model: String): Boolean =
        model.startsWith("nvidia/nemotron-3")

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
        "nvidia_nano" -> NVIDIA_NANO_MODEL
        "nvidia_ultra", "nvidia_nemotron" -> NVIDIA_ULTRA_MODEL
        // Legacy ids no longer map to a live endpoint; fall back to Super rather
        // than resolving to a removed model string.
        "nvidia_llama", "nvidia_mistral" -> NVIDIA_SUPER_MODEL
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

    // REMOVED (owner decision, 2026-09-07): the abandoned Rork Toolkit gateway
    // (TOOLKIT_URL / TOOLKIT_SECRET_KEY) and a block of aspirational connector
    // constants that were all empty strings with zero references anywhere in the
    // app -- GOOGLE_STT_API_KEY, GOOGLE_TTS_API_KEY, HOME_ASSISTANT_URL/TOKEN,
    // LIVEKIT_URL/API_KEY/API_SECRET and their hasCloudSTT / hasCloudTTS /
    // hasHomeAssistant / hasLiveKit predicates. Dead configuration reads as a
    // capability that does not exist.

    /**
     * Best-effort provider detection for a key pasted in Settings.
     *
     * CHANGED (owner decision, 2026-09-07): the ElevenLabs branch is gone with the
     * rest of that integration. Only NVIDIA and Gemini keys are supported, so an
     * unrecognised shape falls back to gemini_flash rather than being routed to a
     * provider that no longer exists.
     */
    fun autoDetectProvider(key: String): String {
        val trimmed = key.trim()
        return when {
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
