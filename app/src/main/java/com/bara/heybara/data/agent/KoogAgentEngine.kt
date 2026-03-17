package com.bara.heybara.data.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import com.bara.heybara.data.calendar.GoogleCalendarClient
import com.bara.heybara.data.notification.BaraNotificationListener
import com.bara.heybara.data.tasks.GoogleTasksClient
import com.bara.heybara.domain.action.ActionConfirmation
import com.bara.heybara.domain.action.ActionType
import com.bara.heybara.domain.action.ContactResolver
import com.bara.heybara.domain.agent.AgentEngine
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
2. 검색 결과가 1개면 바로 make_call 또는 send_sms를 호출해 (시스템이 사용자 확인을 처리함)
3. 검색 결과가 여러 개면 사용자에게 누구인지 물어봐
4. 검색 결과가 없으면 연락처를 찾을 수 없다고 말해

중요 규칙:
- make_call과 send_sms 호출 시 반드시 전화번호를 사용해
- 도구가 "사용자가 취소했습니다"를 반환하면 "알겠어요, 취소할게요"라고 답해
- 도구가 성공을 반환하면 완료되었다고 알려줘
"""
    }

    init {
        SearchContactsTool.resolver = contactResolver
    }

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    // ── 연락처/전화/SMS Tool (기존) ──

    object SearchContactsTool : SimpleTool<SearchContactsTool.Args>(
        argsSerializer = Args.serializer(),
        name = "search_contacts",
        description = "연락처에서 이름으로 검색한다. 전화나 문자를 보내기 전에 반드시 먼저 호출해야 한다."
    ) {
        var resolver: ContactResolver? = null
        @Serializable
        data class Args(@property:LLMDescription("검색할 이름 또는 별명") val query: String)
        override suspend fun execute(args: Args): String {
            val contacts = resolver?.searchContacts(args.query) ?: emptyList()
            return if (contacts.isEmpty()) "연락처에서 '${args.query}'을(를) 찾을 수 없습니다."
            else contacts.joinToString("\n") { "${it.name}: ${it.phoneNumber}" }
        }
    }

    object MakeCallTool : SimpleTool<MakeCallTool.Args>(
        argsSerializer = Args.serializer(),
        name = "make_call",
        description = "전화번호로 전화를 건다. search_contacts로 번호를 먼저 확인한 후 호출해야 한다."
    ) {
        var appContext: Context? = null
        @Serializable
        data class Args(
            @property:LLMDescription("전화할 사람 이름") val contact: String,
            @property:LLMDescription("전화번호 (예: 010-1234-5678)") val phoneNumber: String
        )
        override suspend fun execute(args: Args): String {
            val confirmed = ActionConfirmation.requestConfirmation("${args.contact}님에게 전화를 겁니다", ActionType.CALL)
            if (!confirmed) return "사용자가 취소했습니다."
            return try {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:${args.phoneNumber}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext?.startActivity(intent)
                "${args.contact}(${args.phoneNumber})에게 전화를 걸었습니다."
            } catch (e: Exception) { "전화 걸기에 실패했습니다: ${e.message}" }
        }
    }

    object SendSmsTool : SimpleTool<SendSmsTool.Args>(
        argsSerializer = Args.serializer(),
        name = "send_sms",
        description = "문자 메시지를 보낸다. search_contacts로 번호를 먼저 확인한 후 호출해야 한다."
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("받는 사람 이름") val contact: String,
            @property:LLMDescription("전화번호 (예: 010-1234-5678)") val phoneNumber: String,
            @property:LLMDescription("보낼 메시지 내용") val message: String
        )
        override suspend fun execute(args: Args): String {
            val confirmed = ActionConfirmation.requestConfirmation("${args.contact}님에게 '${args.message}'라고 문자를 보냅니다", ActionType.SMS)
            if (!confirmed) return "사용자가 취소했습니다."
            return try {
                @Suppress("DEPRECATION")
                SmsManager.getDefault().sendTextMessage(args.phoneNumber, null, args.message, null, null)
                "${args.contact}(${args.phoneNumber})에게 '${args.message}'라고 문자를 보냈습니다."
            } catch (e: Exception) { "문자 보내기에 실패했습니다: ${e.message}" }
        }
    }

    // ── 캘린더 Tool ──

    object ListEventsTool : SimpleTool<ListEventsTool.Args>(
        argsSerializer = Args.serializer(),
        name = "list_events",
        description = "캘린더에서 일정을 조회한다."
    ) {
        var calendarClient: GoogleCalendarClient? = null
        @Serializable
        data class Args(
            @property:LLMDescription("조회 시작 날짜 (YYYY-MM-DD)") val date: String,
            @property:LLMDescription("조회 범위 일수 (기본 1)") val days: Int = 1
        )
        override suspend fun execute(args: Args): String {
            val client = calendarClient ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."
            return client.listEvents(args.date, args.days)
        }
    }

    object CreateEventTool : SimpleTool<CreateEventTool.Args>(
        argsSerializer = Args.serializer(),
        name = "create_event",
        description = "캘린더에 새 일정을 추가한다."
    ) {
        var calendarClient: GoogleCalendarClient? = null
        @Serializable
        data class Args(
            @property:LLMDescription("일정 제목") val title: String,
            @property:LLMDescription("시작 시간 (ISO 8601, 예: 2026-03-18T15:00:00+09:00)") val startDateTime: String,
            @property:LLMDescription("종료 시간 (ISO 8601, 생략하면 시작+1시간)") val endDateTime: String = "",
            @property:LLMDescription("설명 (선택)") val description: String = ""
        )
        override suspend fun execute(args: Args): String {
            val client = calendarClient ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."
            return client.createEvent(args.title, args.startDateTime, args.endDateTime.ifBlank { null }, args.description.ifBlank { null })
        }
    }

    object UpdateEventTool : SimpleTool<UpdateEventTool.Args>(
        argsSerializer = Args.serializer(),
        name = "update_event",
        description = "캘린더 일정을 수정한다. list_events로 eventId를 먼저 확인해야 한다."
    ) {
        var calendarClient: GoogleCalendarClient? = null
        @Serializable
        data class Args(
            @property:LLMDescription("일정 ID") val eventId: String,
            @property:LLMDescription("변경할 제목 (선택)") val title: String = "",
            @property:LLMDescription("변경할 시작 시간 (ISO 8601, 선택)") val startDateTime: String = "",
            @property:LLMDescription("변경할 종료 시간 (ISO 8601, 선택)") val endDateTime: String = ""
        )
        override suspend fun execute(args: Args): String {
            val client = calendarClient ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."
            return client.updateEvent(args.eventId, args.title.ifBlank { null }, args.startDateTime.ifBlank { null }, args.endDateTime.ifBlank { null })
        }
    }

    object DeleteEventTool : SimpleTool<DeleteEventTool.Args>(
        argsSerializer = Args.serializer(),
        name = "delete_event",
        description = "캘린더 일정을 삭제한다. list_events로 eventId를 먼저 확인해야 한다."
    ) {
        var calendarClient: GoogleCalendarClient? = null
        @Serializable
        data class Args(@property:LLMDescription("삭제할 일정 ID") val eventId: String)
        override suspend fun execute(args: Args): String {
            val client = calendarClient ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."
            return client.deleteEvent(args.eventId)
        }
    }

    // ── 할일 Tool ──

    object ListTasksTool : SimpleTool<ListTasksTool.Args>(
        argsSerializer = Args.serializer(),
        name = "list_tasks",
        description = "할일 목록을 조회한다."
    ) {
        var tasksClient: GoogleTasksClient? = null
        @Serializable
        data class Args(
            @property:LLMDescription("완료된 할일도 포함할지 여부 (기본 false)") val showCompleted: Boolean = false
        )
        override suspend fun execute(args: Args): String {
            val client = tasksClient ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."
            return client.listTasks(args.showCompleted)
        }
    }

    object CreateTaskTool : SimpleTool<CreateTaskTool.Args>(
        argsSerializer = Args.serializer(),
        name = "create_task",
        description = "새 할일을 추가한다."
    ) {
        var tasksClient: GoogleTasksClient? = null
        @Serializable
        data class Args(
            @property:LLMDescription("할일 제목") val title: String,
            @property:LLMDescription("기한 (YYYY-MM-DD, 선택)") val dueDate: String = "",
            @property:LLMDescription("메모 (선택)") val notes: String = ""
        )
        override suspend fun execute(args: Args): String {
            val client = tasksClient ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."
            return client.createTask(args.title, args.dueDate.ifBlank { null }, args.notes.ifBlank { null })
        }
    }

    object CompleteTaskTool : SimpleTool<CompleteTaskTool.Args>(
        argsSerializer = Args.serializer(),
        name = "complete_task",
        description = "할일을 완료 처리한다. list_tasks로 taskId를 먼저 확인해야 한다."
    ) {
        var tasksClient: GoogleTasksClient? = null
        @Serializable
        data class Args(@property:LLMDescription("완료할 할일 ID") val taskId: String)
        override suspend fun execute(args: Args): String {
            val client = tasksClient ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."
            return client.completeTask(args.taskId)
        }
    }

    object DeleteTaskTool : SimpleTool<DeleteTaskTool.Args>(
        argsSerializer = Args.serializer(),
        name = "delete_task",
        description = "할일을 삭제한다. list_tasks로 taskId를 먼저 확인해야 한다."
    ) {
        var tasksClient: GoogleTasksClient? = null
        @Serializable
        data class Args(@property:LLMDescription("삭제할 할일 ID") val taskId: String)
        override suspend fun execute(args: Args): String {
            val client = tasksClient ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."
            return client.deleteTask(args.taskId)
        }
    }

    // ── 알림 Tool ──

    object ListNotificationsTool : SimpleTool<ListNotificationsTool.Args>(
        argsSerializer = Args.serializer(),
        name = "list_notifications",
        description = "현재 알림 목록을 조회한다."
    ) {
        var appContext: Context? = null
        @Serializable
        class Args
        override suspend fun execute(args: Args): String {
            val ctx = appContext ?: return "알림 서비스가 연결되지 않았습니다."
            if (!BaraNotificationListener.isEnabled(ctx)) {
                return "알림 접근 권한이 필요합니다. 설정에서 Hey Bara의 알림 접근을 허용해 주세요."
            }
            val notifications = BaraNotificationListener.getActiveNotificationList(ctx)
                ?: return "알림 서비스가 연결되지 않았습니다."
            if (notifications.isEmpty()) return "알림이 없습니다."
            return notifications.mapIndexed { i, n ->
                "${i + 1}. ${n.appName}: ${n.title} — ${n.content}"
            }.joinToString("\n")
        }
    }

    // ── Engine 설정 ──

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
        tool(ListEventsTool)
        tool(CreateEventTool)
        tool(UpdateEventTool)
        tool(DeleteEventTool)
        tool(ListTasksTool)
        tool(CreateTaskTool)
        tool(CompleteTaskTool)
        tool(DeleteTaskTool)
        tool(ListNotificationsTool)
    }

    private fun buildSystemPrompt(): String {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm (E)", Locale.KOREAN).format(Date())
        val base = """
${BASE_SYSTEM_PROMPT.trimIndent()}

현재 시각: $now
타임존: Asia/Seoul (KST, +09:00)

일정 관련 요청이 오면 캘린더 도구(list_events, create_event, update_event, delete_event)를 사용해.
할일 관련 요청이 오면 할일 도구(list_tasks, create_task, complete_task, delete_task)를 사용해.

규칙:
- 일정 수정/삭제 전에 list_events로 eventId를 먼저 확인해
- 할일 완료/삭제 전에 list_tasks로 taskId를 먼저 확인해
- 날짜는 ISO 8601 형식으로 변환해 (예: 2026-03-18T15:00:00+09:00)
- "내일", "다음 주 월요일" 같은 상대 날짜는 현재 시각 기준으로 계산해

알림 관련 요청이 오면 list_notifications를 사용해.
""".trimIndent()

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
        maxIterations = 15
    )

    override suspend fun process(text: String): String {
        Log.d(TAG, "Processing: $text")
        return try {
            val result = createAgent().run(text)
            Log.d(TAG, "Result: $result")
            conversationHistory.add(text to result)
            if (conversationHistory.size > 10) conversationHistory.removeAt(0)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Agent error", e)
            throw e
        }
    }

    fun setContext(context: Context) {
        MakeCallTool.appContext = context.applicationContext
        ListNotificationsTool.appContext = context.applicationContext
    }

    fun setGoogleClients(calendarClient: GoogleCalendarClient?, tasksClient: GoogleTasksClient?) {
        ListEventsTool.calendarClient = calendarClient
        CreateEventTool.calendarClient = calendarClient
        UpdateEventTool.calendarClient = calendarClient
        DeleteEventTool.calendarClient = calendarClient
        ListTasksTool.tasksClient = tasksClient
        CreateTaskTool.tasksClient = tasksClient
        CompleteTaskTool.tasksClient = tasksClient
        DeleteTaskTool.tasksClient = tasksClient
    }

    fun getConversationTranscript(): String {
        return conversationHistory.joinToString("\n") { (user, assistant) ->
            "사용자: $user\n바라: $assistant"
        }
    }

    override fun release() {
        conversationHistory.clear()
    }
}
