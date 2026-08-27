package com.jarvis.feature.onboarding

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.android.permissions.PermissionAndSetupHelper
import com.jarvis.android.voice.JarvisSoundManager
import com.jarvis.android.voice.SoundEvent
import com.jarvis.app.assistant.GeminiService
import com.jarvis.app.config.ApiConfig
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.JarvisCore
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotificationListener: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableIntStateOf(0) }
    
    var userNameInput by remember { mutableStateOf(ApiConfig.userName) }
    var apiKeyInput by remember { mutableStateOf(ApiConfig.customApiKey ?: "") }
    var keyValidationStatus by remember { mutableStateOf<String?>(null) }
    var isValidatingKey by remember { mutableStateOf(false) }

    // Live permission pollers
    var hasMic by remember { mutableStateOf(PermissionAndSetupHelper.hasMicrophone(context)) }
    var hasA11y by remember { mutableStateOf(PermissionAndSetupHelper.hasAccessibilityService(context)) }
    var hasNotif by remember { mutableStateOf(PermissionAndSetupHelper.hasNotificationListener(context)) }
    var hasBattery by remember { mutableStateOf(PermissionAndSetupHelper.hasBatteryOptimizationExemption(context)) }

    LaunchedEffect(currentStep) {
        hasMic = PermissionAndSetupHelper.hasMicrophone(context)
        hasA11y = PermissionAndSetupHelper.hasAccessibilityService(context)
        hasNotif = PermissionAndSetupHelper.hasNotificationListener(context)
        hasBattery = PermissionAndSetupHelper.hasBatteryOptimizationExemption(context)
    }

    val visualState = when (currentStep) {
        0 -> JarvisVisualState.WAKING
        1 -> JarvisVisualState.IDLE
        2 -> JarvisVisualState.IDLE
        3 -> if (hasMic) JarvisVisualState.LISTENING else JarvisVisualState.WAKING
        4 -> if (hasA11y) JarvisVisualState.SUCCESS else JarvisVisualState.EXECUTING
        5 -> if (hasNotif) JarvisVisualState.SUCCESS else JarvisVisualState.THINKING
        6 -> JarvisVisualState.IDLE
        7 -> if (isValidatingKey) JarvisVisualState.THINKING else JarvisVisualState.IDLE
        else -> JarvisVisualState.SUCCESS
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF03060E),
                        Color(0xFF060B16),
                        Color(0xFF020408)
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Step indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "JARVIS PROTOCOL",
                    color = JarvisColors.CyanPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "PHASE ${currentStep + 1} / 8",
                    color = JarvisColors.TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Hero Orb
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                JarvisCore(
                    state = visualState,
                    audioLevel = if (visualState == JarvisVisualState.LISTENING) 0.35f else 0f,
                    size = if (currentStep == 0) 260.dp else 190.dp
                )
            }

            // Cinematic Content Box
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut()
                    )
                },
                label = "onboarding_step"
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (step) {
                        0 -> StepWelcome()
                        1 -> StepThreeBeats()
                        2 -> StepUserIdentity(
                            name = userNameInput,
                            onNameChange = { userNameInput = it }
                        )
                        3 -> StepVoice(
                            hasMic = hasMic,
                            onRequest = {
                                onRequestMicrophone()
                                hasMic = PermissionAndSetupHelper.hasMicrophone(context)
                            }
                        )
                        4 -> StepDeviceControl(
                            hasA11y = hasA11y,
                            onOpen = {
                                onOpenAccessibility()
                            }
                        )
                        5 -> StepNotifications(
                            hasNotif = hasNotif,
                            onOpen = {
                                onOpenNotificationListener()
                            }
                        )
                        6 -> StepBattery(
                            hasBattery = hasBattery,
                            onOpen = {
                                PermissionAndSetupHelper.openBatteryOptimizationSettings(context)
                            }
                        )
                        7 -> StepNeuralCore(
                            apiKey = apiKeyInput,
                            onKeyChange = { apiKeyInput = it },
                            isValidating = isValidatingKey,
                            statusText = keyValidationStatus,
                            onTestKey = {
                                scope.launch {
                                    isValidatingKey = true
                                    keyValidationStatus = "Verifying neural key..."
                                    try {
                                        val testResult = GeminiService().generateChatResponse(
                                            userMessage = "Status ping",
                                            apiKey = apiKeyInput.trim()
                                        )
                                        if (testResult.isSuccess) {
                                            keyValidationStatus = "Neural Gateway: Active & Connected"
                                            JarvisSoundManager.play(SoundEvent.SUCCESS)
                                        } else {
                                            keyValidationStatus = "Key Error: ${testResult.exceptionOrNull()?.message?.take(50)}"
                                            JarvisSoundManager.play(SoundEvent.ERROR)
                                        }
                                    } catch (e: Exception) {
                                        keyValidationStatus = "Connection error. Local Core active."
                                    } finally {
                                        isValidatingKey = false
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Bottom Navigation Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisColors.TextSecondary)
                    ) {
                        Text("BACK", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (currentStep == 2) {
                            ApiConfig.saveUserName(context, userNameInput)
                        }
                        if (currentStep == 7) {
                            if (apiKeyInput.isNotBlank()) {
                                ApiConfig.saveCustomKey(context, apiKeyInput)
                            }
                            ApiConfig.setOnboardingCompleted(context, true)
                            JarvisSoundManager.play(SoundEvent.ACTIVATE)
                            onFinishOnboarding()
                        } else {
                            currentStep++
                            JarvisSoundManager.play(SoundEvent.LISTENING_START)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisColors.CyanPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = if (currentStep == 7) "ACTIVATE JARVIS" else "CONTINUE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Next",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepWelcome() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "JARVIS",
            color = JarvisColors.TextPrimary,
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Ready to become your operating layer on this phone.",
            color = JarvisColors.CyanBright,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun StepThreeBeats() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "THE ARCHITECTURE",
            color = JarvisColors.CyanPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0x1400E5FF)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                BeatRow("VOICE", "Fluid, real-time auditory interaction and wake commands.")
                Spacer(modifier = Modifier.height(14.dp))
                BeatRow("CONTROL", "Deep device automation and accessibility execution.")
                Spacer(modifier = Modifier.height(14.dp))
                BeatRow("MEMORY", "Private, on-device contextual persistence.")
            }
        }
    }
}

@Composable
private fun BeatRow(title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .padding(top = 6.dp)
                .clip(CircleShape)
                .background(JarvisColors.CyanPrimary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = JarvisColors.TextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = JarvisColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun StepUserIdentity(
    name: String,
    onNameChange: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "LOCAL IDENTITY",
            color = JarvisColors.CyanPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "How should JARVIS address you?",
            color = JarvisColors.TextPrimary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Custom name input field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x1A00E5FF))
                .border(1.dp, JarvisColors.BorderCyan, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                textStyle = TextStyle(
                    color = JarvisColors.TextPrimary,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(JarvisColors.CyanPrimary),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        // Quick preset chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Macaulay", "Sir", "Boss", "Commander").forEach { preset ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (name == preset) JarvisColors.CyanPrimary.copy(alpha = 0.25f) else Color(0x10FFFFFF))
                        .border(1.dp, if (name == preset) JarvisColors.CyanPrimary else Color(0x22FFFFFF), CircleShape)
                        .clickable { onNameChange(preset) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = preset,
                        color = if (name == preset) JarvisColors.CyanBright else JarvisColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun StepVoice(
    hasMic: Boolean,
    onRequest: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "AUDIO SENSORY LAYER",
            color = JarvisColors.CyanPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enable microphone for hands-free speech recognition.",
            color = JarvisColors.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        StatusActionCard(
            title = "Microphone Permission",
            isGranted = hasMic,
            actionLabel = if (hasMic) "Calibrated" else "Grant Access",
            onAction = onRequest
        )
    }
}

@Composable
private fun StepDeviceControl(
    hasA11y: Boolean,
    onOpen: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "DEVICE CONTROL MATRIX",
            color = JarvisColors.CyanPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Accessibility powers screen reading, tap automation, and app actions.",
            color = JarvisColors.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        StatusActionCard(
            title = "Jarvis Accessibility Service",
            isGranted = hasA11y,
            actionLabel = if (hasA11y) "Active" else "Open Settings",
            onAction = onOpen
        )
    }
}

@Composable
private fun StepNotifications(
    hasNotif: Boolean,
    onOpen: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "NOTIFICATION STREAM",
            color = JarvisColors.CyanPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Permit JARVIS to read incoming messages and draft intelligent replies.",
            color = JarvisColors.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        StatusActionCard(
            title = "Notification Listener",
            isGranted = hasNotif,
            actionLabel = if (hasNotif) "Active" else "Enable Listener",
            onAction = onOpen
        )
    }
}

@Composable
private fun StepBattery(
    hasBattery: Boolean,
    onOpen: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "BACKGROUND RESILIENCE",
            color = JarvisColors.CyanPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Disable battery restrictions so JARVIS remains responsive 24/7.",
            color = JarvisColors.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        StatusActionCard(
            title = "Unrestricted Battery Mode",
            isGranted = hasBattery,
            actionLabel = if (hasBattery) "Exempted" else "Request Exemption",
            onAction = onOpen
        )
    }
}

@Composable
private fun StepNeuralCore(
    apiKey: String,
    onKeyChange: (String) -> Unit,
    isValidating: Boolean,
    statusText: String?,
    onTestKey: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "NEURAL GATEWAY (OPTIONAL)",
            color = JarvisColors.CyanPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Paste your Google AI Studio / Gemini API key, or proceed in local zero-key mode.",
            color = JarvisColors.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x1A00E5FF))
                .border(1.dp, JarvisColors.BorderCyan, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = apiKey,
                onValueChange = onKeyChange,
                textStyle = TextStyle(
                    color = JarvisColors.TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(JarvisColors.CyanPrimary),
                modifier = Modifier.fillMaxWidth()
            )
            if (apiKey.isEmpty()) {
                Text(
                    text = "AIzaSy... (Paste Gemini Key)",
                    color = JarvisColors.TextMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (apiKey.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = JarvisColors.CyanPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "TEST KEY CONNECTION",
                    color = JarvisColors.CyanBright,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !isValidating) { onTestKey() }
                )
            }
        }

        if (statusText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusText,
                color = if (statusText.contains("Active")) Color(0xFF00F5D4) else Color(0xFFFFB703),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun StatusActionCard(
    title: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (isGranted) Color(0x1800F5D4) else Color(0x1400E5FF),
        borderColor = if (isGranted) Color(0x5500F5D4) else JarvisColors.BorderCyan
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Security,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF00F5D4) else JarvisColors.CyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = JarvisColors.TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isGranted) Color(0x2200F5D4) else JarvisColors.CyanPrimary)
                    .clickable(enabled = !isGranted) { onAction() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = actionLabel.uppercase(),
                    color = if (isGranted) Color(0xFF00F5D4) else Color.Black,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
