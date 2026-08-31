package com.jarvis.feature.splash

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.JarvisCore
import kotlinx.coroutines.delay

/**
 * Splash Screen — JARVIS branding moment.
 *
 * Design: calm, precise, alive.
 * - Deep graphite-blue background
 * - Centered Orb at idle state
 * - "JARVIS" wordmark with letter-spacing
 * - Tagline: "Your intelligent assistant. Always here."
 * - Subtle breathing animation on Orb
 */
@Composable
fun SplashScreen(
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-advance after 2.5 seconds
    LaunchedEffect(Unit) {
        delay(2500)
        onTimeout()
    }

    // Breathing animation for Orb
    val transition = rememberInfiniteTransition(label = "splash")
    val breathe by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0B0F17),
                        Color(0xFF0E1420),
                        Color(0xFF0B0F17)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Orb
            JarvisCore(
                state = JarvisVisualState.IDLE,
                size = 160.dp * breathe,
                onClick = null
            )

            Spacer(modifier = Modifier.height(40.dp))

            // JARVIS wordmark
            Text(
                text = "JARVIS",
                color = JarvisColors.TextPrimary,
                fontSize = 32.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                letterSpacing = 8.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tagline
            Text(
                text = "Your intelligent assistant.\nAlways here.",
                color = JarvisColors.TextSecondary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
