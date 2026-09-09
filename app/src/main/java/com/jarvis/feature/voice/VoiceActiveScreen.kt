package com.jarvis.feature.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.agent.orchestrator.AssistantOrchestrator
import com.jarvis.app.voice.VoiceBus
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.HudBackground
import com.jarvis.core.ui.JarvisCore
import kotlin.math.sin

/**
 * Screen 3 (LISTENING) & Screen 4 (THINKING...) from official design spec.
 *
 * Screen 3:
 * - Title: "Listening..."
 * - Pulsing Arc Reactor Orb in vibrant Stark Cyan
 * - Multi-frequency audio waveform bars + "Speak now"
 * - CANCEL button at bottom
 *
 * Screen 4:
 * - Title: "Thinking..."
 * - Vortex Arc Reactor Orb in deep Violet
 * - "Understanding your request"
 * - • Analyzing context
 * - • Checking device state
 * - • Determining best action
 * - CANCEL button at bottom
 */
@Composable
fun VoiceActiveScreen(
    orchestrator: AssistantOrchestrator,
    onCancel: () -> Unit
) {
    val visualState by orchestrator.visualState.collectAsStateWithLifecycle()
    val audioLevel by VoiceBus.audioLevel.collectAsStateWithLifecycle()

    val isThinking = visualState == JarvisVisualState.THINKING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF020409),
                        Color(0xFF06101D),
                        Color(0xFF020409)
                    )
                )
            )
    ) {
        HudBackground(
            modifier = Modifier.fillMaxSize(),
            glowColor = visualState.orbColor()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top Title ────────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = if (isThinking) "Thinking..." else "Listening...",
                    color = JarvisColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }

            // ── Central Dynamic Orb ───────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                JarvisCore(
                    state = if (isThinking) JarvisVisualState.THINKING else JarvisVisualState.LISTENING,
                    size = 230.dp,
                    audioLevel = audioLevel
                )

                Spacer(modifier = Modifier.height(36.dp))

                if (!isThinking) {
                    // Screen 3: Waveform visualizer + "Speak now"
                    AudioWaveformVisualizer(
                        isListening = true,
                        audioLevel = audioLevel,
                        accentColor = JarvisColors.Presence
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Speak now",
                        color = JarvisColors.TextSecondary,
                        fontSize = 13.5.sp
                    )
                } else {
                    // Screen 4: Thinking status breakdown
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(JarvisColors.SurfaceGlassElevated)
                            .border(
                                0.8.dp,
                                JarvisColors.StateThinking.copy(alpha = 0.25f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Understanding your request",
                            color = JarvisColors.TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ThinkingBulletItem("Analyzing context")
                        Spacer(modifier = Modifier.height(6.dp))
                        ThinkingBulletItem("Checking device state")
                        Spacer(modifier = Modifier.height(6.dp))
                        ThinkingBulletItem("Determining best action")
                    }
                }
            }

            // ── Bottom CANCEL Button ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(JarvisColors.SurfaceGlassElevated)
                    .border(
                        0.8.dp,
                        Color.White.copy(alpha = 0.15f),
                        RoundedCornerShape(24.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCancel
                    )
                    .padding(horizontal = 48.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CANCEL",
                    color = JarvisColors.TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

@Composable
private fun ThinkingBulletItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(JarvisColors.Presence)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = JarvisColors.TextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun AudioWaveformVisualizer(
    isListening: Boolean,
    audioLevel: Float,
    accentColor: Color
) {
    val transition = rememberInfiniteTransition(label = "audioWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(28.dp)
    ) {
        repeat(16) { i ->
            val harmonic = sin(phase + i * 0.4f) * 0.5f + 0.5f
            val dynamicFactor = if (isListening) (0.25f + harmonic * (0.4f + audioLevel * 0.6f)) else 0.2f
            val height = (6.dp + (22.dp * dynamicFactor)).coerceIn(4.dp, 28.dp)

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.4f)
                            )
                        )
                    )
            )
        }
    }
}
