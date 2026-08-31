package com.jarvis.feature.tasks

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.JarvisCore
import kotlinx.coroutines.delay

data class TaskStep(
    val description: String,
    val status: TaskStepStatus = TaskStepStatus.Pending
)

enum class TaskStepStatus {
    Pending,
    InProgress,
    Completed,
    Failed
}

/**
 * Task Execution Screen — shows multi-step task progress.
 * Matches design: step-by-step progress with checkmarks.
 */
@Composable
fun TaskExecutionScreen(
    taskDescription: String,
    steps: List<TaskStep>,
    currentStepIndex: Int = 0,
    isComplete: Boolean = false,
    finalResult: String? = null,
    onDismiss: () -> Unit = {}
) {
    var animatedStepIndex by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(currentStepIndex) {
        if (currentStepIndex > animatedStepIndex) {
            delay(300)
            animatedStepIndex = currentStepIndex
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Task description
        Text(
            text = taskDescription,
            color = JarvisColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Orb in executing state
        JarvisCore(
            state = if (isComplete) JarvisVisualState.SUCCESS else JarvisVisualState.EXECUTING,
            size = 80.dp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // JARVIS status
        Text(
            text = "JARVIS",
            color = JarvisColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = when {
                isComplete -> "Task complete"
                currentStepIndex < steps.size -> "Working on it..."
                else -> "Preparing..."
            },
            color = JarvisColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Steps list
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(steps) { index, step ->
                val status = when {
                    index < animatedStepIndex -> TaskStepStatus.Completed
                    index == animatedStepIndex && !isComplete -> TaskStepStatus.InProgress
                    isComplete && index == steps.lastIndex -> TaskStepStatus.Completed
                    else -> TaskStepStatus.Pending
                }
                
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status icon
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when (status) {
                                        TaskStepStatus.Completed -> JarvisColors.StateSuccess.copy(alpha = 0.15f)
                                        TaskStepStatus.InProgress -> JarvisColors.StateExecuting.copy(alpha = 0.15f)
                                        TaskStepStatus.Failed -> JarvisColors.StateError.copy(alpha = 0.15f)
                                        TaskStepStatus.Pending -> JarvisColors.SurfaceCard
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (status) {
                                    TaskStepStatus.Completed -> "✓"
                                    TaskStepStatus.InProgress -> "●"
                                    TaskStepStatus.Failed -> "✗"
                                    TaskStepStatus.Pending -> "○"
                                },
                                color = when (status) {
                                    TaskStepStatus.Completed -> JarvisColors.StateSuccess
                                    TaskStepStatus.InProgress -> JarvisColors.StateExecuting
                                    TaskStepStatus.Failed -> JarvisColors.StateError
                                    TaskStepStatus.Pending -> JarvisColors.TextMuted
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Step description
                        Text(
                            text = step.description,
                            color = when (status) {
                                TaskStepStatus.Completed -> JarvisColors.TextSecondary
                                TaskStepStatus.InProgress -> JarvisColors.TextPrimary
                                TaskStepStatus.Failed -> JarvisColors.StateError
                                TaskStepStatus.Pending -> JarvisColors.TextMuted
                            },
                            fontSize = 14.sp,
                            fontWeight = if (status == TaskStepStatus.InProgress) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }
        
        // Final result
        if (isComplete && finalResult != null) {
            Spacer(modifier = Modifier.height(16.dp))
            
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Result",
                        color = JarvisColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = finalResult,
                        color = JarvisColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}
