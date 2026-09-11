import re

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "r") as f:
    content = f.read()

fallback_chain_replacement = """    val PROVIDER_FALLBACK_CHAIN = listOf(
        "gemini_flash",
        "gemini_pro",
        "nvidia_super",
        "grok",
        "openai",
        "anthropic",
        "groq",
        "openrouter",
        "cerebras",
        "mistral"
    )"""
content = re.sub(r'    val PROVIDER_FALLBACK_CHAIN = listOf\([^)]+\)', fallback_chain_replacement, content)

active_provider_replacement = """    val activeProvider: String
        get() {
            val key = activeApiKey
            return when {
                key.startsWith("AIzaSy") -> "gemini_flash"
                key.startsWith("AQ.") -> "grok"
                key.startsWith("nvapi-") -> "nvidia_super"
                key.startsWith("sk-ant-") -> "anthropic"
                key.startsWith("gsk_") -> "groq"
                key.startsWith("sk-or-") -> "openrouter"
                key.startsWith("csk-") -> "cerebras"
                key.startsWith("mx-") -> "mistral"
                key.startsWith("sk-") -> "openai"
                key.isNotBlank() -> "gemini_flash"
                else -> "gemini_flash"
            }
        }"""
content = re.sub(r'    val activeProvider: String\n        get\(\) \{[\s\S]*?\}', active_provider_replacement, content, count=1)

active_api_key_replacement = """    val activeApiKey: String
        get() {
            val gKey = currentGeminiKey
            if (gKey.isNotBlank()) return gKey
            val custom = customApiKey?.trim()
            if (!custom.isNullOrBlank()) return custom
            return GEMINI_API_KEY
        }"""
content = re.sub(r'    val activeApiKey: String\n        get\(\) \{[\s\S]*?\}', active_api_key_replacement, content, count=1)

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "w") as f:
    f.write(content)

print("done")
