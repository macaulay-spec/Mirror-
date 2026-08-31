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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
