package com.jarvis.app.config

import android.content.Context
import android.content.SharedPreferences
import com.jarvis.app.BuildConfig

/**
 * Central JARVIS Neural Configuration.
 *
 * Provider priority:
 *   1. User-entered custom key (saved in SharedPreferences, survives reinstall)
 *   2. BuildConfig.XAI_API_KEY  (injected from local.properties — xAI Grok)
 *   3. BuildConfig.GEMINI_API_KEY (fallback)
 *
 * Key auto-detection:
 *   AQ.*       → xAI Grok (grok-3-mini or grok-2)
 *   AIzaSy*    → Google Gemini
 *   sk-ant-*   → Anthropic Claude
 *   sk-*       → OpenAI
 *   gsk_*      → Groq
 *   csk-*      → Cerebras
 *   sk-or-*    → OpenRouter
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

    // ── ElevenLabs British voice presets ─────────────────────────────────────
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

    // ── Runtime state ──────────────────────────────────────────────────────
    var userName: String = "Macaulay"
        private set
    var personalityTone: String = "jarvis_protocol"
        private set   // jarvis_protocol | conversational | executive
    var isOnboardingCompleted: Boolean = false
        private set
    var voiceEngineType: String = "elevenlabs"
        private set      // always "elevenlabs"
    var selectedVoiceId: String = "JBFqnCBsd6RMkjVDRZzb"
        private set

    // Custom key entered by the user in Settings
    var customApiKey: String? = null
        private set
    var customProvider: String? = null
        private set

    // ── BuildConfig keys (injected from local.properties at compile time) ──
    // TODO: Remove these hardcoded keys after deploying Convex backend!
    // These are temporary fallbacks for testing. Move to Convex env vars.
    val XAI_API_KEY: String    get() = BuildConfig.XAI_API_KEY.ifBlank { "AQ.Ab8RN6LVmURwb8YsZu0kcyO1cI5BHpsBen2Re1h4Sv31VnJhGA" }
    val GEMINI_API_KEY: String get() = BuildConfig.GEMINI_API_KEY
    val ELEVENLABS_API_KEY: String get() = BuildConfig.ELEVENLABS_API_KEY

    // ── Provider/key resolution ───────────────────────────────────────────

    /** The provider JARVIS uses for all AI calls right now. */
    val activeProvider: String
        get() {
            // 1. User's custom key
            customProvider?.takeIf { it.isNotBlank() }?.let { return it }
            // 2. xAI BuildConfig key
            if (XAI_API_KEY.isNotBlank()) return "xai"
            // 3. Gemini BuildConfig key
            if (GEMINI_API_KEY.isNotBlank()) return "gemini"
            return "gemini"
        }

    /** The API key that matches `activeProvider`. */
    val activeApiKey: String
        get() {
            // 1. User's custom key
            val custom = customApiKey?.trim()
            if (!custom.isNullOrBlank()) return custom
            // 2. xAI BuildConfig key
            if (XAI_API_KEY.isNotBlank()) return XAI_API_KEY
            // 3. Gemini BuildConfig key
            if (GEMINI_API_KEY.isNotBlank()) return GEMINI_API_KEY
            return ""
        }

    val hasAI: Boolean get() = activeApiKey.isNotBlank()
    val hasCustomKey: Boolean get() = !customApiKey.isNullOrBlank()

    /** Human-readable label for the Diagnostics screen. */
    fun getProviderLabel(): String = when (activeProvider) {
        "xai"        -> "xAI Grok"
        "gemini"     -> "Google Gemini"
        "openai"     -> "OpenAI"
        "cerebras"   -> "Cerebras"
        "groq"       -> "Groq"
        "anthropic"  -> "Anthropic Claude"
        "mistral"    -> "Mistral AI"
        "cohere"     -> "Cohere"
        "openrouter" -> "OpenRouter"
        else -> activeProvider.replaceFirstChar { it.uppercase() }
    }

    // ── Model IDs ─────────────────────────────────────────────────────────
    const val XAI_MODEL       = "grok-3-mini"        // fast, cheap, great for tool-calling
    const val XAI_MODEL_FULL  = "grok-3"             // highest quality
    const val GEMINI_MODEL    = "gemini-2.5-flash"
    const val CEREBRAS_MODEL  = "llama3.1-70b"
    const val OPENAI_MODEL    = "gpt-4o-mini"
    const val GROK_MODEL      = "grok-2"
    const val MISTRAL_MODEL   = "mistral-small-latest"
    const val COHERE_MODEL    = "command-r-plus"
    const val ANTHROPIC_MODEL = "claude-3-5-sonnet-latest"
    const val GROQ_MODEL      = "llama-3.3-70b-versatile"
    const val OPENROUTER_MODEL= "openai/gpt-4o-mini"

    // ── Optional connectors ───────────────────────────────────────────────
    const val GOOGLE_STT_API_KEY  = ""
    const val GOOGLE_TTS_API_KEY  = ""
    const val HOME_ASSISTANT_URL  = ""
    const val HOME_ASSISTANT_TOKEN= ""
    const val LIVEKIT_URL         = ""
    const val LIVEKIT_API_KEY     = ""
    const val LIVEKIT_API_SECRET  = ""
    val hasCloudSTT       get() = GOOGLE_STT_API_KEY.isNotBlank()
    val hasCloudTTS       get() = GOOGLE_TTS_API_KEY.isNotBlank()
    val hasHomeAssistant  get() = HOME_ASSISTANT_URL.isNotBlank() && HOME_ASSISTANT_TOKEN.isNotBlank()
    val hasLiveKit        get() = LIVEKIT_URL.isNotBlank() && LIVEKIT_API_KEY.isNotBlank()

    // ── Key auto-detection ────────────────────────────────────────────────
    fun autoDetectProvider(key: String): String = when {
        key.startsWith("AQ.")          -> "xai"
        key.startsWith("AIzaSy")       -> "gemini"
        key.startsWith("sk-ant-")      -> "anthropic"
        key.startsWith("sk-proj-") || (key.startsWith("sk-") && !key.startsWith("sk-ant-")) -> "openai"
        key.startsWith("gsk_")         -> "groq"
        key.startsWith("csk-")         -> "cerebras"
        key.startsWith("sk-or-")       -> "openrouter"
        else                           -> "xai"   // default to xAI for unknown format
    }

    // ── Persistence helpers ───────────────────────────────────────────────
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
        userName              = p.getString(PREF_KEY_USER_NAME, "Macaulay") ?: "Macaulay"
        personalityTone       = p.getString(PREF_KEY_AI_TONE, "jarvis_protocol") ?: "jarvis_protocol"
        isOnboardingCompleted = p.getBoolean(PREF_KEY_ONBOARDING_DONE, false)
        voiceEngineType       = p.getString(PREF_KEY_VOICE_ENGINE, "elevenlabs") ?: "elevenlabs"
        selectedVoiceId       = p.getString(PREF_KEY_VOICE_ID, "JBFqnCBsd6RMkjVDRZzb") ?: "JBFqnCBsd6RMkjVDRZzb"
        customApiKey          = p.getString(PREF_KEY_CUSTOM_API_KEY, null)?.takeIf { it.isNotBlank() }
        customProvider        = p.getString(PREF_KEY_CUSTOM_PROVIDER, null)?.takeIf { it.isNotBlank() }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
