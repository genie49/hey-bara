package com.bara.evaluation.graders

import com.bara.evaluation.core.*

/**
 * 제약 조건(턴 수, tool call 수, 타임아웃) 검증 Grader
 */
class ConstraintsGrader {

    fun grade(constraints: ConstraintsExpected, metrics: Metrics): GraderResult {
        val violations = mutableListOf<String>()

        constraints.maxTurns?.let { max ->
            if (metrics.turns > max) violations.add("turns ${metrics.turns} > max $max")
        }
        constraints.maxToolcalls?.let { max ->
            if (metrics.toolCallCount > max) violations.add("toolCalls ${metrics.toolCallCount} > max $max")
        }
        constraints.timeout?.let { max ->
            if (metrics.durationMs > max) violations.add("duration ${metrics.durationMs}ms > timeout ${max}ms")
        }

        return GraderResult(
            grader = "ConstraintsGrader",
            passed = violations.isEmpty(),
            details = if (violations.isNotEmpty()) mapOf("violations" to violations.joinToString("; ")) else emptyMap(),
        )
    }
}
