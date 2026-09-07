package com.jarvis.feature.home

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.agent.orchestrator.AssistantOrchestrator
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.voice.VoiceBus
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.HudBackground
import com.jarvis.core.ui.JarvisCore
import com.jarvis.feature.navigation.BottomNavigationBar
import java.util.Calendar

/**
 * Screen 2 (HOME) from the official JARVIS specification design.
 *
 * Exact 100% specification alignment:
 * - Header: [☰ Menu]   J . A . R . V . I . S   [⚙ Settings]
 * - Greeting: "Good evening, Macaulay." / "How can I help you?"
 * - Centerpiece: High-resolution Arc Reactor Core with sonic concentric rings
 * - Status indicator: (● IDLE) with animated audio waveform bars
 * - Cards: [RECENT TASK: Opened WhatsApp · 2m ago] and [MEMORY: You prefer concise answers.]
 * - Section: QUICK ACTIONS [Open App] [Volume] [Flashlight] [Screenshot]
 * - Bottom Bar: [Home] [Chat] [Memory] [Settings]
 */
@Composable
fun HomeScreen(
    orchestrator: AssistantOrchestrator,
    onNavigate: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleVoice: () -> Unit,
    onQuickAction: (String) -> Unit
) {
    val visualState by orchestrator.visualState.collectAsState()
    val audioLevel by VoiceBus.audioLevel.collectAsState()

    val userName = remember {
        val name = ApiConfig.userName.trim()
        if (name.isBlank() || name.equals("Sir", ignoreCase = true)) "Macaulay" else name
    }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = JarvisColors.VoidBlack,
        bottomBar = {
            BottomNavigationBar(
                currentRoute = "home",
                onNavigate = onNavigate,
                onToggleVoice = onToggleVoice
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF020409),
                            Color(0xFF06101D),
                            Color(0xFF030508)
                        )
                    )
                )
        ) {
            HudBackground(
                modifier = Modifier.fillMaxSize(),
                glowColor = visualState.orbColor()
            )

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // ── 1. Top Header ──────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = JarvisColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Text(
                        text = "J . A . R . V . I . S",
                        color = JarvisColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = JarvisColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── 2. Greeting ────────────────────────────────────────────
                Text(
                    text = "$greeting, $userName.",
                    color = JarvisColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "How can I help you?",
                    color = JarvisColors.TextSecondary,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── 3. Central Orb with Concentric HUD Rims ────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        JarvisCore(
                            state = visualState,
                            size = 190.dp,
                            audioLevel = audioLevel,
                            onClick = onToggleVoice
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Status Badge: (● IDLE) + Audio Waveform
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(JarvisColors.SurfaceGlassElevated)
                                .border(
                                    0.8.dp,
                                    JarvisColors.Presence.copy(alpha = 0.25f),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(visualState.orbColor())
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = when (visualState) {
                                    JarvisVisualState.LISTENING -> "LISTENING"
                                    JarvisVisualState.THINKING -> "THINKING"
                                    JarvisVisualState.EXECUTING -> "EXECUTING"
                                    JarvisVisualState.SPEAKING -> "SPEAKING"
                                    JarvisVisualState.ERROR -> "ERROR"
                                    else -> "IDLE"
                                },
                                color = visualState.orbColor(),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))

                            // Miniature Audio Waveform Bars
                            WaveformBarMini(
                                isAlive = visualState == JarvisVisualState.LISTENING || visualState == JarvisVisualState.SPEAKING,
                                accentColor = visualState.orbColor()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── 4. Two Info Cards (Recent Task & Memory) ───────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Recent Task Card
                    InfoGlassCard(
                        modifier = Modifier.weight(1f),
                        badge = "RECENT TASK",
                        title = "Opened WhatsApp",
                        subtitle = "2m ago",
                        onClick = { onQuickAction("recent_task") }
                    )

                    // Memory Card
                    InfoGlassCard(
                        modifier = Modifier.weight(1f),
                        badge = "MEMORY",
                        title = "You prefer concise answers.",
                        subtitle = "2d ago",
                        onClick = { onNavigate("memory") }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── 5. Quick Actions Section ───────────────────────────────
                Text(
                    text = "QUICK ACTIONS",
                    color = JarvisColors.TextMuted,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Apps,
                        label = "Open App",
                        onClick = { onQuickAction("open_app") }
                    )
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.VolumeUp,
                        label = "Volume",
                        onClick = { onQuickAction("volume") }
                    )
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.FlashlightOn,
                        label = "Flashlight",
                        onClick = { onQuickAction("flashlight") }
                    )
                    QuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CropFree,
                        label = "Screenshot",
                        onClick = { onQuickAction("screenshot") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Clean glassmorphic card for Recent Task & Memory.
 */
@Composable
private fun InfoGlassCard(
    modifier: Modifier = Modifier,
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(JarvisColors.SurfaceGlassElevated)
            .border(
                0.8.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.12f),
                        JarvisColors.Presence.copy(alpha = 0.18f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = badge,
                color = JarvisColors.Presence,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = JarvisColors.TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = JarvisColors.TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Quick action square icon card.
 */
@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(JarvisColors.SurfaceGlassElevated)
            .border(
                0.8.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color(0x22336699)
                    )
                ),
                RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = JarvisColors.Presence,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = label,
            color = JarvisColors.TextSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Animated miniature audio waveform bars shown in the status pill.
 */
@Composable
private fun WaveformBarMini(
    isAlive: Boolean,
    accentColor: Color
) {
    val transition = rememberInfiniteTransition(label = "miniWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(12.dp)
    ) {
        repeat(5) { i ->
            val factor = if (isAlive) {
                kotlin.math.sin(phase + i * 1.2f) * 0.5f + 0.5f
            } else {
                0.3f + (i % 2) * 0.2f
            }
            val height = (4.dp + (8.dp * factor))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(accentColor.copy(alpha = 0.85f))
            )
        }
    }
}
