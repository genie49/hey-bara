package com.bara.evaluation.core

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 판정관 — LLM을 사용하여 롤플레이 목표 달성 여부를 평가
 *
 * 대화 기록과 목표를 기반으로 대화를 계속할지, 목표가 달성되었는지,
 * 더 이상 진행할 수 없는지를 판정한다.
 */
@Serializable
data class JudgeVerdict(
    val verdict: String,  // "continue" | "goal_achieved" | "cannot_continue"
    val reasoning: String,
    val goalProgress: String = "",
)

class Judge(apiKey: String, private val model: String = "gemini-3.1-pro-preview") {

    private val client: Client by lazy {
        Client.builder().apiKey(apiKey).build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 목표 달성 여부 평가
     *
     * @param goal 달성해야 할 목표 설명
     * @param conversationHistory 지금까지의 대화 기록
     * @return 판정 결과 (verdict, reasoning, goalProgress)
     */
    suspend fun evaluate(goal: String, conversationHistory: List<Message>): JudgeVerdict {
        val prompt = buildPrompt(goal, conversationHistory)

        val config = GenerateContentConfig.builder()
            .temperature(0f)
            .responseMimeType("application/json")
            .build()

        val response = client.models.generateContent(model, prompt, config)
        val text = response.text()?.trim()
            ?: error("Judge: LLM returned empty response")

        return json.decodeFromString<JudgeVerdict>(text)
    }

    private fun buildPrompt(goal: String, conversationHistory: List<Message>): String = buildString {
        appendLine("당신은 AI 음성 비서 롤플레이 테스트의 판정관입니다.")
        appendLine("대화 기록을 분석하여 목표 달성 여부를 판정하세요.")
        appendLine()
        appendLine("## 목표")
        appendLine(goal)
        appendLine()
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
        appendLine("## 판정 기준")
        appendLine("- goal_achieved: 목표가 완전히 달성됨")
        appendLine("- continue: 아직 진행 중이며 더 대화가 필요함")
        appendLine("- cannot_continue: 대화가 막혀서 더 이상 진행할 수 없음")
        appendLine()
        appendLine("## 응답 형식")
        appendLine("반드시 아래 JSON 형식으로만 응답하세요:")
        appendLine("""{"verdict": "continue|goal_achieved|cannot_continue", "reasoning": "판정 근거를 한국어로 작성", "goalProgress": "현재까지의 목표 달성 상황"}""")
    }
}
