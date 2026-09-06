package com.jarvis.feature.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.JarvisCore
import com.jarvis.core.model.ToolExecutionResult

/**
 * DeviceActionCards - Displays tool execution results as visual cards.
 *
 * This component shows the results of JARVIS actions in a card-based layout.
 * Each card represents a completed action with its status and details.
 *
 * Design requirements from Mistral.md:
 * - Shows action results as cards
 * - Uses JARVIS visual language (glass cards, cyan/blue glow)
 * - Displays success/failure states clearly
 * - Shows action description, status, and result
 */

/**
 * Data class for displaying an action result card.
 */
data class DeviceActionCardData(
    val title: String,
    val description: String,
    val status: ActionCardStatus,
    val timestamp: String? = null,
    val details: String? = null
)

enum class ActionCardStatus {
    SUCCESS,
    FAILED,
    PENDING,
    EXECUTING
}

/**
 * Main composable for displaying a list of action result cards.
 */
@Composable
fun DeviceActionCards(
    results: List<DeviceActionCardData>,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) {
        EmptyState("No actions yet")
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(results) { action ->
            ActionCard(action = action)
        }
    }
}

/**
 * Individual action card showing a single action result.
 */
@Composable
fun ActionCard(action: DeviceActionCardData) {
    val statusColor = when (action.status) {
        ActionCardStatus.SUCCESS -> JarvisColors.StateSuccess
        ActionCardStatus.FAILED -> JarvisColors.StateError
        ActionCardStatus.PENDING -> JarvisColors.TextMuted
        ActionCardStatus.EXECUTING -> JarvisColors.StarkGold
    }

    val statusIcon = when (action.status) {
        ActionCardStatus.SUCCESS -> "✓"
        ActionCardStatus.FAILED -> "✕"
        ActionCardStatus.PENDING -> "○"
        ActionCardStatus.EXECUTING -> "⚡"
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = JarvisColors.SurfaceGlassElevated,
        borderColor = statusColor.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator glyph with subtle glow
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusIcon,
                    color = statusColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DIRECTIVE EXECUTED",
                        color = statusColor.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = action.title,
                    color = JarvisColors.TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                )

                if (action.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = action.description,
                        color = JarvisColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when (action.status) {
                        ActionCardStatus.SUCCESS -> "CONFIRMED"
                        ActionCardStatus.FAILED -> "FAILED"
                        ActionCardStatus.PENDING -> "STANDBY"
                        ActionCardStatus.EXECUTING -> "ACTIVE"
                    },
                    color = statusColor,
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Empty state for when there are no actions to display.
 */
@Composable
fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        JarvisCore(
            state = JarvisVisualState.IDLE,
            size = 48.dp
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = message,
            color = JarvisColors.TextMuted,
            fontSize = 14.sp
        )
    }
}

/**
 * Converts a ToolExecutionResult to DeviceActionCardData.
 */
fun ToolExecutionResult.toActionCardData(): DeviceActionCardData {
    return DeviceActionCardData(
        title = this.toolId,
        description = this.verificationDetails ?: this.error ?: "",
        status = if (this.success) ActionCardStatus.SUCCESS else ActionCardStatus.FAILED,
        details = this.error
    )
}

/**
 * Creates a list of action cards from multiple tool results.
 */
fun List<ToolExecutionResult>.toActionCards(): List<DeviceActionCardData> {
    return this.map { it.toActionCardData() }
}
