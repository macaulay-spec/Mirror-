package com.jarvis.core.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * Risk levels for tool execution safety.
 */
enum class RiskLevel {
    LEVEL_0, // Informational (read time, battery, status) - auto-execute
    LEVEL_1, // Low-risk reversible (search, open app) - auto-execute
    LEVEL_2, // Communication / External action (send message, draft email) - confirm
    LEVEL_3, // Account / Data modification (delete file, modify calendar) - confirm
    LEVEL_4  // Financial / Security / Destructive - always confirm + authenticate
}

/**
 * Living Holographic Core States.
 */
enum class JarvisVisualState(val label: String) {
    IDLE("Ready"),
    WAKING("Initializing"),
    LISTENING("Listening"),
    THINKING("Processing"),
    EXECUTING("Executing"),
    SPEAKING("Transmitting"),
    SUCCESS("Completed"),
    ERROR("Alert"),
    OFFLINE("Offline");

    companion object {
        val PROCESSING = THINKING
    }

    fun accent(): Color = when (this) {
        IDLE -> Color(0x666FD3FF)       // Dim presence (40%)
        WAKING -> Color(0xFF6FD3FF)     // Presence, partial
        LISTENING -> Color(0xFF6FD3FF)  // Bright presence
        THINKING -> Color(0xFFB79CFF)   // Soft violet
        EXECUTING -> Color(0xFFF5B87A)  // Warmth amber
        SPEAKING -> Color(0xFF6FD3FF)   // Full presence
        SUCCESS -> Color(0xFF7EE8B8)    // Soft green
        ERROR -> Color(0xFFFF8A80)      // Warm coral
        OFFLINE -> Color(0xFF4D5B6E)    // Muted text
    }
}

typealias JarvisState = JarvisVisualState

/**
 * Unified Chat Message Item.
 */
data class AssistantMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val toolCall: ToolExecutionRequest? = null,
    val toolResult: ToolExecutionResult? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole {
    USER,
    JARVIS,
    SYSTEM,
    TOOL
}

/**
 * Structured Tool Execution Request.
 */
data class ToolExecutionRequest(
    val toolId: String,
    val name: String,
    val arguments: Map<String, Any?>,
    val riskLevel: RiskLevel,
    val requiresConfirmation: Boolean = riskLevel >= RiskLevel.LEVEL_2
)

/**
 * Result returned after verified tool execution.
 */
data class ToolExecutionResult(
    val toolId: String,
    val success: Boolean,
    val data: Any?,
    val error: String? = null,
    val verificationDetails: String? = null
)
