package com.jarvis.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.voice.ElevenLabsTts
import com.jarvis.core.theme.JarvisColors
import com.jarvis.feature.history.ChatHistoryScreen
import com.jarvis.feature.memory.MemoryPeopleScreen
import com.jarvis.feature.setup.SetupScreen
import com.jarvis.feature.voice.VoiceSelectionScreen

@Composable
fun SettingsHubScreen(
    onClose: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotificationListener: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("history") }
    val context = LocalContext.current
    val voiceTts = remember { runCatching { ElevenLabsTts(context) }.getOrNull() }

    Column(modifier = Modifier.fillMaxSize().background(JarvisColors.VoidBlack)) {
        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                "history" -> ChatHistoryScreen()
                "memory" -> MemoryPeopleScreen(onBack = { selectedTab = "history" })
                "voice" -> {
                    if (voiceTts != null) {
                        VoiceSelectionScreen(
                            tts = voiceTts,
                            onContinue = { selectedTab = "history" },
                            onSkip = { selectedTab = "history" }
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Voice Engine unavailable", color = JarvisColors.StateError)
                        }
                    }
                }
                "system" -> SetupScreen(
                    onClose = onClose,
                    onRequestPermissions = onRequestPermissions,
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenNotificationListener = onOpenNotificationListener
                )
            }
        }

        // Bottom Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JarvisColors.SurfaceGlass)
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HubTab("history", Icons.Default.History, "History", selectedTab) { selectedTab = it }
            HubTab("memory", Icons.Default.Memory, "Memory", selectedTab) { selectedTab = it }
            HubTab("voice", Icons.Default.SpatialAudio, "Voice", selectedTab) { selectedTab = it }
            HubTab("system", Icons.Default.Settings, "System", selectedTab) { selectedTab = it }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = JarvisColors.TextMuted)
            }
        }
    }
}

@Composable
private fun HubTab(id: String, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, current: String, onSelect: (String) -> Unit) {
    val isSelected = id == current
    val color = if (isSelected) JarvisColors.Presence else JarvisColors.TextMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSelect(id) }
            .padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 10.sp)
    }
}
