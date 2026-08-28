package com.jarvis.agent.ai

object ReplySanitizer {
    fun sanitize(text: String): String {
        return text.replace(Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL), "").trim()
    }
}
