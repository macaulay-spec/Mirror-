package com.jarvis.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.JarvisCore

/**
 * Onboarding Flow — 4 pages introducing JARVIS.
 *
 * Design: calm, precise, alive.
 * - Each page explains one thing clearly
 * - Orb appears on every page for consistency
 * - Skip/Next buttons at bottom
 * - Progress dots
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingFlow(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val totalPages = 4

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
            )
    ) {
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInHorizontally()).togetherWith(
                    fadeOut(tween(300)) + slideOutHorizontally()
                )
            },
            modifier = Modifier.fillMaxSize(),
            label = "onboarding_page"
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> OrbIntroPage()
                2 -> CapabilitiesPage()
                3 -> GetStartedPage()
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(totalPages) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentPage)
                                    JarvisColors.Presence
                                else
                                    JarvisColors.TextMuted.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Skip button
                Text(
                    text = "Skip",
                    color = JarvisColors.TextMuted,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Default,
                    modifier = Modifier
                        .clickable { onSkip() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Next / Get Started button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(JarvisColors.Presence)
                        .clickable {
                            if (currentPage < totalPages - 1) {
                                currentPage++
                            } else {
                                onComplete()
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (currentPage < totalPages - 1) "Next" else "Get Started",
                        color = JarvisColors.VoidBlack,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── Page 1: Welcome ──────────────────────────────────────────────────────

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        JarvisCore(
            state = JarvisVisualState.IDLE,
            size = 120.dp,
            onClick = null
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Welcome to",
            color = JarvisColors.TextSecondary,
            fontSize = 16.sp,
            fontFamily = FontFamily.Default
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "JARVIS",
            color = JarvisColors.TextPrimary,
            fontSize = 36.sp,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            letterSpacing = 6.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Your intelligent assistant that\nsees, hears, and helps.",
            color = JarvisColors.TextSecondary,
            fontSize = 15.sp,
            fontFamily = FontFamily.Default,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ── Page 2: Meet the Orb ────────────────────────────────────────────────

@Composable
private fun OrbIntroPage() {
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        JarvisCore(
            state = JarvisVisualState.IDLE,
            size = 140.dp * breathe,
            onClick = null
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Meet the Orb",
            color = JarvisColors.TextPrimary,
            fontSize = 24.sp,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your AI companion, always with you.\nIt sees your screen, hears your voice,\nand acts on your behalf.",
            color = JarvisColors.TextSecondary,
            fontSize = 15.sp,
            fontFamily = FontFamily.Default,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Orb states preview
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OrbStatePreview("Idle", JarvisColors.Presence.copy(alpha = 0.4f))
            OrbStatePreview("Listening", JarvisColors.Presence)
            OrbStatePreview("Thinking", JarvisColors.StateThinking)
        }
    }
}

@Composable
private fun OrbStatePreview(label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f))
                .border(1.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = JarvisColors.TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Default
        )
    }
}

// ── Page 3: Capabilities ────────────────────────────────────────────────

@Composable
private fun CapabilitiesPage() {
    val capabilities = listOf(
        CapabilityItem(
            icon = Icons.Default.Mic,
            title = "Voice control",
            description = "Speak naturally, Jarvis understands"
        ),
        CapabilityItem(
            icon = Icons.Default.ChatBubbleOutline,
            title = "Read & reply",
            description = "Messages from any app"
        ),
        CapabilityItem(
            icon = Icons.Default.Bolt,
            title = "Device control",
            description = "Volume, brightness, apps, and more"
        ),
        CapabilityItem(
            icon = Icons.Default.Security,
            title = "Screen awareness",
            description = "Sees what's on your screen"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Powerful Capabilities",
            color = JarvisColors.TextPrimary,
            fontSize = 24.sp,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Understand. Act. Assist.\nAcross your entire device.",
            color = JarvisColors.TextSecondary,
            fontSize = 15.sp,
            fontFamily = FontFamily.Default,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        capabilities.forEach { capability ->
            CapabilityRow(capability)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private data class CapabilityItem(
    val icon: ImageVector,
    val title: String,
    val description: String
)

@Composable
private fun CapabilityRow(item: CapabilityItem) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(JarvisColors.Presence.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = JarvisColors.Presence,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = item.title,
                    color = JarvisColors.TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = item.description,
                    color = JarvisColors.TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Default
                )
            }
        }
    }
}

// ── Page 4: Get Started ────────────────────────────────────────────────

@Composable
private fun GetStartedPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        JarvisCore(
            state = JarvisVisualState.IDLE,
            size = 100.dp,
            onClick = null
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Let's get started",
            color = JarvisColors.TextPrimary,
            fontSize = 24.sp,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Set up JARVIS to unlock\nits full potential.",
            color = JarvisColors.TextSecondary,
            fontSize = 15.sp,
            fontFamily = FontFamily.Default,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Setup items
        SetupItem(Icons.Default.Mic, "Microphone access")
        SetupItem(Icons.Default.Notifications, "Notification access")
        SetupItem(Icons.Default.Security, "Accessibility service")
    }
}

@Composable
private fun SetupItem(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = JarvisColors.Presence,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = JarvisColors.TextPrimary,
            fontSize = 15.sp,
            fontFamily = FontFamily.Default
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Required",
            tint = JarvisColors.TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}
