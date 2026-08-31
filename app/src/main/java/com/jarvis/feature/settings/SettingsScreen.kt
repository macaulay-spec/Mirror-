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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
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
import com.jarvis.app.config.ApiConfig
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard

/**
 * Settings Screen — JARVIS configuration.
 *
 * Design: calm, precise, alive.
 * - Clean sections with glass panels
 * - Each setting has icon, title, and description
 * - Arrow indicators for navigation
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onVoiceSettings: () -> Unit,
    onPermissions: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = JarvisColors.TextSecondary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onBack() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Settings",
                    color = JarvisColors.TextPrimary,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Assistant section
            SettingsSection(title = "Assistant") {
                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "General",
                    description = "Name, personality, and behavior",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.Mic,
                    title = "Voice",
                    description = "Voice selection and TTS settings",
                    onClick = onVoiceSettings
                )
                SettingsItem(
                    icon = Icons.Default.VolumeUp,
                    title = "AI Provider",
                    description = ApiConfig.getProviderLabel(),
                    onClick = { }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // System section
            SettingsSection(title = "System") {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    description = "Message and alert settings",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.Accessibility,
                    title = "Accessibility",
                    description = "Screen reading and interaction",
                    onClick = onPermissions
                )
                SettingsItem(
                    icon = Icons.Default.Security,
                    title = "Permissions",
                    description = "App permissions and access",
                    onClick = onPermissions
                )
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "Privacy & Security",
                    description = "Data and security settings",
                    onClick = { }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Advanced section
            SettingsSection(title = "Advanced") {
                SettingsItem(
                    icon = Icons.Default.BrightnessMedium,
                    title = "Display",
                    description = "Theme and appearance",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.FlashlightOn,
                    title = "Quick Actions",
                    description = "Customize quick action buttons",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.PowerSettingsNew,
                    title = "Battery",
                    description = "Power management settings",
                    onClick = { }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About section
            SettingsSection(title = "About") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About JARVIS",
                    description = "Version, credits, and links",
                    onClick = onAbout
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Version
            Text(
                text = "JARVIS v1.0.0",
                color = JarvisColors.TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Default,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            color = JarvisColors.TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = JarvisColors.Presence,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = JarvisColors.TextPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = JarvisColors.TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Default
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Open",
            tint = JarvisColors.TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}
