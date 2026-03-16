package com.bara.heybara.data.agent

import android.util.Log
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import com.bara.heybara.domain.agent.AgentAction
import com.bara.heybara.domain.agent.AgentEngine
import com.bara.heybara.domain.agent.AgentResponse
import kotlinx.serialization.Serializable

class KoogAgentEngine(
    private val apiKey: String
) : AgentEngine {

    companion object {
        private const val TAG = "KoogAgentEngine"
        private const val MODEL_ID = "gemini-3.1-flash-lite-preview"
        private const val BASE_SYSTEM_PROMPT = """
너는 "바라"라는 이름의 한국어 음성 비서야.
사용자의 음성 명령을 이해하고 적절한 도구를 호출해.
응답은 짧고 자연스러운 한국어로 해.
"""
    }

    // 대화 기록 직접 관리
    private val conversationHistory = mutableListOf<Pair<String, String>>() // (user, assistant)

    // 전화 걸기 Tool 정의
    object MakeCallTool : SimpleTool<MakeCallTool.Args>(
        argsSerializer = Args.serializer(),
        name = "make_call",
        description = "연락처에게 전화를 건다"
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("전화할 상대 이름")
            val contact: String
        )

        override suspend fun execute(args: Args): String {
            Log.d("AgentTool", "Call requested: contact=${args.contact}")
            return "${args.contact}한테 전화를 걸게요"
        }
    }

    private val executor = simpleGoogleAIExecutor(apiKey)
    private val model = LLModel(
        provider = LLMProvider.Google,
        id = MODEL_ID,
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Temperature,
        ),
    )
    private val tools = ToolRegistry { tool(MakeCallTool) }

    // 대화 기록을 포함한 시스템 프롬프트 생성
    private fun buildSystemPrompt(): String {
        val base = BASE_SYSTEM_PROMPT.trimIndent()
        if (conversationHistory.isEmpty()) return base

        val history = conversationHistory.joinToString("\n") { (user, assistant) ->
            "사용자: $user\n바라: $assistant"
        }
        return "$base\n\n이전 대화:\n$history"
    }

    // 매 요청마다 새 agent 생성 (대화 기록은 프롬프트에 포함)
    private fun createAgent() = AIAgent(
        promptExecutor = executor,
        systemPrompt = buildSystemPrompt(),
        llmModel = model,
        toolRegistry = tools,
        maxIterations = 5
    )

    override suspend fun process(text: String): AgentResponse {
        Log.d(TAG, "Processing: $text")
        return try {
            val result = createAgent().run(text)
            Log.d(TAG, "Result: $result")

            // 대화 기록에 추가
            conversationHistory.add(text to result)
            // 최근 10턴만 유지
            if (conversationHistory.size > 10) {
                conversationHistory.removeAt(0)
            }

            // Tool이 호출됐는지 판단
            if (result.contains("전화를 걸")) {
                val contact = extractContact(text)
                AgentResponse(
                    text = "${contact}한테 전화를 걸까요?",
                    action = AgentAction.Call(contact),
                    requiresConfirmation = true
                )
            } else {
                AgentResponse(
                    text = result,
                    action = null,
                    requiresConfirmation = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Agent error", e)
            throw e
        }
    }

    private fun extractContact(text: String): String {
        val pattern = "(.+?)한테|(.+?)에게".toRegex()
        val match = pattern.find(text)
        return match?.groupValues?.firstOrNull { it.isNotEmpty() && it != match.value } ?: "상대방"
    }

    override fun release() {
        conversationHistory.clear()
    }
}
