package com.jarvis.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors

/**
 * Glass card — translucent panel with soft inner highlight, not a flat border.
 *
 * Design spec: panels are translucent (--bg-glass) with soft definition from
 * a 1px inner highlight (top edge) plus soft shadow. Color appears in a panel
 * because something inside it is that color, not because the container is
 * outlined in it.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = JarvisColors.SurfaceGlass,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.15f)
            )
            .clip(shape)
            .background(backgroundColor)
            // Soft inner highlight — top edge only, 1px
            .border(
                border = BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                ),
                shape = shape
            ),
        content = content
    )
}

/**
 * Action pill — rounded chip with subtle glass background.
 * Not flat black with cyan border.
 */
@Composable
fun ActionPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = JarvisColors.Presence,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(JarvisColors.SurfaceGlass)
            .border(
                BorderStroke(0.8.dp, JarvisColors.Hairline),
                RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = JarvisColors.TextPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * Status beacon — small pulsing dot + label.
 * Uses presence color, not monospace all-caps.
 */
@Composable
fun StatusBeacon(
    state: JarvisVisualState,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val accent = state.orbColor()
    val transition = rememberInfiniteTransition(label = "beacon")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = alpha))
        )
        if (label != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = accent,
                fontSize = 12.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

/**
 * Divider — subtle horizontal line, not holographic cyan.
 */
@Composable
fun HolographicDivider(
    modifier: Modifier = Modifier,
    color: Color = JarvisColors.Hairline
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, color, color, Color.Transparent)
                )
            )
    )
}

/**
 * Terminal badge — monospace badge for technical values only.
 * Not used for regular UI labels.
 */
@Composable
fun TerminalBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = JarvisColors.Presence
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = color.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * Metric bar — progress indicator with label.
 */
@Composable
fun MetricBar(
    label: String,
    value: Float, // 0f..1f
    modifier: Modifier = Modifier,
    accentColor: Color = JarvisColors.Presence
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = JarvisColors.TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Default,
            modifier = Modifier.width(72.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(JarvisColors.SurfaceCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor.copy(alpha = 0.7f))
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${(value * 100).toInt()}%",
            color = JarvisColors.TextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Default,
            modifier = Modifier.width(36.dp)
        )
    }
}

// ── v3 Command Deck components ─────────────────────────────────────────────

/**
 * HUD background — true black with the faint technical grid and a soft cyan
 * glow bleeding from the top edge, exactly as in the approved mockups.
 * Draw behind any screen content.
 */
@Composable
fun HudBackground(
    modifier: Modifier = Modifier,
    glowColor: Color = JarvisColors.Presence
) {
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Top-edge cyan bleed
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    glowColor.copy(alpha = 0.10f),
                    glowColor.copy(alpha = 0.03f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.35f
            ),
            size = size
        )

        // Faint technical grid: 32dp-ish cells, barely visible
        val cell = 34.dp.toPx()
        val gridColor = JarvisColors.GridLine
        var x = 0f
        while (x < w) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            x += cell
        }
        var y = 0f
        while (y < h) {
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            y += cell
        }
    }
}

/**
 * The glowing cyan mic button from the mockups — a circular glass button with
 * a neon halo. Used in the chat input pill and the voice stage.
 */
@Composable
fun GlowMicButton(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    accent: Color = JarvisColors.Presence
) {
    val pulse = rememberInfiniteTransition(label = "glowMic")
    val glowAlpha by pulse.animateFloat(
        initialValue = if (isListening) 0.35f else 0.18f,
        targetValue = if (isListening) 0.60f else 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Neon halo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = glowAlpha), Color.Transparent)
                    )
                )
        )
        // Button body
        Box(
            modifier = Modifier
                .size(size * 0.72f)
                .clip(CircleShape)
                .background(if (isListening) accent.copy(alpha = 0.22f) else JarvisColors.SurfaceGlassElevated)
                .border(1.dp, accent.copy(alpha = if (isListening) 0.9f else 0.55f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Microphone",
                tint = accent,
                modifier = Modifier.size(size * 0.36f)
            )
        }
    }
}

/**
 * "JARVIS is thinking" indicator — three glowing dots breathing in sequence,
 * as in the mockup chat stream.
 */
@Composable
fun ThinkingDots(
    modifier: Modifier = Modifier,
    accent: Color = JarvisColors.Presence
) {
    val transition = rememberInfiniteTransition(label = "thinkingDots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val near = (phase - i).let { it * it }
            val alpha = (1f - (near / 4f)).coerceIn(0.25f, 1f)
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = alpha))
            )
        }
    }
}

/**
 * Blinking cursor shown at the end of a streaming reply.
 */
@Composable
fun StreamingCursor(
    modifier: Modifier = Modifier,
    accent: Color = JarvisColors.Presence
) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    Box(
        modifier = modifier
            .width(2.dp)
            .height(15.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(accent.copy(alpha = alpha))
    )
}
