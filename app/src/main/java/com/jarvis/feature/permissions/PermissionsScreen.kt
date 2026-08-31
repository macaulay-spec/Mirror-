package com.jarvis.feature.permissions

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard

/**
 * Permissions Screen — explains what permissions JARVIS needs and why.
 *
 * Design: calm, precise, alive.
 * - Each permission explains the one concrete thing it unlocks
 * - Not a feature list — one sentence per permission
 * - Simple continue button
 */
@Composable
fun PermissionsScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissions = listOf(
        PermissionItem(
            icon = Icons.Default.Mic,
            title = "Microphone",
            description = "Listen to your voice commands"
        ),
        PermissionItem(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            description = "Read and reply to messages"
        ),
        PermissionItem(
            icon = Icons.Default.Accessibility,
            title = "Accessibility",
            description = "Read screen and interact with apps"
        ),
        PermissionItem(
            icon = Icons.Default.Storage,
            title = "Storage",
            description = "Save files and access photos"
        ),
        PermissionItem(
            icon = Icons.Default.Phone,
            title = "Phone",
            description = "Make calls and access contacts"
        ),
        PermissionItem(
            icon = Icons.Default.Dashboard,
            title = "Calendar",
            description = "Create and manage events"
        ),
        PermissionItem(
            icon = Icons.Default.FlashlightOn,
            title = "Flashlight",
            description = "Control device flashlight"
        ),
        PermissionItem(
            icon = Icons.Default.BrightnessMedium,
            title = "Brightness",
            description = "Adjust screen brightness"
        )
    )

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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Header
            Text(
                text = "Permissions",
                color = JarvisColors.TextPrimary,
                fontSize = 24.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "JARVIS needs a few permissions\nto help you better.",
                color = JarvisColors.TextSecondary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Default,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Permission list
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    permissions.forEach { permission ->
                        PermissionRow(permission)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Continue button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(JarvisColors.Presence)
                    .clickable { onContinue() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Continue",
                    color = JarvisColors.VoidBlack,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Skip button
            Text(
                text = "Skip for now",
                color = JarvisColors.TextMuted,
                fontSize = 14.sp,
                fontFamily = FontFamily.Default,
                modifier = Modifier
                    .clickable { onSkip() }
                    .padding(8.dp)
            )
        }
    }
}

private data class PermissionItem(
    val icon: ImageVector,
    val title: String,
    val description: String
)

@Composable
private fun PermissionRow(item: PermissionItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(JarvisColors.Presence.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = JarvisColors.Presence,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = JarvisColors.TextPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = item.description,
                color = JarvisColors.TextSecondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Default
            )
        }

        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Required",
            tint = JarvisColors.Presence,
            modifier = Modifier.size(18.dp)
        )
    }
}
