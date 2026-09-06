package com.jarvis.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.agent.orchestrator.AssistantOrchestrator
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.voice.VoiceBus
import com.jarvis.core.model.AssistantMessage
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.model.MessageRole
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.GlowMicButton
import com.jarvis.core.ui.HudBackground
import com.jarvis.core.ui.JarvisCore
import com.jarvis.core.ui.StarkTelemetryBadge
import com.jarvis.core.ui.StreamingCursor
import com.jarvis.core.ui.ThinkingDots
import com.jarvis.feature.actions.ActionCard
import com.jarvis.feature.actions.toActionCardData
import kotlinx.coroutines.launch

enum class StageMode {
    VOICE_STAGE,
    CONVERSATION
}

/**
 * JARVIS Mark 85 + Apple Liquid Glass Host
 *
 * Implements an authentic Tony Stark command experience paired with
 * Apple-grade design polish:
 * - Full edge-to-edge with status bar and navigation bar insets
 * - Dynamic Stark Mark 85 Arc Reactor centerpiece with audio harmonic ripples
 * - Apple Liquid Glass floating surfaces with multi-tier specular edge highlights
 * - High-contrast nanotech intelligence stream for conversation
 * - Floating Apple-style glass island bottom dock with instant screen vision triggers
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DualModeHost(
    orchestrator: AssistantOrchestrator,
    onOpenSettings: () -> Unit = {},
    onToggleVoice: () -> Unit = {},
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {}
) {
    val visualState by orchestrator.visualState.collectAsState()
    val messages by orchestrator.messages.collectAsState()

    var stageMode by remember { mutableStateOf(StageMode.VOICE_STAGE) }
    var drawerOpen by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Keep list scrolled to latest message when in conversation view
    val thinkingIndicatorVisible = visualState == JarvisVisualState.THINKING &&
            messages.lastOrNull()?.role == MessageRole.USER
    LaunchedEffect(messages.size, thinkingIndicatorVisible) {
        if (messages.isNotEmpty() && stageMode == StageMode.CONVERSATION) {
            listState.animateScrollToItem(
                if (thinkingIndicatorVisible) messages.size else messages.size - 1
            )
        }
    }

    val userName = ApiConfig.userName

    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JarvisColors.VoidBlack
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    // Deep nano-carbon obsidian background
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF06090E),
                            Color(0xFF0A121C),
                            Color(0xFF06090E)
                        )
                    )
                )
        ) {
            // Stark technical grid + dynamic neon bloom bleed
            HudBackground(
                modifier = Modifier.fillMaxSize(),
                glowColor = visualState.orbColor()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                // ── Apple-Stark Frosted Telemetry Header ─────────────────
                TopPresenceHeader(
                    mode = stageMode,
                    visualState = visualState,
                    onSwitchMode = {
                        stageMode = if (stageMode == StageMode.VOICE_STAGE)
                            StageMode.CONVERSATION else StageMode.VOICE_STAGE
                    },
                    onOpenSettings = onOpenSettings,
                    onEmergencyStop = { orchestrator.emergencyStop() },
                    onOpenDrawer = { drawerOpen = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedContent(
                    targetState = stageMode,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInVertically()).togetherWith(
                            fadeOut(tween(260)) + slideOutVertically()
                        )
                    },
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    label = "mode_transition"
                ) { mode ->
                    when (mode) {
                        StageMode.VOICE_STAGE -> {
                            VoiceStageView(
                                visualState = visualState,
                                userName = userName,
                                greeting = greeting,
                                onOrbTap = { onToggleVoice() },
                                onSwitchToChat = { stageMode = StageMode.CONVERSATION },
                                onQuickAction = { action ->
                                    scope.launch {
                                        orchestrator.submitUserInput(action)
                                    }
                                }
                            )
                        }
                        StageMode.CONVERSATION -> {
                            ConversationView(
                                orchestrator = orchestrator,
                                visualState = visualState,
                                messages = messages,
                                listState = listState,
                                onOrbTap = { onToggleVoice() }
                            )
                        }
                    }
                }

                if (stageMode == StageMode.CONVERSATION) {
                    ChatInputBar(
                        value = inputText,
                        onValueChange = { inputText = it },
                        onSend = {
                            val text = inputText.trim()
                            if (text.isNotBlank()) {
                                inputText = ""
                                scope.launch {
                                    orchestrator.submitUserInput(text)
                                }
                            }
                        },
                        onVoiceClick = onToggleVoice,
                        onVisionClick = {
                            scope.launch {
                                orchestrator.submitUserInput("Analyze what is currently on my screen")
                            }
                        },
                        isListening = visualState == JarvisVisualState.LISTENING
                    )
                }
            }

            // ── Mark 85 Tactical Drawer ──────────────────────────────────
            if (drawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { drawerOpen = false }
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(270.dp)
                            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                            .background(JarvisColors.SurfaceGlassElevated)
                            .border(
                                1.dp,
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.15f),
                                        JarvisColors.Presence.copy(alpha = 0.30f)
                                    )
                                ),
                                RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            JarvisCore(state = visualState, size = 38.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "J A R V I S",
                                    color = JarvisColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 3.sp
                                )
                                Text(
                                    text = "MARK 85 NEURAL MATRIX",
                                    color = JarvisColors.StarkGold,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Cluster telemetry badge
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(JarvisColors.VoidBlack.copy(alpha = 0.55f))
                                .border(0.6.dp, JarvisColors.BorderSteel, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "AI DUAL-CORE STATUS",
                                    color = JarvisColors.TextMuted,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    ApiConfig.getProviderLabel(),
                                    color = JarvisColors.PresenceBright,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        DrawerItem(Icons.Default.Dashboard, "Command Deck") {
                            drawerOpen = false
                        }
                        DrawerItem(Icons.Default.ChatBubbleOutline, "Tactical History") {
                            onNavigate("history")
                            drawerOpen = false
                        }
                        DrawerItem(Icons.Default.FlashlightOn, "Device Control HUD") {
                            onNavigate("device")
                            drawerOpen = false
                        }
                        DrawerItem(Icons.Default.Storage, "Long-term Memory") {
                            onNavigate("memory")
                            drawerOpen = false
                        }
                        DrawerItem(Icons.Default.KeyboardVoice, "Acoustic Synthesizer") {
                            onNavigate("voice")
                            drawerOpen = false
                        }
                        DrawerItem(Icons.Default.Settings, "Core Configuration") {
                            onOpenSettings()
                            drawerOpen = false
                        }
                    }
                }
            }
        }
    }
}

/** One glass drawer row. */
@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(JarvisColors.Presence.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = JarvisColors.PresenceBright,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = JarvisColors.TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

// ── Top Header (Apple Liquid Glass + Stark Telemetry Strip) ────────────────

@Composable
private fun TopPresenceHeader(
    mode: StageMode,
    visualState: JarvisVisualState,
    onSwitchMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onEmergencyStop: () -> Unit,
    onOpenDrawer: () -> Unit = {}
) {
    val accent by animateColorAsState(
        targetValue = visualState.orbColor(),
        animationSpec = spring(),
        label = "headerAccent"
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = JarvisColors.SurfaceGlassElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: mini arc-reactor emblem & title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSwitchMode
                )
            ) {
                Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    JarvisCore(
                        state = if (visualState == JarvisVisualState.OFFLINE)
                            JarvisVisualState.OFFLINE else JarvisVisualState.IDLE,
                        size = 32.dp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "J A R V I S",
                        color = JarvisColors.TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.5.sp
                    )
                    Text(
                        text = "MARK 85",
                        color = JarvisColors.StarkGold,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            // Center: Telemetry status badge
            StarkTelemetryBadge(
                text = when (visualState) {
                    JarvisVisualState.IDLE -> "CORE ONLINE"
                    JarvisVisualState.OFFLINE -> "OFFLINE"
                    JarvisVisualState.LISTENING -> "SONIC SENSORS"
                    JarvisVisualState.THINKING -> "SYNAPSE RUN"
                    JarvisVisualState.SPEAKING -> "VOICE STREAM"
                    else -> visualState.label.uppercase()
                },
                accent = accent
            )

            // Right: frosted actions cluster
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WaveformGlyph(
                    active = visualState == JarvisVisualState.LISTENING ||
                            visualState == JarvisVisualState.SPEAKING,
                    accent = accent
                )

                IconButton(onClick = onOpenDrawer, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = JarvisColors.TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = onSwitchMode, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (mode == StageMode.VOICE_STAGE)
                            Icons.Default.ChatBubbleOutline else Icons.Default.KeyboardVoice,
                        contentDescription = "Switch Mode",
                        tint = JarvisColors.TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = onEmergencyStop, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Emergency Stop",
                        tint = JarvisColors.StarkCrimson.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = JarvisColors.TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

/** Audio waveform frequency indicator glyph. */
@Composable
private fun WaveformGlyph(active: Boolean, accent: Color) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(1000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { i ->
            val wave = kotlin.math.sin(Math.toRadians((phase + i * 55f).toDouble())).toFloat()
            val h = if (active) (8 + 9 * kotlin.math.abs(wave)).dp else 4.dp
            val alpha = if (active) 0.95f else 0.35f
            Box(
                modifier = Modifier
                    .width(2.2.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.dp))
                    .background(accent.copy(alpha = alpha))
            )
        }
    }
}

// ── Voice Stage View (Mark 85 Centerpiece + Apple Micro-interactions) ──────

@Composable
private fun VoiceStageView(
    visualState: JarvisVisualState,
    userName: String,
    greeting: String,
    onOrbTap: () -> Unit,
    onSwitchToChat: () -> Unit,
    onQuickAction: (String) -> Unit
) {
    val accent by animateColorAsState(
        targetValue = visualState.orbColor(),
        animationSpec = spring(),
        label = "voiceAccent"
    )
    val audioLevel by VoiceBus.audioLevel.collectAsState()
    val transcript by VoiceBus.transcript.collectAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(visualState) {
        when (visualState) {
            JarvisVisualState.WAKING,
            JarvisVisualState.LISTENING,
            JarvisVisualState.EXECUTING -> haptic.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
            )
            else -> {}
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "speakPulse")
    val speakPulse by pulseTransition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            tween(400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "speakPulse"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Stark Salutation
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Text(
                text = "$greeting, ${if (userName.isBlank()) "Sir" else userName}.",
                color = JarvisColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Mark 85 systems online. Ready for directive.",
                color = JarvisColors.TextSecondary,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center
            )
        }

        // Center: Hero Mark 85 Arc Reactor Core
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            JarvisCore(
                state = visualState,
                audioLevel = when {
                    visualState == JarvisVisualState.LISTENING ->
                        (0.35f + audioLevel * 0.65f).coerceIn(0f, 1f)
                    visualState == JarvisVisualState.SPEAKING -> speakPulse
                    else -> 0f
                },
                size = 260.dp,
                onClick = onOrbTap
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Live speech transcription glass card
            if (visualState == JarvisVisualState.LISTENING && transcript.isNotBlank()) {
                GlassCard(
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = JarvisColors.SurfaceGlassElevated,
                    borderColor = JarvisColors.Presence.copy(alpha = 0.40f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = transcript.take(90),
                            color = JarvisColors.TextPrimary,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        StreamingCursor(accent = accent)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Text(
                        text = when (visualState) {
                            JarvisVisualState.IDLE -> "STANDBY // TAP OR SAY 'HEY JARVIS'"
                            JarvisVisualState.WAKING -> "INITIALIZING NEURAL CLUSTERS..."
                            JarvisVisualState.LISTENING -> "AWAITING AUDIO INPUT..."
                            JarvisVisualState.THINKING -> "COMPUTING TACTICAL RESPONSE..."
                            JarvisVisualState.EXECUTING -> "EXECUTING DIRECTIVE..."
                            JarvisVisualState.SPEAKING -> "SYNTHESIZING ACOUSTICS..."
                            JarvisVisualState.SUCCESS -> "TASK COMPLETE"
                            JarvisVisualState.ERROR -> "SYSTEM DISRUPTION DETECTED"
                            JarvisVisualState.OFFLINE -> "OFFLINE MODE"
                        },
                        color = accent,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Bottom: Tactical Directive Chips & Glow Mic
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TacticalDirectiveChips(onQuickAction = onQuickAction)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                GlowMicButton(
                    isListening = visualState == JarvisVisualState.LISTENING,
                    onClick = onOrbTap,
                    size = 56.dp
                )
                Text(
                    text = if (visualState == JarvisVisualState.LISTENING)
                        "Listening — Tap to finish" else "Tap reactor or speak to begin",
                    color = JarvisColors.TextSecondary,
                    fontSize = 12.5.sp
                )
            }
        }
    }
}

// ── Tactical Directive Chips ───────────────────────────────────────────────

@Composable
private fun TacticalDirectiveChips(onQuickAction: (String) -> Unit) {
    val chips = listOf(
        Triple("Screen", Icons.Default.Visibility, "What is currently on my screen?"),
        Triple("Apps", Icons.Default.Apps, "Open WhatsApp"),
        Triple("Alerts", Icons.Default.Notifications, "Read my notifications"),
        Triple("Torch", Icons.Default.FlashlightOn, "Turn on the flashlight"),
        Triple("Optimize", Icons.Default.Tune, "Analyze phone system status")
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        chips.forEach { (label, icon, query) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onQuickAction(query) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(JarvisColors.SurfaceGlass)
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    JarvisColors.Presence.copy(alpha = 0.25f)
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = JarvisColors.PresenceBright,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    color = JarvisColors.TextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Conversation View (Nanotech Intelligence Stream) ──────────────────────

@Composable
private fun ConversationView(
    orchestrator: AssistantOrchestrator,
    visualState: JarvisVisualState,
    messages: List<AssistantMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOrbTap: () -> Unit
) {
    val pendingConfirmation by orchestrator.pendingConfirmation.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    JarvisCore(
                        state = JarvisVisualState.IDLE,
                        size = 180.dp,
                        onClick = onOrbTap
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Mark 85 Core Standing By.",
                        color = JarvisColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Speak or enter your directive below, Sir.",
                        color = JarvisColors.TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        message = msg,
                        showStreamingCursor = msg.role == MessageRole.JARVIS &&
                                msg.id == messages.lastOrNull()?.id &&
                                (visualState == JarvisVisualState.THINKING ||
                                        visualState == JarvisVisualState.SPEAKING ||
                                        visualState == JarvisVisualState.EXECUTING),
                        confirmationActive = msg.toolCall == pendingConfirmation,
                        onConfirmTool = { req -> orchestrator.confirmToolExecution(req) },
                        onRejectTool = { orchestrator.rejectToolExecution() }
                    )
                }

                // Synapse Processing Indicator
                if (visualState == JarvisVisualState.THINKING &&
                    messages.lastOrNull()?.role == MessageRole.USER
                ) {
                    item(key = "thinking-indicator") {
                        GlassCard(
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = JarvisColors.SurfaceGlassCyan,
                            borderColor = JarvisColors.Presence.copy(alpha = 0.35f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ThinkingDots()
                                Text(
                                    text = "SYNAPSE ROUTING THROUGH 7-TIER CLUSTER...",
                                    color = JarvisColors.PresenceBright,
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Chat Message Item (Apple Liquid Glass + Stark Nanotech Card) ──────────

@Composable
private fun ChatMessageItem(
    message: AssistantMessage,
    showStreamingCursor: Boolean = false,
    confirmationActive: Boolean = true,
    onConfirmTool: (com.jarvis.core.model.ToolExecutionRequest) -> Unit,
    onRejectTool: (() -> Unit)? = null
) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // User query card (Apple titanium frosted)
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(JarvisColors.SurfaceGlassElevated)
                    .border(
                        0.8.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color(0x33336699)
                            )
                        ),
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "YOU",
                            color = JarvisColors.StarkGold,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message.text,
                        color = JarvisColors.TextPrimary,
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp
                    )
                }
            }
        } else {
            // JARVIS Intelligence Transcript Card (Apple Liquid Glass with holographic power rail)
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Glowing cyan power conductor rail
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    JarvisColors.PresenceBright,
                                    JarvisColors.PresenceDeep,
                                    Color.Transparent
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(
                            RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 20.dp,
                                bottomStart = 20.dp,
                                bottomEnd = 20.dp
                            )
                        )
                        .background(
                            if (isSystem) JarvisColors.SurfaceGlass
                            else JarvisColors.SurfaceGlassCyan
                        )
                        .border(
                            0.8.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    JarvisColors.Presence.copy(alpha = 0.22f),
                                    Color.Transparent
                                )
                            ),
                            RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 20.dp,
                                bottomStart = 20.dp,
                                bottomEnd = 20.dp
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        // Card Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(JarvisColors.PresenceBright)
                                )
                                Text(
                                    text = "J.A.R.V.I.S. // MK-85",
                                    color = JarvisColors.PresenceBright,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "AI RESPONSE",
                                color = JarvisColors.TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = message.text,
                            color = if (isSystem) JarvisColors.TextSecondary else JarvisColors.TextPrimary,
                            fontSize = 14.5.sp,
                            lineHeight = 22.sp
                        )

                        if (showStreamingCursor && message.text.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            StreamingCursor(accent = JarvisColors.PresenceBright)
                        }

                        // High-Risk Tool Execution Confirmation Card
                        if (confirmationActive &&
                            message.toolCall != null &&
                            message.toolCall.requiresConfirmation &&
                            message.toolResult == null
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(JarvisColors.Warmth.copy(alpha = 0.08f))
                                    .border(
                                        0.8.dp,
                                        JarvisColors.Warmth.copy(alpha = 0.35f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = JarvisColors.Warmth,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "AUTHORIZATION REQUIRED",
                                                color = JarvisColors.Warmth,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = message.toolCall.name,
                                                color = JarvisColors.TextPrimary,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(JarvisColors.Presence)
                                                .clickable { onConfirmTool(message.toolCall) }
                                                .padding(vertical = 9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Confirm Directive",
                                                color = JarvisColors.VoidBlack,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(JarvisColors.StarkCrimson.copy(alpha = 0.12f))
                                                .border(
                                                    0.8.dp,
                                                    JarvisColors.StarkCrimson.copy(alpha = 0.35f),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable { onRejectTool?.invoke() }
                                                .padding(horizontal = 16.dp, vertical = 9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Decline",
                                                color = JarvisColors.StarkCrimson,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Hardware / System Action Card
                        if (message.toolResult != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            ActionCard(action = message.toolResult.toActionCardData())
                        }
                    }
                }
            }
        }
    }
}

// ── Floating Apple-Style Glass Dock (ChatInputBar) ────────────────────────

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    onVisionClick: () -> Unit,
    isListening: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
    ) {
        // Floating shortcut pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "What's on screen?",
                "Read notifications",
                "Battery status",
                "Flashlight",
                "Set reminder",
                "Open WhatsApp"
            ).forEach { query ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(JarvisColors.SurfaceGlass)
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
                        .clickable { onValueChange(query) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = query,
                        color = JarvisColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // The Master Floating Glass Pill
        GlassCard(
            shape = RoundedCornerShape(28.dp),
            backgroundColor = JarvisColors.SurfaceGlassElevated,
            borderColor = JarvisColors.Presence.copy(alpha = 0.25f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vision Eye trigger
                IconButton(
                    onClick = onVisionClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Analyze Screen",
                        tint = JarvisColors.PresenceBright,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Ask JARVIS or give a directive…",
                            color = JarvisColors.TextMuted,
                            fontSize = 14.5.sp
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(
                            color = JarvisColors.TextPrimary,
                            fontSize = 14.5.sp,
                            lineHeight = 22.sp
                        ),
                        cursorBrush = SolidColor(JarvisColors.PresenceBright),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (value.isNotBlank()) {
                    IconButton(onClick = onSend, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = JarvisColors.PresenceBright,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                GlowMicButton(
                    isListening = isListening,
                    onClick = onVoiceClick,
                    size = 44.dp
                )
            }
        }
    }
}
