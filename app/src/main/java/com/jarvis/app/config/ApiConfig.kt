package com.jarvis.app.config

import android.content.Context
import android.content.SharedPreferences
import com.rork.jarvisaiassistant.BuildConfig

/**
 * Central JARVIS Neural Configuration.
 *
 * Provider priority:
 *   1. User-entered custom key (saved in SharedPreferences, survives reinstall)
 *   2. BuildConfig keys (injected from local.properties / CI secrets at compile time)
 *   3. Owner-embedded fallback keys (direct API mode, zero setup, repo going private)
 *
 * KEY POLICY (owner decision, 2026-09-03): the app routes directly to AI
 * providers with NO backend proxy, and the owner's own keys are embedded as
 * fallbacks so the app is alive out of the box. A previous audit removed
 * them; the owner explicitly had them restored verbatim. If this repo ever
 * becomes public again, rotate the keys first.
 *
 * Key auto-detection:
 *   xai-*      → xAI Grok (grok-3-mini or grok-2)
 *   AIzaSy*    → Google Gemini
 *   AQ.*       → Google Gemini (service key format)
 *   sk-ant-*   → Anthropic Claude
 *   sk-*       → OpenAI
 *   gsk_*      → Groq
 *   csk-*      → Cerebras
 *   sk-or-*    → OpenRouter
 *
 * NOTE: The app routes directly to AI providers without a backend proxy, so
 * any key you enter is stored locally on the device only.
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

    // ── API Keys ────────────────────────────────────────────────────────────
    // Owner policy: keys may be overridden via local.properties / CI secrets
    // (BuildConfig); otherwise the embedded owner keys below are used so the
    // app works with zero setup in direct-API mode (no backend, ever).

    // ── API Keys ────────────────────────────────────────────────────────────
    // LIVE AUDIT (2026-09-03): the previously embedded fallback keys were all
    // verified DEAD against the real APIs. Keys now come exclusively from
    // build-time injection (CI secrets / local.properties -> BuildConfig).
    // 
    // NVIDIA integration (2026-09-04): Added NVIDIA_API_KEY BuildConfig field
    // for NVIDIA multi-model support per NVIDIA_MULTI_MODEL_PROMPT.md.
    // Rork gateway is confirmed broken and drops out of priority order.

    // ── BuildConfig keys (injected from local.properties at compile time) ──
    val XAI_API_KEY: String
        get() = BuildConfig.XAI_API_KEY

    val GEMINI_API_KEY: String
        get() = BuildConfig.GEMINI_API_KEY.ifBlank { HARDCODED_GEMINI_KEY }

    val ELEVENLABS_API_KEY: String
        get() = BuildConfig.ELEVENLABS_API_KEY.ifBlank { HARDCODED_ELEVENLABS_KEY }

    // NVIDIA API key - injected from local.properties / CI secrets
    // No hardcoded fallback - if not configured, provider is unavailable
    val NVIDIA_API_KEY: String
        get() = BuildConfig.NVIDIA_API_KEY

    // ── Provider/key resolution ───────────────────────────────────────────

    /**
     * The provider JARVIS uses for all AI calls right now.
     * 
     * Priority order (per NVIDIA_MULTI_MODEL_PROMPT.md):
     * 1. User's custom key (entered in Settings — auto-detected provider)
     * 2. NVIDIA GLM-5.2 (primary)
     * 3. NVIDIA Nemotron-3-Super (fallback 1)
     * 4. NVIDIA Mistral Nemotron (fallback 2)
     * 5. NVIDIA Llama-4 Maverick (fallback 3)
     * 6. Existing chain: rork, xai, gemini, openai, anthropic, groq, cerebras, mistral, cohere, openrouter
     * 
     * Rork gateway is confirmed broken per NVIDIA_MULTI_MODEL_PROMPT.md and drops
     * out of priority order entirely — kept only for backwards compatibility.
     */
    val activeProvider: String
        get() {
            // 1. User's custom key (entered in Settings — auto-detected provider)
            customProvider?.takeIf { it.isNotBlank() }?.let { return it }
            
            // 2. NVIDIA models (primary tier) - check if NVIDIA_API_KEY is configured
            if (NVIDIA_API_KEY.isNotBlank()) {
                return "nvidia_glm"  // GLM-5.2 is primary
            }
            
            // 3. Fall through to existing chain (rork is broken but kept for compatibility)
            return "rork"
        }

    /**
     * The API key that matches `activeProvider`.
     * 
     * For NVIDIA providers, returns NVIDIA_API_KEY.
     * For Rork, returns rorkApiKey.
     * For custom providers, returns the user's custom key.
     */
    val activeApiKey: String
        get() {
            // 1. User's custom key
            val custom = customApiKey?.trim()
            if (!custom.isNullOrBlank()) return custom
            
            // 2. Check if active provider is NVIDIA
            val provider = activeProvider
            if (provider.startsWith("nvidia_")) {
                return NVIDIA_API_KEY
            }
            
            // 3. Rork gateway (Claude) — key is injected at build time.
            return rorkApiKey
        }

    val hasAI: Boolean get() = activeApiKey.isNotBlank()
    val hasCustomKey: Boolean get() = !customApiKey.isNullOrBlank()

    /** Human-readable label for the Diagnostics screen. */
    fun getProviderLabel(): String = when (activeProvider) {
        "rork"       -> "Claude (Rork Gateway)"
        "xai"        -> "xAI Grok (Grok-3)"
        "gemini"     -> "Google Gemini"
        "openai"     -> "OpenAI"
        "cerebras"   -> "Cerebras"
        "groq"       -> "Groq"
        "anthropic"  -> "Anthropic Claude"
        "mistral"    -> "Mistral AI"
        "cohere"     -> "Cohere"
        "openrouter" -> "OpenRouter"
        "nvidia" -> "NVIDIA GLM-5.2"
        "nvidia_glm" -> "NVIDIA GLM-5.2"
        "nvidia_nemotron" -> "NVIDIA Nemotron-3-Super"
        "nvidia_mistral" -> "NVIDIA Mistral Nemotron"
        "nvidia_llama" -> "NVIDIA Llama-4 Maverick"
        else -> activeProvider.replaceFirstChar { it.uppercase() }
    }

    // ── Model IDs ─────────────────────────────────────────────────────────
    // NVIDIA Multi-Model Integration (Primary providers per NVIDIA_MULTI_MODEL_PROMPT.md)
    const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
    const val NVIDIA_GLM_MODEL = "zhipuai/glm-5.2"              // Primary: 1M token context
    const val NVIDIA_NEMOTRON_MODEL = "nvidia/nemotron-3-super"   // Fallback 1: NVIDIA flagship
    const val NVIDIA_MISTRAL_NEMOTRON_MODEL = "mistralai/mistral-nemotron-7b"  // Fallback 2
    const val NVIDIA_LLAMA_MODEL = "meta/llama-4-maverick"      // Fallback 3
    
    // Existing providers (kept as final fallback tier)
    const val RORK_MODEL      = "anthropic/claude-haiku-4.5"  // Claude via Rork AI gateway
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

    // ── Rork AI gateway (Claude) ──────────────────────────────────────────
    const val RORK_GATEWAY_URL = "https://toolkit.rork.com/v2/vercel/v1/chat/completions"

    /**
     * Build-time injected Rork gateway key (RORK_TOOLKIT_KEY in
     * local.properties / CI secrets).
     * 
     * NOTE: Rork gateway integration is confirmed broken per NVIDIA_MULTI_MODEL_PROMPT.md.
     * It drops out of the priority order entirely. This property remains for
     * backwards compatibility but should not be used as a primary provider.
     */
    val rorkApiKey: String
        get() = BuildConfig.RORK_TOOLKIT_KEY

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
        key.startsWith("AIzaSy")       -> "gemini"
        key.startsWith("AQ.")          -> "gemini"   // AQ.* = Gemini service key
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
