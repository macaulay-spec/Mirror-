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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    var hasDefaultAssistant by remember { mutableStateOf(PermissionAndSetupHelper.isDefaultAssistant(context)) }

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
        hasDefaultAssistant = PermissionAndSetupHelper.isDefaultAssistant(context)
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
            // Faint HUD grid + top cyan bleed (v3 mockup background)
            com.jarvis.core.ui.HudBackground(modifier = Modifier.matchParentSize())
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to AI Interface",
                                    tint = JarvisColors.Presence
                                )
                            }
                            Column {
                                Text(
                                    text = "JARVIS ACCESS CONTROL",
                                    color = JarvisColors.Presence,
                                    fontSize = 17.sp,
                                    fontFamily = FontFamily.Default,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Subsystem Permissions & Config",
                                    color = JarvisColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
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
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisColors.Presence),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("DIAGNOSTICS", fontFamily = FontFamily.Default, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onClose,
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.Presence, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("← AI INTERFACE", fontFamily = FontFamily.Default, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                
                // Section 0: USER PROFILE & AI PERSONA
                item {
                    var currentName by remember { mutableStateOf(ApiConfig.userName) }
                    var currentTone by remember { mutableStateOf(ApiConfig.personalityTone) }
                    var savedProfile by remember { mutableStateOf(false) }

                    PermissionCard(
                        title = "USER IDENTITY & AI PROTOCOL",
                        subtitle = "Personalized addressing and tone profile",
                        icon = Icons.Default.AccessibilityNew,
                        isGranted = true,
                        actionLabel = if (savedProfile) "SAVED ✓" else "SAVE PROFILE",
                        onAction = {
                            ApiConfig.saveUserName(context, currentName)
                            ApiConfig.savePersonalityTone(context, currentTone)
                            savedProfile = true
                            android.widget.Toast.makeText(context, "User profile updated!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = currentName,
                                onValueChange = {
                                    currentName = it
                                    savedProfile = false
                                },
                                label = { Text("Your Preferred Name", color = JarvisColors.TextSecondary, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JarvisColors.Presence,
                                    unfocusedBorderColor = JarvisColors.Hairline,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Text(
                                text = "AI Interaction Persona:",
                                color = JarvisColors.TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Default
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val tones = listOf(
                                    "jarvis_protocol" to "JARVIS",
                                    "conversational" to "WARM",
                                    "executive" to "EXECUTIVE"
                                )
                                tones.forEach { (key, label) ->
                                    Button(
                                        onClick = {
                                            currentTone = key
                                            savedProfile = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (currentTone == key) JarvisColors.Presence else JarvisColors.SurfaceCard,
                                            contentColor = if (currentTone == key) Color.Black else JarvisColors.TextPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Voice & Persona Customization Card
                item {
                    val engineType = "elevenlabs"
                    var selectedVoice by remember { mutableStateOf(ApiConfig.selectedVoiceId) }
                    var savedVoice by remember { mutableStateOf(false) }

                    PermissionCard(
                        title = "VOICE SYNTHESIS & ELEVENLABS",
                        subtitle = "Select voice character & regional accent",
                        icon = Icons.Default.Mic,
                        isGranted = true,
                        actionLabel = if (savedVoice) "APPLIED ✓" else "APPLY VOICE",
                        onAction = {
                            ApiConfig.saveVoicePreferences(context, engineType, selectedVoice)
                            savedVoice = true
                            android.widget.Toast.makeText(context, "Voice configured!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Select Voice Profile:",
                                color = JarvisColors.TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Default
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ApiConfig.PRESET_VOICES.forEach { preset ->
                                    val isSelected = selectedVoice == preset.id
                                    Card(
                                        onClick = {
                                            selectedVoice = preset.id
                                            savedVoice = false
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) JarvisColors.Presence.copy(alpha = 0.15f) else JarvisColors.SurfaceCard
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, JarvisColors.Presence) else null,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "${preset.name} (${preset.accent} ${preset.gender})",
                                                        color = if (isSelected) JarvisColors.Presence else Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        fontFamily = FontFamily.Default
                                                    )
                                                    Text(
                                                        text = preset.description,
                                                        color = JarvisColors.TextSecondary,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = JarvisColors.Presence,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                            OutlinedButton(
                                onClick = {
                                    ApiConfig.saveVoicePreferences(context, engineType, selectedVoice)
                                    android.widget.Toast.makeText(context, "Streaming ElevenLabs preview...", android.widget.Toast.LENGTH_SHORT).show()
                                    coroutineScope.launch {
                                        try {
                                            com.jarvis.app.voice.ElevenLabsVoicePlayer.speak(
                                                context, 
                                                "Greetings ${ApiConfig.userName}. Systems operational. How may I assist you today?", 
                                                selectedVoice
                                            )
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisColors.Presence),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("🔊 PREVIEW VOICE OUTPUT", fontFamily = FontFamily.Default, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Personal Context Graph Card
                item {
                    var peopleCount by remember { mutableStateOf(0) }
                    var aliasesCount by remember { mutableStateOf(0) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        try {
                            val db = com.jarvis.app.memory.AppDatabase.get(context)
                            peopleCount = db.personDao().all().size
                            aliasesCount = db.contextGraphDao().getAllAppAliasesSync().size
                        } catch (_: Exception) {}
                    }
                    PermissionCard(
                        title = "PERSONAL CONTEXT GRAPH",
                        subtitle = "Offline knowledge of family, aliases, and habits",
                        icon = Icons.Default.AccessibilityNew,
                        isGranted = true,
                        actionLabel = "ADD RELATION",
                        onAction = {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val db = com.jarvis.app.memory.AppDatabase.get(context)
                                    db.personDao().insert(
                                        com.jarvis.app.memory.PersonEntity(
                                            displayName = "Mumsi",
                                            relationship = "Mother",
                                            nicknames = "[\"Mum\",\"Mom\",\"Mama\"]"
                                        )
                                    )
                                    peopleCount = db.personDao().all().size
                                } catch (_: Exception) {}
                            }
                            android.widget.Toast.makeText(context, "Sample relation 'Mumsi' (Mother) registered to Graph.", android.widget.Toast.LENGTH_SHORT).show()
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
                        countGranted = listOf(hasDefaultAssistant, hasAccessibility, hasNotifListener, hasOverlay, hasUsage, hasWriteSettings, hasAllFiles, hasBatteryOpt).count { it },
                        total = 8
                    )
                }

                item {
                    PermissionCard(
                        title = "DEFAULT DIGITAL ASSISTANT",
                        subtitle = "Summon JARVIS via long-press Home, swipe-up, or headset button",
                        icon = Icons.Default.Settings,
                        isGranted = hasDefaultAssistant,
                        actionLabel = "ENABLE",
                        onAction = {
                            PermissionAndSetupHelper.openDefaultAssistantSettings(context)
                        }
                    )
                }

                item {
                    PermissionCard(
                        title = "ACCESSIBILITY & BUTTON TRIGGER",
                        subtitle = "Screen interaction, UI automation & toggle HUD overlay via Accessibility Button",
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
                        title = "NOTIFICATION ACCESS & AUTO-REPLY",
                        subtitle = "Read incoming messages, one-time verification codes & direct replies",
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
                        title = "SYSTEM OVERLAY WINDOW",
                        subtitle = "Floating Jarvis living core & holographic HUD over all other applications",
                        icon = Icons.Default.Layers,
                        isGranted = hasOverlay,
                        actionLabel = if (com.jarvis.android.overlay.JarvisFloatingOrbService.isRunning) "RUNNING" else "ENABLE",
                        onAction = {
                            if (!hasOverlay) {
                                PermissionAndSetupHelper.openOverlaySettings(context)
                            } else {
                                // Toggle directly
                                val service = com.jarvis.android.accessibility.JarvisAccessibilityService.instance
                                if (service != null) {
                                    service.toggleOverlay()
                                } else {
                                    try {
                                        if (com.jarvis.android.overlay.JarvisFloatingOrbService.isRunning) {
                                            context.stopService(android.content.Intent(context, com.jarvis.android.overlay.JarvisFloatingOrbService::class.java))
                                        } else {
                                            // CHANGED (item 10): foreground service requires startForegroundService on API 26+.
                                            androidx.core.content.ContextCompat.startForegroundService(context, android.content.Intent(context, com.jarvis.android.overlay.JarvisFloatingOrbService::class.java))
                                        }
                                    } catch (_: Exception) {
                                        PermissionAndSetupHelper.openOverlaySettings(context)
                                    }
                                }
                            }
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
            color = JarvisColors.Presence,
            fontFamily = FontFamily.Default,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Text(
            text = "$countGranted / $total ACTIVE",
            color = if (countGranted == total) JarvisColors.StateSuccess else JarvisColors.Warmth,
            fontFamily = FontFamily.Default,
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
        colors = CardDefaults.cardColors(containerColor = JarvisColors.DarkSpace)
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
                        tint = if (isGranted) JarvisColors.Presence else JarvisColors.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = JarvisColors.TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Default,
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
                            tint = JarvisColors.StateSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "GRANTED",
                            color = JarvisColors.StateSuccess,
                            fontFamily = FontFamily.Default,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisColors.SurfaceCard,
                            contentColor = JarvisColors.Presence
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = actionLabel,
                            fontFamily = FontFamily.Default,
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
