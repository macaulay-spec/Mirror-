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
        IDLE -> Color(0xFF00E5FF)       // Laser Cyan
        WAKING -> Color(0xFF5CEBFF)     // Bright Cyan
        LISTENING -> Color(0xFF00F5D4)  // Emerald Pulse
        THINKING -> Color(0xFF9D4EDD)   // Purple Sync
        EXECUTING -> Color(0xFFFFB703)  // Solar Gold
        SPEAKING -> Color(0xFF00B4D8)   // Deep Aqua
        SUCCESS -> Color(0xFF00F5D4)    // Emerald Success
        ERROR -> Color(0xFFFF3366)      // Crimson Alert
        OFFLINE -> Color(0xFF4D657C)    // Muted Gray
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
