import re

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "r") as f:
    content = f.read()

# Remove BackendConfig imports
content = re.sub(r"import com\.jarvis\.app\.config\.BackendConfig\n", "", content)

# Remove BackendConfig logic in chat
chat_logic = """        if (BackendConfig.isBackendReady) {
            chatViaProxy(systemPrompt, history, userMessage, provider, model)
        } else {
            chatDirect(systemPrompt, history, userMessage, provider, model, allowTools)
        }"""
content = content.replace(chat_logic, "        chatDirect(systemPrompt, history, userMessage, provider, model, allowTools)")

# Remove BackendConfig logic in tryStreamWithProvider
stream_logic = """            if (BackendConfig.isBackendReady || providerToTry == "anthropic") {
                return fallbackBlocking()
            }"""
content = content.replace(stream_logic, """            if (providerToTry == "anthropic") {
                return fallbackBlocking()
            }""")

# Remove proxy methods
content = re.sub(r"    private suspend fun chatViaProxy\([\s\S]*?\}", "", content)

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "w") as f:
    f.write(content)

print("done")
