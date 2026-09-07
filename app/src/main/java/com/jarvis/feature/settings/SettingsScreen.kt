package com.jarvis.feature.settings

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.HudBackground
import com.jarvis.core.ui.JarvisCore
import com.jarvis.feature.navigation.BottomNavigationBar

/**
 * Screen 15 (SETTINGS) from official JARVIS specification.
 *
 * Exact 100% specification alignment:
 * - Header: "Settings"
 * - 10 Core setting items with chevron navigation:
 *   1. Assistant >
 *   2. Voice >
 *   3. AI Providers >
 *   4. Memory >
 *   5. Accessibility >
 *   6. Notifications >
 *   7. Overlay >
 *   8. Privacy & Security >
 *   9. Advanced >
 *   10. About >
 * - Mark 85 Arc Reactor Emblem Card with 5 capabilities:
 *   [SEE] [THINK] [ACT] [VERIFY] [HELP]
 * - Persistent BottomNavigationBar: [Home] [Chat] [Memory] [Settings]
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onVoiceSettings: () -> Unit = {},
    onPermissions: () -> Unit = {},
    onAbout: () -> Unit = {}
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = JarvisColors.VoidBlack,
        bottomBar = {
            BottomNavigationBar(
                currentRoute = "settings",
                onNavigate = onNavigate
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
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // ── Top Header ──────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = JarvisColors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Settings",
                        color = JarvisColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Settings List with Chevron Navigation ───────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(JarvisColors.SurfaceGlassElevated)
                        .border(
                            0.8.dp,
                            Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    SettingsItemRow("Assistant", onClick = onPermissions)
                    SettingsDivider()
                    SettingsItemRow("Voice", onClick = onVoiceSettings)
                    SettingsDivider()
                    SettingsItemRow("AI Providers", onClick = onPermissions)
                    SettingsDivider()
                    SettingsItemRow("Memory", onClick = { onNavigate("memory") })
                    SettingsDivider()
                    SettingsItemRow("Accessibility", onClick = onPermissions)
                    SettingsDivider()
                    SettingsItemRow("Notifications", onClick = onPermissions)
                    SettingsDivider()
                    SettingsItemRow("Overlay", onClick = onPermissions)
                    SettingsDivider()
                    SettingsItemRow("Privacy & Security", onClick = onPermissions)
                    SettingsDivider()
                    SettingsItemRow("Advanced", onClick = onPermissions)
                    SettingsDivider()
                    SettingsItemRow("About", onClick = onAbout)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── High-Tech Arc Reactor Emblem Banner (Screen 15) ─────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF071220),
                                    Color(0xFF03070E)
                                )
                            )
                        )
                        .border(
                            0.8.dp,
                            JarvisColors.Presence.copy(alpha = 0.3f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        JarvisCore(
                            state = JarvisVisualState.IDLE,
                            size = 110.dp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "J  A  R  V  I  S",
                            color = JarvisColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Not just an assistant.\nAn intelligence that acts.",
                            color = JarvisColors.TextSecondary,
                            fontSize = 12.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // 5 Capabilities: SEE, THINK, ACT, VERIFY, HELP
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CapabilityBadge("SEE", Icons.Default.Visibility)
                            CapabilityBadge("THINK", Icons.Default.Psychology)
                            CapabilityBadge("ACT", Icons.Default.TouchApp)
                            CapabilityBadge("VERIFY", Icons.Default.VerifiedUser)
                            CapabilityBadge("HELP", Icons.Default.Favorite)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsItemRow(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = JarvisColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Open",
            tint = JarvisColors.TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

@Composable
private fun CapabilityBadge(label: String, icon: ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = JarvisColors.Presence,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = JarvisColors.TextMuted,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
