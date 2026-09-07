package com.jarvis.app.config

/**
 * LegacyProviderCatalog — Preserves configurations for non-Gemini providers
 * (NVIDIA NIM, OpenAI, Anthropic) for reference, keeping them out of the active
 * runtime pipeline to eliminate timeout cascades and latency stalls.
 */
object LegacyProviderCatalog {
    const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
    const val NVIDIA_GLM_MODEL = "z-hipu/glm-5.2"
    const val NVIDIA_NEMOTRON_MODEL = "nvidia/nemotron-3-super"
    const val NVIDIA_MISTRAL_MODEL = "mistralai/mistral-nemotron"
    const val NVIDIA_LLAMA_MODEL = "meta/llama-4-maverick"
    const val OPENAI_BASE_URL = "https://api.openai.com/v1"
    const val OPENAI_MINI_MODEL = "gpt-4o-mini"
}
