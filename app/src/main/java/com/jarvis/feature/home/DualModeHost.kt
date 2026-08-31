package com.jarvis.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.agent.orchestrator.AssistantOrchestrator
import com.jarvis.app.config.ApiConfig
import com.jarvis.core.model.AssistantMessage
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.model.MessageRole
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.HolographicDivider
import com.jarvis.core.ui.JarvisCore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StageMode {
    VOICE_STAGE,
    CONVERSATION
}

/**
 * Dual-mode host: Voice Stage (orb-centric) and Conversation (chat-centric).
 *
 * Design fixes:
 * - Message list fills available vertical space (no dead zone)
 * - Empty state shows Orb centered with "Standing by."
 * - Quick-command chips always visible, never overlapping messages
 * - No duplicate Orb graphics
 * - Uses new design tokens (graphite-blue, presence/warmth accents)
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DualModeHost(
    orchestrator: AssistantOrchestrator,
    onOpenSettings: () -> Unit = {},
    onToggleVoice: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val visualState by orchestrator.visualState.collectAsState()
    val messages by orchestrator.messages.collectAsState()

    var stageMode by remember { mutableStateOf(StageMode.VOICE_STAGE) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Call duration timer
    var callSeconds by remember { mutableIntStateOf(0) }
    val isCallActive = visualState == JarvisVisualState.LISTENING ||
            visualState == JarvisVisualState.SPEAKING ||
            visualState == JarvisVisualState.THINKING

    LaunchedEffect(isCallActive) {
        if (isCallActive) {
            callSeconds = 0
            while (true) {
                delay(1000)
                callSeconds++
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && stageMode == StageMode.CONVERSATION) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val userName = ApiConfig.userName

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JarvisColors.VoidBlack
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Top header
                TopPresenceHeader(
                    mode = stageMode,
                    visualState = visualState,
                    callDurationSeconds = if (isCallActive) callSeconds else null,
                    onSwitchMode = {
                        stageMode = if (stageMode == StageMode.VOICE_STAGE)
                            StageMode.CONVERSATION else StageMode.VOICE_STAGE
                    },
                    onOpenSettings = onOpenSettings,
                    onEmergencyStop = { orchestrator.emergencyStop() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Main content area — fills available space
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
                                isCallActive = isCallActive,
                                callSeconds = callSeconds,
                                lastMessage = messages.lastOrNull()?.text,
                                onOrbTap = { onToggleVoice() },
                                onSwitchToChat = { stageMode = StageMode.CONVERSATION }
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

                // Bottom input — only in conversation mode
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
        }
    }
}

// ── Top Header ────────────────────────────────────────────────────────────

@Composable
private fun TopPresenceHeader(
    mode: StageMode,
    visualState: JarvisVisualState,
    callDurationSeconds: Int?,
    onSwitchMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    val accent by animateColorAsState(
        targetValue = visualState.orbColor(),
        animationSpec = spring(),
        label = "headerAccent"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSwitchMode() }
        ) {
            Text(
                text = "JARVIS",
                color = JarvisColors.TextPrimary,
                fontSize = 18.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (callDurationSeconds != null) {
                    val mins = callDurationSeconds / 60
                    val secs = callDurationSeconds % 60
                    "Live %02d:%02d".format(mins, secs)
                } else {
                    visualState.label
                },
                color = accent,
                fontSize = 12.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onSwitchMode,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (mode == StageMode.VOICE_STAGE)
                        Icons.Default.ChatBubbleOutline else Icons.Default.KeyboardVoice,
                    contentDescription = "Switch Mode",
                    tint = JarvisColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onEmergencyStop,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Stop",
                    tint = JarvisColors.StateError.copy(alpha = 0.8f),
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = JarvisColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── Voice Stage View ──────────────────────────────────────────────────────

@Composable
private fun VoiceStageView(
    visualState: JarvisVisualState,
    userName: String,
    isCallActive: Boolean,
    callSeconds: Int,
    lastMessage: String?,
    onOrbTap: () -> Unit,
    onSwitchToChat: () -> Unit
) {
    val accent by animateColorAsState(
        targetValue = visualState.orbColor(),
        animationSpec = spring(),
        label = "voiceAccent"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Status bar — clean, no holographic gimmicks
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Core",
                        color = JarvisColors.TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default
                    )
                    Text(
                        text = ApiConfig.getProviderLabel(),
                        color = JarvisColors.Presence,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Overlay",
                        color = JarvisColors.TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default
                    )
                    Text(
                        text = if (com.jarvis.android.overlay.JarvisFloatingOrbService.isRunning)
                            "Active" else "Ready",
                        color = if (com.jarvis.android.overlay.JarvisFloatingOrbService.isRunning)
                            JarvisColors.Presence else JarvisColors.TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Center: Orb + status text
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                JarvisCore(
                    state = visualState,
                    audioLevel = if (visualState == JarvisVisualState.LISTENING) 0.45f
                    else if (visualState == JarvisVisualState.SPEAKING) 0.3f else 0f,
                    size = 220.dp,
                    onClick = onOrbTap
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = when (visualState) {
                        JarvisVisualState.IDLE -> "Standing by, $userName."
                        JarvisVisualState.WAKING -> "Initializing..."
                        JarvisVisualState.LISTENING -> "Listening..."
                        JarvisVisualState.THINKING -> "Thinking..."
                        JarvisVisualState.EXECUTING -> "Working on it..."
                        JarvisVisualState.SPEAKING -> "Speaking..."
                        JarvisVisualState.SUCCESS -> "Done."
                        JarvisVisualState.ERROR -> "Something went wrong."
                        JarvisVisualState.OFFLINE -> "Offline mode."
                    },
                    color = JarvisColors.TextPrimary,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                if (lastMessage != null && visualState != JarvisVisualState.IDLE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = lastMessage.take(120),
                        color = JarvisColors.TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Default,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mic button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (visualState == JarvisVisualState.LISTENING)
                            JarvisColors.StateError.copy(alpha = 0.15f)
                        else JarvisColors.SurfaceGlass
                    )
                    .border(
                        1.dp,
                        if (visualState == JarvisVisualState.LISTENING)
                            JarvisColors.StateError.copy(alpha = 0.4f)
                        else JarvisColors.Hairline,
                        CircleShape
                    )
                    .clickable { onOrbTap() }
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (visualState == JarvisVisualState.LISTENING)
                            Icons.Default.CallEnd else Icons.Default.Mic,
                        contentDescription = "Mic",
                        tint = if (visualState == JarvisVisualState.LISTENING)
                            JarvisColors.StateError else JarvisColors.Presence,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (visualState == JarvisVisualState.LISTENING)
                            "End session" else "Tap to speak",
                        color = JarvisColors.TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Quick switch hint
            Text(
                text = "Tap the orb or accessibility button to toggle overlay",
                color = JarvisColors.TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Default,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

// ── Conversation View ─────────────────────────────────────────────────────

@Composable
private fun ConversationView(
    orchestrator: AssistantOrchestrator,
    visualState: JarvisVisualState,
    messages: List<AssistantMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOrbTap: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Docked mini-orb status
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JarvisCore(
                        state = visualState,
                        audioLevel = if (visualState == JarvisVisualState.LISTENING) 0.35f else 0f,
                        size = 36.dp,
                        onClick = onOrbTap
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = visualState.label,
                            color = visualState.orbColor(),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Tap orb for voice",
                            color = JarvisColors.TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Default
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Message list — fills available space, no dead zone
        if (messages.isEmpty()) {
            // Empty state: centered Orb + "Standing by."
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Standing by.",
                        color = JarvisColors.TextMuted,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        message = msg,
                        onConfirmTool = { req -> orchestrator.confirmToolExecution(req) },
                        onRejectTool = { orchestrator.rejectToolExecution() }
                    )
                }
            }
        }
    }
}

// ── Chat Message Item ─────────────────────────────────────────────────────

@Composable
private fun ChatMessageItem(
    message: AssistantMessage,
    onConfirmTool: (com.jarvis.core.model.ToolExecutionRequest) -> Unit,
    onRejectTool: (() -> Unit)? = null
) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM

    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
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
                    if (isUser) JarvisColors.Presence.copy(alpha = 0.08f)
                    else if (isSystem) JarvisColors.SurfaceGlass
                    else JarvisColors.DarkSpace
                )
                .border(
                    0.5.dp,
                    JarvisColors.Hairline,
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isUser) "You" else if (isSystem) "System" else "Jarvis",
                        color = if (isUser) JarvisColors.Presence
                        else if (isSystem) JarvisColors.TextMuted
                        else JarvisColors.Warmth.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = timeStr,
                        color = JarvisColors.TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Default
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message.text,
                    color = JarvisColors.TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Default,
                    lineHeight = 22.sp
                )

                // Tool confirmation card
                if (message.toolCall != null && message.toolCall.requiresConfirmation && message.toolResult == null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(JarvisColors.Warmth.copy(alpha = 0.08f))
                            .border(0.5.dp, JarvisColors.Warmth.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
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
                                    fontFamily = FontFamily.Default,
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
                                        .background(JarvisColors.Warmth)
                                        .clickable { onConfirmTool(message.toolCall) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Confirm",
                                        color = JarvisColors.VoidBlack,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Default,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(JarvisColors.StateError.copy(alpha = 0.10f))
                                        .border(0.5.dp, JarvisColors.StateError.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                        .clickable { onRejectTool?.invoke() }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Cancel",
                                        color = JarvisColors.StateError,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Default,
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

// ── Chat Input Bar ────────────────────────────────────────────────────────

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
        // Quick action chips — always visible, horizontal scroll
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Read notifications",
                "What's on screen?",
                "Battery status",
                "Flashlight",
                "Volume up",
                "Set alarm",
                "Open WhatsApp",
                "Remember this"
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
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }

        // Input field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(JarvisColors.DarkSpace)
                .border(0.5.dp, JarvisColors.Hairline, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = if (isListening) JarvisColors.StateError else JarvisColors.Presence,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Ask Jarvis anything...",
                            color = JarvisColors.TextMuted,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Default
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(
                            color = JarvisColors.TextPrimary,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Default,
                            lineHeight = 22.sp
                        ),
                        cursorBrush = SolidColor(JarvisColors.Presence),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                IconButton(
                    onClick = onSend,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (value.isNotBlank()) JarvisColors.Presence else JarvisColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
