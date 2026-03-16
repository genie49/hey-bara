package com.bara.evaluation.graders

import com.bara.evaluation.core.*

/**
 * Tool call 검증 Grader
 *
 * 모드:
 * - "strict"   : 순서·개수 정확히 일치
 * - "unordered" : 순서 무관, 개수 일치
 * - "subset"   : actual이 expected의 부분집합
 * - "superset" : expected가 actual의 부분집합 (기본값)
 */
class ToolCallsGrader {

    fun grade(expected: ToolCallsExpected, actual: List<ToolCall>): GraderResult {
        return when (expected.mode) {
            "strict" -> gradeStrict(expected.calls, actual)
            "unordered" -> gradeUnordered(expected.calls, actual)
            "subset" -> gradeSubset(expected.calls, actual)
            else -> gradeSuperset(expected.calls, actual)
        }
    }

    /** strict: 순서·개수 정확히 일치 */
    private fun gradeStrict(expected: List<ToolCallExpected>, actual: List<ToolCall>): GraderResult {
        if (expected.size != actual.size) {
            return fail("expected ${expected.size} calls but got ${actual.size}")
        }
        val mismatches = mutableListOf<String>()
        expected.zip(actual).forEachIndexed { i, (exp, act) ->
            if (!callMatches(exp, act)) {
                mismatches.add("[$i] expected ${exp.tool} but got ${act.tool}")
            }
        }
        return if (mismatches.isEmpty()) pass()
        else fail(mismatches.joinToString("; "))
    }

    /** unordered: 순서 무관, 개수 일치 */
    private fun gradeUnordered(expected: List<ToolCallExpected>, actual: List<ToolCall>): GraderResult {
        if (expected.size != actual.size) {
            return fail("expected ${expected.size} calls but got ${actual.size}")
        }
        return gradeSuperset(expected, actual)
    }

    /** subset: actual ⊆ expected */
    private fun gradeSubset(expected: List<ToolCallExpected>, actual: List<ToolCall>): GraderResult {
        val unexpected = actual.filter { act -> expected.none { exp -> callMatches(exp, act) } }
        return if (unexpected.isEmpty()) pass()
        else fail("unexpected calls: ${unexpected.map { it.tool }}")
    }

    /** superset: expected ⊆ actual (기본) */
    private fun gradeSuperset(expected: List<ToolCallExpected>, actual: List<ToolCall>): GraderResult {
        val remaining = actual.toMutableList()
        val missing = mutableListOf<String>()
        for (exp in expected) {
            val match = remaining.firstOrNull { callMatches(exp, it) }
            if (match != null) {
                remaining.remove(match)
            } else {
                missing.add(exp.tool)
            }
        }
        val score = if (expected.isEmpty()) 1.0 else (expected.size - missing.size).toDouble() / expected.size
        return if (missing.isEmpty()) pass()
        else GraderResult(grader = GRADER_NAME, passed = false, score = score, details = mapOf("missing" to missing.joinToString(", ")))
    }

    /** 개별 tool call 매치 검사 */
    private fun callMatches(expected: ToolCallExpected, actual: ToolCall): Boolean {
        if (expected.tool != actual.tool) return false
        // 기대 파라미터가 비어 있으면 이름만 매치
        if (expected.params.isEmpty()) return true
        // 기대하는 모든 파라미터가 actual에 존재하고 매치해야 함
        return expected.params.all { (key, expectValue) ->
            val actualValue = actual.params[key]
            matchExpect(expectValue, actualValue)
        }
    }

    private fun pass() = GraderResult(grader = GRADER_NAME, passed = true)
    private fun fail(reason: String) = GraderResult(grader = GRADER_NAME, passed = false, details = mapOf("reason" to reason))

    companion object {
        private const val GRADER_NAME = "ToolCallsGrader"
    }
}
