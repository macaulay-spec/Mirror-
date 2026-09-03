package com.jarvis.core.ui

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/**
 * JARVIS Core — ONE identity, everywhere (hero, mini orb, header emblem).
 *
 * v3 "carbon copy" geometry, straight from the approved mockups:
 *  - deep black glass field with a soft neon halo
 *  - a small luminous core
 *  - a thin inner ring (cyan)
 *  - a RADIAL TICK ring (the HUD dial marks)
 *  - an outer ring of fine ARC SEGMENTS that slowly rotates
 *  - TWO TINY ORBITING LIGHT DOTS (one cyan, one electric blue)
 *
 * State behaviour:
 *  - Idle: slow breathing, dial drifts, everything calm
 *  - Listening: outer ring becomes a live circular equalizer (mic amplitude)
 *  - Thinking: electric-blue tint + inward particle gather
 *  - Executing: fast electric-blue arc sweeping the outer ring
 *  - Speaking: core pulses with TTS amplitude
 *  - Error: sharp contraction + coral flash
 */
@Composable
fun JarvisCore(
    state: JarvisVisualState,
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier,
    size: Dp = 320.dp,
    onClick: (() -> Unit)? = null
) {
    val accentColor = state.orbColor()
    val secondaryColor = if (
        state == JarvisVisualState.ERROR ||
        state == JarvisVisualState.SUCCESS ||
        state == JarvisVisualState.OFFLINE
    ) accentColor else JarvisColors.ElectricBlue

    val transition = rememberInfiniteTransition(label = "orb")

    // Idle breathing: 96% → 104%, 4200ms ease-in-out
    val breathe by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // Thinking particle drift
    val particleDrift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleDrift"
    )

    // Outer dial slow rotation (always alive, barely perceptible)
    val dialRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dialRotation"
    )

    // Executing arc sweep (fast)
    val arcRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arcRotation"
    )

    // Orbiting dots (slow, opposite-feeling phases)
    val orbitDrift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitDrift"
    )

    // Smooth state transitions
    val targetBrightness = when (state) {
        JarvisVisualState.IDLE -> 0.60f
        JarvisVisualState.WAKING -> 0.75f
        JarvisVisualState.LISTENING -> 1.0f
        JarvisVisualState.THINKING -> 0.90f
        JarvisVisualState.EXECUTING -> 0.95f
        JarvisVisualState.SPEAKING -> 0.95f
        JarvisVisualState.SUCCESS -> 1.0f
        JarvisVisualState.ERROR -> 0.90f
        JarvisVisualState.OFFLINE -> 0.30f
    }
    val brightness by animateFloatAsState(
        targetValue = targetBrightness,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "brightness"
    )

    val baseScale = when (state) {
        JarvisVisualState.IDLE, JarvisVisualState.OFFLINE -> breathe
        JarvisVisualState.ERROR -> 0.90f
        JarvisVisualState.SUCCESS -> 1.06f
        else -> 1.0f
    }

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
            val canvasSize = this.size
            val cx = canvasSize.width / 2f
            val cy = canvasSize.height / 2f
            val center = Offset(cx, cy)
            val baseRadius = canvasSize.minDimension / 2f

            val coreRadius = baseRadius * 0.20f
            val ringInner = baseRadius * 0.42f
            val ringTicks = baseRadius * 0.64f
            val ringDial = baseRadius * 0.88f

            // ── Neon halo ──────────────────────────────────────────────
            drawAmbientGlow(center, baseRadius, accentColor, brightness)

            // ── Outer dial ring (state-dependent) ──────────────────────
            when (state) {
                JarvisVisualState.LISTENING -> {
                    // Live circular equalizer driven by mic amplitude
                    drawWaveformRing(center, ringDial, accentColor, audioLevel, brightness)
                }
                JarvisVisualState.EXECUTING -> {
                    drawDialArcs(center, ringDial, accentColor, secondaryColor, dialRotation, brightness)
                    rotate(arcRotation, pivot = center) {
                        drawArc(
                            color = secondaryColor.copy(alpha = 0.95f * brightness),
                            startAngle = -18f,
                            sweepAngle = 72f,
                            useCenter = false,
                            topLeft = Offset(center.x - ringDial, center.y - ringDial),
                            size = androidx.compose.ui.geometry.Size(ringDial * 2f, ringDial * 2f),
                            style = Stroke(width = 3f, cap = StrokeCap.Round)
                        )
                    }
                }
                else -> {
                    drawDialArcs(center, ringDial, accentColor, secondaryColor, dialRotation, brightness)
                }
            }

            // ── Radial tick ring (the HUD dial marks) ──────────────────
            drawTickRing(
                center, ringTicks,
                if (state == JarvisVisualState.LISTENING) accentColor else secondaryColor,
                brightness * (if (state == JarvisVisualState.LISTENING) 1.6f else 1f)
            )

            // ── Thin inner ring ────────────────────────────────────────
            drawCircle(
                color = accentColor.copy(alpha = 0.35f * brightness),
                radius = ringInner * baseScale,
                center = center,
                style = Stroke(width = 1.2f, cap = StrokeCap.Round)
            )

            // ── Thinking particles (inward gather) ─────────────────────
            if (state == JarvisVisualState.THINKING) {
                drawThinkingParticles(center, ringDial * 1.05f, accentColor, particleDrift, brightness)
            }

            // ── Two orbiting light dots ────────────────────────────────
            drawOrbitDot(center, ringTicks, orbitDrift, accentColor, brightness)
            drawOrbitDot(center, ringDial, orbitDrift * 0.7f + 140f, secondaryColor, brightness)

            // ── Luminous core ──────────────────────────────────────────
            drawLuminousCore(center, coreRadius * baseScale, accentColor, brightness, audioLevel)

            // ── Error flash overlay ────────────────────────────────────
            if (state == JarvisVisualState.ERROR) {
                drawCircle(
                    color = JarvisColors.StateError.copy(alpha = 0.30f),
                    radius = coreRadius * 1.6f,
                    center = center
                )
            }
        }
    }
}

// ── Drawing functions ──────────────────────────────────────────────────────

private fun DrawScope.drawAmbientGlow(
    center: Offset,
    radius: Float,
    color: Color,
    brightness: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                color.copy(alpha = 0.18f * brightness),
                color.copy(alpha = 0.07f * brightness),
                Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                color.copy(alpha = 0.28f * brightness),
                color.copy(alpha = 0.09f * brightness),
                Color.Transparent
            ),
            center = center,
            radius = radius * 0.55f
        ),
        radius = radius * 0.55f,
        center = center
    )
}

private fun DrawScope.drawLuminousCore(
    center: Offset,
    radius: Float,
    color: Color,
    brightness: Float,
    audioLevel: Float
) {
    val dynamicR = radius * (1f + audioLevel * 0.18f)

    // Volumetric glow
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                color.copy(alpha = 0.40f * brightness),
                color.copy(alpha = 0.14f * brightness),
                Color.Transparent
            ),
            center = center,
            radius = dynamicR * 3.2f
        ),
        radius = dynamicR * 3.2f,
        center = center
    )

    // Solid luminous sphere
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = 0.95f * brightness),
                color.copy(alpha = 0.90f * brightness),
                color.copy(alpha = 0.45f * brightness)
            ),
            center = center,
            radius = dynamicR
        ),
        radius = dynamicR,
        center = center
    )

    // Hot center
    drawCircle(
        color = Color.White.copy(alpha = 0.85f * brightness),
        radius = dynamicR * 0.42f,
        center = center
    )
}

/** The HUD dial marks: 72 short radial ticks, every 6th emphasized. */
private fun DrawScope.drawTickRing(
    center: Offset,
    radius: Float,
    color: Color,
    brightness: Float
) {
    val ticks = 72
    val angleStep = (2f * PI.toFloat()) / ticks
    for (i in 0 until ticks) {
        val emphasized = i % 6 == 0
        val len = if (emphasized) radius * 0.075f else radius * 0.038f
        val angle = i * angleStep - PI.toFloat() / 2f
        val cosA = cos(angle)
        val sinA = sin(angle)
        val inner = radius - len
        val outer = radius + len
        drawLine(
            color = color.copy(
                alpha = ((if (emphasized) 0.55f else 0.20f) * brightness).coerceIn(0f, 1f)
            ),
            start = Offset(center.x + cosA * inner, center.y + sinA * inner),
            end = Offset(center.x + cosA * outer, center.y + sinA * outer),
            strokeWidth = if (emphasized) 2f else 1f,
            cap = StrokeCap.Round
        )
    }
    // Faint full circle underneath the ticks
    drawCircle(
        color = color.copy(alpha = 0.08f * brightness),
        radius = radius,
        center = center,
        style = Stroke(width = 1f)
    )
}

/** Fine arc segments around the outer ring, slowly rotating. */
private fun DrawScope.drawDialArcs(
    center: Offset,
    radius: Float,
    colorA: Color,
    colorB: Color,
    rotation: Float,
    brightness: Float
) {
    // Base ring
    drawCircle(
        color = colorA.copy(alpha = 0.10f * brightness),
        radius = radius,
        center = center,
        style = Stroke(width = 1f)
    )
    rotate(rotation, pivot = center) {
        val segments = 8
        for (i in 0 until segments) {
            val start = i * (360f / segments)
            val sweep = if (i % 2 == 0) 20f else 9f
            val alpha = if (i % 2 == 0) 0.55f else 0.28f
            val col = if (i % 4 == 0) colorB else colorA
            drawArc(
                color = col.copy(alpha = alpha * brightness),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )
        }
    }
}

/** One tiny glowing dot orbiting at [angleDeg] on the given [radius]. */
private fun DrawScope.drawOrbitDot(
    center: Offset,
    radius: Float,
    angleDeg: Float,
    color: Color,
    brightness: Float
) {
    val angle = angleDeg * (PI.toFloat() / 180f)
    val x = center.x + cos(angle) * radius
    val y = center.y + sin(angle) * radius
    // Halo
    drawCircle(
        color = color.copy(alpha = 0.25f * brightness),
        radius = radius * 0.045f,
        center = Offset(x, y)
    )
    // Dot
    drawCircle(
        color = color.copy(alpha = 0.95f * brightness),
        radius = radius * 0.018f,
        center = Offset(x, y)
    )
}

private fun DrawScope.drawWaveformRing(
    center: Offset,
    radius: Float,
    color: Color,
    audioLevel: Float,
    brightness: Float
) {
    val segments = 72
    val angleStep = (2f * PI.toFloat()) / segments

    for (i in 0 until segments) {
        val angle = i * angleStep - PI.toFloat() / 2f
        val wave = sin(angle * 3f + audioLevel * 8f) * audioLevel * radius * 0.25f
        val innerR = radius - wave.coerceAtLeast(0f)
        val outerR = radius + wave.coerceAtMost(radius * 0.30f)

        val segAlpha = (0.3f + audioLevel * 0.6f) * brightness
        drawLine(
            color = color.copy(alpha = segAlpha.coerceIn(0.1f, 1f)),
            start = Offset(center.x + cos(angle) * innerR, center.y + sin(angle) * innerR),
            end = Offset(center.x + cos(angle) * outerR, center.y + sin(angle) * outerR),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        color = color.copy(alpha = 0.10f * brightness),
        radius = radius,
        center = center,
        style = Stroke(width = 1f)
    )
}

private fun DrawScope.drawThinkingParticles(
    center: Offset,
    outerRadius: Float,
    color: Color,
    drift: Float,
    brightness: Float
) {
    val count = 14
    for (i in 0 until count) {
        val angle = (i * (360f / count) + drift) * (PI.toFloat() / 180f)
        val progress = ((drift + i * 25f) % 360f) / 360f
        val dist = outerRadius * (1f - progress * 0.7f)
        val alpha = (1f - progress) * 0.6f * brightness
        if (alpha > 0.02f) {
            val px = center.x + cos(angle) * dist
            val py = center.y + sin(angle) * dist
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = 1.5f + (1f - progress) * 1.5f,
                center = Offset(px, py)
            )
        }
    }
}

// orbColor() and accent() are defined on JarvisVisualState in AssistantModels.kt
