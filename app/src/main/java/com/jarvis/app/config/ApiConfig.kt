package com.jarvis.app.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Central JARVIS Neural Configuration.
 *
 * API keys are injected from BuildConfig at compile time (from environment
 * variables in CI or local.properties for development). There are no hardcoded
 * fallback keys in source code.
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
        

    // API Keys — BuildConfig injection from compile-time environment variables.
    // Keys are never hardcoded in source; they must be provided via CI secrets
    // or local.properties (which is gitignored).
    val GEMINI_API_KEY: String get() = BuildConfig.GEMINI_API_KEY
    val NVIDIA_API_KEY: String get() = BuildConfig.NVIDIA_API_KEY
    val ELEVENLABS_API_KEY: String get() = BuildConfig.ELEVENLABS_API_KEY

    // OpenAI — optional extra cloud brain. Fill OPENAI_API_KEY to activate;
    // it automatically joins the provider fallback chain after NVIDIA.
    const val OPENAI_BASE_URL = "https://api.openai.com/v1"
    const val OPENAI_MODEL = "gpt-4o-mini"
    var OPENAI_API_KEY: String = ""
        get() = BuildConfig.OPENAI_API_KEY.ifEmpty { field }

    // xAI Grok — optional extra cloud brain.
    const val XAI_BASE_URL = "https://api.x.ai/v1"
    const val XAI_MODEL = "grok-3-mini"
    val XAI_API_KEY: String get() = BuildConfig.XAI_API_KEY

    // Groq — fast inference, free developer tier (~30 rpm).
    const val GROQ_BASE_URL = "https://api.groq.com/openai/v1"
    const val GROQ_MODEL_FAST = "llama-3.3-70b-versatile"
    const val GROQ_MODEL_DEEP = "deepseek-r1-distill-llama-70b"
    val GROQ_API_KEY: String get() = BuildConfig.GROQ_API_KEY

    // OpenRouter — pool of free models, 50 req/day free without card.
    const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
    const val OPENROUTER_MODEL_FAST = "google/gemini-2.5-flash"
    const val OPENROUTER_MODEL_DEEP = "google/gemini-2.5-pro"
    val OPENROUTER_API_KEY: String get() = BuildConfig.OPENROUTER_API_KEY

    // Mistral — free 'Experiment' tier, 1 req/s, 500k tokens/min, no card.
    const val MISTRAL_BASE_URL = "https://api.mistral.ai/v1"
    const val MISTRAL_MODEL = "mistral-large-latest"
    val MISTRAL_API_KEY: String get() = BuildConfig.MISTRAL_API_KEY

    // Cloudflare Workers AI — 10,000 free Neurons/day, no card.
    const val CLOUDFLARE_BASE_URL = "https://api.cloudflare.com/client/v4/accounts"
    const val CLOUDFLARE_MODEL_FAST = "@cf/meta/llama-3.1-8b"
    val CLOUDFLARE_ACCOUNT_ID: String get() = BuildConfig.CLOUDFLARE_ACCOUNT_ID
    val CLOUDFLARE_API_KEY: String get() = BuildConfig.CLOUDFLARE_API_KEY

    // ── On-device LLM (llama.cpp) ─────────────────────────────────────────
    // Fully local inference — zero API key, zero cost, zero network.
    // Models are GGUF format bundles that ship with the app or are
    // downloaded on first use into the app's files dir.
    // The model file name (without extension) is configurable via
    // local.properties:  LOCAL_MODEL_NAME=gemma-3-1b-it
    // or the "local" provider can be selected in Settings.
    var localModelName: String = "gemma-3-1b-it"
        @JvmName("getLocalModelName") get
        @JvmName("setLocalModelName") set

    /** Returns the on-device model path if the model file exists locally. */
    fun localModelPath(context: Context): String? {
        val dir = context.filesDir
        val candidates = listOf(
            "$localModelName.gguf",
            "$localModelName-q4_k_m.gguf",
            "$localModelName-q4_0.gguf",
            "$localModelName-q5_k_m.gguf"
        )
        for (name in candidates) {
            val file = java.io.File(dir, "models/$name")
            if (file.exists() && file.length() > 0) return file.absolutePath
        }
        // Also check external files dir (for user-downloaded models)
        val extDir = context.getExternalFilesDir("models")
        if (extDir != null) {
            for (name in candidates) {
                val file = java.io.File(extDir, name)
                if (file.exists() && file.length() > 0) return file.absolutePath
            }
        }
        return null
    }

    val hasLocalModel: Boolean
        get() = false // checked at runtime via localModelPath(context)

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
        "nvidia_super" -> "NVIDIA Nemotron-3 Super"
        "nvidia_ultra" -> "NVIDIA Nemotron Ultra"
        "openai_mini" -> "OpenAI GPT-4o Mini"
        "groq_fast" -> "Groq Llama 3.3 70B (Fast)"
        "groq_deep" -> "Groq DeepSeek R1 (Deep)"
        "openrouter_fast" -> "OpenRouter Gemini 2.5 Flash"
        "openrouter_deep" -> "OpenRouter Gemini 2.5 Pro"
        "mistral" -> "Mistral Large"
        "cloudflare" -> "Cloudflare LLaMA 3.1 8B"
        "xai" -> "xAI Grok-3"
        "gemini_lite" -> "Gemini 2.5 Flash Lite"
        else -> "Gemini AI"
    }

    // Google Gemini Brain Integration (per official Gemini guidelines).
    const val GEMINI_FLASH_MODEL = "gemini-2.5-flash"
    const val GEMINI_PRO_MODEL = "gemini-2.5-pro"
    const val GEMINI_LITE_MODEL = "gemini-2.5-flash"

    // Legacy fallback constants
    const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
    const val NVIDIA_SUPER_MODEL = "nvidia/nemotron-3-super-120b-a12b"

    // Gemini high-speed pool + expanded free provider fallback chain.
    // The model picks the first provider in this chain that has a key or model available.
    // local_llm runs entirely on-device via llama.cpp — no key needed, works offline.
    val PROVIDER_FALLBACK_CHAIN = listOf(
        "gemini_flash",
        "local_llm",
        "openai_mini",
        "groq_fast",
        "openrouter_fast",
        "mistral",
        "cloudflare",
        "gemini_pro"
    )

    /** Returns the API key for a given provider, checking BuildConfig and custom keys. */
    fun keyForProvider(provider: String): String = when {
        provider.startsWith("gemini") -> currentGeminiKey
        provider.startsWith("nvidia") -> NVIDIA_API_KEY
        provider.startsWith("openai") -> OPENAI_API_KEY
        provider.startsWith("xai") -> XAI_API_KEY
        provider.startsWith("groq") -> GROQ_API_KEY
        provider.startsWith("openrouter") -> OPENROUTER_API_KEY
        provider.startsWith("mistral") -> MISTRAL_API_KEY
        provider.startsWith("cloudflare") -> CLOUDFLARE_API_KEY
        else -> activeApiKey
    }

    /** Returns true if the given provider has a non-blank API key configured. */
    fun hasKeyForProvider(provider: String): Boolean = when {
        provider.startsWith("gemini") -> hasUsableGeminiKey
        provider.startsWith("nvidia") -> NVIDIA_API_KEY.isNotBlank()
        provider.startsWith("openai") -> OPENAI_API_KEY.isNotBlank()
        provider.startsWith("xai") -> XAI_API_KEY.isNotBlank()
        provider.startsWith("groq") -> GROQ_API_KEY.isNotBlank()
        provider.startsWith("openrouter") -> OPENROUTER_API_KEY.isNotBlank()
        provider.startsWith("mistral") -> MISTRAL_API_KEY.isNotBlank()
        provider.startsWith("cloudflare") -> CLOUDFLARE_ACCOUNT_ID.isNotBlank() && CLOUDFLARE_API_KEY.isNotBlank()
        else -> currentApiKey.isNotBlank()
    }

    /**
     * Get the next provider in the fallback chain that actually has an available API key.
     */
    fun getNextProvider(currentProvider: String): String? {
        val currentIndex = PROVIDER_FALLBACK_CHAIN.indexOf(currentProvider)
        val startIndex = if (currentIndex >= 0) currentIndex + 1 else 0

        for (i in startIndex until PROVIDER_FALLBACK_CHAIN.size) {
            val candidate = PROVIDER_FALLBACK_CHAIN[i]
            if (!hasKeyForProvider(candidate)) continue
            return candidate
        }
        return null
    }

    /** Resolve model ID for a given provider. */
    fun resolveModel(provider: String): String = when (provider) {
        "gemini_flash" -> GEMINI_FLASH_MODEL
        "gemini_pro" -> GEMINI_PRO_MODEL
        "gemini_lite" -> GEMINI_LITE_MODEL
        "nvidia_super" -> NVIDIA_SUPER_MODEL
        "nvidia_ultra" -> "nvidia/nemotron-4-340b-reward"
        "openai_mini" -> OPENAI_MODEL
        "xai" -> XAI_MODEL
        "groq_fast" -> GROQ_MODEL_FAST
        "groq_deep" -> GROQ_MODEL_DEEP
        "openrouter_fast" -> OPENROUTER_MODEL_FAST
        "openrouter_deep" -> OPENROUTER_MODEL_DEEP
        "mistral" -> MISTRAL_MODEL
        "cloudflare" -> CLOUDFLARE_MODEL_FAST
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
     * Fast conversational tasks stay on Gemini Flash / Groq Llama.
     * Deep reasoning requests route to Gemini Pro / Groq DeepSeek.
     */
    fun providerForUtterance(text: String): String {
        if (activeProvider.startsWith("openai")) return "openai_mini"
        val isDeep = text.length > 160 || DEEP_THINK_HINTS.any { it in text.lowercase() }
        val isNvidia = activeProvider.startsWith("nvidia")
        val isGroq = activeProvider.startsWith("groq")
        val isOpenRouter = activeProvider.startsWith("openrouter")
        return when {
            isNvidia -> if (isDeep) "nvidia_ultra" else "nvidia_super"
            isGroq -> if (isDeep) "groq_deep" else "groq_fast"
            isOpenRouter -> if (isDeep) "openrouter_deep" else "openrouter_fast"
            else -> if (isDeep) "gemini_pro" else "gemini_flash"
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
            trimmed.startsWith("xai-") || trimmed.startsWith("sk-xai-") -> "xai"
            trimmed.startsWith("gsk_") -> "groq_fast"
            trimmed.startsWith("sk-or-") || trimmed.startsWith("org-") -> "openrouter_fast"
            trimmed.startsWith("mistral-") -> "mistral"
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
