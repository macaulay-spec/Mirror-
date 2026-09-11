import re

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "r") as f:
    content = f.read()

new_gemini_keys = """    val geminiKeys: List<String>
        get() {
            val list = mutableListOf<String>()
            val nonGeminiPrefixes = listOf("sk-", "xai-", "nvapi-", "gsk_", "csk-", "mx-")
            
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

content = re.sub(
    r"    val geminiKeys: List<String>\n        get\(\) \{\n            val list = mutableListOf<String>\(\)[\s\S]*?return list\.distinct\(\)\n        \}",
    new_gemini_keys,
    content
)

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "w") as f:
    f.write(content)

print("done")
