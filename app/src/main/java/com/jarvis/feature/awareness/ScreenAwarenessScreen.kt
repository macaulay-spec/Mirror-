package com.jarvis.feature.awareness

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.core.theme.JarvisColors
import com.jarvis.android.accessibility.JarvisAccessibilityService
import kotlinx.coroutines.delay

@Composable
fun ScreenAwarenessScreen(
    onDismiss: () -> Unit = {}
) {
    val service = JarvisAccessibilityService.instance
    val appName = service?.currentPackageName ?: "Unknown App"
    val isReady = service != null

    var analysisProgress by remember { mutableIntStateOf(0) }

    val capabilities = remember(appName) {
        if (isReady) {
            listOf(
                "I can see this is $appName.",
                "I am reading the screen layout.",
                "I can interact with the elements here."
            )
        } else {
            listOf(
                "Accessibility Service is not running.",
                "Please enable JARVIS in Settings."
            )
        }
    }

    LaunchedEffect(isReady) {
        capabilities.forEachIndexed { index, _ ->
            delay(1000)
            analysisProgress = index + 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(JarvisColors.SurfaceGlass)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Screen Awareness",
                    color = JarvisColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = JarvisColors.TextMuted,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            capabilities.forEachIndexed { index, text ->
                AnimatedVisibility(visible = analysisProgress > index) {
                    Text(
                        text = text,
                        color = JarvisColors.Presence,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}
