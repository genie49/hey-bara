package com.bara.evaluation.core

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig

/**
 * 롤플레이어 — LLM을 사용하여 사용자 페르소나를 시뮬레이션
 *
 * 주어진 페르소나와 대화 히스토리를 기반으로 사용자 발화를 생성한다.
 */
class Roleplayer(apiKey: String, private val model: String = "gemini-3.1-pro-preview") {

    private val client: Client by lazy {
        Client.builder().apiKey(apiKey).build()
    }

    /**
     * 페르소나 기반 사용자 메시지 생성
     *
     * @param persona 시뮬레이션할 사용자 페르소나 설명
     * @param conversationHistory 지금까지의 대화 기록
     * @return 생성된 사용자 메시지
     */
    suspend fun generateMessage(persona: String, conversationHistory: List<Message>): String {
        val prompt = buildPrompt(persona, conversationHistory)

        val config = GenerateContentConfig.builder()
            .temperature(0.7f)
            .build()

        val response = client.models.generateContent(model, prompt, config)
        return response.text()?.trim()
            ?: error("Roleplayer: LLM returned empty response")
    }

    private fun buildPrompt(persona: String, conversationHistory: List<Message>): String = buildString {
        appendLine("당신은 AI 음성 비서를 테스트하기 위해 사용자 역할을 수행합니다.")
        appendLine("아래 페르소나에 맞게 자연스러운 한국어로 사용자 발화를 생성하세요.")
        appendLine()
        appendLine("## 페르소나")
        appendLine(persona)
        appendLine()
        if (conversationHistory.isNotEmpty()) {
            appendLine("## 대화 기록")
            conversationHistory.forEach { msg ->
                val role = when (msg.role) {
                    "user" -> "사용자"
                    "assistant" -> "비서"
                    else -> msg.role
                }
                appendLine("[$role] ${msg.content}")
            }
            appendLine()
        }
        appendLine("## 지시사항")
        appendLine("- 위 대화 기록의 흐름에 맞는 다음 사용자 발화를 생성하세요.")
        appendLine("- 페르소나에 맞는 자연스러운 한국어로 작성하세요.")
        appendLine("- 사용자 발화만 출력하세요. 역할 태그나 설명은 포함하지 마세요.")
    }
}
