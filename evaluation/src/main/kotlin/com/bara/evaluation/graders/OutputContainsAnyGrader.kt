package com.bara.evaluation.graders

import com.bara.evaluation.core.GraderResult

/**
 * 출력에 키워드 중 하나라도 포함되어 있는지 검증하는 Grader
 */
class OutputContainsAnyGrader {

    fun grade(keywords: List<String>, output: String): GraderResult {
        val matched = keywords.filter { output.contains(it) }
        val score = if (keywords.isEmpty()) 1.0 else matched.size.toDouble() / keywords.size
        return GraderResult(
            grader = "OutputContainsAnyGrader",
            passed = matched.isNotEmpty(),
            score = score,
            details = buildMap {
                put("matched", matched.joinToString(", "))
                if (matched.isEmpty()) put("missing", keywords.joinToString(", "))
            },
        )
    }
}
