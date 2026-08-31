package com.jarvis.feature.actions

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.JarvisCore
import kotlinx.coroutines.delay

/**
 * Device Actions Screen — shows the result of a device action.
 * Matches design: flashlight toggle, volume control, etc.
 */
@Composable
fun DeviceActionsScreen(
    actionName: String,
    actionResult: String,
    actionIcon: String = "⚡",
    isComplete: Boolean = false,
    onDismiss: () -> Unit = {}
) {
    var showResult by remember { mutableStateOf(false) }
    
    LaunchedEffect(actionResult) {
        delay(500)
        showResult = true
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Action title
        Text(
            text = actionName,
            color = JarvisColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Orb in executing state
        JarvisCore(
            state = if (isComplete) JarvisVisualState.Success else JarvisVisualState.Executing,
            size = 120
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // JARVIS response
        Text(
            text = "JARVIS",
            color = JarvisColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = actionResult,
            color = JarvisColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Action status card
        if (showResult) {
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = actionName.uppercase(),
                            color = JarvisColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = if (isComplete) "ON" else "OFF",
                            color = if (isComplete) JarvisColors.StateSuccess else JarvisColors.TextSecondary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Action icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isComplete) 
                                    JarvisColors.StateSuccess.copy(alpha = 0.15f)
                                else 
                                    JarvisColors.SurfaceCard
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = actionIcon,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Completion status
        if (isComplete) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(JarvisColors.StateSuccess)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Done.",
                    color = JarvisColors.StateSuccess,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
