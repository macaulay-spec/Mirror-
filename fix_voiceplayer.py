import re

with open("app/src/main/java/com/jarvis/app/voice/GeminiVoicePlayer.kt", "r") as f:
    content = f.read()

content = content.replace(
    'builder.header("Authorization", "Bearer $k")',
    'builder.header("x-goog-api-key", k)'
)

with open("app/src/main/java/com/jarvis/app/voice/GeminiVoicePlayer.kt", "w") as f:
    f.write(content)
print("done")
