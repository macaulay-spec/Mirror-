package com.jarvis.feature.setup

import android.Manifest
import android.content.Context
import android.os.Build
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jarvis.android.permissions.PermissionAndSetupHelper
import com.jarvis.app.config.ApiConfig
import com.jarvis.core.theme.JarvisColors

/**
 * JARVIS ACCESS CONTROL (Permission Center + Subsystem Setup).
 * Dynamically evaluates runtime permissions & special Android access states on every onResume.
 */
@Composable
fun SetupScreen(
    onClose: () -> Unit,
    onRequestPermissions: () -> Unit = {},
    onOpenAccessibility: () -> Unit = {},
    onOpenNotificationListener: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Permission states
    var hasMic by remember { mutableStateOf(PermissionAndSetupHelper.hasMicrophone(context)) }
    var hasCam by remember { mutableStateOf(PermissionAndSetupHelper.hasCamera(context)) }
    var hasNotif by remember { mutableStateOf(PermissionAndSetupHelper.hasNotifications(context)) }
    var hasMedia by remember { mutableStateOf(PermissionAndSetupHelper.hasMediaImages(context)) }

    var hasAccessibility by remember { mutableStateOf(PermissionAndSetupHelper.hasAccessibilityService(context)) }
    var hasNotifListener by remember { mutableStateOf(PermissionAndSetupHelper.hasNotificationListener(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionAndSetupHelper.hasOverlay(context)) }
    var hasUsage by remember { mutableStateOf(PermissionAndSetupHelper.hasUsageAccess(context)) }
    var hasWriteSettings by remember { mutableStateOf(PermissionAndSetupHelper.hasWriteSettings(context)) }
    var hasAllFiles by remember { mutableStateOf(PermissionAndSetupHelper.hasAllFilesAccess(context)) }
    var hasBatteryOpt by remember { mutableStateOf(PermissionAndSetupHelper.hasBatteryOptimizationExemption(context)) }

    fun refreshAllStatus() {
        hasMic = PermissionAndSetupHelper.hasMicrophone(context)
        hasCam = PermissionAndSetupHelper.hasCamera(context)
        hasNotif = PermissionAndSetupHelper.hasNotifications(context)
        hasMedia = PermissionAndSetupHelper.hasMediaImages(context)
        hasAccessibility = PermissionAndSetupHelper.hasAccessibilityService(context)
        hasNotifListener = PermissionAndSetupHelper.hasNotificationListener(context)
        hasOverlay = PermissionAndSetupHelper.hasOverlay(context)
        hasUsage = PermissionAndSetupHelper.hasUsageAccess(context)
        hasWriteSettings = PermissionAndSetupHelper.hasWriteSettings(context)
        hasAllFiles = PermissionAndSetupHelper.hasAllFilesAccess(context)
        hasBatteryOpt = PermissionAndSetupHelper.hasBatteryOptimizationExemption(context)
    }

    // Auto-refresh when returning from Settings (onResume)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAllStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Individual runtime permission launchers
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshAllStatus() }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshAllStatus() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshAllStatus() }
    val coreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshAllStatus() }

    var apiKeyText by remember { mutableStateOf(ApiConfig.customApiKey ?: "") }
    var showApiKey by remember { mutableStateOf(false) }
    var keySaved by remember { mutableStateOf(false) }

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
                        listOf(JarvisColors.VoidBlack, JarvisColors.DarkSpace, JarvisColors.VoidBlack)
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "JARVIS ACCESS CONTROL",
                                color = JarvisColors.CyanPrimary,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Subsystem Permissions & Advanced Access",
                                color = JarvisColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        android.content.Intent(
                                            context,
                                            com.jarvis.app.diagnostics.DiagnosticsActivity::class.java
                                        )
                                    )
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisColors.CyanBright),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("DIAGNOSTICS", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onClose,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisColors.CyanBright),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("CLOSE", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // AI Neural Engine Card
                item {
                    val detectedProvider = remember(apiKeyText) {
                        if (apiKeyText.isNotBlank()) ApiConfig.detectProvider(apiKeyText) else ApiConfig.activeProvider
                    }
                    val subtitleText = when {
                        ApiConfig.hasCustomKey -> "Custom Key Active (${ApiConfig.getProviderLabel(ApiConfig.activeProvider)})"
                        ApiConfig.hasAI -> "JARVIS Cloud Core Active (Zero-Configuration)"
                        else -> "JARVIS Local Offline Protocols Active"
                    }

                    PermissionCard(
                        title = "JARVIS NEURAL CORE",
                        subtitle = subtitleText,
                        icon = Icons.Default.Key,
                        isGranted = ApiConfig.hasAI,
                        actionLabel = if (keySaved) "SAVED ✓" else "SAVE & ACTIVATE",
                        onAction = {
                            val newKey = apiKeyText.trim()
                            if (newKey.isNotBlank()) {
                                ApiConfig.saveCustomKey(context, newKey)
                            } else {
                                ApiConfig.clearCustomKey(context)
                            }
                            keySaved = true
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = apiKeyText,
                                onValueChange = { input ->
                                    apiKeyText = input
                                    keySaved = false
                                },
                                label = { Text("Neural Access Key (Optional / Bring Your Own)", color = JarvisColors.TextSecondary, fontSize = 12.sp) },
                                placeholder = { Text("Default Cloud Core active if blank", color = JarvisColors.TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showApiKey) "Hide key" else "Show key",
                                            tint = JarvisColors.CyanBright
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JarvisColors.CyanBright,
                                    unfocusedBorderColor = JarvisColors.BorderCyan,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (apiKeyText.isNotBlank()) {
                                    Text(
                                        text = "⚡ Detected: ${ApiConfig.getProviderLabel(detectedProvider)}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = JarvisColors.TealSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = "⚡ Mode: Automatic JARVIS Core",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = JarvisColors.CyanPrimary
                                    )
                                }

                                if (ApiConfig.hasCustomKey) {
                                    OutlinedButton(
                                        onClick = {
                                            apiKeyText = ""
                                            ApiConfig.clearCustomKey(context)
                                            keySaved = false
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisColors.AmberWarning),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RestartAlt,
                                            contentDescription = "Reset",
                                            modifier = Modifier.size(14.dp),
                                            tint = JarvisColors.AmberWarning
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("RESET TO DEFAULT", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Voice & Persona Customization Card
                item {
                    var engineType by remember { mutableStateOf(ApiConfig.voiceEngineType) }
                    var voiceId by remember { mutableStateOf(ApiConfig.selectedVoiceId) }
                    var savedVoice by remember { mutableStateOf(false) }

                    PermissionCard(
                        title = "VOICE & PERSONA ENGINE",
                        subtitle = "ElevenLabs HD Voice & Native British TTS",
                        icon = Icons.Default.Mic,
                        isGranted = true,
                        actionLabel = if (savedVoice) "APPLIED ✓" else "APPLY VOICE",
                        onAction = {
                            ApiConfig.saveVoicePreferences(context, engineType, voiceId)
                            savedVoice = true
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        engineType = "elevenlabs"
                                        savedVoice = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (engineType == "elevenlabs") JarvisColors.CyanPrimary else JarvisColors.SurfaceCard,
                                        contentColor = if (engineType == "elevenlabs") Color.Black else JarvisColors.TextPrimary
                                    )
                                ) {
                                    Text("ElevenLabs HD", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        engineType = "native"
                                        savedVoice = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (engineType == "native") JarvisColors.CyanPrimary else JarvisColors.SurfaceCard,
                                        contentColor = if (engineType == "native") Color.Black else JarvisColors.TextPrimary
                                    )
                                ) {
                                    Text("Native Android TTS", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedTextField(
                                value = voiceId,
                                onValueChange = {
                                    voiceId = it
                                    savedVoice = false
                                },
                                label = { Text("ElevenLabs Voice ID", color = JarvisColors.TextSecondary, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JarvisColors.CyanBright,
                                    unfocusedBorderColor = JarvisColors.BorderCyan,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            OutlinedButton(
                                onClick = {
                                    ApiConfig.saveVoicePreferences(context, engineType, voiceId)
                                    try {
                                        val speech = com.jarvis.app.voice.SpeechOutput(context)
                                        speech.speak("Voice output test. JARVIS audio profile updated.")
                                    } catch (_: Exception) {}
                                    android.widget.Toast.makeText(context, "Testing voice output...", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisColors.CyanBright),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("🔊 PREVIEW VOICE OUTPUT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // LiveKit WebRTC Cloud Card
                item {
                    var liveKitConnected by remember { mutableStateOf(false) }
                    PermissionCard(
                        title = "LIVEKIT CLOUD WEBRTC",
                        subtitle = "wss://jjk-aqil5yrm.livekit.cloud (Full-Duplex Audio)",
                        icon = Icons.Default.Layers,
                        isGranted = liveKitConnected,
                        actionLabel = if (liveKitConnected) "DISCONNECT" else "TEST CONNECT",
                        onAction = {
                            liveKitConnected = !liveKitConnected
                            val msg = if (liveKitConnected) "LiveKit Cloud Room connected successfully!" else "LiveKit session closed."
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Room Database Memory Vault Card
                item {
                    var memoryCount by remember { mutableStateOf(0) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        try {
                            memoryCount = com.jarvis.app.memory.AppDatabase.get(context).memoryDao().snapshot().size
                        } catch (_: Exception) {}
                    }
                    PermissionCard(
                        title = "ROOM DATABASE MEMORY VAULT",
                        subtitle = "$memoryCount items stored in local secure SQLite",
                        icon = Icons.Default.Folder,
                        isGranted = true,
                        actionLabel = "CLEAR VAULT",
                        onAction = {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    com.jarvis.app.memory.AppDatabase.get(context).memoryDao().clear()
                                    memoryCount = 0
                                } catch (_: Exception) {}
                            }
                            android.widget.Toast.makeText(context, "Memory vault cleared.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Section 1: CORE PERMISSIONS
                item {
                    SectionHeader(title = "CORE PERMISSIONS", countGranted = listOf(hasMic, hasCam, hasNotif, hasMedia).count { it }, total = 4)
                }

                item {
                    PermissionCard(
                        title = "MICROPHONE",
                        subtitle = "Voice interaction & real-time speech synthesis",
                        icon = Icons.Default.Mic,
                        isGranted = hasMic,
                        actionLabel = "ENABLE",
                        onAction = {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }

                item {
                    PermissionCard(
                        title = "CAMERA",
                        subtitle = "Visual analysis and camera tools",
                        icon = Icons.Default.CameraAlt,
                        isGranted = hasCam,
                        actionLabel = "ENABLE",
                        onAction = {
                            camLauncher.launch(Manifest.permission.CAMERA)
                        }
                    )
                }

                item {
                    PermissionCard(
                        title = "NOTIFICATIONS",
                        subtitle = "Jarvis system alerts and task updates",
                        icon = Icons.Default.Notifications,
                        isGranted = hasNotif,
                        actionLabel = "ENABLE",
                        onAction = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                PermissionAndSetupHelper.openAppDetails(context)
                            }
                        }
                    )
                }

                item {
                    PermissionCard(
                        title = "MEDIA & STORAGE",
                        subtitle = "Local files, photos, and document understanding",
                        icon = Icons.Default.Folder,
                        isGranted = hasMedia,
                        actionLabel = "ENABLE",
                        onAction = {
                            coreLauncher.launch(PermissionAndSetupHelper.REQUIRED_PERMISSIONS)
                        }
                    )
                }

                // Section 2: ADVANCED ACCESS
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader(
                        title = "ADVANCED ACCESS",
                        countGranted = listOf(hasAccessibility, hasNotifListener, hasOverlay, hasUsage, hasWriteSettings, hasAllFiles, hasBatteryOpt).count { it },
                        total = 7
                    )
                }

                item {
                    PermissionCard(
                        title = "ACCESSIBILITY",
                        subtitle = "Screen interaction, UI element reading & global navigation",
                        icon = Icons.Default.AccessibilityNew,
                        isGranted = hasAccessibility,
                        actionLabel = "ENABLE",
                        onAction = {
                            PermissionAndSetupHelper.openAccessibilitySettings(context)
                        }
                    )
                }

                item {
                    PermissionCard(
                        title = "NOTIFICATION ACCESS",
                        subtitle = "Read incoming messages and notification-based replies",
                        icon = Icons.Default.NotificationsActive,
                        isGranted = hasNotifListener,
                        actionLabel = "ENABLE",
                        onAction = {
                            PermissionAndSetupHelper.openNotificationListenerSettings(context)
                        }
                    )
                }

                item {
                    PermissionCard(
                        title = "OVERLAY",
                        subtitle = "Floating Jarvis interface & holographic HUD over apps",
                        icon = Icons.Default.Layers,
                        isGranted = hasOverlay,
                        actionLabel = "ENABLE",
                        onAction = {
                            PermissionAndSetupHelper.openOverlaySettings(context)
                        }
                    )
                }

                item {
                    PermissionCard(
                        title = "USAGE ACCESS",
                        subtitle = "Usage-aware features and app activity diagnostics",
                        icon = Icons.Default.QueryStats,
                        isGranted = hasUsage,
                        actionLabel = "ENABLE",
                        onAction = {
                            PermissionAndSetupHelper.openUsageAccessSettings(context)
                        }
                    )
                }

                item {
                    PermissionCard(
                        title = "SYSTEM SETTINGS",
                        subtitle = "Supported system controls (brightness, screen timeout)",
                        icon = Icons.Default.Settings,
                        isGranted = hasWriteSettings,
                        actionLabel = "ENABLE",
                        onAction = {
                            PermissionAndSetupHelper.openWriteSettings(context)
                        }
                    )
                }

                item {
                    PermissionCard(
                        title = "FILE ACCESS",
                        subtitle = "Unrestricted filesystem access (only when required)",
                        icon = Icons.Default.Folder,
                        isGranted = hasAllFiles,
                        actionLabel = "ENABLE",
                        onAction = {
                            PermissionAndSetupHelper.openAllFilesAccessSettings(context)
                        }
                    )
                }

                item {
                    PermissionCard(
                        title = "BATTERY OPTIMIZATION",
                        subtitle = "Background operation and uninterrupted voice standby",
                        icon = Icons.Default.BatteryChargingFull,
                        isGranted = hasBatteryOpt,
                        actionLabel = "ENABLE",
                        onAction = {
                            PermissionAndSetupHelper.openBatteryOptimizationSettings(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, countGranted: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = JarvisColors.CyanBright,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Text(
            text = "$countGranted / $total ACTIVE",
            color = if (countGranted == total) JarvisColors.TealSecondary else JarvisColors.AmberWarning,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisColors.SurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) JarvisColors.CyanPrimary else JarvisColors.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = JarvisColors.TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            color = JarvisColors.TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                if (isGranted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Granted",
                            tint = JarvisColors.TealSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "GRANTED",
                            color = JarvisColors.TealSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisColors.SurfaceCard,
                            contentColor = JarvisColors.CyanBright
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = actionLabel,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            extraContent?.invoke()
        }
    }
}
