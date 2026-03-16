package com.bara.evaluation.cli

import com.bara.evaluation.core.Cache
import com.bara.evaluation.core.EvalRunner
import com.bara.evaluation.core.RoleplayRunner
import com.bara.evaluation.core.ScenarioManager
import com.bara.evaluation.core.TaskManager
import com.bara.evaluation.reporters.ConsoleReporter
import com.bara.evaluation.reporters.JsonReporter
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking

/** CLI 진입점 빌더 */
fun buildCli(): NoOpCliktCommand {
    return NoOpCliktCommand("bara-eval")
        .subcommands(
            TasksCommand(),
            RunCommand(),
            ScenariosCommand(),
            RoleplayCommand(),
            CacheStatusCommand(),
            ClearCacheCommand(),
        )
}

/** tasks — 등록된 태스크 목록 출력 */
class TasksCommand : CliktCommand("tasks") {
    private val category by option("--category", "-c", help = "카테고리 필터")
    private val tag by option("--tag", "-t", help = "태그 필터")

    override fun run() {
        val manager = TaskManager()
        val tasks = when {
            category != null -> manager.loadByCategory(category!!)
            tag != null -> manager.loadByTag(tag!!)
            else -> manager.loadAll()
        }
        if (tasks.isEmpty()) {
            echo("등록된 태스크가 없습니다.")
            return
        }
        echo("등록된 태스크 (${tasks.size}개):")
        echo()
        tasks.forEach { task ->
            echo("  ${task.id}")
            echo("    input: ${task.input}")
            if (task.metadata.category.isNotEmpty()) echo("    category: ${task.metadata.category}")
            if (task.metadata.tags.isNotEmpty()) echo("    tags: ${task.metadata.tags.joinToString(", ")}")
            echo()
        }
    }
}

/** run — 평가 실행 */
class RunCommand : CliktCommand("run") {
    private val apiKey by option("--api-key", "-k", help = "Gemini API 키", envvar = "GEMINI_API_KEY")
    private val concurrency by option("--concurrency", "-j", help = "동시 실행 수").int().default(4)
    private val category by option("--category", "-c", help = "카테고리 필터")
    private val tag by option("--tag", "-t", help = "태그 필터")
    private val useCache by option("--cache", help = "캐시 사용 여부 (true/false)").default("true")

    override fun run() {
        val key = apiKey ?: run {
            echo("API 키가 필요합니다. --api-key 또는 GEMINI_API_KEY 환경변수를 설정하세요.", err = true)
            return
        }
        val manager = TaskManager()
        val tasks = when {
            category != null -> manager.loadByCategory(category!!)
            tag != null -> manager.loadByTag(tag!!)
            else -> manager.loadAll()
        }
        if (tasks.isEmpty()) {
            echo("실행할 태스크가 없습니다.")
            return
        }

        echo("${tasks.size}개 태스크 실행 중... (concurrency=$concurrency)")

        val cache = if (useCache == "true") Cache() else null
        val runner = EvalRunner(key, concurrency, cache = cache)
        val outcomes = runBlocking { runner.run(tasks) }

        ConsoleReporter().report(outcomes)
        val file = JsonReporter().report(outcomes)
        echo("결과 저장: ${file.absolutePath}")
    }
}

/** scenarios — 등록된 롤플레이 시나리오 목록 출력 */
class ScenariosCommand : CliktCommand("scenarios") {
    private val agent by option("--agent", "-a", help = "에이전트 ID 필터")

    override fun run() {
        val manager = ScenarioManager()
        val scenarios = when {
            agent != null -> manager.loadByAgent(agent!!)
            else -> manager.loadAll()
        }
        if (scenarios.isEmpty()) {
            echo("등록된 시나리오가 없습니다.")
            return
        }
        echo("등록된 시나리오 (${scenarios.size}개):")
        echo()
        scenarios.forEach { scenario ->
            echo("  ${scenario.id}")
            echo("    persona: ${scenario.persona.take(60)}...")
            echo("    goal: ${scenario.goal.take(60)}...")
            echo("    maxTurns: ${scenario.maxTurns}")
            if (scenario.metadata.category.isNotEmpty()) echo("    category: ${scenario.metadata.category}")
            echo()
        }
    }
}

/** roleplay — 롤플레이 평가 실행 */
class RoleplayCommand : CliktCommand("roleplay") {
    private val apiKey by option("--api-key", "-k", help = "Gemini API 키", envvar = "GEMINI_API_KEY")
    private val scenarioId by option("--scenario", "-s", help = "실행할 시나리오 ID")
    private val agent by option("--agent", "-a", help = "에이전트 ID 필터")

    override fun run() {
        val key = apiKey ?: run {
            echo("API 키가 필요합니다. --api-key 또는 GEMINI_API_KEY 환경변수를 설정하세요.", err = true)
            return
        }
        val manager = ScenarioManager()
        val scenarios = when {
            scenarioId != null -> listOfNotNull(manager.loadById(scenarioId!!))
            agent != null -> manager.loadByAgent(agent!!)
            else -> manager.loadAll()
        }
        if (scenarios.isEmpty()) {
            echo("실행할 시나리오가 없습니다.")
            return
        }

        echo("${scenarios.size}개 시나리오 롤플레이 실행 중...")

        val runner = RoleplayRunner(key)
        val outcomes = runBlocking {
            scenarios.map { scenario ->
                echo("  실행 중: ${scenario.id}")
                runner.run(scenario)
            }
        }

        ConsoleReporter().report(outcomes)
        val file = JsonReporter().report(outcomes)
        echo("결과 저장: ${file.absolutePath}")
    }
}

/** cache-status — 캐시 상태 출력 */
class CacheStatusCommand : CliktCommand("cache-status") {
    override fun run() {
        val cache = Cache()
        echo("캐시된 태스크 수: ${cache.size()}")
    }
}

/** clear-cache — 캐시 초기화 */
class ClearCacheCommand : CliktCommand("clear-cache") {
    override fun run() {
        val cache = Cache()
        cache.clear()
        echo("캐시가 초기화되었습니다.")
    }
}
