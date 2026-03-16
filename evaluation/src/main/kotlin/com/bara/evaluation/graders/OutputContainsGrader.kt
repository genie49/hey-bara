package com.bara.evaluation.graders

import com.bara.evaluation.core.GraderResult

/**
 * 출력에 모든 키워드가 포함되어 있는지 검증하는 Grader
 */
class OutputContainsGrader {

    fun grade(keywords: List<String>, output: String): GraderResult {
        val matched = keywords.filter { output.contains(it) }
        val missing = keywords - matched.toSet()
        val score = if (keywords.isEmpty()) 1.0 else matched.size.toDouble() / keywords.size
        return GraderResult(
            grader = "OutputContainsGrader",
            passed = missing.isEmpty(),
            score = score,
            details = buildMap {
                put("matched", matched.joinToString(", "))
                if (missing.isNotEmpty()) put("missing", missing.joinToString(", "))
            },
        )
    }
}
