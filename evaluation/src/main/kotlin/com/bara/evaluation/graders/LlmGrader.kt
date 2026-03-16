package com.bara.evaluation.graders

import com.bara.evaluation.core.retryWithBackoff
import com.bara.evaluation.core.*
import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * LLM 기반 Grader — Gemini API를 사용하여 응답 품질을 평가
 *
 * rubric 마크다운 파일을 로드하고, 프롬프트를 구성하여 LLM에 평가를 요청한다.
 * LLM은 JSON 형태로 score(0.0~1.0)와 reasoning을 반환한다.
 */
class LlmGrader(
    private val apiKey: String,
) {
    @Serializable
    data class LlmGraderOutput(
        val score: Double,
        val reasoning: String,
    )

    private val client: Client by lazy {
        Client.builder().apiKey(apiKey).build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 단일 턴 평가
     *
     * @param expected LLM 채점 기준 (rubric 파일 경로, 최소 점수)
     * @param input 사용자 입력
     * @param output 에이전트 출력
     * @param toolCalls 에이전트가 호출한 tool 목록
     */
    suspend fun grade(
        expected: LlmGraderExpected,
        input: String,
        output: String,
        toolCalls: List<ToolCall>,
    ): GraderResult {
        val rubricContent = loadRubric(expected.rubric)
        val rubricMeta = parseRubricMeta(rubricContent)
        val model = rubricMeta["model"] ?: DEFAULT_MODEL
        val temperature = rubricMeta["temperature"]?.toFloatOrNull() ?: 0f

        val prompt = buildSingleTurnPrompt(rubricContent, input, output, toolCalls)
        val llmOutput = callLlm(model, temperature, prompt)

        return GraderResult(
            grader = GRADER_NAME,
            passed = llmOutput.score >= expected.minScore,
            score = llmOutput.score,
            details = mapOf("reasoning" to llmOutput.reasoning),
        )
    }

    /**
     * 멀티턴 대화 평가
     *
     * @param rubricFile rubric 파일 경로 (classpath 기준)
     * @param minScore 통과 최소 점수
     * @param messages 대화 전체 메시지 목록
     */
    suspend fun gradeConversation(
        rubricFile: String,
        minScore: Double,
        messages: List<Message>,
    ): GraderResult {
        val rubricContent = loadRubric(rubricFile)
        val rubricMeta = parseRubricMeta(rubricContent)
        val model = rubricMeta["model"] ?: DEFAULT_MODEL
        val temperature = rubricMeta["temperature"]?.toFloatOrNull() ?: 0f

        val prompt = buildConversationPrompt(rubricContent, messages)
        val llmOutput = callLlm(model, temperature, prompt)

        return GraderResult(
            grader = GRADER_NAME,
            passed = llmOutput.score >= minScore,
            score = llmOutput.score,
            details = mapOf("reasoning" to llmOutput.reasoning),
        )
    }

    /** classpath에서 rubric 마크다운 파일 로드 */
    private fun loadRubric(path: String): String {
        val resourcePath = if (path.startsWith("/")) path else "/rubrics/$path"
        return this::class.java.getResourceAsStream(resourcePath)?.bufferedReader()?.readText()
            ?: error("Rubric file not found: $resourcePath")
    }

    /** rubric YAML front matter에서 메타데이터 추출 */
    private fun parseRubricMeta(rubricContent: String): Map<String, String> {
        if (!rubricContent.startsWith("---")) return emptyMap()
        val endIndex = rubricContent.indexOf("---", 3)
        if (endIndex == -1) return emptyMap()
        val frontMatter = rubricContent.substring(3, endIndex).trim()
        return frontMatter.lines().mapNotNull { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                key to value
            } else null
        }.toMap()
    }

    /** 단일 턴 평가 프롬프트 구성 */
    private fun buildSingleTurnPrompt(
        rubric: String,
        input: String,
        output: String,
        toolCalls: List<ToolCall>,
    ): String = buildString {
        appendLine("당신은 AI 음성 비서의 응답 품질을 평가하는 평가자입니다.")
        appendLine()
        appendLine("## 평가 기준")
        appendLine(rubric)
        appendLine()
        appendLine("## 사용자 입력")
        appendLine(input)
        appendLine()
        appendLine("## 에이전트 응답")
        appendLine(output)
        appendLine()
        if (toolCalls.isNotEmpty()) {
            appendLine("## Tool 호출 기록")
            toolCalls.forEach { call ->
                appendLine("- ${call.tool}(${call.params.entries.joinToString(", ") { "${it.key}=${it.value}" }})")
            }
            appendLine()
        }
        appendLine("## 응답 형식")
        appendLine("반드시 아래 JSON 형식으로만 응답하세요:")
        appendLine("""{"score": 0.0~1.0, "reasoning": "평가 근거를 한국어로 작성"}""")
    }

    /** 멀티턴 대화 평가 프롬프트 구성 */
    private fun buildConversationPrompt(
        rubric: String,
        messages: List<Message>,
    ): String = buildString {
        appendLine("당신은 AI 음성 비서의 대화 품질을 평가하는 평가자입니다.")
        appendLine()
        appendLine("## 평가 기준")
        appendLine(rubric)
        appendLine()
        appendLine("## 대화 기록")
        messages.forEach { msg ->
            val role = when (msg.role) {
                "user" -> "사용자"
                "assistant" -> "비서"
                else -> msg.role
            }
            appendLine("[$role] ${msg.content}")
        }
        appendLine()
        appendLine("## 응답 형식")
        appendLine("반드시 아래 JSON 형식으로만 응답하세요:")
        appendLine("""{"score": 0.0~1.0, "reasoning": "평가 근거를 한국어로 작성"}""")
    }

    /** Gemini API 호출 및 결과 파싱 (retry 포함) */
    private suspend fun callLlm(model: String, temperature: Float, prompt: String): LlmGraderOutput {
        return retryWithBackoff(maxRetries = 3) {
            val config = GenerateContentConfig.builder()
                .temperature(temperature)
                .responseMimeType("application/json")
                .build()

            val response = client.models.generateContent(model, prompt, config)
            val text = response.text()?.trim()
                ?: error("LLM returned empty response")

            json.decodeFromString<LlmGraderOutput>(text)
        }
    }

    companion object {
        private const val GRADER_NAME = "LlmGrader"
        private const val DEFAULT_MODEL = "gemini-3.1-pro-preview"
    }
}
