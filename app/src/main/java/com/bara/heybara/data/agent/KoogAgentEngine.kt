package com.bara.heybara.data.agent

import android.util.Log
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
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
        private const val SYSTEM_PROMPT = """
너는 "바라"라는 이름의 한국어 음성 비서야.
사용자의 음성 명령을 이해하고 적절한 도구를 호출해.
응답은 짧고 자연스러운 한국어로 해.
"""
    }

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
            // Phase 2: 로그만 출력
            Log.d("AgentTool", "Call requested: contact=${args.contact}")
            return "${args.contact}한테 전화를 걸게요"
        }
    }

    private val agent = AIAgent(
        promptExecutor = simpleGoogleAIExecutor(apiKey),
        systemPrompt = SYSTEM_PROMPT.trimIndent(),
        llmModel = LLModel(provider = LLMProvider.Google, id = MODEL_ID),
        toolRegistry = ToolRegistry {
            tool(MakeCallTool)
        },
        maxIterations = 5
    )

    override suspend fun process(text: String): AgentResponse {
        Log.d(TAG, "Processing: $text")
        return try {
            val result = agent.run(text)
            Log.d(TAG, "Result: $result")

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

    // 텍스트에서 연락처 이름 추출 (간단한 패턴)
    private fun extractContact(text: String): String {
        val pattern = "(.+?)한테|(.+?)에게".toRegex()
        val match = pattern.find(text)
        return match?.groupValues?.firstOrNull { it.isNotEmpty() && it != match.value } ?: "상대방"
    }

    override fun release() {
        // Koog agent 정리
    }
}
