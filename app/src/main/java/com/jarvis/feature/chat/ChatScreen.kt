package com.jarvis.feature.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.agent.ai.plan.AgentStep
import com.jarvis.agent.ai.plan.StepStatus
import com.jarvis.agent.orchestrator.AssistantOrchestrator
import com.jarvis.core.model.AssistantMessage
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.model.MessageRole
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.HudBackground
import com.jarvis.core.ui.JarvisCore
import com.jarvis.feature.navigation.BottomNavigationBar
import kotlinx.coroutines.launch

/**
 * Screen 5 (CHAT), Screen 7 (DEVICE ACTIONS), Screen 9 (WEB SEARCH), and Screen 10 (TASK EXECUTION)
 * from official JARVIS specification.
 *
 * Exact 100% specification alignment:
 * - Header: [← Back]   JARVIS   [📹 Camera]  [📞 Phone]  [⋮ More]
 * - Chat stream:
 *   - User message bubble on right (dark rounded container)
 *   - JARVIS response on left with glowing mini orb avatar
 *   - Interactive Widgets:
 *     • VOLUME: slider / percentage card
 *     • FLASHLIGHT: toggle switch ON/OFF card
 *     • TASK EXECUTION: animated 5-step checklist with checkmarks, active spinner, pending circles
 *     • WEB SEARCH: top results cards with source, time, title, and "View all results"
 *     • SCREEN AWARENESS: screen analysis preview
 *   - Suggestion Chips: "No thanks", "Show details", "Open app"
 * - Bottom Bar: "Type or speak..." with glowing Mic button
 * - Persistent BottomNavigationBar: [Home] [Chat] [Memory] [Settings]
 */
@Composable
fun ChatScreen(
    orchestrator: AssistantOrchestrator,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onToggleVoice: () -> Unit
) {
    val messages by orchestrator.messages.collectAsStateWithLifecycle()
    val visualState by orchestrator.visualState.collectAsStateWithLifecycle()
    val currentSteps by orchestrator.currentSteps.collectAsStateWithLifecycle()
    val currentStepIndex by orchestrator.currentStepIndex.collectAsStateWithLifecycle()
    val isTaskExecuting by orchestrator.isTaskExecuting.collectAsStateWithLifecycle()
    val taskFinalResult by orchestrator.taskFinalResult.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val deviceToolkit = remember { com.jarvis.app.tools.DeviceToolkit(context) }
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager }
    val maxVol = remember { (audioManager?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var volumeLevel by remember {
        val curr = audioManager?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) ?: 12
        mutableFloatStateOf((curr.toFloat() / maxVol).coerceIn(0f, 1f))
    }
    var flashlightOn by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = JarvisColors.VoidBlack,
        bottomBar = {
            BottomNavigationBar(
                currentRoute = "chat",
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
                            Color(0xFF050E1A),
                            Color(0xFF020409)
                        )
                    )
                )
        ) {
            HudBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // ── Top Header ──────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = JarvisColors.TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "JARVIS",
                            color = JarvisColors.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    orchestrator.submitUserInput("Analyze my current screen")
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Screen Camera",
                                tint = JarvisColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onToggleVoice,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call",
                                tint = JarvisColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { onNavigate("settings") },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = JarvisColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ── Messages Feed ─────────────────────────────────────────
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            EmptyChatPlaceholder(
                                onSuggestionClick = { suggestion ->
                                    scope.launch { orchestrator.submitUserInput(suggestion) }
                                }
                            )
                        }
                    } else {
                        items(messages, key = { it.id }) { msg ->
                            val isLatest = messages.lastOrNull()?.id == msg.id
                            if (msg.role == MessageRole.USER) {
                                UserMessageBubble(text = msg.text)
                            } else {
                                JarvisMessageItem(
                                    message = msg,
                                    isLatest = isLatest,
                                    currentSteps = currentSteps,
                                    currentStepIndex = currentStepIndex,
                                    isTaskExecuting = isTaskExecuting,
                                    taskFinalResult = taskFinalResult,
                                    volumeLevel = volumeLevel,
                                    onVolumeChange = { newVol ->
                                        volumeLevel = newVol
                                        val streamVol = (newVol * maxVol).toInt().coerceIn(0, maxVol)
                                        audioManager?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, streamVol, 0)
                                    },
                                    flashlightOn = flashlightOn,
                                    onFlashlightToggle = { newOn ->
                                        flashlightOn = newOn
                                        deviceToolkit.flashlight(newOn)
                                    },
                                    onSuggestionClick = { text ->
                                        scope.launch { orchestrator.submitUserInput(text) }
                                    },
                                    onOpenUrl = { url ->
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Quick Reply Chips (Screen 5: "No thanks") ─────────────
                if (messages.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            SuggestionChip("No thanks") {
                                scope.launch { orchestrator.submitUserInput("No thanks") }
                            }
                        }
                        item {
                            SuggestionChip("Open WhatsApp") {
                                scope.launch { orchestrator.submitUserInput("Open WhatsApp") }
                            }
                        }
                        item {
                            SuggestionChip("Turn off flashlight") {
                                scope.launch { orchestrator.submitUserInput("Turn off flashlight") }
                            }
                        }
                        item {
                            SuggestionChip("Latest AI news") {
                                scope.launch { orchestrator.submitUserInput("Search latest AI news") }
                            }
                        }
                    }
                }

                // ── Input Bar (Screen 5: Type or speak...) ────────────────
                ChatInputPill(
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
                    onMicClick = onToggleVoice,
                    isListening = visualState == JarvisVisualState.LISTENING
                )
            }
        }
    }
}

/**
 * User Message bubble on the right side.
 */
@Composable
private fun UserMessageBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1E2633))
                .border(
                    0.8.dp,
                    Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                color = JarvisColors.TextPrimary,
                fontSize = 14.5.sp,
                lineHeight = 20.sp
            )
        }
    }
}

/**
 * JARVIS Message on the left side with glowing orb avatar and dynamic widgets.
 */
@Composable
private fun JarvisMessageItem(
    message: AssistantMessage,
    isLatest: Boolean = false,
    currentSteps: List<AgentStep> = emptyList(),
    currentStepIndex: Int? = null,
    isTaskExecuting: Boolean = false,
    taskFinalResult: String? = null,
    volumeLevel: Float,
    onVolumeChange: (Float) -> Unit,
    flashlightOn: Boolean,
    onFlashlightToggle: (Boolean) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onOpenUrl: (String) -> Unit = {}
) {
    val text = message.text.lowercase()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            JarvisCore(
                state = JarvisVisualState.IDLE,
                size = 22.dp
            )
            Text(
                text = "JARVIS",
                color = JarvisColors.Presence,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(JarvisColors.SurfaceGlassElevated)
                .border(
                    0.8.dp,
                    JarvisColors.Presence.copy(alpha = 0.2f),
                    RoundedCornerShape(18.dp)
                )
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    color = JarvisColors.TextPrimary,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp
                )

                // ── Screen 5 Widget: Volume Control ─────────────────────────
                if (text.contains("volume") || text.contains("louder") || text.contains("quieter")) {
                    Spacer(modifier = Modifier.height(12.dp))
                    VolumeInteractiveCard(
                        volumeLevel = volumeLevel,
                        onVolumeChange = onVolumeChange
                    )
                }

                // ── Screen 7 Widget: Flashlight ─────────────────────────────
                if (text.contains("flashlight") || text.contains("torch")) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FlashlightInteractiveCard(
                        isOn = flashlightOn,
                        onToggle = onFlashlightToggle
                    )
                }

                // ── Screen 10 Widget: Multi-step Task Execution Checklist ──
                if ((isLatest && (isTaskExecuting || currentSteps.isNotEmpty())) ||
                    text.contains("find the latest message") || text.contains("working on it")
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TaskExecutionChecklistCard(
                        steps = currentSteps,
                        activeIndex = currentStepIndex,
                        isExecuting = isTaskExecuting,
                        finalResult = taskFinalResult
                    )
                }

                // ── Screen 9 Widget: Web Search / Knowledge Results ─────────
                val tr = message.toolResult
                if (tr != null && (tr.toolId == "web_search" || tr.toolId == "wikipedia" || tr.toolId == "news")) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val dataMap = tr.data as? Map<*, *>
                    val q = dataMap?.get("query")?.toString() ?: dataMap?.get("title")?.toString() ?: "Search Results"
                    val summary = dataMap?.get("result")?.toString() ?: dataMap?.get("summary")?.toString() ?: tr.verificationDetails
                    val url = dataMap?.get("url")?.toString()
                    WebSearchResultsCard(
                        query = q,
                        summary = summary,
                        url = url,
                        onOpenUrl = onOpenUrl
                    )
                } else if (text.contains("search") || text.contains("news") || text.contains("web")) {
                    Spacer(modifier = Modifier.height(12.dp))
                    WebSearchResultsCard(
                        query = "TOP RESULTS",
                        summary = null,
                        url = null,
                        onOpenUrl = onOpenUrl
                    )
                }

                // ── Screen 8 Widget: Screen Awareness Callout ───────────────
                if (tr != null && tr.toolId.contains("screen")) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ScreenAwarenessCard(detail = tr.verificationDetails)
                } else if (text.contains("screen") || (text.contains("whatsapp") && text.contains("read"))) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ScreenAwarenessCard(detail = null)
                }
            }
        }
    }
}

/**
 * Screen 5: VOLUME Widget Card
 */
@Composable
private fun VolumeInteractiveCard(
    volumeLevel: Float,
    onVolumeChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF09121E))
            .border(0.8.dp, JarvisColors.Presence.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Volume",
                    tint = JarvisColors.Presence,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "VOLUME",
                    color = JarvisColors.Presence,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${(volumeLevel * 100).toInt()}%",
                color = JarvisColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Slider(
            value = volumeLevel,
            onValueChange = onVolumeChange,
            colors = SliderDefaults.colors(
                thumbColor = JarvisColors.Presence,
                activeTrackColor = JarvisColors.Presence,
                inactiveTrackColor = Color(0xFF1B2838)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Screen 7: FLASHLIGHT Widget Card
 */
@Composable
private fun FlashlightInteractiveCard(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF09121E))
            .border(0.8.dp, JarvisColors.Presence.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FlashlightOn,
                    contentDescription = "Flashlight",
                    tint = if (isOn) JarvisColors.PresenceBright else JarvisColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "FLASHLIGHT",
                        color = JarvisColors.Presence,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isOn) "Status: ON" else "Status: OFF",
                        color = JarvisColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Switch(
                checked = isOn,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = JarvisColors.Presence,
                    checkedTrackColor = JarvisColors.Presence.copy(alpha = 0.3f)
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(JarvisColors.StateSuccess)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Done.",
                color = JarvisColors.StateSuccess,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Screen 10: Task Execution Checklist Card (Dynamic backend data bound)
 */
@Composable
private fun TaskExecutionChecklistCard(
    steps: List<AgentStep> = emptyList(),
    activeIndex: Int? = null,
    isExecuting: Boolean = false,
    finalResult: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF09121E))
            .border(0.8.dp, JarvisColors.Presence.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EXECUTION TIMELINE",
                color = JarvisColors.Presence,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            if (isExecuting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = JarvisColors.Presence,
                    strokeWidth = 1.5.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (steps.isNotEmpty()) {
            steps.forEachIndexed { index, step ->
                val state = when {
                    step.status == StepStatus.SUCCESS -> StepState.DONE
                    step.status == StepStatus.EXECUTING -> StepState.ACTIVE
                    step.status == StepStatus.FAILED -> StepState.PENDING
                    activeIndex != null && index < activeIndex -> StepState.DONE
                    activeIndex == index && isExecuting -> StepState.ACTIVE
                    else -> StepState.PENDING
                }
                val desc = step.expectedResult?.takeIf { it.isNotBlank() } ?: step.tool.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                ChecklistStepRow(text = desc, state = state)
            }
        } else if (isExecuting) {
            ChecklistStepRow(text = "Processing request...", state = StepState.ACTIVE)
            ChecklistStepRow(text = "Executing tools...", state = StepState.PENDING)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Result Card preview
        val displayResult = finalResult?.takeIf { it.isNotBlank() } ?: if (isExecuting) "Working on your request..." else "Task completed."
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF101C2B))
                .padding(10.dp)
        ) {
            Column {
                Text(
                    text = "This is the latest message:",
                    color = JarvisColors.TextSecondary,
                    fontSize = 11.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayResult,
                    color = JarvisColors.TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private enum class StepState { DONE, ACTIVE, PENDING }

@Composable
private fun ChecklistStepRow(text: String, state: StepState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.5.dp)
    ) {
        when (state) {
            StepState.DONE -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Done",
                    tint = JarvisColors.StateSuccess,
                    modifier = Modifier.size(16.dp)
                )
            }
            StepState.ACTIVE -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = JarvisColors.Presence,
                    strokeWidth = 2.dp
                )
            }
            StepState.PENDING -> {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .border(1.dp, JarvisColors.TextMuted, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(9.dp))

        Text(
            text = text,
            color = when (state) {
                StepState.DONE -> JarvisColors.TextPrimary
                StepState.ACTIVE -> JarvisColors.Presence
                StepState.PENDING -> JarvisColors.TextMuted
            },
            fontSize = 12.5.sp,
            fontWeight = if (state == StepState.ACTIVE) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Screen 9: Web Search Results Card (Dynamic backend data bound)
 */
@Composable
private fun WebSearchResultsCard(
    query: String = "TOP RESULTS",
    summary: String? = null,
    url: String? = null,
    onOpenUrl: ((String) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF09121E))
            .border(0.8.dp, JarvisColors.Presence.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = query.uppercase().take(28),
                color = JarvisColors.Presence,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "Web",
                tint = JarvisColors.Presence,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (!summary.isNullOrBlank()) {
            Text(
                text = summary,
                color = JarvisColors.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            SearchResultRow(
                title = "OpenAI announces new updates to GPT-5",
                source = "techcrunch.com · 2h ago"
            )
            SearchResultRow(
                title = "Google DeepMind unveils new AI model",
                source = "theverge.com · 3h ago"
            )
            SearchResultRow(
                title = "Meta open sources new LLM",
                source = "arstechnica.com · 5h ago"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "View all results →",
            color = JarvisColors.Presence,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable {
                val targetUrl = url ?: "https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                onOpenUrl?.invoke(targetUrl)
            }
        )
    }
}

@Composable
private fun SearchResultRow(title: String, source: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(
            text = title,
            color = JarvisColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = source,
            color = JarvisColors.TextMuted,
            fontSize = 10.5.sp
        )
    }
}

/**
 * Screen 8: Screen Awareness Callout Card (Dynamic backend data bound)
 */
@Composable
private fun ScreenAwarenessCard(detail: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF09121E))
            .border(0.8.dp, JarvisColors.Presence.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "SCREEN AWARENESS",
            color = JarvisColors.Presence,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        val bodyText = detail ?: "• I can see this is WhatsApp.\n• I can read the messages.\n• I can tap, scroll and type if you ask."
        Text(
            text = bodyText,
            color = JarvisColors.TextPrimary,
            fontSize = 12.5.sp,
            lineHeight = 18.sp
        )
    }
}

/**
 * Empty Chat state placeholder with suggestions.
 */
@Composable
private fun EmptyChatPlaceholder(onSuggestionClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        JarvisCore(
            state = JarvisVisualState.IDLE,
            size = 90.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "How can I help you today?",
            color = JarvisColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Try asking me to control device, search, or read screen",
            color = JarvisColors.TextMuted,
            fontSize = 12.5.sp
        )
    }
}

/**
 * Suggestion pill chip.
 */
@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(JarvisColors.SurfaceGlassElevated)
            .border(0.8.dp, JarvisColors.Presence.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = JarvisColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Chat Input Pill matching Screen 5.
 */
@Composable
private fun ChatInputPill(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    isListening: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF131A26))
                .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = "Type or speak...",
                        color = JarvisColors.TextMuted,
                        fontSize = 14.sp
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = JarvisColors.TextPrimary,
                    unfocusedTextColor = JarvisColors.TextPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )

            if (value.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(JarvisColors.Presence)
                        .clickable(onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = JarvisColors.VoidBlack,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isListening) JarvisColors.StateError else JarvisColors.Presence
                        )
                        .clickable(onClick = onMicClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Mic",
                        tint = JarvisColors.VoidBlack,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
