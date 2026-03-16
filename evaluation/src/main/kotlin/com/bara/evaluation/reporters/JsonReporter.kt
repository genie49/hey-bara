package com.bara.evaluation.reporters

import com.bara.evaluation.core.Outcome
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * JSON 리포터 — 평가 결과를 JSON 파일로 저장
 *
 * evaluation/results/{timestamp}/summary.json 에 저장한다.
 */
class JsonReporter(private val baseDir: String = "evaluation/results") {
    private val json = Json { prettyPrint = true }

    fun report(outcomes: List<Outcome>): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val dir = File(baseDir, timestamp)
        dir.mkdirs()

        val summary = Summary(
            timestamp = timestamp,
            total = outcomes.size,
            passed = outcomes.count { it.status == "passed" },
            failed = outcomes.count { it.status == "failed" },
            errors = outcomes.count { it.status == "error" },
            cached = outcomes.count { it.status == "cached" },
            results = outcomes.map { outcome ->
                TaskResult(
                    taskId = outcome.taskId,
                    status = outcome.status,
                    error = outcome.error,
                    graders = outcome.graderResults.map { gr ->
                        GraderSummary(
                            grader = gr.grader,
                            passed = gr.passed,
                            score = gr.score,
                            details = gr.details,
                        )
                    },
                )
            },
        )

        val file = File(dir, "summary.json")
        file.writeText(json.encodeToString(summary))
        return file
    }
}

@Serializable
data class Summary(
    val timestamp: String,
    val total: Int,
    val passed: Int,
    val failed: Int,
    val errors: Int,
    val cached: Int,
    val results: List<TaskResult>,
)

@Serializable
data class TaskResult(
    val taskId: String,
    val status: String,
    val error: String? = null,
    val graders: List<GraderSummary>,
)

@Serializable
data class GraderSummary(
    val grader: String,
    val passed: Boolean,
    val score: Double,
    val details: Map<String, String>,
)
