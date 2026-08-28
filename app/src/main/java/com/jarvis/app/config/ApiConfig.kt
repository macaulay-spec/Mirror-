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
    private const val PREF_KEY_ONBOARDING_DONE = "onboarding_completed"

    var userName: String = "Macaulay"
        private set
    var isOnboardingCompleted: Boolean = false
        private set
    var voiceEngineType: String = "elevenlabs" // "elevenlabs" or "native"
        private set
    var selectedVoiceId: String = "21m00Tcm4TlvDq8ikWAM"
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

    // Bundled Zero-Config Multi-Model AI Key Vault
    const val BUNDLED_CEREBRAS_KEY = "csk-83exn9prmf29re665xpw46pey62np5vpch3k4xxyvfmnjw8v"
    const val BUNDLED_OPENAI_KEY = "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"
    const val BUNDLED_GROK_KEY = "AQ.Ab8RN6JpFdEy5KZ06i3LzgQRv10lqEYMAzIncMAdEnb4dLNP7Q."
    const val BUNDLED_MISTRAL_KEY = "key_CeTGWYuhXeyJkKTrahnep."
    const val BUNDLED_COHERE_KEY = "cohere_kqX8R0ZQcveFcVczw8uQHIndhccSWzFzw9NSFV0J2X1WcG."

    // Voice & Audio Infrastructure Keys
    /**
     * Local override wins: put ELEVENLABS_API_KEY in local.properties (gitignored) or in
     * the environment. The fallback below is the key that shipped in this public repo and
     * may be throttled or revoked at any time — treat the local one as the real key.
     */
    var ELEVENLABS_API_KEY: String = BuildConfig.ELEVENLABS_API_KEY
        .takeIf { it.isNotBlank() }
        ?: "sk_7049a5e7b9f58b28ba134bb1a0e195de5d00b98ee0e44450"
    const val ELEVENLABS_DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM" // Rachel / Adam British / Jarvis voice profile
    val hasElevenLabs get() = ELEVENLABS_API_KEY.isNotBlank()

    // LiveKit Cloud WebRTC Configuration
    const val LIVEKIT_URL = "wss://jjk-aqil5yrm.livekit.cloud"
    const val LIVEKIT_API_KEY = "APIsrEpY2CN9n4b"
    const val LIVEKIT_API_SECRET = "rJjF4z8DeB25nfkD7MgkCZY5QbbAvcbtcF2dvLM0ioZ"
    val hasLiveKit get() = LIVEKIT_URL.isNotBlank() && LIVEKIT_API_KEY.isNotBlank()

    // Developer Key Pool (injected via BuildConfig / CI / .env)
    private val DEVELOPER_GEMINI_KEYS = listOfNotNull(
        BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() },
        System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }
    ).distinct()

    private var activeDevKeyIndex = 0

    // Custom user key and provider
    var customApiKey: String? = null
        private set
    var customProvider: String? = null
        private set

    // Backward-compatibility alias
    var customGeminiApiKey: String?
        get() = if (customProvider == "gemini" || customProvider == null) customApiKey else null
        set(value) {
            customApiKey = value
            customProvider = if (!value.isNullOrBlank()) detectProvider(value) else null
        }

    val activeApiKey: String
        get() {
            if (!customApiKey.isNullOrBlank()) return customApiKey!!
            if (BUNDLED_CEREBRAS_KEY.isNotBlank()) return BUNDLED_CEREBRAS_KEY
            if (BUNDLED_OPENAI_KEY.isNotBlank()) return BUNDLED_OPENAI_KEY
            if (DEVELOPER_GEMINI_KEYS.isNotEmpty()) {
                val idx = activeDevKeyIndex.coerceIn(0, DEVELOPER_GEMINI_KEYS.size - 1)
                return DEVELOPER_GEMINI_KEYS[idx]
            }
            return ""
        }

    val activeProvider: String
        get() {
            if (!customApiKey.isNullOrBlank()) {
                return customProvider ?: detectProvider(customApiKey!!)
            }
            if (BUNDLED_CEREBRAS_KEY.isNotBlank()) return "cerebras"
            if (BUNDLED_OPENAI_KEY.isNotBlank()) return "openai"
            return "gemini"
        }

    // Standard getters
    val GEMINI_API_KEY: String get() = if (activeProvider == "gemini" && !customApiKey.isNullOrBlank()) customApiKey!! else (DEVELOPER_GEMINI_KEYS.firstOrNull() ?: "")
    const val GEMINI_MODEL = "gemini-2.5-flash"

    const val CEREBRAS_MODEL = "llama3.1-70b"
    const val OPENAI_MODEL = "gpt-4o-mini"
    const val GROK_MODEL = "grok-2"
    const val MISTRAL_MODEL = "mistral-small-latest"
    const val COHERE_MODEL = "command-r-plus"
    const val ANTHROPIC_MODEL = "claude-3-5-sonnet-latest"
    const val GROQ_MODEL = "llama-3.3-70b-versatile"
    const val OPENROUTER_MODEL = "openai/gpt-4o-mini"

    val hasGemini: Boolean get() = GEMINI_API_KEY.isNotBlank()
    val hasCustomKey: Boolean get() = !customApiKey.isNullOrBlank()
    val hasAI: Boolean get() = activeApiKey.isNotBlank() || BUNDLED_CEREBRAS_KEY.isNotBlank() || BUNDLED_OPENAI_KEY.isNotBlank()

    val CEREBRAS_API_KEY: String get() = if (activeProvider == "cerebras" && !customApiKey.isNullOrBlank()) customApiKey!! else BUNDLED_CEREBRAS_KEY
    val OPENAI_API_KEY: String get() = if (activeProvider == "openai" && !customApiKey.isNullOrBlank()) customApiKey!! else BUNDLED_OPENAI_KEY
    val GROK_API_KEY: String get() = if (activeProvider == "grok" && !customApiKey.isNullOrBlank()) customApiKey!! else BUNDLED_GROK_KEY
    val MISTRAL_API_KEY: String get() = if (activeProvider == "mistral" && !customApiKey.isNullOrBlank()) customApiKey!! else BUNDLED_MISTRAL_KEY
    val COHERE_API_KEY: String get() = if (activeProvider == "cohere" && !customApiKey.isNullOrBlank()) customApiKey!! else BUNDLED_COHERE_KEY

    // Anthropic (fallback) -> https://console.anthropic.com/
    val ANTHROPIC_API_KEY: String get() = if (activeProvider == "anthropic") activeApiKey else ""
    val hasAnthropic get() = ANTHROPIC_API_KEY.isNotBlank()

    // ===== Cloud STT (optional) =====
    const val GOOGLE_STT_API_KEY = ""
    val hasCloudSTT get() = GOOGLE_STT_API_KEY.isNotBlank()

    // ===== Cloud TTS (optional) =====
    const val GOOGLE_TTS_API_KEY = ""
    val hasCloudTTS get() = GOOGLE_TTS_API_KEY.isNotBlank()

    // ===== ElevenLabs (optional) =====
    // ELEVENLABS_API_KEY is configured above with user's key

    // ===== Connectors (optional, later) =====
    const val HOME_ASSISTANT_URL = ""
    const val HOME_ASSISTANT_TOKEN = ""
    val hasHomeAssistant get() = HOME_ASSISTANT_URL.isNotBlank() && HOME_ASSISTANT_TOKEN.isNotBlank()

    /**
     * Automatically detects the AI provider based on API key prefix.
     */
    fun detectProvider(key: String): String {
        val trimmed = key.trim()
        return when {
            trimmed.startsWith("AIzaSy") -> "gemini"
            trimmed.startsWith("sk-ant-") -> "anthropic"
            trimmed.startsWith("sk-or-") -> "openrouter"
            trimmed.startsWith("gsk_") -> "groq"
            trimmed.startsWith("csk-") -> "cerebras"
            trimmed.startsWith("cohere_") -> "cohere"
            trimmed.startsWith("key_") -> "mistral"
            // xAI Grok keys: "AQ.Ab8RN6..." — easy to mistake for a Gemini key
            trimmed.startsWith("AQ.") -> "grok"
            trimmed.startsWith("sk-proj-") -> "openai"
            trimmed.startsWith("sk-") -> "openai"
            // ElevenLabs: "sk_" followed by a hex blob. A voice key, not a chat key.
            trimmed.startsWith("sk_") -> "elevenlabs"
            else -> "gemini"
        }
    }

    /**
     * Friendly display label for provider.
     */
    fun getProviderLabel(provider: String = activeProvider): String {
        return when (provider.lowercase()) {
            "gemini" -> "Google Gemini Core"
            "openai" -> "OpenAI Engine"
            "anthropic" -> "Anthropic Claude"
            "groq" -> "Groq Ultra-Fast Core"
            "openrouter" -> "OpenRouter Gateway"
            "cerebras" -> "Cerebras Core"
            "mistral" -> "Mistral Core"
            "cohere" -> "Cohere Core"
            "grok" -> "Grok (xAI) Core"
            "elevenlabs" -> "ElevenLabs — this is a VOICE key. Paste it in Voice settings, not here."
            else -> "Neural Gateway"
        }
    }

    /**
     * Rotate to next developer key in pool when quota exhausted (429).
     */
    fun rotateDeveloperKey(): Boolean {
        if (DEVELOPER_GEMINI_KEYS.size > 1) {
            activeDevKeyIndex = (activeDevKeyIndex + 1) % DEVELOPER_GEMINI_KEYS.size
            return true
        }
        return false
    }

    /**
     * Loads persisted settings on app startup.
     */
    fun load(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedKey = prefs.getString(PREF_KEY_CUSTOM_API_KEY, null)
        val savedProvider = prefs.getString(PREF_KEY_CUSTOM_PROVIDER, null)
        
        // Also check legacy prefs
        val legacyPrefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val legacyKey = legacyPrefs.getString("custom_gemini_api_key", null)

        val keyToUse = savedKey?.takeIf { it.isNotBlank() } ?: legacyKey?.takeIf { it.isNotBlank() }
        if (keyToUse != null) {
            customApiKey = keyToUse
            customProvider = savedProvider ?: detectProvider(keyToUse)
        }
        userName = prefs.getString(PREF_KEY_USER_NAME, "Macaulay") ?: "Macaulay"
        isOnboardingCompleted = prefs.getBoolean(PREF_KEY_ONBOARDING_DONE, false)
        voiceEngineType = prefs.getString(PREF_KEY_VOICE_ENGINE, "elevenlabs") ?: "elevenlabs"
        selectedVoiceId = prefs.getString(PREF_KEY_VOICE_ID, "21m00Tcm4TlvDq8ikWAM") ?: "21m00Tcm4TlvDq8ikWAM"
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

    /**
     * Saves user-provided custom API key.
     */
    fun saveCustomKey(context: Context, key: String, provider: String? = null) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            clearCustomKey(context)
            return
        }
        val detected = provider ?: detectProvider(trimmed)
        customApiKey = trimmed
        customProvider = detected

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_KEY_CUSTOM_API_KEY, trimmed)
            .putString(PREF_KEY_CUSTOM_PROVIDER, detected)
            .apply()
    }

    /**
     * Clears user custom key and reverts back to default developer core.
     */
    fun clearCustomKey(context: Context) {
        customApiKey = null
        customProvider = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_KEY_CUSTOM_API_KEY)
            .remove(PREF_KEY_CUSTOM_PROVIDER)
            .apply()

        context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("custom_gemini_api_key")
            .apply()
    }
}
