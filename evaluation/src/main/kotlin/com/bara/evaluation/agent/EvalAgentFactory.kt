package com.bara.evaluation.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import com.bara.evaluation.datasets.DEFAULT_EVENTS
import com.bara.evaluation.datasets.DEFAULT_TASKS
import com.bara.evaluation.mocks.MockContactResolver
import com.bara.evaluation.mocks.MockStateStore

const val SYSTEM_PROMPT = """
너는 "바라"라는 이름의 한국어 음성 비서야.
사용자의 음성 명령을 이해하고 적절한 도구를 호출해.
응답은 짧고 자연스러운 한국어로 해.

전화를 걸거나 문자를 보내라는 요청이 오면:
1. 먼저 search_contacts로 연락처를 검색해
2. 검색 결과가 1개면 바로 make_call 또는 send_sms를 호출해 (시스템이 사용자 확인을 처리함)
3. 검색 결과가 여러 개면 사용자에게 누구인지 물어봐
4. 검색 결과가 없으면 연락처를 찾을 수 없다고 말해

중요 규칙:
- make_call과 send_sms 호출 시 반드시 전화번호를 사용해
- 도구가 "사용자가 취소했습니다"를 반환하면 "알겠어요, 취소할게요"라고 답해
- 도구가 성공을 반환하면 완료되었다고 알려줘

현재 시각: 2026-03-17 14:00 (화요일)
타임존: Asia/Seoul (KST, +09:00)

일정 관련 요청이 오면 캘린더 도구(list_events, create_event, update_event, delete_event)를 사용해.
할일 관련 요청이 오면 할일 도구(list_tasks, create_task, complete_task, delete_task)를 사용해.

규칙:
- 일정 수정/삭제 전에 list_events로 eventId를 먼저 확인해
- 할일 완료/삭제 전에 list_tasks로 taskId를 먼저 확인해
- 날짜는 ISO 8601 형식으로 변환해 (예: 2026-03-18T15:00:00+09:00)
- "내일", "다음 주 월요일" 같은 상대 날짜는 현재 시각 기준으로 계산해

카카오톡 메시지를 보내라는 요청이 오면 send_kakao를 사용해.
알림 관련 요청이 오면 list_notifications를 사용해.
"""

class EvalAgentFactory(private val config: AgentConfig) {
    private val executor = simpleGoogleAIExecutor(config.apiKey)
    private val model = LLModel(
        provider = LLMProvider.Google,
        id = config.modelId,
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
        ),
    )

    fun create(resolver: MockContactResolver, stateStore: MockStateStore): AIAgent<String, String> {
        stateStore.loadEvents(DEFAULT_EVENTS.toList())
        stateStore.loadTasks(DEFAULT_TASKS.toList())

        val toolRegistry = buildToolRegistry(resolver, stateStore)
        return AIAgent(
            promptExecutor = executor,
            systemPrompt = SYSTEM_PROMPT.trimIndent(),
            llmModel = model,
            toolRegistry = toolRegistry,
            maxIterations = config.maxIterations,
        )
    }

    fun createWithHistory(
        resolver: MockContactResolver,
        stateStore: MockStateStore,
        conversationHistory: String,
    ): AIAgent<String, String> {
        stateStore.loadEvents(DEFAULT_EVENTS.toList())
        stateStore.loadTasks(DEFAULT_TASKS.toList())

        val toolRegistry = buildToolRegistry(resolver, stateStore)
        val promptWithHistory = if (conversationHistory.isBlank()) {
            SYSTEM_PROMPT.trimIndent()
        } else {
            "${SYSTEM_PROMPT.trimIndent()}\n\n이전 대화:\n$conversationHistory"
        }
        return AIAgent(
            promptExecutor = executor,
            systemPrompt = promptWithHistory,
            llmModel = model,
            toolRegistry = toolRegistry,
            maxIterations = config.maxIterations,
        )
    }

    private fun buildToolRegistry(resolver: MockContactResolver, stateStore: MockStateStore) = ToolRegistry {
        // 연락처/전화/SMS
        tool(createSearchContactsTool(resolver, stateStore))
        tool(createMakeCallTool(stateStore))
        tool(createSendSmsTool(stateStore))
        // 캘린더
        tool(createListEventsTool(stateStore))
        tool(createCreateEventTool(stateStore))
        tool(createUpdateEventTool(stateStore))
        tool(createDeleteEventTool(stateStore))
        // 할일
        tool(createListTasksTool(stateStore))
        tool(createCreateTaskTool(stateStore))
        tool(createCompleteTaskTool(stateStore))
        tool(createDeleteTaskTool(stateStore))
        // 알림/카카오톡
        tool(createListNotificationsTool(stateStore))
        tool(createSendKakaoTool(stateStore))
    }
}
