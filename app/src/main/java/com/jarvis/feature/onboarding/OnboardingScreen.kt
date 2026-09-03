package com.jarvis.feature.onboarding

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.android.permissions.PermissionAndSetupHelper
import com.jarvis.android.voice.JarvisSoundManager
import com.jarvis.android.voice.SoundEvent
import com.jarvis.app.config.ApiConfig
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.JarvisCore

/**
 * Onboarding — v3 carbon copy of mockup 01.
 *
 * Two decks:
 *  0 · Identity  — the emblem, the J A R V I S wordmark, the tagline
 *                  "Your personal AI operating layer", and a name field.
 *  1 · Senses    — a stack of glass permission cards (microphone,
 *                  accessibility, notifications, battery) with live status,
 *                  and the full-width glowing "Initialize JARVIS" pill.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotificationListener: () -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var userNameInput by remember { mutableStateOf(ApiConfig.userName) }

    var hasMic by remember { mutableStateOf(PermissionAndSetupHelper.hasMicrophone(context)) }
    var hasA11y by remember { mutableStateOf(PermissionAndSetupHelper.hasAccessibilityService(context)) }
    var hasNotif by remember { mutableStateOf(PermissionAndSetupHelper.hasNotificationListener(context)) }
    var hasBattery by remember { mutableStateOf(PermissionAndSetupHelper.hasBatteryOptimizationExemption(context)) }

    // Refresh permission states whenever the deck changes (users return from Settings)
    LaunchedEffect(step) {
        hasMic = PermissionAndSetupHelper.hasMicrophone(context)
        hasA11y = PermissionAndSetupHelper.hasAccessibilityService(context)
        hasNotif = PermissionAndSetupHelper.hasNotificationListener(context)
        hasBattery = PermissionAndSetupHelper.hasBatteryOptimizationExemption(context)
    }

    val visualState = when {
        step == 0 -> JarvisVisualState.WAKING
        hasMic && hasA11y && hasNotif -> JarvisVisualState.SUCCESS
        else -> JarvisVisualState.EXECUTING
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack)
            .padding(24.dp)
    ) {
        // Faint HUD grid + top cyan bleed (v3 mockup background)
        com.jarvis.core.ui.HudBackground(modifier = Modifier.matchParentSize())

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Minimal header: skip chip (right-aligned row keeps balance)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(JarvisColors.SurfaceGlass)
                        .border(0.5.dp, JarvisColors.Hairline, RoundedCornerShape(50))
                        .clickable {
                            ApiConfig.setOnboardingCompleted(context, true)
                            JarvisSoundManager.play(SoundEvent.ACTIVATE)
                            onFinishOnboarding()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Skip to app →",
                        color = JarvisColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Deck content
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut()
                    )
                },
                label = "onboarding_deck"
            ) { deck ->
                when (deck) {
                    0 -> IdentityDeck(
                        visualState = visualState,
                        name = userNameInput,
                        onNameChange = { userNameInput = it }
                    )
                    else -> SensesDeck(
                        visualState = visualState,
                        hasMic = hasMic,
                        hasA11y = hasA11y,
                        hasNotif = hasNotif,
                        hasBattery = hasBattery,
                        onRequestMicrophone = onRequestMicrophone,
                        onOpenAccessibility = onOpenAccessibility,
                        onOpenNotificationListener = onOpenNotificationListener,
                        onOpenBattery = {
                            PermissionAndSetupHelper.openBatteryOptimizationSettings(context)
                        }
                    )
                }
            }

            // Bottom CTA
            if (step == 0) {
                Button(
                    onClick = {
                        ApiConfig.saveUserName(context, userNameInput)
                        step = 1
                        JarvisSoundManager.play(SoundEvent.LISTENING_START)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisColors.Presence,
                        contentColor = JarvisColors.VoidBlack
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "Continue",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Next",
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(JarvisColors.SurfaceGlass)
                            .border(0.5.dp, JarvisColors.Hairline, RoundedCornerShape(50))
                            .clickable { step = 0 }
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "Back",
                            color = JarvisColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }

                    Button(
                        onClick = {
                            ApiConfig.setOnboardingCompleted(context, true)
                            JarvisSoundManager.play(SoundEvent.ACTIVATE)
                            onFinishOnboarding()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisColors.Presence,
                            contentColor = JarvisColors.VoidBlack
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = "Initialize JARVIS",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Deck 0 · Identity ──────────────────────────────────────────────────────

@Composable
private fun IdentityDeck(
    visualState: JarvisVisualState,
    name: String,
    onNameChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // The emblem
        JarvisCore(state = visualState, size = 210.dp)

        Spacer(modifier = Modifier.height(20.dp))

        // The wordmark
        Text(
            text = "J A R V I S",
            color = JarvisColors.TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 6.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Your personal AI operating layer",
            color = JarvisColors.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Name field
        Text(
            text = "What should I call you?",
            color = JarvisColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(JarvisColors.SurfaceGlassElevated)
                .border(0.5.dp, JarvisColors.Hairline, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                textStyle = TextStyle(
                    color = JarvisColors.TextPrimary,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(JarvisColors.Presence),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Macaulay", "Sir", "Boss", "Commander").forEach { preset ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (name == preset) JarvisColors.Presence.copy(alpha = 0.15f)
                            else JarvisColors.SurfaceGlass
                        )
                        .border(
                            0.5.dp,
                            if (name == preset) JarvisColors.Presence.copy(alpha = 0.4f)
                            else JarvisColors.Hairline,
                            CircleShape
                        )
                        .clickable { onNameChange(preset) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = preset,
                        color = if (name == preset) JarvisColors.Presence
                        else JarvisColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ── Deck 1 · Senses (permission cards, mockup 01 lower half) ───────────────

@Composable
private fun SensesDeck(
    visualState: JarvisVisualState,
    hasMic: Boolean,
    hasA11y: Boolean,
    hasNotif: Boolean,
    hasBattery: Boolean,
    onRequestMicrophone: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotificationListener: () -> Unit,
    onOpenBattery: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            JarvisCore(state = visualState, size = 64.dp)
            Column {
                Text(
                    text = "Initialize JARVIS",
                    color = JarvisColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Grant JARVIS its senses",
                    color = JarvisColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        PermissionDeckCard(
            icon = Icons.Default.Mic,
            title = "Microphone",
            subtitle = "Hands-free speech recognition",
            granted = hasMic,
            actionLabel = if (hasMic) "Granted" else "Grant access",
            onAction = onRequestMicrophone
        )
        Spacer(modifier = Modifier.height(10.dp))
        PermissionDeckCard(
            icon = Icons.Default.Accessibility,
            title = "Accessibility",
            subtitle = "Screen reading, taps and scrolling",
            granted = hasA11y,
            actionLabel = if (hasA11y) "Active" else "Open settings",
            onAction = onOpenAccessibility
        )
        Spacer(modifier = Modifier.height(10.dp))
        PermissionDeckCard(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            subtitle = "Read and answer messages",
            granted = hasNotif,
            actionLabel = if (hasNotif) "Active" else "Enable listener",
            onAction = onOpenNotificationListener
        )
        Spacer(modifier = Modifier.height(10.dp))
        PermissionDeckCard(
            icon = Icons.Default.BatteryChargingFull,
            title = "Background power",
            subtitle = "Stay responsive at all times",
            granted = hasBattery,
            actionLabel = if (hasBattery) "Exempted" else "Request exemption",
            onAction = onOpenBattery
        )
    }
}

@Composable
private fun PermissionDeckCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = JarvisColors.SurfaceGlassElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon in a thin-ringed circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(JarvisColors.SurfaceGlass)
                        .border(
                            0.8.dp,
                            if (granted) JarvisColors.StateSuccess.copy(alpha = 0.45f)
                            else JarvisColors.Hairline,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (granted) JarvisColors.StateSuccess else JarvisColors.Presence,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = JarvisColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = subtitle,
                        color = JarvisColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            // Right status: glowing check when granted, enable chip otherwise
            if (granted) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(JarvisColors.StateSuccess.copy(alpha = 0.15f))
                        .border(0.8.dp, JarvisColors.StateSuccess.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Granted",
                        tint = JarvisColors.StateSuccess,
                        modifier = Modifier.size(15.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(JarvisColors.Presence)
                        .clickable { onAction() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = actionLabel,
                        color = JarvisColors.VoidBlack,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
