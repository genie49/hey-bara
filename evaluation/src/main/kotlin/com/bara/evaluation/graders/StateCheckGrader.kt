package com.bara.evaluation.graders

import com.bara.evaluation.core.*
import com.bara.evaluation.mocks.MockStateStore

/**
 * 상태 저장소의 값을 검증하는 Grader
 *
 * 각 StateCheckExpected의 key 경로로 MockStateStore를 조회하고
 * expect 패턴과 matchExpect()로 비교
 */
class StateCheckGrader {

    fun grade(checks: List<StateCheckExpected>, store: MockStateStore): GraderResult {
        val failures = mutableListOf<String>()
        var passedCount = 0

        for (check in checks) {
            val actual = store.get(check.key)
            if (matchExpect(check.expect, actual)) {
                passedCount++
            } else {
                failures.add("${check.key}: expected '${check.expect}' but got '$actual'")
            }
        }

        val score = if (checks.isEmpty()) 1.0 else passedCount.toDouble() / checks.size
        return GraderResult(
            grader = "StateCheckGrader",
            passed = failures.isEmpty(),
            score = score,
            details = if (failures.isNotEmpty()) mapOf("failures" to failures.joinToString("; ")) else emptyMap(),
        )
    }
}
