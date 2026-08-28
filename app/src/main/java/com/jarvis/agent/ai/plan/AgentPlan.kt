package com.jarvis.agent.ai.plan

data class AgentPlan(
    val goal: String,
    val steps: MutableList<AgentStep> = mutableListOf()
)

data class AgentStep(
    val tool: String,
    val arguments: Map<String, Any?> = emptyMap(),
    val expectedResult: String? = null,
    var status: StepStatus = StepStatus.PENDING,
    var result: String? = null,
    var error: String? = null
)

enum class StepStatus { PENDING, EXECUTING, SUCCESS, FAILED }
enum class PlanStatus { ACTIVE, COMPLETED, FAILED }
