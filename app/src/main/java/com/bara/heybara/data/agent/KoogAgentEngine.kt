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
import com.bara.heybara.domain.action.ContactResolver
import com.bara.heybara.domain.agent.AgentAction
import com.bara.heybara.domain.agent.AgentEngine
import com.bara.heybara.domain.agent.AgentResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class KoogAgentEngine(
    private val apiKey: String,
    private val contactResolver: ContactResolver? = null
) : AgentEngine {

    companion object {
        private const val TAG = "KoogAgentEngine"
        private const val MODEL_ID = "gemini-3.1-flash-lite-preview"
        private const val BASE_SYSTEM_PROMPT = """
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

중요: 전화나 문자를 실행하기 전에 반드시 사용자 확인을 받아야 해. 확인 없이 바로 실행하지 마.
make_call과 send_sms 호출 시 반드시 전화번호를 사용해.
"""
    }

    init {
        SearchContactsTool.resolver = contactResolver
    }

    // 대화 기록 직접 관리
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    // 연락처 검색 Tool (contactResolver를 companion의 static 참조로 전달)
    object SearchContactsTool : SimpleTool<SearchContactsTool.Args>(
        argsSerializer = Args.serializer(),
        name = "search_contacts",
        description = "연락처에서 이름으로 검색한다. 전화나 문자를 보내기 전에 반드시 먼저 호출해야 한다."
    ) {
        // 외부에서 주입
        var resolver: ContactResolver? = null

        @Serializable
        data class Args(
            @property:LLMDescription("검색할 이름 또는 별명")
            val query: String
        )

        override suspend fun execute(args: Args): String {
            val contacts = resolver?.searchContacts(args.query) ?: emptyList()
            Log.d("AgentTool", "연락처 검색: query=${args.query}, 결과=${contacts.size}건")
            return if (contacts.isEmpty()) {
                "연락처에서 '${args.query}'을(를) 찾을 수 없습니다."
            } else {
                contacts.joinToString("\n") { "${it.name}: ${it.phoneNumber}" }
            }
        }
    }

    // 전화 걸기 Tool
    object MakeCallTool : SimpleTool<MakeCallTool.Args>(
        argsSerializer = Args.serializer(),
        name = "make_call",
        description = "전화번호로 전화를 건다. search_contacts로 번호를 먼저 확인한 후 호출해야 한다."
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("전화할 사람 이름")
            val contact: String,
            @property:LLMDescription("전화번호 (예: 010-1234-5678)")
            val phoneNumber: String
        )

        override suspend fun execute(args: Args): String {
            Log.d("AgentTool", "전화 걸기: contact=${args.contact}, number=${args.phoneNumber}")
            return "${args.contact}(${args.phoneNumber})한테 전화를 겁니다."
        }
    }

    // SMS 보내기 Tool
    object SendSmsTool : SimpleTool<SendSmsTool.Args>(
        argsSerializer = Args.serializer(),
        name = "send_sms",
        description = "문자 메시지를 보낸다. search_contacts로 번호를 먼저 확인한 후 호출해야 한다."
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("받는 사람 이름")
            val contact: String,
            @property:LLMDescription("전화번호 (예: 010-1234-5678)")
            val phoneNumber: String,
            @property:LLMDescription("보낼 메시지 내용")
            val message: String
        )

        override suspend fun execute(args: Args): String {
            Log.d("AgentTool", "SMS 전송: contact=${args.contact}, number=${args.phoneNumber}, message=${args.message}")
            return "${args.contact}(${args.phoneNumber})한테 '${args.message}'라고 문자를 보냅니다."
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

    private fun buildTools() = ToolRegistry {
        tool(SearchContactsTool)
        tool(MakeCallTool)
        tool(SendSmsTool)
    }

    private fun buildSystemPrompt(): String {
        val base = BASE_SYSTEM_PROMPT.trimIndent()
        if (conversationHistory.isEmpty()) return base
        val history = conversationHistory.joinToString("\n") { (user, assistant) ->
            "사용자: $user\n바라: $assistant"
        }
        return "$base\n\n이전 대화:\n$history"
    }

    private fun createAgent() = AIAgent(
        promptExecutor = executor,
        systemPrompt = buildSystemPrompt(),
        llmModel = model,
        toolRegistry = buildTools(),
        maxIterations = 10
    )

    override suspend fun process(text: String): AgentResponse {
        Log.d(TAG, "Processing: $text")
        return try {
            val result = createAgent().run(text)
            Log.d(TAG, "Result: $result")

            conversationHistory.add(text to result)
            if (conversationHistory.size > 10) {
                conversationHistory.removeAt(0)
            }

            // Tool 호출 결과에서 AgentAction 파싱
            parseResponse(text, result)
        } catch (e: Exception) {
            Log.e(TAG, "Agent error", e)
            throw e
        }
    }

    private fun parseResponse(input: String, result: String): AgentResponse {
        // make_call이 호출된 경우
        val callPattern = "(.+?)\\((.+?)\\)한테 전화를 겁니다".toRegex()
        callPattern.find(result)?.let { match ->
            val contact = match.groupValues[1]
            val number = match.groupValues[2]
            return AgentResponse(
                text = "${contact}한테 전화를 걸까요?",
                action = AgentAction.Call(contact, number),
                requiresConfirmation = true
            )
        }

        // send_sms가 호출된 경우
        val smsPattern = "(.+?)\\((.+?)\\)한테 '(.+?)'라고 문자를 보냅니다".toRegex()
        smsPattern.find(result)?.let { match ->
            val contact = match.groupValues[1]
            val number = match.groupValues[2]
            val message = match.groupValues[3]
            return AgentResponse(
                text = "${contact}한테 '${message}'라고 문자를 보낼까요?",
                action = AgentAction.SendSms(contact, number, message),
                requiresConfirmation = true
            )
        }

        // Tool 호출 없는 일반 응답
        return AgentResponse(
            text = result,
            action = null,
            requiresConfirmation = false
        )
    }

    // 대화 기록을 JSON으로 반환 (히스토리 저장용)
    fun getConversationTranscript(): String {
        return conversationHistory.joinToString("\n") { (user, assistant) ->
            "사용자: $user\n바라: $assistant"
        }
    }

    override fun release() {
        conversationHistory.clear()
    }
}
