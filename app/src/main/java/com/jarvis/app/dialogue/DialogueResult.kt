package com.jarvis.app.dialogue

sealed interface DialogueResult {
    data class Reply(val message: String) : DialogueResult
    data class ToolCall(val tool: String, val arguments: Map<String, Any?>) : DialogueResult
    data class Ask(val slot: String, val question: String, val options: List<String>? = null) : DialogueResult
    data class Confirm(
        val tool: String,
        val arguments: Map<String, Any?>,
        val prompt: String,
        val risk: Int = 2
    ) : DialogueResult
}
