import re

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "r") as f:
    content = f.read()

replacement = """        val requestBuilder = Request.Builder()
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
        
        if (apiKey.startsWith("AIzaSy") || apiKey.startsWith("AQ.")) {
            requestBuilder.url("$endpoint?key=$apiKey")
        } else {
            requestBuilder.url(endpoint)
            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
        }"""

content = re.sub(r'        val requestBuilder = Request\.Builder\(\)\n            \.post\(payload\.toString\(\)\.toRequestBody\("application/json"\.toMediaType\(\)\)\)\n            \.url\(endpoint\)\n\n        if \(apiKey\.isNotBlank\(\)\) \{\n            requestBuilder\.header\("Authorization", "Bearer \$apiKey"\)\n        \}', replacement, content)

with open("app/src/main/java/com/jarvis/app/assistant/JarvisApiClient.kt", "w") as f:
    f.write(content)

print("done")
