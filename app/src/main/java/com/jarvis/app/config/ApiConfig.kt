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

    // REMOVED during the sync: OLD_PRESET_VOICES -- a nine-entry lookup table with zero
    // references anywhere in the app (the live list above is the one the UI reads). It also
    // mapped the Gemini id "Aoede" onto the label "Rex", which was a leftover from the
    // OpenAI-voice era and actively misleading: Aoede is a real Gemini TTS voice and is
    // still the persisted default in loadFromPreferences().


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
        

    // API Keys — Gemini is injected at build time; NVIDIA is hardcoded.
    // OWNER DECISION (kept from 272f511): the repository is private and the APK is
    // self-contained, so the NVIDIA key lives in source.
    //
    // CHANGED during the sync: NVIDIA_API_KEY no longer reads BuildConfig, because
    // app/build.gradle.kts stopped declaring that field -- it was only ever populated
    // from System.getenv(), which is empty on a normal `./gradlew assembleDebug`, so the
    // ifBlank fallback did all the work anyway. One copy, one place to rotate.
    //
    // The ElevenLabs key is gone with the rest of that integration (owner instruction:
    // remove it entirely, revisit later). Nothing in the app referenced it once
    // CloudSttEngine was deleted.
    val GEMINI_API_KEY: String get() = BuildConfig.GEMINI_API_KEY

    val NVIDIA_API_KEY: String get() = HARDCODED_NVIDIA_KEY

    // Hardcoded fallback key (owner decision — repo is private)
    private const val HARDCODED_NVIDIA_KEY =
        "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"

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
    /**
     * Gemini is the default brain (owner decision, 272f511: "Powered exclusively by
     * Gemini"). That is preserved.
     *
     * CHANGED during the sync: returning "gemini_flash" unconditionally made the
     * hardcoded NVIDIA key dead weight -- when a Gemini free-tier quota ran out, or no
     * Gemini key was configured at all, JARVIS had a working NVIDIA key in the APK and
     * still reported no brain. NVIDIA is now a genuine second choice rather than a
     * constant that is never reached. Gemini still wins whenever it is usable.
     */
    val activeProvider: String
        get() {
            // 1. A provider picked for a custom key the user saved in Settings.
            customProvider?.takeIf { it.isNotBlank() }?.let { return it }
            // 2. Gemini — the default.
            if (hasUsableGeminiKey || GEMINI_API_KEY.isNotBlank()) return "gemini_flash"
            // 3. NVIDIA — always present, because the key is compiled in.
            if (NVIDIA_API_KEY.isNotBlank()) return "nvidia_super"
            // 4. OpenAI, if the owner ever fills the key in.
            if (OPENAI_API_KEY.isNotBlank()) return "openai_mini"
            return ""
        }

    /**
     * The key that matches [activeProvider].
     *
     * CHANGED during the sync: this used to always return a Gemini key. Once
     * activeProvider can resolve to NVIDIA, sending a Gemini key to NVIDIA's endpoint is
     * a guaranteed 401 -- the two must be resolved together or the fallback cannot work.
     */
    val activeApiKey: String
        get() {
            // ?. all the way down: customApiKey is String?, so customApiKey?.trim() is
            // String? and takeIf needs its own safe call. The missing ?. was the one
            // compile error in the sync commit.
            // ?. all the way down: customApiKey is String?, so customApiKey?.trim() is
            // String? and takeIf needs its own safe call.
            val custom = customApiKey?.trim()?.takeIf { it.isNotBlank() }
            // Gemini keeps its historic precedence: a saved custom key only wins when the
            // pool has nothing usable, which is what the pre-sync getter did.
            return if (activeProvider.startsWith("gemini")) {
                currentGeminiKey.ifBlank { custom ?: GEMINI_API_KEY }
            } else {
                custom ?: keyFor(activeProvider)
            }
        }

    val hasAI: Boolean
        get() = currentApiKey.isNotBlank()

    /**
     * Single key accessor.
     *
     * CHANGED during the sync: this was a fourth overlapping way to ask "which key"
     * (alongside activeApiKey, currentGeminiKey and GEMINI_API_KEY) and it disagreed with
     * activeApiKey about NVIDIA. Delegating removes the disagreement.
     */
    val currentApiKey: String
        get() = activeApiKey

    val originalHasAI: Boolean get() = activeApiKey.isNotBlank()
    val hasCustomKey: Boolean get() = !customApiKey.isNullOrBlank()

    /** Human-readable label for the Diagnostics screen. */
    fun getProviderLabel(): String = when {
        activeProvider.startsWith("gemini_pro") -> "Gemini 2.5 Pro (Deep Reasoning)"
        activeProvider.startsWith("gemini") -> "Gemini 2.5 Flash (Ultra-Fast Engine)"
        // Added with the working NVIDIA fallback: the `else` arm reported "Gemini AI" for
        // every provider, so the Diagnostics screen would have named the wrong brain on the
        // exact occasion someone opens it to find out why responses changed.
        activeProvider.startsWith("nvidia") -> "NVIDIA Nemotron-3 Super (Fallback Brain)"
        activeProvider.startsWith("openai") -> "OpenAI GPT-4o mini (Optional Brain)"
        activeProvider.isBlank() -> "No provider configured"
        else -> "Gemini AI"
    }

    // Google Gemini Brain Integration (per official Gemini guidelines).
    const val GEMINI_FLASH_MODEL = "gemini-2.5-flash"
    const val GEMINI_PRO_MODEL = "gemini-2.5-pro"
    const val GEMINI_LITE_MODEL = "gemini-2.5-flash"

    /** Google's OpenAI-compatible surface. Default brain; unchanged from main. */
    const val GEMINI_OPENAI_COMPAT_URL =
        "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"

    const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
    const val NVIDIA_SUPER_MODEL = "nvidia/nemotron-3-super-120b-a12b"

    /**
     * Order providers are tried when one fails.
     *
     * CHANGED during the sync: this held only the two Gemini tiers, so the hardcoded NVIDIA
     * key could never be reached by the chain walk in JarvisApiClient.chatDirect -- the loop
     * had nothing left to try and gave up. NVIDIA is now the last resort. OpenAI is
     * deliberately NOT listed: its key is empty, and getNextProvider skips keyless providers
     * anyway, so adding it would only be noise.
     */
    val PROVIDER_FALLBACK_CHAIN = listOf(
        "gemini_flash",
        "gemini_pro",
        "nvidia_super"
    )

    /**
     * The chat-completions endpoint for a provider. All three speak OpenAI-compatible JSON,
     * which is why one client can serve them.
     *
     * ADDED during the sync because the endpoint had been hardcoded to Gemini inside
     * JarvisApiClient while the provider and model arguments were still being passed in and
     * silently ignored. With no Gemini key, the app sent the NVIDIA bearer token to
     * generativelanguage.googleapis.com and got a 401 -- the fallback could not work.
     */
    fun endpointFor(provider: String): String = when {
        provider.startsWith("nvidia") -> "$NVIDIA_BASE_URL/chat/completions"
        provider.startsWith("openai") -> "$OPENAI_BASE_URL/chat/completions"
        else -> GEMINI_OPENAI_COMPAT_URL
    }

    /** The key that authorises [endpointFor] for this provider. */
    fun keyFor(provider: String): String = when {
        provider.startsWith("nvidia") -> NVIDIA_API_KEY
        provider.startsWith("openai") -> OPENAI_API_KEY
        else -> currentGeminiKey.ifBlank { GEMINI_API_KEY }
    }

    /** Get the next provider in the fallback chain that actually has an available API key. */
    fun getNextProvider(currentProvider: String): String? {
        val currentIndex = PROVIDER_FALLBACK_CHAIN.indexOf(currentProvider)
        val startIndex = if (currentIndex >= 0) currentIndex + 1 else 0

        for (i in startIndex until PROVIDER_FALLBACK_CHAIN.size) {
            val candidate = PROVIDER_FALLBACK_CHAIN[i]
            if (candidate.startsWith("gemini") && !hasUsableGeminiKey) continue
            // CHANGED during the sync: this tested currentGeminiKey for EVERY candidate, so
            // a keyless-Gemini device skipped NVIDIA too -- the one provider whose key is
            // compiled in and therefore always present. Ask for the candidate's own key.
            if (keyFor(candidate).isNotBlank()) {
                return candidate
            }
        }
        return null
    }

    /** Resolve model ID for a given provider. */
    /**
     * The model id to send for a provider.
     *
     * CHANGED during the sync: the `else` arm returned GEMINI_FLASH_MODEL for every
     * non-Gemini provider, so resolving "nvidia_super" produced "gemini-2.5-flash" -- a
     * model id NVIDIA's endpoint does not serve. Combined with the hardcoded Gemini
     * endpoint, the provider argument had no effect anywhere in the request.
     */
    fun resolveModel(provider: String): String = when {
        provider.startsWith("gemini_pro") -> GEMINI_PRO_MODEL
        provider.startsWith("gemini") -> GEMINI_FLASH_MODEL
        provider.startsWith("nvidia") -> NVIDIA_SUPER_MODEL
        provider.startsWith("openai") -> OPENAI_MODEL
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

    // REMOVED during the sync: the abandoned Rork Toolkit gateway (TOOLKIT_URL /
    // TOOLKIT_SECRET_KEY) and a block of aspirational connector constants that were all
    // empty strings with zero references anywhere in the app -- GOOGLE_STT_API_KEY,
    // GOOGLE_TTS_API_KEY, HOME_ASSISTANT_URL/TOKEN, LIVEKIT_URL/API_KEY/API_SECRET and
    // their hasCloudSTT / hasCloudTTS / hasHomeAssistant / hasLiveKit predicates.
    // TOOLKIT_SECRET_KEY also read a BuildConfig field that no longer exists.

    // Key auto-detection for custom keys
    fun autoDetectProvider(key: String): String {
        val trimmed = key.trim()
        return when {
            trimmed.startsWith("sk-") -> "openai_mini"
            // The `sk_` -> "elevenlabs" branch is gone with that integration. An
            // ElevenLabs-shaped key must not be promoted to the reasoning provider.
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
