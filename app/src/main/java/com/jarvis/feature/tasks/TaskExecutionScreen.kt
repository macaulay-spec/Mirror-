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
import androidx.compose.runtime.collectAsState
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
import com.jarvis.agent.ai.AgentExecutor
import com.jarvis.agent.ai.plan.AgentStep
import com.jarvis.agent.ai.plan.StepStatus
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.JarvisCore
import kotlinx.coroutines.delay

/**
 * Task Execution Screen - Visualizes multi-step task execution.
 * 
 * This screen displays the step-by-step progress of JARVIS executing a task.
 * Each step shows its state (Pending, In Progress, Completed, Failed) with
 * appropriate visual feedback.
 *
 * Design requirements from Mistral.md:
 * - Shows vertical timeline of execution steps
 * - Each step has a status indicator (circle, checkmark, cross)
 * - Steps animate as they progress
 * - Orb shows executing state during processing
 * - Final result displayed in a card
 *
 * Example flow:
 * Understanding request ↓ Opening WhatsApp ↓ Finding conversation with John ↓ Reading latest message ↓ Preparing response
 */

// Status for UI display
enum class TaskStepStatus {
    Pending,
    InProgress,
    Completed,
    Failed
}

/**
 * Data class for displaying a single step in the execution pipeline.
 */
data class TaskStep(
    val description: String,
    val status: TaskStepStatus = TaskStepStatus.Pending
)

/**
 * Main Task Execution Screen composable.
 * 
 * @param agentExecutor The executor to observe for real-time progress
 * @param taskDescription The user's original request/description
 * @param onDismiss Callback when user dismisses the screen
 */
@Composable
fun TaskExecutionScreen(
    agentExecutor: AgentExecutor?,
    taskDescription: String,
    onDismiss: () -> Unit = {}
) {
    // Collect state from the executor
    val currentSteps = agentExecutor?.currentPlan?.collectAsState()?.value?.steps ?: emptyList()
    val currentStepIndex = agentExecutor?.currentStepIndex?.collectAsState()?.value ?: 0
    val executionState = agentExecutor?.executionState?.collectAsState()?.value ?: JarvisVisualState.IDLE
    val isComplete = agentExecutor?.isExecutionComplete?.collectAsState()?.value ?: false
    val finalResult = agentExecutor?.finalResult?.collectAsState()?.value

    // Animated step index for smooth transitions
    var animatedStepIndex by remember { mutableIntStateOf(0) }

    // Update animated index when current step changes
    LaunchedEffect(currentStepIndex) {
        if (currentStepIndex > animatedStepIndex) {
            delay(200)
            animatedStepIndex = currentStepIndex
        }
    }

    // Convert AgentStep to TaskStep for UI
    val displaySteps = remember(currentSteps, animatedStepIndex, isComplete) {
        currentSteps.mapIndexed { index, step ->
            val status = when {
                index < animatedStepIndex -> TaskStepStatus.Completed
                index == animatedStepIndex && !isComplete -> TaskStepStatus.InProgress
                isComplete && index == currentSteps.lastIndex -> TaskStepStatus.Completed
                step.status == StepStatus.FAILED -> TaskStepStatus.Failed
                else -> TaskStepStatus.Pending
            }
            
            TaskStep(
                description = step.expectedResult ?: step.tool.replace("_", " "),
                status = status
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header: Task description
        Text(
            text = taskDescription,
            color = JarvisColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Orb showing current state
        JarvisCore(
            state = when {
                isComplete -> JarvisVisualState.SUCCESS
                executionState == JarvisVisualState.ERROR -> JarvisVisualState.ERROR
                else -> JarvisVisualState.EXECUTING
            },
            size = 80.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // JARVIS label
        Text(
            text = "JARVIS",
            color = JarvisColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status text
        Text(
            text = when {
                isComplete -> "Task complete"
                executionState == JarvisVisualState.ERROR -> "Error occurred"
                currentStepIndex < displaySteps.size -> "Working on it..."
                else -> "Preparing..."
            },
            color = when {
                executionState == JarvisVisualState.ERROR -> JarvisColors.StateError
                else -> JarvisColors.TextPrimary
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Steps list - vertical timeline
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(displaySteps) { index, step ->
                StepCard(
                    step = step,
                    isLast = index == displaySteps.lastIndex,
                    isFirst = index == 0
                )
            }
        }

        // Final result display
        if (isComplete && !finalResult.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            ResultCard(result = finalResult)
        }

        // Error display
        if (executionState == JarvisVisualState.ERROR) {
            Spacer(modifier = Modifier.height(16.dp))
            ErrorCard(message = finalResult ?: "An error occurred during execution")
        }
    }
}

/**
 * Individual step card for the execution pipeline.
 */
@Composable
private fun StepCard(
    step: TaskStep,
    isFirst: Boolean,
    isLast: Boolean
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator icon
            StatusIndicator(status = step.status)

            Spacer(modifier = Modifier.width(12.dp))

            // Step description
            Text(
                text = step.description,
                color = when (step.status) {
                    TaskStepStatus.Completed -> JarvisColors.TextSecondary
                    TaskStepStatus.InProgress -> JarvisColors.TextPrimary
                    TaskStepStatus.Failed -> JarvisColors.StateError
                    TaskStepStatus.Pending -> JarvisColors.TextMuted
                },
                fontSize = 14.sp,
                fontWeight = if (step.status == TaskStepStatus.InProgress) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

/**
 * Status indicator component showing step state.
 */
@Composable
private fun StatusIndicator(status: TaskStepStatus) {
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
                TaskStepStatus.Completed -> "✓"  // Checkmark
                TaskStepStatus.InProgress -> "●"  // Filled circle
                TaskStepStatus.Failed -> "✗"  // Cross
                TaskStepStatus.Pending -> "○"  // Empty circle
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
}

/**
 * Result card showing the final output of task execution.
 */
@Composable
private fun ResultCard(result: String) {
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
                text = result,
                color = JarvisColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

/**
 * Error card showing error message.
 */
@Composable
private fun ErrorCard(message: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Error",
                color = JarvisColors.StateError,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                color = JarvisColors.StateError,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}
