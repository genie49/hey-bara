package com.bara.evaluation.core

import com.bara.evaluation.graders.*
import com.bara.evaluation.mocks.MockStateStore

/**
 * 평가 엔진 — 모든 grader를 조합하여 평가 실행
 *
 * expected 필드 유무에 따라 해당 grader만 실행하고,
 * graderFilter로 특정 grader만 선택적으로 실행할 수 있다.
 */
class GraderEngine(
    private val apiKey: String = "",
    private val graderFilter: Set<String>? = null,
) {
    private val toolCallsGrader = ToolCallsGrader()
    private val outputContainsGrader = OutputContainsGrader()
    private val outputContainsAnyGrader = OutputContainsAnyGrader()
    private val constraintsGrader = ConstraintsGrader()
    private val stateCheckGrader = StateCheckGrader()
    private val llmGrader: LlmGrader? = if (apiKey.isNotEmpty()) LlmGrader(apiKey) else null

    private fun shouldRun(name: String): Boolean = graderFilter == null || name in graderFilter

    /**
     * 동기 평가 — 결정적 grader들만 실행 (LLM 불필요)
     */
    fun gradeSync(
        expected: Expected,
        transcript: Transcript,
        stateStore: MockStateStore,
        input: String,
    ): List<GraderResult> {
        val results = mutableListOf<GraderResult>()
        val output = transcript.messages.lastOrNull { it.role == "assistant" }?.content ?: ""

        if (expected.toolCalls != null && shouldRun("tool_calls"))
            results.add(toolCallsGrader.grade(expected.toolCalls, transcript.toolCalls))
        if (expected.outputContains != null && shouldRun("output_contains"))
            results.add(outputContainsGrader.grade(expected.outputContains, output))
        if (expected.outputContainsAny != null && shouldRun("output_contains_any"))
            results.add(outputContainsAnyGrader.grade(expected.outputContainsAny, output))
        if (expected.constraints != null && shouldRun("constraints"))
            results.add(constraintsGrader.grade(expected.constraints, transcript.metrics))
        if (expected.stateCheck != null && shouldRun("state_check"))
            results.add(stateCheckGrader.grade(expected.stateCheck, stateStore))
        return results
    }

    /**
     * 전체 평가 — 결정적 grader + LLM grader 포함 (suspend)
     */
    suspend fun gradeFull(
        expected: Expected,
        transcript: Transcript,
        stateStore: MockStateStore,
        input: String,
    ): List<GraderResult> {
        val results = gradeSync(expected, transcript, stateStore, input).toMutableList()
        val output = transcript.messages.lastOrNull { it.role == "assistant" }?.content ?: ""
        if (expected.llmGrader != null && shouldRun("llm_grader") && llmGrader != null)
            results.add(llmGrader.grade(expected.llmGrader, input, output, transcript.toolCalls))
        return results
    }
}
