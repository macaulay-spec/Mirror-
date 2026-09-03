package com.jarvis.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Apps
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.jarvis.core.ui.StreamingCursor
import com.jarvis.core.ui.ThinkingDots

enum class StageMode {
    VOICE_STAGE,
    CONVERSATION
}

/**
 * Dual-mode host — v3 "Command Deck" design (carbon copy of the approved
 * mockups 02/03): true-black HUD grid background, frosted-glass header with
 * the J A R V I S wordmark, concentric-ring core, glass chat bubbles with a
 * cyan left edge for JARVIS, live streaming text with cursor, thinking dots,
 * and the glowing mic pill.
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

    // Auto-switch to the conversation deck once a conversation starts
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && stageMode == StageMode.VOICE_STAGE &&
            messages.size > 1) {
            stageMode = StageMode.CONVERSATION
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && stageMode == StageMode.CONVERSATION) {
            listState.animateScrollToItem(messages.size - 1)
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
                .background(JarvisColors.VoidBlack)
        ) {
            // Faint technical grid + top cyan bleed (mockup background)
            HudBackground(
                modifier = Modifier.fillMaxSize(),
                glowColor = visualState.orbColor()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // ── Frosted-glass HUD header ────────────────────────────
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

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = stageMode,
                    transitionSpec = {
                        (fadeIn(tween(300)) + slideInVertically()).togetherWith(
                            fadeOut(tween(300)) + slideOutVertically()
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
                        isListening = visualState == JarvisVisualState.LISTENING
                    )
                }
            }

            // ── Navigation drawer (glass, slides over content) ────────────
            if (drawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x88000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { drawerOpen = false }
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(250.dp)
                            .clip(RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))
                            .background(JarvisColors.SurfaceGlassElevated)
                            .border(
                                0.8.dp, JarvisColors.Hairline,
                                RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            JarvisCore(state = visualState, size = 34.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "J A R V I S",
                                color = JarvisColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 3.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        DrawerItem(Icons.Default.Dashboard, "Command Deck") {
                            drawerOpen = false
                        }
                        DrawerItem(Icons.Default.FlashlightOn, "Device Control") {
                            onNavigate("device")
                            drawerOpen = false
                        }
                        DrawerItem(Icons.Default.Storage, "Memory") {
                            onNavigate("memory")
                            drawerOpen = false
                        }
                        DrawerItem(Icons.Default.KeyboardVoice, "Voice") {
                            onNavigate("voice")
                            drawerOpen = false
                        }
                        DrawerItem(Icons.Default.Settings, "Settings") {
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = JarvisColors.Presence,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = JarvisColors.TextPrimary,
            fontSize = 14.sp
        )
    }
}

// ── Top Header (frosted-glass HUD bar, mockup: slim glass strip) ───────────

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
        shape = RoundedCornerShape(16.dp),
        backgroundColor = JarvisColors.SurfaceGlassElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: mini arc-orb emblem
            Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                JarvisCore(
                    state = if (visualState == JarvisVisualState.OFFLINE)
                        JarvisVisualState.OFFLINE else JarvisVisualState.IDLE,
                    size = 34.dp,
                    onClick = onSwitchMode
                )
            }

            // Center: the wordmark
            Text(
                text = "J A R V I S",
                color = JarvisColors.TextPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp
            )

            // Right: status + waveform + actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (visualState) {
                        JarvisVisualState.IDLE -> "ONLINE"
                        JarvisVisualState.OFFLINE -> "OFFLINE"
                        else -> visualState.label.uppercase()
                    },
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                WaveformGlyph(active = visualState == JarvisVisualState.LISTENING ||
                        visualState == JarvisVisualState.SPEAKING, accent = accent)

                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onOpenDrawer, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = JarvisColors.TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = onSwitchMode, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = if (mode == StageMode.VOICE_STAGE)
                            Icons.Default.ChatBubbleOutline else Icons.Default.KeyboardVoice,
                        contentDescription = "Switch Mode",
                        tint = JarvisColors.TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = onEmergencyStop, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Stop",
                        tint = JarvisColors.StateError.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(30.dp)) {
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

/** Tiny live audio waveform glyph for the header (4 breathing bars). */
@Composable
private fun WaveformGlyph(active: Boolean, accent: Color) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = androidx.compose.animation.core.LinearEasing),
            RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { i ->
            val wave = kotlin.math.sin(Math.toRadians((phase + i * 55f).toDouble())).toFloat()
            val h = if (active) (8 + 8 * kotlin.math.abs(wave)).dp else 5.dp
            val alpha = if (active) 0.95f else 0.35f
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.dp))
                    .background(accent.copy(alpha = alpha))
            )
        }
    }
}

// ── Voice Stage View (mockup 02: hero orb + live transcript) ───────────────

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

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Greeting block
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "$greeting, $userName.",
                color = JarvisColors.TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "How can I help you?",
                color = JarvisColors.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        // Center: hero orb
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            JarvisCore(
                state = visualState,
                audioLevel = if (visualState == JarvisVisualState.LISTENING)
                    (0.3f + audioLevel * 0.7f).coerceIn(0f, 1f)
                else if (visualState == JarvisVisualState.SPEAKING) 0.35f else 0f,
                size = 250.dp,
                onClick = onOrbTap
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Live transcript glass bar (with blinking cursor while listening)
            if (visualState == JarvisVisualState.LISTENING && transcript.isNotBlank()) {
                GlassCard(
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = JarvisColors.SurfaceGlassElevated
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = transcript.take(80),
                            color = JarvisColors.TextPrimary,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        StreamingCursor(accent = accent)
                    }
                }
            } else {
                Text(
                    text = when (visualState) {
                        JarvisVisualState.IDLE -> "Standing by"
                        JarvisVisualState.WAKING -> "Initializing"
                        JarvisVisualState.LISTENING -> "Listening…"
                        JarvisVisualState.THINKING -> "Thinking"
                        JarvisVisualState.EXECUTING -> "Executing"
                        JarvisVisualState.SPEAKING -> "Speaking"
                        JarvisVisualState.SUCCESS -> "Done"
                        JarvisVisualState.ERROR -> "Something went wrong"
                        JarvisVisualState.OFFLINE -> "Offline"
                    },
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Bottom: quick-action chips + glowing mic pill
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            QuickActionChips(onQuickAction = onQuickAction)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                GlowMicButton(
                    isListening = visualState == JarvisVisualState.LISTENING,
                    onClick = onOrbTap,
                    size = 54.dp
                )
                Text(
                    text = if (visualState == JarvisVisualState.LISTENING)
                        "Listening — tap to stop" else "Tap to speak",
                    color = JarvisColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ── Quick action chips (4 minimal glass chips, mockup 02/03) ───────────────

@Composable
private fun QuickActionChips(onQuickAction: (String) -> Unit) {
    val chips = listOf(
        Triple("Screen", Icons.Default.Search, "What's on my screen?"),
        Triple("Apps", Icons.Default.Apps, "Open WhatsApp"),
        Triple("Alerts", Icons.Default.Notifications, "Read my notifications"),
        Triple("Torch", Icons.Default.FlashlightOn, "Turn on the flashlight")
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
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onQuickAction(query) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(JarvisColors.SurfaceGlass)
                        .border(0.8.dp, JarvisColors.Hairline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = JarvisColors.Presence,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = label,
                    color = JarvisColors.TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ── Conversation View (mockup 03: glass chat deck) ─────────────────────────

@Composable
private fun ConversationView(
    orchestrator: AssistantOrchestrator,
    visualState: JarvisVisualState,
    messages: List<AssistantMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOrbTap: () -> Unit
) {
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
                        size = 170.dp,
                        onClick = onOrbTap
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Standing by.",
                        color = JarvisColors.TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        message = msg,
                        showStreamingCursor = msg.role == MessageRole.JARVIS &&
                                msg.id == messages.lastOrNull()?.id &&
                                (visualState == JarvisVisualState.THINKING ||
                                        visualState == JarvisVisualState.SPEAKING ||
                                        visualState == JarvisVisualState.EXECUTING),
                        onConfirmTool = { req -> orchestrator.confirmToolExecution(req) },
                        onRejectTool = { orchestrator.rejectToolExecution() }
                    )
                }
                // "JARVIS is thinking" indicator while the reply streams in
                if (visualState == JarvisVisualState.THINKING &&
                    messages.lastOrNull()?.role == MessageRole.USER
                ) {
                    item(key = "thinking-indicator") {
                        GlassCard(
                            shape = RoundedCornerShape(
                                topStart = 4.dp, topEnd = 16.dp,
                                bottomStart = 16.dp, bottomEnd = 16.dp
                            ),
                            backgroundColor = JarvisColors.SurfaceGlassCyan
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ThinkingDots()
                                Text(
                                    text = "JARVIS is thinking",
                                    color = JarvisColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Chat Message Item (mockup 03: glass bubbles, cyan edge for JARVIS) ─────

@Composable
private fun ChatMessageItem(
    message: AssistantMessage,
    showStreamingCursor: Boolean = false,
    onConfirmTool: (com.jarvis.core.model.ToolExecutionRequest) -> Unit,
    onRejectTool: (() -> Unit)? = null
) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isUser && !isSystem) {
                // Cyan left edge accent for JARVIS bubbles (per mockup)
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(JarvisColors.Presence)
                )
            }
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        when {
                            isUser -> JarvisColors.SurfaceGlassElevated
                            isSystem -> JarvisColors.SurfaceGlass
                            else -> JarvisColors.SurfaceGlassCyan   // faint cyan tint
                        }
                    )
                    .border(0.5.dp, JarvisColors.Hairline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = message.text,
                        color = if (isSystem) JarvisColors.TextSecondary
                        else JarvisColors.TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    if (showStreamingCursor && message.text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        StreamingCursor()
                    }

                    // Tool confirmation card (risk >= 2)
                    if (message.toolCall != null &&
                        message.toolCall.requiresConfirmation &&
                        message.toolResult == null
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(JarvisColors.Warmth.copy(alpha = 0.08f))
                                .border(
                                    0.5.dp,
                                    JarvisColors.Warmth.copy(alpha = 0.25f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = JarvisColors.Warmth,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = message.toolCall.name,
                                        color = JarvisColors.Warmth,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(JarvisColors.Presence)
                                            .clickable { onConfirmTool(message.toolCall) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Confirm",
                                            color = JarvisColors.VoidBlack,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(JarvisColors.StateError.copy(alpha = 0.10f))
                                            .border(
                                                0.5.dp,
                                                JarvisColors.StateError.copy(alpha = 0.3f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable { onRejectTool?.invoke() }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            color = JarvisColors.StateError,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Chat Input Bar (mockup 03: floating glass pill + glowing mic) ──────────

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    isListening: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp)
    ) {
        // Shortcut chips floating above the pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "What's on screen?",
                "Read notifications",
                "Battery status",
                "Flashlight",
                "Set alarm",
                "Open WhatsApp"
            ).forEach { query ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(JarvisColors.SurfaceGlass)
                        .border(0.5.dp, JarvisColors.Hairline, RoundedCornerShape(14.dp))
                        .clickable { onValueChange(query) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = query,
                        color = JarvisColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // The glass pill
        GlassCard(
            shape = RoundedCornerShape(26.dp),
            backgroundColor = JarvisColors.SurfaceGlassElevated
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Ask JARVIS anything…",
                            color = JarvisColors.TextMuted,
                            fontSize = 15.sp
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(
                            color = JarvisColors.TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        ),
                        cursorBrush = SolidColor(JarvisColors.Presence),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (value.isNotBlank()) {
                    IconButton(onClick = onSend, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = JarvisColors.Presence,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                GlowMicButton(
                    isListening = isListening,
                    onClick = onVoiceClick,
                    size = 42.dp
                )
            }
        }
    }
}
