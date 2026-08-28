package com.jarvis.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
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
    val isCallActive = visualState == JarvisVisualState.LISTENING || visualState == JarvisVisualState.SPEAKING || visualState == JarvisVisualState.THINKING

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
                            Color(0xFF03060E),
                            Color(0xFF070D18),
                            Color(0xFF020408)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Top Minimal HUD
                TopPresenceHeader(
                    mode = stageMode,
                    visualState = visualState,
                    callDurationSeconds = if (isCallActive) callSeconds else null,
                    onSwitchMode = {
                        stageMode = if (stageMode == StageMode.VOICE_STAGE) StageMode.CONVERSATION else StageMode.VOICE_STAGE
                    },
                    onOpenSettings = onOpenSettings,
                    onEmergencyStop = { orchestrator.emergencyStop() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Shared Living Orb Morphing Container
                AnimatedContent(
                    targetState = stageMode,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(400)) + slideInVertically()).togetherWith(
                            fadeOut(animationSpec = tween(400)) + slideOutVertically()
                        )
                    },
                    modifier = Modifier.weight(1f),
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

                // Bottom Input & Controls
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

@Composable
private fun TopPresenceHeader(
    mode: StageMode,
    visualState: JarvisVisualState,
    callDurationSeconds: Int?,
    onSwitchMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    val accent = visualState.accent()

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
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
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
                    "LIVE %02d:%02d".format(mins, secs)
                } else {
                    visualState.label.uppercase()
                },
                color = accent,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onSwitchMode,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (mode == StageMode.VOICE_STAGE) Icons.Default.ChatBubbleOutline else Icons.Default.KeyboardVoice,
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
                    tint = JarvisColors.CrimsonAlert.copy(alpha = 0.8f),
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
    val accent = visualState.accent()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Holographic Telemetry Bar
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            backgroundColor = Color(0x1000E5FF),
            borderColor = JarvisColors.BorderCyan.copy(alpha = 0.35f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = JarvisColors.CyanBright,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "CORE: GEMINI 2.5",
                        color = JarvisColors.CyanBright,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = JarvisColors.TealSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "AUDIO: ${ApiConfig.voiceEngineType.uppercase()}",
                        color = JarvisColors.TealSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = if (com.jarvis.android.overlay.JarvisFloatingOrbService.isRunning) JarvisColors.CyanBright else JarvisColors.TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (com.jarvis.android.overlay.JarvisFloatingOrbService.isRunning) "OVERLAY: ON" else "OVERLAY: READY",
                        color = if (com.jarvis.android.overlay.JarvisFloatingOrbService.isRunning) JarvisColors.CyanBright else JarvisColors.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Center Hero Orb Area
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
                    audioLevel = if (visualState == JarvisVisualState.LISTENING) 0.45f else if (visualState == JarvisVisualState.SPEAKING) 0.3f else 0f,
                    size = 250.dp,
                    onClick = onOrbTap
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = when (visualState) {
                        JarvisVisualState.IDLE -> "Standing by, $userName."
                        JarvisVisualState.WAKING -> "Initializing neural core..."
                        JarvisVisualState.LISTENING -> "Listening to speech..."
                        JarvisVisualState.THINKING -> "Processing neural intent..."
                        JarvisVisualState.EXECUTING -> "Executing device tools..."
                        JarvisVisualState.SPEAKING -> "Transmitting response..."
                        JarvisVisualState.SUCCESS -> "Action completed."
                        JarvisVisualState.ERROR -> "Subsystem notice."
                        JarvisVisualState.OFFLINE -> "Local protocols active."
                    },
                    color = JarvisColors.TextPrimary,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )

                if (lastMessage != null && visualState != JarvisVisualState.IDLE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = lastMessage.take(120),
                        color = JarvisColors.TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }

        // Voice Stage Controls & Quick Mode
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (visualState == JarvisVisualState.LISTENING) JarvisColors.CrimsonAlert.copy(alpha = 0.25f) else Color(0x1400E5FF)
                    )
                    .border(
                        1.dp,
                        if (visualState == JarvisVisualState.LISTENING) JarvisColors.CrimsonAlert else JarvisColors.BorderCyan,
                        CircleShape
                    )
                    .clickable { onOrbTap() }
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (visualState == JarvisVisualState.LISTENING) Icons.Default.CallEnd else Icons.Default.Mic,
                        contentDescription = "Mic",
                        tint = if (visualState == JarvisVisualState.LISTENING) JarvisColors.CrimsonAlert else JarvisColors.CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (visualState == JarvisVisualState.LISTENING) "END VOICE SESSION" else "TAP TO SPEAK",
                        color = JarvisColors.TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Text(
                text = "⚡ Press Accessibility Button or swipe edge to toggle HUD overlay over any app",
                color = JarvisColors.TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun ConversationView(
    orchestrator: AssistantOrchestrator,
    visualState: JarvisVisualState,
    messages: List<AssistantMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOrbTap: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Docked Mini Orb Status Bar
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            backgroundColor = Color(0x1000E5FF),
            borderColor = JarvisColors.BorderCyan.copy(alpha = 0.25f)
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
                        size = 44.dp,
                        onClick = onOrbTap
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "STATUS: ${visualState.label.uppercase()}",
                            color = visualState.accent(),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap orb to toggle speech capture",
                            color = JarvisColors.TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Transcript
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Awaiting command. Speak or type below.",
                            color = JarvisColors.TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                ChatMessageItem(
                    message = msg,
                    onConfirmTool = { req ->
                        orchestrator.confirmToolExecution(req)
                    },
                    onRejectTool = {
                        orchestrator.rejectToolExecution()
                    }
                )
            }
        }
    }
}

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
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 14.dp
                    )
                )
                .background(
                    if (isUser) Color(0x1F00E5FF)
                    else if (isSystem) Color(0x18FFFFFF)
                    else Color(0x180E1626)
                )
                .border(
                    0.8.dp,
                    if (isUser) JarvisColors.CyanPrimary.copy(alpha = 0.35f) else JarvisColors.BorderCyan.copy(alpha = 0.2f),
                    RoundedCornerShape(14.dp)
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
                        text = if (isUser) "YOU" else if (isSystem) "SYSTEM" else "JARVIS",
                        color = if (isUser) JarvisColors.CyanBright else if (isSystem) Color.White else JarvisColors.TealSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = timeStr,
                        color = JarvisColors.TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message.text,
                    color = JarvisColors.TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Default,
                    lineHeight = 18.sp
                )

                // Tool Execution Confirmation Card (Risk Tier 2)
                if (message.toolCall != null && message.toolCall.requiresConfirmation && message.toolResult == null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(JarvisColors.AmberWarning.copy(alpha = 0.15f))
                            .border(1.dp, JarvisColors.AmberWarning.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
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
                                    tint = JarvisColors.AmberWarning,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "CONFIRMATION PROTOCOL: ${message.toolCall.name.uppercase()}",
                                    color = JarvisColors.AmberWarning,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(JarvisColors.AmberWarning)
                                        .clickable { onConfirmTool(message.toolCall) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "CONFIRM & EXECUTE",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x25FF0055))
                                        .border(1.dp, Color(0x66FF0055), RoundedCornerShape(8.dp))
                                        .clickable { onRejectTool?.invoke() }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "CANCEL",
                                        color = Color(0xFFFF5588),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
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
        // Quick Action Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Read Notifications",
                "What's on screen?",
                "Read my OTP code",
                "Battery & Network",
                "Flashlight Toggle",
                "Volume 80%",
                "Set alarm 7 AM",
                "Open WhatsApp",
                "Remember Mumsi is my Mom"
            ).forEach { query ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x1400E5FF))
                        .border(0.8.dp, JarvisColors.BorderCyan.copy(alpha = 0.3f), CircleShape)
                        .clickable {
                            onValueChange(query)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = query,
                        color = JarvisColors.CyanBright,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Input Field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x180E1626))
                .border(1.dp, JarvisColors.BorderCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
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
                        tint = if (isListening) JarvisColors.CrimsonAlert else JarvisColors.CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(
                            color = JarvisColors.TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Default
                        ),
                        cursorBrush = SolidColor(JarvisColors.CyanPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (value.isEmpty()) {
                        Text(
                            text = "Command JARVIS...",
                            color = JarvisColors.TextMuted,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Default
                        )
                    }
                }

                IconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank(),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (value.isNotBlank()) JarvisColors.CyanPrimary else JarvisColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
