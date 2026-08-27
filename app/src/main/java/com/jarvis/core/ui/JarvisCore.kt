package com.jarvis.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisCore(
    state: JarvisVisualState,
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier,
    size: Dp = 320.dp,
    onClick: (() -> Unit)? = null
) {
    val accentColor = state.accent()

    val transition = rememberInfiniteTransition(label = "jarvis_core_anim")

    val baseRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "base_rotation"
    )

    val counterRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter_rotation"
    )

    val fastSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fast_spin"
    )

    val pulseScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 2f * 0.88f

            drawAmbientHalo(center, baseRadius, accentColor, pulseScale, audioLevel)
            drawOuterHudRing(center, baseRadius * 0.98f, accentColor, baseRotation)
            drawOrbitalRings(center, baseRadius * 0.82f, accentColor, counterRotation, baseRotation)
            drawSphericalWireframe(center, baseRadius * 0.65f, accentColor, fastSpin, pulseScale)
            drawQuantumCore(center, baseRadius * 0.32f, accentColor, state, audioLevel, pulseScale)
            drawParticleCloud(center, baseRadius, accentColor, fastSpin, state, audioLevel)
        }
    }
}

private fun DrawScope.drawAmbientHalo(
    center: Offset,
    radius: Float,
    accent: Color,
    pulse: Float,
    audioLevel: Float
) {
    val dynamicRadius = radius * pulse * (1f + audioLevel.coerceIn(0f, 1f) * 0.25f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                accent.copy(alpha = 0.35f),
                accent.copy(alpha = 0.12f),
                Color.Transparent
            ),
            center = center,
            radius = dynamicRadius
        ),
        radius = dynamicRadius,
        center = center
    )
}

private fun DrawScope.drawOuterHudRing(
    center: Offset,
    radius: Float,
    accent: Color,
    rotation: Float
) {
    rotate(rotation, pivot = center) {
        drawCircle(
            color = accent.copy(alpha = 0.25f),
            radius = radius,
            center = center,
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 25f, 10f, 25f), 0f)
            )
        )
        val tickCount = 24
        for (i in 0 until tickCount) {
            val angle = (i * (360f / tickCount)) * (PI.toFloat() / 180f)
            val outerX = center.x + cos(angle) * radius
            val outerY = center.y + sin(angle) * radius
            val innerX = center.x + cos(angle) * (radius - 8.dp.toPx())
            val innerY = center.y + sin(angle) * (radius - 8.dp.toPx())

            drawLine(
                color = if (i % 4 == 0) accent.copy(alpha = 0.8f) else accent.copy(alpha = 0.35f),
                start = Offset(innerX, innerY),
                end = Offset(outerX, outerY),
                strokeWidth = if (i % 4 == 0) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawOrbitalRings(
    center: Offset,
    radius: Float,
    accent: Color,
    rot1: Float,
    rot2: Float
) {
    rotate(rot1, pivot = center) {
        drawOval(
            color = accent.copy(alpha = 0.45f),
            topLeft = Offset(center.x - radius, center.y - radius * 0.38f),
            size = Size(radius * 2f, radius * 0.76f),
            style = Stroke(
                width = 1.8.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(60f, 30f), 0f)
            )
        )
    }

    rotate(rot2 + 60f, pivot = center) {
        drawOval(
            color = JarvisColors.TealSecondary.copy(alpha = 0.4f),
            topLeft = Offset(center.x - radius * 0.9f, center.y - radius * 0.32f),
            size = Size(radius * 1.8f, radius * 0.64f),
            style = Stroke(
                width = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 15f), 0f)
            )
        )
    }
}

private fun DrawScope.drawSphericalWireframe(
    center: Offset,
    radius: Float,
    accent: Color,
    spin: Float,
    pulse: Float
) {
    val dynRadius = radius * pulse

    rotate(spin, pivot = center) {
        for (step in 1..3) {
            val scale = step / 3.5f
            drawOval(
                color = accent.copy(alpha = 0.25f),
                topLeft = Offset(center.x - dynRadius, center.y - dynRadius * scale),
                size = Size(dynRadius * 2f, dynRadius * scale * 2f),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        for (angle in listOf(0f, 45f, 90f, 135f)) {
            rotate(angle, pivot = center) {
                drawOval(
                    color = accent.copy(alpha = 0.25f),
                    topLeft = Offset(center.x - dynRadius * 0.35f, center.y - dynRadius),
                    size = Size(dynRadius * 0.7f, dynRadius * 2f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

private fun DrawScope.drawQuantumCore(
    center: Offset,
    coreRadius: Float,
    accent: Color,
    state: JarvisVisualState,
    audioLevel: Float,
    pulse: Float
) {
    val dynamicRadius = coreRadius * (1f + (audioLevel.coerceIn(0f, 1f) * 0.4f) + (pulse * 0.08f))

    drawCircle(
        brush = Brush.radialGradient(
            listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.4f), Color.Transparent),
            center = center,
            radius = dynamicRadius * 1.5f
        ),
        radius = dynamicRadius * 1.5f,
        center = center
    )

    drawCircle(
        color = accent.copy(alpha = 0.9f),
        radius = dynamicRadius * 0.75f,
        center = center
    )

    drawCircle(
        color = Color.White.copy(alpha = 0.95f),
        radius = dynamicRadius * 0.45f,
        center = center
    )

    drawCircle(
        color = JarvisColors.VoidBlack,
        radius = dynamicRadius * 0.28f,
        center = center
    )

    drawCircle(
        color = accent,
        radius = dynamicRadius * 0.12f,
        center = center
    )
}

private fun DrawScope.drawParticleCloud(
    center: Offset,
    baseRadius: Float,
    accent: Color,
    spin: Float,
    state: JarvisVisualState,
    audioLevel: Float
) {
    val count = when (state) {
        JarvisVisualState.THINKING -> 36
        JarvisVisualState.LISTENING, JarvisVisualState.SPEAKING -> 28
        else -> 18
    }

    for (i in 0 until count) {
        val phi = (i * 137.508f + spin * 0.4f) * (PI.toFloat() / 180f)
        val spread = baseRadius * (0.55f + ((i % 7) * 0.08f)) + (audioLevel * 15f)
        val px = center.x + cos(phi) * spread
        val py = center.y + sin(phi) * spread
        val pSize = 1.2f + (i % 4) * 0.8f

        drawCircle(
            color = if (i % 3 == 0) Color.White.copy(alpha = 0.8f) else accent.copy(alpha = 0.6f),
            radius = pSize,
            center = Offset(px, py)
        )
    }
}
