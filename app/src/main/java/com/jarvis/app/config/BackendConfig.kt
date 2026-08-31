package com.jarvis.app.config

/**
 * Backend configuration for JARVIS — Convex-powered.
 *
 * All API keys are stored as Convex environment variables (secrets).
 * The app never sees or embeds any API keys.
 *
 * Setup:
 *   1. Create a Convex project: `npx convex init`
 *   2. Set secrets: `npx convex env set GEMINI_API_KEY <key>` etc.
 *   3. Deploy: `npx convex deploy`
 *   4. Set the deployment URL below (or in BuildConfig via local.properties)
 *
 * Convex HTTP action URLs follow this pattern:
 *   https://<deployment-id>.convex.site/<path>
 */
object BackendConfig {

    /**
     * Your Convex deployment URL.
     * Replace with your actual deployment URL after `npx convex deploy`.
     *
     * Format: https://<your-deployment>.convex.site
     */
    const val WORKER_URL = "https://YOUR_DEPLOYMENT.convex.site"

    /** Whether to use the backend proxy (true) or direct API calls (false). */
    const val USE_BACKEND = true

    // ── Convex HTTP Action Endpoints ─────────────────────────────────────
    const val LLM_CHAT_ENDPOINT = "/api/llm/chat"
    const val TTS_SPEAK_ENDPOINT = "/api/tts/speak"
    const val TTS_VOICES_ENDPOINT = "/api/tts/voices"
    const val STT_TRANSCRIBE_ENDPOINT = "/api/stt/transcribe"
    const val PREFERENCES_ENDPOINT = "/api/preferences"
    const val HEALTH_ENDPOINT = "/api/health"
}
