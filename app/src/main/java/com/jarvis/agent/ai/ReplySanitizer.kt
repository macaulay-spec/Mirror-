package com.jarvis.agent.ai

import org.json.JSONObject

object ReplySanitizer {
    fun sanitize(raw: String): String {
        if (raw.isBlank()) return "I'm standing by."

        var text = raw.trim()

        // 1. Handle markdown code blocks
        if (text.contains("```")) {
            val stripped = text.substringAfter("```json")
                .substringAfter("```")
                .substringBefore("```")
                .trim()
            if (stripped.isNotBlank()) {
                text = stripped
            }
        }

        // 2. Parse JSON if present
        if ((text.startsWith("{") && text.endsWith("}")) || (text.contains("\"action\"") && text.contains("{"))) {
            try {
                val jsonStart = text.indexOf('{')
                val jsonEnd = text.lastIndexOf('}')
                if (jsonStart != -1 && jsonEnd > jsonStart) {
                    val jsonStr = text.substring(jsonStart, jsonEnd + 1)
                    val json = JSONObject(jsonStr)
                    if (json.has("message")) {
                        val msg = json.optString("message")
                        if (msg.isNotBlank()) return msg
                    }
                    if (json.has("reply")) {
                        val msg = json.optString("reply")
                        if (msg.isNotBlank()) return msg
                    }
                    if (json.has("expectedResult")) {
                        val res = json.optString("expectedResult")
                        if (res.isNotBlank()) return res
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Friendly error messages
        if (text.contains("Agent failure") || text.contains("Neural gateway is currently offline") || text.contains("quota") || text.contains("resource_exhausted")) {
            return "I'm currently running in local mode. Cloud chat is offline, but device controls are fully active."
        }
        if (text.contains("UnknownHostException") || text.contains("ConnectException") || text.contains("SocketTimeoutException")) {
            return "I couldn't reach the cloud network right now. Local device controls remain active."
        }
        if (text.contains("Tool with ID") && text.contains("is not registered")) {
            val toolId = text.substringAfter("Tool with ID '").substringBefore("'")
            return "The requested feature ($toolId) is not supported on this system."
        }

        // Clean up quotes or braces artifacts if raw JSON leaked
        if (text.startsWith("{") && text.endsWith("}")) {
            text = text.removeSurrounding("{", "}").trim()
        }

        return text.trim()
    }
}
