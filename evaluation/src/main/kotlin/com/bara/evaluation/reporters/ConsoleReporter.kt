package com.bara.evaluation.reporters

import com.bara.evaluation.core.Outcome

/**
 * 콘솔 리포터 — 평가 결과를 테이블 형태로 출력
 */
class ConsoleReporter {
    fun report(outcomes: List<Outcome>) {
        val separator = "─".repeat(80)
        println()
        println(separator)
        println("  평가 결과 요약")
        println(separator)
        println()
        println("  %-40s  %-10s  %s".format("Task ID", "Status", "Graders"))
        println("  ${"─".repeat(40)}  ${"─".repeat(10)}  ${"─".repeat(24)}")

        outcomes.forEach { outcome ->
            val statusIcon = when (outcome.status) {
                "passed" -> "PASS"
                "failed" -> "FAIL"
                "cached" -> "CACHED"
                "error" -> "ERROR"
                else -> outcome.status
            }
            val graderSummary = if (outcome.error != null) {
                outcome.error
            } else if (outcome.cached) {
                "(cached)"
            } else {
                val passed = outcome.graderResults.count { it.passed }
                val total = outcome.graderResults.size
                "$passed/$total passed"
            }
            println("  %-40s  %-10s  %s".format(outcome.taskId, statusIcon, graderSummary))
        }

        println()
        println(separator)

        val total = outcomes.size
        val passed = outcomes.count { it.status == "passed" }
        val failed = outcomes.count { it.status == "failed" }
        val errors = outcomes.count { it.status == "error" }
        val cached = outcomes.count { it.status == "cached" }

        println("  Total: $total | Passed: $passed | Failed: $failed | Errors: $errors | Cached: $cached")
        if (total > 0) {
            val passRate = (passed + cached).toDouble() / total * 100
            println("  Pass Rate: ${"%.1f".format(passRate)}%")
        }
        println(separator)
        println()
    }
}
