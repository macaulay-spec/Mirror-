with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "r") as f:
    content = f.read()

import re

fallback_chain_replacement = """    val PROVIDER_FALLBACK_CHAIN = listOf(
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

content = re.sub(r'    val PROVIDER_FALLBACK_CHAIN = listOf\(\n        "gemini_flash",\n        "gemini_pro"\n    \)', fallback_chain_replacement, content)

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "w") as f:
    f.write(content)
print("done")
