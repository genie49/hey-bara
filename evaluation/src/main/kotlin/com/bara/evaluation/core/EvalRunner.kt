package com.bara.evaluation.core

import com.bara.evaluation.agent.AgentConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 평가 실행 엔진 — 태스크 목록을 병렬 실행하고 채점 결과를 수집
 *
 * 동시성 제한, 캐시, grader 필터를 지원한다.
 */
class EvalRunner(
    private val apiKey: String,
    private val concurrency: Int = 4,
    private val graderFilter: Set<String>? = null,
    private val cache: Cache? = null,
) {
    suspend fun run(tasks: List<Task>): List<Outcome> {
        val semaphore = Semaphore(concurrency)
        val config = AgentConfig(apiKey)
        val agentRunner = AgentRunner(config)
        val graderEngine = GraderEngine(apiKey, graderFilter)

        return coroutineScope {
            tasks.map { task ->
                async { semaphore.withPermit { runTask(task, agentRunner, graderEngine) } }
            }.awaitAll()
        }
    }

    private suspend fun runTask(task: Task, agentRunner: AgentRunner, graderEngine: GraderEngine): Outcome {
        if (cache?.has(task) == true) {
            return Outcome(taskId = task.id, graderResults = emptyList(), cached = true)
        }
        return try {
            val result = agentRunner.run(task)
            val graderResults = graderEngine.gradeFull(
                expected = task.expected,
                transcript = result.transcript,
                stateStore = result.stateStore,
                input = task.input,
            )
            val outcome = Outcome(taskId = task.id, graderResults = graderResults, transcript = result.transcript)
            if (outcome.status == "passed") cache?.add(task)
            outcome
        } catch (e: Exception) {
            Outcome(taskId = task.id, graderResults = emptyList(), error = e.message ?: "Unknown error")
        }
    }
}
