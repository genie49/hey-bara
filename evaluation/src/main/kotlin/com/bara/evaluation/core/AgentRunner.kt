package com.bara.evaluation.core

import com.bara.evaluation.agent.AgentConfig
import com.bara.evaluation.agent.EvalAgentFactory
import com.bara.evaluation.datasets.DATASETS
import com.bara.evaluation.mocks.MockContactResolver
import com.bara.evaluation.mocks.MockStateStore

/**
 * 에이전트 실행기 — 태스크 하나를 에이전트에 전달하고 결과를 수집
 *
 * Mock 데이터를 준비하고, EvalAgentFactory로 에이전트를 생성하여 실행한 뒤,
 * MockStateStore에서 tool call 기록을 재구성한다.
 */
class AgentRunner(private val config: AgentConfig) {
    suspend fun run(task: Task): AgentRunResult {
        val contacts = DATASETS[task.dataset] ?: error("Unknown dataset: ${task.dataset}")
        val resolver = MockContactResolver(contacts)
        val stateStore = MockStateStore()
        val factory = EvalAgentFactory(config)
        val agent = factory.create(resolver, stateStore)

        val toolCalls = mutableListOf<ToolCall>()
        val startTime = System.currentTimeMillis()
        val result = agent.run(task.input)
        val durationMs = System.currentTimeMillis() - startTime

        // StateStore에서 tool call 기록 재구성
        stateStore.searchQueries.forEach { query ->
            toolCalls.add(ToolCall("search_contacts", mapOf("query" to query)))
        }
        stateStore.calls.forEach { call ->
            toolCalls.add(ToolCall("make_call", mapOf("contact" to call.contact, "phoneNumber" to call.phoneNumber)))
        }
        stateStore.sms.forEach { sms ->
            toolCalls.add(ToolCall("send_sms", mapOf("contact" to sms.contact, "phoneNumber" to sms.phoneNumber, "message" to sms.message)))
        }

        val transcript = Transcript(
            messages = listOf(Message("user", task.input), Message("assistant", result)),
            toolCalls = toolCalls,
            metrics = Metrics(turns = 1, toolCallCount = toolCalls.size, durationMs = durationMs),
        )
        return AgentRunResult(transcript, stateStore)
    }
}

data class AgentRunResult(val transcript: Transcript, val stateStore: MockStateStore)
