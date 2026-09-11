import re

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "r") as f:
    content = f.read()

# Replace the auth logic for OpenAI compat
auth_replacement = """        val requestBuilder = Request.Builder()
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .url(endpoint)

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }
"""

start_idx = content.find("        val requestBuilder = Request.Builder()")
end_idx = content.find("        val request = requestBuilder.build()", start_idx)

if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + auth_replacement + content[end_idx:]

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "w") as f:
    f.write(content)
print("done")
