import re

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "r") as f:
    content = f.read()

# Remove AQ -> grok mapping
content = content.replace('key.startsWith("AQ.") -> "grok"', 'key.startsWith("AQ.") -> "gemini_flash"')

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "r") as f:
    content = f.read()

# Update AIzaSy check to include AQ.
content = content.replace('if (apiKey.startsWith("AIzaSy")) {', 'if (apiKey.startsWith("AIzaSy") || apiKey.startsWith("AQ.")) {')

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "w") as f:
    f.write(content)

print("done")
