package com.jarvis.app.config

/**
 * Backend configuration for the JARVIS Cloudflare Worker proxy.
 *
 * All API keys are stored server-side as Worker secrets.
 * The app never sees or embeds any API keys.
 *
 * Setup:
 *   1. Deploy the Cloudflare Worker (backend/ directory)
 *   2. Set secrets: `npx wrangler secret put GEMINI_API_KEY` etc.
 *   3. Set the worker URL below (or in BuildConfig via local.properties)
 */
object BackendConfig {

    /**
     * The deployed Cloudflare Worker URL.
     * Replace with your actual worker URL after deploy.
     *
     * Format: https://jarvis-proxy.<your-subdomain>.workers.dev
     */
    const val WORKER_URL = "https://jarvis-proxy.YOUR_SUBDOMAIN.workers.dev"

    /** Whether to use the backend proxy (true) or direct API calls (false). */
    const val USE_BACKEND = true

    // ── Endpoints ──────────────────────────────────────────────────────
    const val LLM_CHAT_ENDPOINT = "/api/llm/chat"
    const val TTS_SPEAK_ENDPOINT = "/api/tts/speak"
    const val TTS_VOICES_ENDPOINT = "/api/tts/voices"
    const val STT_TRANSCRIBE_ENDPOINT = "/api/stt/transcribe"
    const val HEALTH_ENDPOINT = "/api/health"
}
