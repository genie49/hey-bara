package com.bara.evaluation.core

import kotlinx.serialization.Serializable

/** 단일 평가 태스크 정의 */
@Serializable
data class Task(
    val id: String,
    val input: String,
    val dataset: String = "default-contacts",
    val expected: Expected = Expected(),
    val metadata: TaskMetadata = TaskMetadata(),
)

/** 기대 결과 정의 */
@Serializable
data class Expected(
    val toolCalls: ToolCallsExpected? = null,
    val outputContains: List<String>? = null,
    val outputContainsAny: List<String>? = null,
    val constraints: ConstraintsExpected? = null,
    val stateCheck: List<StateCheckExpected>? = null,
    val llmGrader: LlmGraderExpected? = null,
)

/** 기대 tool call 목록 */
@Serializable
data class ToolCallsExpected(
    val mode: String = "superset",
    val calls: List<ToolCallExpected>,
)

/** 개별 기대 tool call */
@Serializable
data class ToolCallExpected(
    val tool: String,
    val params: Map<String, String> = emptyMap(),
)

/** 제약 조건 (턴 수, 호출 수, 타임아웃) */
@Serializable
data class ConstraintsExpected(
    val maxTurns: Int? = null,
    val maxToolcalls: Int? = null,
    val timeout: Long? = null,
)

/** 상태 검증 조건 */
@Serializable
data class StateCheckExpected(
    val key: String,
    val expect: String,
)

/** LLM 기반 채점 기준 */
@Serializable
data class LlmGraderExpected(
    val rubric: String,
    val minScore: Double = 0.7,
)

/** 태스크 메타데이터 */
@Serializable
data class TaskMetadata(
    val category: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
)

/** 멀티턴 시나리오 정의 */
@Serializable
data class Scenario(
    val id: String,
    val type: String = "roleplay",
    val persona: String,
    val goal: String,
    val initialMessage: String? = null,
    val dataset: String = "default-contacts",
    val maxTurns: Int = 10,
    val completionCriteria: CompletionCriteria,
    val metadata: TaskMetadata = TaskMetadata(),
)

/** 시나리오 완료 판정 기준 */
@Serializable
data class CompletionCriteria(
    val rubric: String,
    val minScore: Double = 0.7,
)

/** 실제 tool call 기록 */
data class ToolCall(
    val tool: String,
    val params: Map<String, String>,
)

/** 대화 전문 기록 */
data class Transcript(
    val messages: List<Message>,
    val toolCalls: List<ToolCall>,
    val metrics: Metrics,
)

/** 대화 메시지 */
data class Message(
    val role: String,
    val content: String,
)

/** 실행 메트릭 */
data class Metrics(
    val turns: Int = 0,
    val toolCallCount: Int = 0,
    val durationMs: Long = 0,
)

/** 개별 grader 결과 */
@Serializable
data class GraderResult(
    val grader: String,
    val passed: Boolean,
    val score: Double = if (passed) 1.0 else 0.0,
    val details: Map<String, String> = emptyMap(),
)

/** 최종 평가 결과 */
data class Outcome(
    val taskId: String,
    val graderResults: List<GraderResult>,
    val transcript: Transcript? = null,
    val error: String? = null,
    val cached: Boolean = false,
) {
    val status: String
        get() = when {
            cached -> "cached"
            error != null -> "error"
            graderResults.all { it.passed } -> "passed"
            else -> "failed"
        }
}
