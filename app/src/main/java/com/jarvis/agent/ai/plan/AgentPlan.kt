package com.jarvis.agent.ai.plan

enum class StepStatus {
    PENDING,
    EXECUTING,
    SUCCESS,
    FAILED,
    CANCELLED
}

enum class PlanStatus {
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class AgentStep(
    val id: String = java.util.UUID.randomUUID().toString(),
    val tool: String,
    val arguments: Map<String, Any>,
    val expectedResult: String? = null,
    var status: StepStatus = StepStatus.PENDING,
    var result: String? = null,
    var error: String? = null
)

data class AgentPlan(
    val id: String = java.util.UUID.randomUUID().toString(),
    val goal: String,
    val steps: MutableList<AgentStep> = mutableListOf(),
    var currentStepIndex: Int = 0,
    var status: PlanStatus = PlanStatus.IN_PROGRESS,
    val createdAt: Long = System.currentTimeMillis(),
    val timeoutMs: Long = 60000,
    var retryCount: Int = 0
) {
    fun currentStep(): AgentStep? = steps.getOrNull(currentStepIndex)
}
