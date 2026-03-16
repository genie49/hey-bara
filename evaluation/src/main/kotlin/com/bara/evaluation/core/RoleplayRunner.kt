package com.bara.evaluation.core

import com.bara.evaluation.agent.AgentConfig
import com.bara.evaluation.agent.EvalAgentFactory
import com.bara.evaluation.datasets.DATASETS
import com.bara.evaluation.graders.LlmGrader
import com.bara.evaluation.mocks.MockContactResolver
import com.bara.evaluation.mocks.MockStateStore

/**
 * 롤플레이 실행기 — 시나리오 하나를 멀티턴으로 실행하고 결과를 수집
 *
 * Roleplayer가 사용자 역할을, Judge가 목표 달성 여부를 판정하며,
 * 최종적으로 LlmGrader가 rubric 기반 채점을 수행한다.
 */
class RoleplayRunner(private val apiKey: String) {

    suspend fun run(scenario: Scenario): Outcome {
        // 1. 인프라 셋업
        val contacts = DATASETS[scenario.dataset] ?: error("Unknown dataset: ${scenario.dataset}")
        val resolver = MockContactResolver(contacts)
        val stateStore = MockStateStore()
        val config = AgentConfig(apiKey)
        val factory = EvalAgentFactory(config)
        val roleplayer = Roleplayer(apiKey)
        val judge = Judge(apiKey)
        val llmGrader = LlmGrader(apiKey)

        val messages = mutableListOf<Message>()
        val allToolCalls = mutableListOf<ToolCall>()
        val startTime = System.currentTimeMillis()

        // 2. 첫 번째 사용자 메시지
        val firstMessage = scenario.initialMessage
            ?: roleplayer.generateMessage(scenario.persona, emptyList())
        messages.add(Message("user", firstMessage))

        var finalVerdict = "continue"

        // 3. 멀티턴 대화 루프
        for (turn in 1..scenario.maxTurns) {
            // a. 대화 히스토리를 문자열로 변환
            val historyText = messages.dropLast(1).joinToString("\n") { msg ->
                val role = if (msg.role == "user") "사용자" else "비서"
                "[$role] ${msg.content}"
            }

            // b. 에이전트 생성 및 실행
            val agent = factory.createWithHistory(resolver, stateStore, historyText)
            val lastUserMessage = messages.last { it.role == "user" }.content
            val agentResponse = agent.run(lastUserMessage)
            messages.add(Message("assistant", agentResponse))

            // tool call 기록 수집
            collectToolCalls(stateStore, allToolCalls)

            // c. Judge가 목표 달성 여부 판정
            val verdict = judge.evaluate(scenario.goal, messages)

            // d. 목표 달성 또는 진행 불가 시 종료
            if (verdict.verdict == "goal_achieved" || verdict.verdict == "cannot_continue") {
                finalVerdict = verdict.verdict
                break
            }

            // e. 마지막 턴이 아니면 Roleplayer가 다음 사용자 메시지 생성
            if (turn < scenario.maxTurns) {
                val nextMessage = roleplayer.generateMessage(scenario.persona, messages)
                messages.add(Message("user", nextMessage))
            }
        }

        val durationMs = System.currentTimeMillis() - startTime

        // 4. 최종 rubric 기반 채점
        val graderResult = llmGrader.gradeConversation(
            rubricFile = scenario.completionCriteria.rubric,
            minScore = scenario.completionCriteria.minScore,
            messages = messages,
        )

        // 5. cannot_continue인 경우 자동 실패 처리
        val finalGraderResult = if (finalVerdict == "cannot_continue") {
            graderResult.copy(
                passed = false,
                details = graderResult.details + ("autoFail" to "cannot_continue로 인해 자동 실패"),
            )
        } else {
            graderResult
        }

        // 6. Outcome 반환
        val transcript = Transcript(
            messages = messages,
            toolCalls = allToolCalls,
            metrics = Metrics(
                turns = messages.count { it.role == "user" },
                toolCallCount = allToolCalls.size,
                durationMs = durationMs,
            ),
        )

        return Outcome(
            taskId = scenario.id,
            graderResults = listOf(finalGraderResult),
            transcript = transcript,
        )
    }

    /** MockStateStore에서 새로운 tool call 기록을 수집 */
    private fun collectToolCalls(stateStore: MockStateStore, allToolCalls: MutableList<ToolCall>) {
        val currentSize = allToolCalls.size
        val newToolCalls = mutableListOf<ToolCall>()

        stateStore.searchQueries.drop(allToolCalls.count { it.tool == "search_contacts" }).forEach { query ->
            newToolCalls.add(ToolCall("search_contacts", mapOf("query" to query)))
        }
        stateStore.calls.drop(allToolCalls.count { it.tool == "make_call" }).forEach { call ->
            newToolCalls.add(ToolCall("make_call", mapOf("contact" to call.contact, "phoneNumber" to call.phoneNumber)))
        }
        stateStore.sms.drop(allToolCalls.count { it.tool == "send_sms" }).forEach { sms ->
            newToolCalls.add(ToolCall("send_sms", mapOf("contact" to sms.contact, "phoneNumber" to sms.phoneNumber, "message" to sms.message)))
        }

        allToolCalls.addAll(newToolCalls)
    }
}
