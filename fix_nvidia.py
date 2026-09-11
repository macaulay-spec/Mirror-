import re

# 1. Update JarvisApiClient.kt
with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "r") as f:
    content = f.read()

endpoint_replacement = """        val endpoint = when {
            provider.startsWith("nvidia") -> "https://integrate.api.nvidia.com/v1/chat/completions"
            provider == "grok" || provider == "xai" -> "https://api.x.ai/v1/chat/completions"
            provider == "openai" -> "https://api.openai.com/v1/chat/completions"
            provider == "groq" -> "https://api.groq.com/openai/v1/chat/completions"
            provider == "openrouter" -> "https://openrouter.ai/api/v1/chat/completions"
            provider == "cerebras" -> "https://api.cerebras.ai/v1/chat/completions"
            provider == "mistral" -> "https://api.mistral.ai/v1/chat/completions"
            else -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
        }"""

model_replacement = """        val requestModel = when {
            provider.startsWith("nvidia") -> "meta/llama-3.1-70b-instruct"
            provider == "grok" || provider == "xai" -> "grok-3-mini"
            provider == "groq" -> "llama3-70b-8192"
            provider == "openrouter" -> "mistralai/mistral-7b-instruct:free"
            provider == "cerebras" -> "llama3.1-70b"
            else -> model
        }"""

content = re.sub(r'        val endpoint = when \{[\s\S]*?\}', endpoint_replacement, content, count=1)
content = re.sub(r'        val requestModel = when \{[\s\S]*?\}', model_replacement, content, count=1)

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "w") as f:
    f.write(content)

# 2. Update ApiConfig.kt
with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "r") as f:
    content2 = f.read()

fallback_chain_replacement = """    val PROVIDER_FALLBACK_CHAIN = listOf(
        "nvidia_super",
        "grok",
        "gemini_flash",
        "gemini_pro",
        "openai",
        "anthropic",
        "groq",
        "openrouter",
        "cerebras",
        "mistral"
    )"""

content2 = re.sub(r'    val PROVIDER_FALLBACK_CHAIN = listOf\([^)]+\)', fallback_chain_replacement, content2)

get_next_provider_replacement = """    fun getNextProvider(currentProvider: String): String? {
        val currentIndex = PROVIDER_FALLBACK_CHAIN.indexOf(currentProvider)
        val startIndex = if (currentIndex >= 0) currentIndex + 1 else 0
        for (i in startIndex until PROVIDER_FALLBACK_CHAIN.size) {
            val candidate = PROVIDER_FALLBACK_CHAIN[i]
            if (candidate.startsWith("gemini") && !hasUsableGeminiKey) continue
            val candidateKey = when {
                candidate.startsWith("gemini") -> currentGeminiKey
                candidate.startsWith("nvidia") -> NVIDIA_API_KEY
                candidate.startsWith("openai") -> OPENAI_API_KEY
                else -> currentApiKey
            }
            if (candidateKey.isNotBlank()) {
                return candidate
            }
        }
        return null
    }"""
content2 = re.sub(r'    fun getNextProvider\(currentProvider: String\): String\? \{[\s\S]*?return null\n    \}', get_next_provider_replacement, content2)

active_provider_replacement = """    val activeProvider: String
        get() {
            val key = activeApiKey
            return when {
                key.startsWith("nvapi-") -> "nvidia_super"
                key.startsWith("AQ.") -> "grok"
                key.startsWith("AIzaSy") -> "gemini_flash"
                key.startsWith("sk-ant-") -> "anthropic"
                key.startsWith("gsk_") -> "groq"
                key.startsWith("sk-or-") -> "openrouter"
                key.startsWith("csk-") -> "cerebras"
                key.startsWith("mx-") -> "mistral"
                key.startsWith("sk-") -> "openai"
                key.isNotBlank() -> "gemini_flash"
                else -> "nvidia_super"
            }
        }"""
content2 = re.sub(r'    val activeProvider: String\n        get\(\) \{[\s\S]*?\}', active_provider_replacement, content2, count=1)

active_api_key_replacement = """    val activeApiKey: String
        get() {
            val gKey = currentGeminiKey
            if (gKey.isNotBlank() && gKey != "AQ.Ab8RN6JpFdEy5KZ06i3LzgQRv10lqEYMAzIncMAdEnb4dLNP7Q") return gKey
            val custom = customApiKey?.trim()
            if (!custom.isNullOrBlank()) return custom
            return NVIDIA_API_KEY.ifBlank { gKey }
        }"""
content2 = re.sub(r'    val activeApiKey: String\n        get\(\) \{[\s\S]*?\}', active_api_key_replacement, content2, count=1)

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "w") as f:
    f.write(content2)

print("done")
