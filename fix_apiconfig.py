with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "r") as f:
    content = f.read()

import re

# Find activeProvider
active_provider_replacement = """    val activeProvider: String
        get() {
            val key = activeApiKey
            return when {
                key.startsWith("AQ.") -> "grok"
                key.startsWith("AIzaSy") -> "gemini_flash"
                key.startsWith("sk-ant-") -> "anthropic"
                key.startsWith("gsk_") -> "groq"
                key.startsWith("sk-or-") -> "openrouter"
                key.startsWith("csk-") -> "cerebras"
                key.startsWith("mx-") -> "mistral"
                key.startsWith("sk-") -> "openai"
                key.isNotBlank() -> "gemini_flash"
                else -> "none"
            }
        }"""

content = re.sub(r'    val activeProvider: String\n        get\(\) \{\n            // Powered exclusively by Gemini\n            return "gemini_flash"\n        \}', active_provider_replacement, content)

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "w") as f:
    f.write(content)
print("done")
