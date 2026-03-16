package com.bara.evaluation.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import com.bara.evaluation.mocks.MockContactResolver
import com.bara.evaluation.mocks.MockStateStore

const val SYSTEM_PROMPT = """
너는 "바라"라는 이름의 한국어 음성 비서야.
사용자의 음성 명령을 이해하고 적절한 도구를 호출해.
응답은 짧고 자연스러운 한국어로 해.

전화를 걸거나 문자를 보내라는 요청이 오면:
1. 먼저 search_contacts로 연락처를 검색해
2. 검색 결과가 1개면 사용자에게 확인을 요청해 (예: "엄마한테 전화를 걸까요?")
3. 사용자가 확인하면 make_call 또는 send_sms를 호출해
4. 사용자가 거부하면 "알겠어요, 취소할게요"라고 답해
5. 검색 결과가 여러 개면 사용자에게 누구인지 물어봐
6. 검색 결과가 없으면 연락처를 찾을 수 없다고 말해

중요 규칙:
- 전화나 문자를 실행하기 전에 반드시 사용자 확인을 받아야 해. 확인 없이 바로 실행하지 마.
- 사용자가 "응", "네", "그래", "해줘" 등으로 확인하면 즉시 해당 도구를 호출해. 같은 확인을 다시 묻지 마.
- 이전 대화에서 이미 확인받은 작업은 바로 실행해.
- make_call과 send_sms 호출 시 반드시 전화번호를 사용해.
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
        val toolRegistry = ToolRegistry {
            tool(createSearchContactsTool(resolver, stateStore))
            tool(createMakeCallTool(stateStore))
            tool(createSendSmsTool(stateStore))
        }
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
        val toolRegistry = ToolRegistry {
            tool(createSearchContactsTool(resolver, stateStore))
            tool(createMakeCallTool(stateStore))
            tool(createSendSmsTool(stateStore))
        }
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
}
