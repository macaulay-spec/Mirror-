package com.jarvis.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.jarvis.core.model.AssistantMessage
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.model.MessageRole
import com.jarvis.core.model.ToolExecutionRequest
import com.jarvis.core.theme.JarvisColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * Premium Holographic Chat UI Component for JARVIS.
 * Provides modular, high-performance messaging bubbles, responsive input bars,
 * quick action chips, and real-time visual status telemetry.
 */

@Composable
fun JarvisChatHeader(
    visualState: JarvisVisualState,
    title: String = "JARVIS NEURAL CHAT",
    onSwitchMode: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onEmergencyStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = visualState.accent()

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        backgroundColor = Color(0x1200E5FF),
        borderColor = JarvisColors.BorderCyan.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onSwitchMode?.invoke() }
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        color = JarvisColors.TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "CORE STATUS: ${visualState.label.uppercase()}",
                        color = accent,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onSwitchMode != null) {
                    IconButton(
                        onClick = onSwitchMode,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardVoice,
                            contentDescription = "Voice Mode",
                            tint = JarvisColors.CyanBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (onEmergencyStop != null) {
                    IconButton(
                        onClick = onEmergencyStop,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Emergency Stop",
                            tint = JarvisColors.CrimsonAlert.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (onOpenSettings != null) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = JarvisColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JarvisChatMessageItem(
    message: AssistantMessage,
    onConfirmTool: ((ToolExecutionRequest) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM

    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(message.timestamp))
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { 20 })
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isUser) Color(0x2400E5FF)
                        else if (isSystem) Color(0x20330000)
                        else Color(0x1C0E1626)
                    )
                    .border(
                        1.dp,
                        if (isUser) JarvisColors.CyanPrimary.copy(alpha = 0.45f)
                        else if (isSystem) JarvisColors.CrimsonAlert.copy(alpha = 0.4f)
                        else JarvisColors.BorderCyan.copy(alpha = 0.25f),
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isUser) Icons.Default.Person else if (isSystem) Icons.Default.Warning else Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = if (isUser) JarvisColors.CyanBright else if (isSystem) JarvisColors.CrimsonAlert else JarvisColors.TealSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isUser) "YOU" else if (isSystem) "SYSTEM" else "JARVIS AI",
                                color = if (isUser) JarvisColors.CyanBright else if (isSystem) JarvisColors.CrimsonAlert else JarvisColors.TealSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = timeStr,
                            color = JarvisColors.TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = message.text,
                        color = JarvisColors.TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Default,
                        lineHeight = 19.sp
                    )

                    // Interactive Tool Confirmation Pill
                    if (message.toolCall != null && message.toolCall.requiresConfirmation && message.toolResult == null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(JarvisColors.AmberWarning.copy(alpha = 0.18f))
                                .border(1.dp, JarvisColors.AmberWarning.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "⚡ ACTION APPROVAL: ${message.toolCall.name.uppercase()}",
                                    color = JarvisColors.AmberWarning,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(JarvisColors.AmberWarning)
                                        .clickable { onConfirmTool?.invoke(message.toolCall) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "CONFIRM & AUTHORIZE",
                                        color = Color.Black,
                                        fontSize = 10.sp,
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
fun JarvisChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    isListening: Boolean,
    modifier: Modifier = Modifier,
    quickPrompts: List<String> = listOf("Battery Status", "Device Info", "Volume Up", "Open Browser", "Flashlight On")
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Quick Action Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickPrompts.forEach { prompt ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x1800E5FF))
                        .border(0.8.dp, JarvisColors.BorderCyan.copy(alpha = 0.35f), CircleShape)
                        .clickable { onValueChange(prompt) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = prompt,
                        color = JarvisColors.CyanBright,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Futuristic Holographic Text Input Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0x220E1626))
                .border(1.2.dp, JarvisColors.BorderCyan.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Capture",
                        tint = if (isListening) JarvisColors.CrimsonAlert else JarvisColors.CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(
                            color = JarvisColors.TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Default
                        ),
                        cursorBrush = SolidColor(JarvisColors.CyanPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (value.isEmpty()) {
                        Text(
                            text = "Command JARVIS core...",
                            color = JarvisColors.TextMuted,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Default
                        )
                    }
                }

                IconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Command",
                        tint = if (value.isNotBlank()) JarvisColors.CyanPrimary else JarvisColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun JarvisChatView(
    visualState: JarvisVisualState,
    messages: List<AssistantMessage>,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    onConfirmTool: (ToolExecutionRequest) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        JarvisChatHeader(
            visualState = visualState,
            onSwitchMode = onVoiceClick
        )

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Awaiting intent. Speak or type a command below.",
                            color = JarvisColors.TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                JarvisChatMessageItem(
                    message = msg,
                    onConfirmTool = onConfirmTool
                )
            }
        }

        JarvisChatInputBar(
            value = inputText,
            onValueChange = onInputTextChange,
            onSend = onSend,
            onVoiceClick = onVoiceClick,
            isListening = visualState == JarvisVisualState.LISTENING
        )
    }
}
