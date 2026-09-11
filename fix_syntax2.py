import re

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "r") as f:
    content = f.read()

new_gemini_keys = r"""    val geminiKeys: List<String>
        get() {
            val list = mutableListOf<String>()
            val nonGeminiPrefixes = listOf("sk-", "xai-", "nvapi-", "gsk_", "csk-", "mx-", "AQ.")
            
            fun addTokens(raw: String) {
                val tokens = raw.split(',', ';', '\n', '\r')
                    .map { it.trim() }
                    .filter { it.isNotBlank() && nonGeminiPrefixes.none { prefix -> it.startsWith(prefix) } }
                list.addAll(tokens)
            }

            customApiKey?.let { addTokens(it) }
            if (GEMINI_API_KEY.isNotBlank()) {
                addTokens(GEMINI_API_KEY)
            }
            return list.distinct()
        }"""

# Use string replace for safety instead of regex, targeting the exact block
start_idx = content.find("val geminiKeys: List<String>")
end_idx = content.find("val currentGeminiKey: String")

content = content[:start_idx] + new_gemini_keys + "\n\n    " + content[end_idx:]

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "w") as f:
    f.write(content)

print("done")
