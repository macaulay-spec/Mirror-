package com.jarvis.core.ui

import androidx.compose.animation.animateColorAsState
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * JARVIS Core — ONE identity, everywhere (hero, mini orb, header emblem).
 *
 * v5 "full Iron Man" motion design — the core is never still:
 *  - SONAR WAVES: 4 expanding rings breathe out of the core, phase-shifted,
 *    fading as they travel to the HUD rim
 *  - LIVE WAVEFORM RING: 96 segments with two superposed harmonics that
 *    chase each other around the rim; amplitude follows the real mic/TTS
 *    level and keeps a synthetic idle shimmer when there is no audio
 *  - COUNTER-ROTATING HUD ARCS: two dashed arc layers spinning opposite ways
 *  - PARTICLE FIELD: orbiting motes; in THINKING they spiral inward into the
 *    core (violet), in LISTENING they stream outward
 *  - luminous volumetric core that pulses with audio and breathes when idle
 *  - WAKING does a fast boot-up spin; ERROR shakes and flashes coral
 */
@Composable
fun JarvisCore(
    state: JarvisVisualState,
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier,
    size: Dp = 320.dp,
    onClick: (() -> Unit)? = null
) {
    val accentColor by animateColorAsState(
        targetValue = state.orbColor(),
        animationSpec = tween(600),
        label = "accent"
    )
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

    // Master phase driving the sonar rings + waveform shimmer (fast, seamless)
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    JarvisVisualState.LISTENING, JarvisVisualState.SPEAKING -> 1400
                    JarvisVisualState.WAKING -> 900
                    JarvisVisualState.EXECUTING -> 1000
                    else -> 2200
                },
                easing = LinearEasing
            )
        ),
        label = "wavePhase"
    )

    // Boot-up / executing fast spin
    val fastSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fastSpin"
    )

    // Thinking particle drift (inward spiral driver)
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

    // Counter-rotating HUD arc layers
    val arcRotationA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arcRotationA"
    )
    val arcRotationB by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arcRotationB"
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

    // Smoothed audio level so the waveform never snaps
    val smoothLevel by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = tween(140),
        label = "smoothLevel"
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
            val canvasSize = this.size
            val shake = if (state == JarvisVisualState.ERROR) {
                Offset(sin(wavePhase * 9f) * 2.5f, cos(wavePhase * 11f) * 2f)
            } else Offset.Zero
            val center = Offset(canvasSize.width / 2f + shake.x, canvasSize.height / 2f + shake.y)
            val baseRadius = canvasSize.minDimension / 2f
            val s = (baseRadius / 160f).coerceIn(0.55f, 1.4f) // scale strokes with orb size

            val coreRadius = baseRadius * 0.20f
            val ringInner = baseRadius * 0.42f
            val ringTicks = baseRadius * 0.64f
            val ringDial = baseRadius * 0.88f

            // ── Neon halo ──────────────────────────────────────────────
            drawAmbientGlow(center, baseRadius, accentColor, brightness, smoothLevel)

            // ── SONAR WAVES — expanding rings from the core ────────────
            drawSonarWaves(
                center, coreRadius, ringDial, accentColor, wavePhase, brightness,
                ringCount = if (state == JarvisVisualState.OFFLINE) 2 else 4
            )

            // ── Outer dial ring (state-dependent) ──────────────────────
            when (state) {
                JarvisVisualState.LISTENING -> {
                    drawWaveformRing(
                        center, ringDial, accentColor, smoothLevel, brightness, wavePhase, s
                    )
                }
                JarvisVisualState.SPEAKING -> {
                    // Speaking speaks through BOTH the dial and the tick ring
                    drawWaveformRing(
                        center, ringDial, accentColor, smoothLevel, brightness, wavePhase, s
                    )
                    drawDialArcs(center, ringDial * 0.985f, accentColor, secondaryColor, dialRotation, brightness)
                }
                JarvisVisualState.EXECUTING -> {
                    drawDialArcs(center, ringDial, accentColor, secondaryColor, dialRotation, brightness)
                    rotate(fastSpin, pivot = center) {
                        drawArc(
                            color = secondaryColor.copy(alpha = 0.95f * brightness),
                            startAngle = -18f,
                            sweepAngle = 72f,
                            useCenter = false,
                            topLeft = Offset(center.x - ringDial, center.y - ringDial),
                            size = Size(ringDial * 2f, ringDial * 2f),
                            style = Stroke(width = 3f * s, cap = StrokeCap.Round)
                        )
                    }
                    rotate(-fastSpin * 0.6f, pivot = center) {
                        drawArc(
                            color = accentColor.copy(alpha = 0.5f * brightness),
                            startAngle = 120f,
                            sweepAngle = 34f,
                            useCenter = false,
                            topLeft = Offset(center.x - ringDial * 1.04f, center.y - ringDial * 1.04f),
                            size = Size(ringDial * 2.08f, ringDial * 2.08f),
                            style = Stroke(width = 2f * s, cap = StrokeCap.Round)
                        )
                    }
                }
                JarvisVisualState.WAKING -> {
                    // Boot-up: two fast counter-spinning sweep arcs
                    drawDialArcs(center, ringDial, accentColor, secondaryColor, dialRotation, brightness)
                    rotate(fastSpin, pivot = center) {
                        drawArc(
                            color = accentColor.copy(alpha = 0.8f * brightness),
                            startAngle = -90f,
                            sweepAngle = 110f,
                            useCenter = false,
                            topLeft = Offset(center.x - ringDial, center.y - ringDial),
                            size = Size(ringDial * 2f, ringDial * 2f),
                            style = Stroke(width = 2.5f * s, cap = StrokeCap.Round)
                        )
                    }
                    rotate(-fastSpin, pivot = center) {
                        drawArc(
                            color = secondaryColor.copy(alpha = 0.55f * brightness),
                            startAngle = -90f,
                            sweepAngle = 60f,
                            useCenter = false,
                            topLeft = Offset(center.x - ringDial * 0.92f, center.y - ringDial * 0.92f),
                            size = Size(ringDial * 1.84f, ringDial * 1.84f),
                            style = Stroke(width = 2f * s, cap = StrokeCap.Round)
                        )
                    }
                }
                else -> {
                    drawDialArcs(center, ringDial, accentColor, secondaryColor, dialRotation, brightness)
                }
            }

            // ── Counter-rotating HUD arc layers (inner energy) ─────────
            drawHudArcLayer(center, ringTicks * 0.82f, accentColor, arcRotationA, brightness, s)
            drawHudArcLayer(center, ringInner * 1.12f, secondaryColor, arcRotationB, brightness * 0.85f, s)

            // ── Radial tick ring (the HUD dial marks) ──────────────────
            rotate(dialRotation * 0.25f, pivot = center) {
                drawTickRing(
                    center, ringTicks,
                    if (state == JarvisVisualState.LISTENING) accentColor else secondaryColor,
                    brightness * (if (state == JarvisVisualState.LISTENING) 1.6f else 1f),
                    s
                )
            }

            // ── Thin inner ring ────────────────────────────────────────
            drawCircle(
                color = accentColor.copy(alpha = 0.35f * brightness),
                radius = ringInner * baseScale,
                center = center,
                style = Stroke(width = 1.2f * s, cap = StrokeCap.Round)
            )

            // ── Particle field ─────────────────────────────────────────
            drawParticleField(
                center, coreRadius, ringDial * 1.02f, accentColor,
                particleDrift, wavePhase, state, brightness
            )

            // ── Two orbiting light dots ────────────────────────────────
            drawOrbitDot(center, ringTicks, orbitDrift, accentColor, brightness, s)
            drawOrbitDot(center, ringDial, orbitDrift * 0.7f + 140f, secondaryColor, brightness, s)
            if (state == JarvisVisualState.THINKING || state == JarvisVisualState.EXECUTING) {
                drawOrbitDot(center, ringTicks * 0.86f, -orbitDrift * 1.6f + 60f, accentColor, brightness, s)
            }

            // ── Luminous core ──────────────────────────────────────────
            drawLuminousCore(center, coreRadius * baseScale, accentColor, brightness, smoothLevel, wavePhase)

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
    brightness: Float,
    audioLevel: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                color.copy(alpha = (0.18f + audioLevel * 0.10f) * brightness),
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

/**
 * 4 phase-shifted rings expanding from the core to the HUD rim and fading —
 * the "JARVIS is alive" sonar pulse. Phase offset per ring keeps it seamless.
 */
private fun DrawScope.drawSonarWaves(
    center: Offset,
    coreRadius: Float,
    maxRadius: Float,
    color: Color,
    phase: Float,
    brightness: Float,
    ringCount: Int
) {
    for (i in 0 until ringCount) {
        val p = ((phase / 360f) + i.toFloat() / ringCount) % 1f
        val eased = 1f - (1f - p) * (1f - p)          // ease-out travel
        val radius = coreRadius * 1.4f + (maxRadius - coreRadius * 1.4f) * eased
        val alpha = ((1f - p) * (1f - p) * 0.38f * brightness).coerceIn(0f, 1f)
        if (alpha > 0.01f) {
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = (1.6f + (1f - p) * 1.4f))
            )
        }
    }
}

private fun DrawScope.drawLuminousCore(
    center: Offset,
    radius: Float,
    color: Color,
    brightness: Float,
    audioLevel: Float,
    phase: Float
) {
    // Gentle shimmer even without audio, strong response when audio flows
    val shimmer = 1f + sin(phase * (2f * PI.toFloat() / 360f) * 3f) * 0.03f
    val dynamicR = radius * shimmer * (1f + audioLevel * 0.22f)

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
    brightness: Float,
    s: Float
) {
    val ticks = 72
    val angleStep = (2f * PI.toFloat()) / ticks
    for (i in 0 until ticks) {
        val emphasized = i % 6 == 0
        val len = (if (emphasized) radius * 0.075f else radius * 0.038f)
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
            strokeWidth = (if (emphasized) 2f else 1f) * s,
            cap = StrokeCap.Round
        )
    }
    // Faint full circle underneath the ticks
    drawCircle(
        color = color.copy(alpha = 0.08f * brightness),
        radius = radius,
        center = center,
        style = Stroke(width = 1f * s)
    )
}

/**
 * Inner HUD energy layer: fine dashed arcs rotating as a group. Called twice
 * with opposite rotations for the layered Iron Man HUD feel.
 */
private fun DrawScope.drawHudArcLayer(
    center: Offset,
    radius: Float,
    color: Color,
    rotation: Float,
    brightness: Float,
    s: Float
) {
    rotate(rotation, pivot = center) {
        val segments = 12
        for (i in 0 until segments) {
            val start = i * (360f / segments)
            val sweep = 10f
            val alpha = if (i % 3 == 0) 0.42f else 0.16f
            drawArc(
                color = color.copy(alpha = alpha * brightness),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 1.4f * s, cap = StrokeCap.Round)
            )
        }
    }
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
                size = Size(radius * 2f, radius * 2f),
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
    brightness: Float,
    s: Float
) {
    val angle = angleDeg * (PI.toFloat() / 180f)
    val x = center.x + cos(angle) * radius
    val y = center.y + sin(angle) * radius
    // Halo
    drawCircle(
        color = color.copy(alpha = 0.25f * brightness),
        radius = radius * 0.045f * s,
        center = Offset(x, y)
    )
    // Dot
    drawCircle(
        color = color.copy(alpha = 0.95f * brightness),
        radius = radius * 0.018f * s,
        center = Offset(x, y)
    )
}

/**
 * Live circular equalizer: 96 segments whose length follows two superposed
 * harmonics chasing each other around the rim. Real amplitude drives the
 * overall energy; a synthetic shimmer keeps it alive when silent.
 */
private fun DrawScope.drawWaveformRing(
    center: Offset,
    radius: Float,
    color: Color,
    audioLevel: Float,
    brightness: Float,
    phase: Float,
    s: Float
) {
    val segments = 96
    val angleStep = (2f * PI.toFloat()) / segments
    val phaseRad = phase * (PI.toFloat() / 180f)
    // Silence floor: subtle idle shimmer; audio adds up to ~5x energy
    val energy = 0.06f + audioLevel * 0.34f

    for (i in 0 until segments) {
        val angle = i * angleStep - PI.toFloat() / 2f
        val w1 = sin(angle * 3f + phaseRad * 2f)
        val w2 = sin(angle * 7f - phaseRad * 3f) * 0.45f
        val wave = (w1 + w2) * energy * radius
        val innerR = radius - abs(wave).coerceAtLeast(0f)
        val outerR = radius + wave.coerceAtMost(radius * 0.30f)

        val segAlpha = (0.3f + audioLevel * 0.6f) * brightness
        drawLine(
            color = color.copy(alpha = segAlpha.coerceIn(0.1f, 1f)),
            start = Offset(center.x + cos(angle) * innerR, center.y + sin(angle) * innerR),
            end = Offset(center.x + cos(angle) * outerR, center.y + sin(angle) * outerR),
            strokeWidth = 2f * s,
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        color = color.copy(alpha = 0.10f * brightness),
        radius = radius,
        center = center,
        style = Stroke(width = 1f * s)
    )
}

/**
 * Ambient motes always drifting between the core and the rim. THINKING pulls
 * them inward in a spiral (violet gather); LISTENING streams them outward.
 */
private fun DrawScope.drawParticleField(
    center: Offset,
    coreRadius: Float,
    outerRadius: Float,
    color: Color,
    drift: Float,
    phase: Float,
    state: JarvisVisualState,
    brightness: Float
) {
    val count = when (state) {
        JarvisVisualState.THINKING -> 18
        JarvisVisualState.LISTENING -> 16
        JarvisVisualState.OFFLINE -> 6
        else -> 10
    }
    val inward = state == JarvisVisualState.THINKING
    val outward = state == JarvisVisualState.LISTENING

    for (i in 0 until count) {
        val baseAngle = i * (360f / count)
        val spin = if (inward) drift * 1.4f else drift * 0.6f
        val angle = (baseAngle + spin + i * 7f) * (PI.toFloat() / 180f)

        // Radial life: oscillation for ambient, travel for thinking/listening
        val travel = ((phase / 360f) + i.toFloat() / count) % 1f
        val dist = when {
            inward -> outerRadius * (1f - travel * 0.78f)          // spiral inward
            outward -> outerRadius * (0.35f + travel * 0.6f)       // stream outward
            else -> outerRadius * (0.55f + 0.25f * sin(travel * 2f * PI.toFloat() + i))
        }
        val alpha = when {
            inward -> (1f - travel) * 0.7f
            outward -> (1f - travel) * 0.55f
            else -> 0.28f + 0.18f * sin(travel * 2f * PI.toFloat() + i)
        } * brightness

        if (alpha > 0.02f) {
            val px = center.x + cos(angle) * dist
            val py = center.y + sin(angle) * dist
            drawCircle(
                color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = 1.2f + (1f - travel) * 1.6f,
                center = Offset(px, py)
            )
        }
    }
}

// orbColor() and accent() are defined on JarvisVisualState in AssistantModels.kt
