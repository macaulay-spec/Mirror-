package com.jarvis.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = JarvisColors.SurfaceGlass,
    borderColor: Color = JarvisColors.BorderCyan,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = backgroundColor.alpha * 0.4f)
                    )
                )
            )
            .border(
                border = BorderStroke(borderWidth, borderColor),
                shape = shape
            ),
        content = content
    )
}

@Composable
fun ActionPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = JarvisColors.CyanPrimary,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(JarColorsInternal.PillBg)
            .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)), CircleShape)
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
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun StatusBeacon(
    state: JarvisVisualState,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val accent = state.accent()
    val transition = rememberInfiniteTransition(label = "beacon_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_alpha"
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
                text = label.uppercase(),
                color = accent,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun HolographicDivider(
    modifier: Modifier = Modifier,
    color: Color = JarvisColors.CyanPrimary.copy(alpha = 0.2f)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        color,
                        color,
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
fun TerminalBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = JarvisColors.CyanPrimary
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .border(BorderStroke(0.8.dp, color.copy(alpha = 0.4f)), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun MetricBar(
    label: String,
    value: Float, // 0f..1f
    modifier: Modifier = Modifier,
    accentColor: Color = JarvisColors.CyanPrimary
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            color = JarvisColors.TextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(72.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape)
                .background(JarvisColors.SurfaceCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                accentColor.copy(alpha = 0.7f),
                                accentColor
                            )
                        )
                    )
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${(value * 100).toInt()}%",
            color = JarvisColors.TextPrimary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp)
        )
    }
}

private object JarColorsInternal {
    val PillBg = Color(0x1400E5FF)
}
