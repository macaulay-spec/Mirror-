package com.jarvis.core.ui

import androidx.compose.animation.core.EaseInOutSine
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
import androidx.compose.ui.geometry.Size
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
 * JARVIS Orb — one identity, everywhere.
 *
 * Design spec: a luminous core (soft radial gradient) with a single thin outer ring
 * at ~1.3x the core's radius. Complexity is added by STATE, not by default.
 *
 * - Idle: 40% brightness, slow breathing (4200ms), ring barely visible
 * - Listening: 90% brightness, ring = real waveform from mic amplitude
 * - Thinking: violet hue, inward particle gather (12-16 particles)
 * - Executing: amber hue, single rotating arc segment
 * - Speaking: blue, core pulses with TTS amplitude
 * - Error: sharp contraction + flash (~180ms), settle to idle
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
    val transition = rememberInfiniteTransition(label = "orb")

    // Idle breathing: 95% → 105% → 95%, 4200ms ease-in-out
    val breathe by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
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
            animation = tween(8000),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleDrift"
    )

    // Executing arc rotation
    val arcRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Restart
        ),
        label = "arcRotation"
    )

    // Smooth state transitions
    val targetBrightness = when (state) {
        JarvisVisualState.IDLE -> 0.60f       // bumped from 0.40 — design shows visible Orb at idle
        JarvisVisualState.WAKING -> 0.75f
        JarvisVisualState.LISTENING -> 0.95f
        JarvisVisualState.THINKING -> 0.85f
        JarvisVisualState.EXECUTING -> 0.90f
        JarvisVisualState.SPEAKING -> 0.90f
        JarvisVisualState.SUCCESS -> 0.95f
        JarvisVisualState.ERROR -> 0.90f
        JarvisVisualState.OFFLINE -> 0.30f
    }
    val brightness by animateFloatAsState(
        targetValue = targetBrightness,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "brightness"
    )

    // Scale: breathing in idle, contracted in error
    val baseScale = when (state) {
        JarvisVisualState.IDLE, JarvisVisualState.OFFLINE -> breathe
        JarvisVisualState.ERROR -> 0.88f
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
            val coreRadius = baseRadius * 0.30f
            val ringRadius = coreRadius * 1.6f

            // ── Ambient glow ─────────────────────────────────────────
            drawAmbientGlow(center, coreRadius * 3f, accentColor, brightness)

            // ── Outer ring (state-dependent behavior) ────────────────
            when (state) {
                JarvisVisualState.LISTENING -> {
                    // Ring = waveform driven by real mic amplitude
                    drawWaveformRing(center, ringRadius, accentColor, audioLevel, brightness)
                }
                JarvisVisualState.THINKING -> {
                    // Subtle static ring
                    drawCircle(
                        color = accentColor.copy(alpha = 0.15f * brightness),
                        radius = ringRadius * baseScale,
                        center = center,
                        style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                    )
                }
                JarvisVisualState.EXECUTING -> {
                    // Single rotating arc segment
                    drawExecutingArc(center, ringRadius * baseScale, accentColor, arcRotation, brightness)
                }
                JarvisVisualState.ERROR -> {
                    // Contracted ring with error color
                    drawCircle(
                        color = JarvisColors.StateError.copy(alpha = 0.6f),
                        radius = ringRadius * 0.85f,
                        center = center,
                        style = Stroke(width = 2f, cap = StrokeCap.Round)
                    )
                }
                else -> {
                    // Idle / speaking / success: barely visible static ring
                    drawCircle(
                        color = accentColor.copy(alpha = 0.12f * brightness),
                        radius = ringRadius * baseScale,
                        center = center,
                        style = Stroke(width = 1.2f, cap = StrokeCap.Round)
                    )
                }
            }

            // ── Thinking particles (inward gather) ───────────────────
            if (state == JarvisVisualState.THINKING) {
                drawThinkingParticles(center, ringRadius * 1.8f, accentColor, particleDrift, brightness)
            }

            // ── Luminous core ────────────────────────────────────────
            drawLuminousCore(center, coreRadius * baseScale, accentColor, brightness, audioLevel)

            // ── Error flash overlay ──────────────────────────────────
            if (state == JarvisVisualState.ERROR) {
                drawCircle(
                    color = JarvisColors.StateError.copy(alpha = 0.3f),
                    radius = coreRadius * 1.5f,
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
    // Outer diffuse glow
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                color.copy(alpha = 0.15f * brightness),
                color.copy(alpha = 0.06f * brightness),
                Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
    // Inner brighter glow ring
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                color.copy(alpha = 0.25f * brightness),
                color.copy(alpha = 0.08f * brightness),
                Color.Transparent
            ),
            center = center,
            radius = radius * 0.65f
        ),
        radius = radius * 0.65f,
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
    val dynamicR = radius * (1f + audioLevel * 0.15f)

    // Outer volumetric glow
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                color.copy(alpha = 0.35f * brightness),
                color.copy(alpha = 0.12f * brightness),
                Color.Transparent
            ),
            center = center,
            radius = dynamicR * 2.4f
        ),
        radius = dynamicR * 2.4f,
        center = center
    )

    // Middle glow layer
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                color.copy(alpha = 0.70f * brightness),
                color.copy(alpha = 0.35f * brightness),
                Color.Transparent
            ),
            center = center,
            radius = dynamicR * 1.5f
        ),
        radius = dynamicR * 1.5f,
        center = center
    )

    // Solid core — bright luminous sphere
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = 0.95f * brightness),
                color.copy(alpha = 0.90f * brightness),
                color.copy(alpha = 0.55f * brightness)
            ),
            center = center,
            radius = dynamicR
        ),
        radius = dynamicR,
        center = center
    )

    // Inner nucleus highlight (bright white center)
    drawCircle(
        color = Color.White.copy(alpha = 0.85f * brightness),
        radius = dynamicR * 0.40f,
        center = center
    )

    // Dark pupil (the eye-like center from the design)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                JarvisColors.VoidBlack.copy(alpha = 0.90f),
                JarvisColors.VoidBlack.copy(alpha = 0.50f),
                Color.Transparent
            ),
            center = center,
            radius = dynamicR * 0.25f
        ),
        radius = dynamicR * 0.25f,
        center = center
    )

    // Iris ring (glowing ring around the pupil)
    drawCircle(
        color = color.copy(alpha = 0.70f * brightness),
        radius = dynamicR * 0.22f,
        center = center,
        style = Stroke(width = 1.5f)
    )
}

private fun DrawScope.drawWaveformRing(
    center: Offset,
    radius: Float,
    color: Color,
    audioLevel: Float,
    brightness: Float
) {
    val segments = 64
    val angleStep = (2f * PI.toFloat()) / segments

    for (i in 0 until segments) {
        val angle = i * angleStep - PI.toFloat() / 2f
        // Waveform: audio level drives amplitude, position in ring drives phase
        val wave = sin(angle * 3f + audioLevel * 8f) * audioLevel * radius * 0.25f
        val innerR = radius - wave.coerceAtLeast(0f)
        val outerR = radius + wave.coerceAtMost(radius * 0.3f)

        val innerX = center.x + cos(angle) * innerR
        val innerY = center.y + sin(angle) * innerR
        val outerX = center.x + cos(angle) * outerR
        val outerY = center.y + sin(angle) * outerR

        val segAlpha = (0.3f + audioLevel * 0.6f) * brightness
        drawLine(
            color = color.copy(alpha = segAlpha.coerceIn(0.1f, 1f)),
            start = Offset(innerX, innerY),
            end = Offset(outerX, outerY),
            strokeWidth = 1.5f,
            cap = StrokeCap.Round
        )
    }

    // Base ring (faint)
    drawCircle(
        color = color.copy(alpha = 0.10f * brightness),
        radius = radius,
        center = center,
        style = Stroke(width = 1f)
    )
}

private fun DrawScope.drawExecutingArc(
    center: Offset,
    radius: Float,
    color: Color,
    rotation: Float,
    brightness: Float
) {
    // Base ring (more visible)
    drawCircle(
        color = color.copy(alpha = 0.15f * brightness),
        radius = radius,
        center = center,
        style = Stroke(width = 1.5f)
    )

    // Rotating arc segment (determinate-ish progress)
    rotate(rotation, pivot = center) {
        drawArc(
            color = color.copy(alpha = 0.85f * brightness),
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
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
        // Particles drift inward over their lifecycle
        val progress = ((drift + i * 25f) % 360f) / 360f  // 0..1 lifecycle
        val dist = outerRadius * (1f - progress * 0.7f)    // move inward
        val alpha = (1f - progress) * 0.6f * brightness     // fade as they approach center

        if (alpha > 0.02f) {
            val px = center.x + cos(angle) * dist
            val py = center.y + sin(angle) * dist
            val pSize = 1.5f + (1f - progress) * 1.5f

            drawCircle(
                color = color.copy(alpha = alpha),
                radius = pSize,
                center = Offset(px, py)
            )
        }
    }
}

// orbColor() and accent() are defined on JarvisVisualState in AssistantModels.kt
