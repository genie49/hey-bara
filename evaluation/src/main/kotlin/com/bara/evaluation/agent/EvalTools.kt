package com.bara.evaluation.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import com.bara.evaluation.mocks.MockContactResolver
import com.bara.evaluation.mocks.MockStateStore
import kotlinx.serialization.Serializable
import java.util.UUID

// ── 연락처/전화/SMS Args ──

@Serializable
data class SearchContactsArgs(
    @property:LLMDescription("검색할 이름 또는 별명") val query: String,
)

@Serializable
data class MakeCallArgs(
    @property:LLMDescription("전화할 사람 이름") val contact: String,
    @property:LLMDescription("전화번호 (예: 010-1234-5678)") val phoneNumber: String,
)

@Serializable
data class SendSmsArgs(
    @property:LLMDescription("받는 사람 이름") val contact: String,
    @property:LLMDescription("전화번호 (예: 010-1234-5678)") val phoneNumber: String,
    @property:LLMDescription("보낼 메시지 내용") val message: String,
)

// ── 캘린더 Args ──

@Serializable
data class ListEventsArgs(
    @property:LLMDescription("조회 시작 날짜 (YYYY-MM-DD)") val date: String,
    @property:LLMDescription("조회 범위 일수 (기본 1)") val days: Int = 1,
)

@Serializable
data class CreateEventArgs(
    @property:LLMDescription("일정 제목") val title: String,
    @property:LLMDescription("시작 시간 (ISO 8601, 예: 2026-03-18T15:00:00+09:00)") val startDateTime: String,
    @property:LLMDescription("종료 시간 (ISO 8601, 생략하면 시작+1시간)") val endDateTime: String = "",
    @property:LLMDescription("설명 (선택)") val description: String = "",
)

@Serializable
data class UpdateEventArgs(
    @property:LLMDescription("일정 ID") val eventId: String,
    @property:LLMDescription("변경할 제목 (선택)") val title: String = "",
    @property:LLMDescription("변경할 시작 시간 (ISO 8601, 선택)") val startDateTime: String = "",
    @property:LLMDescription("변경할 종료 시간 (ISO 8601, 선택)") val endDateTime: String = "",
)

@Serializable
data class DeleteEventArgs(
    @property:LLMDescription("삭제할 일정 ID") val eventId: String,
)

// ── 할일 Args ──

@Serializable
data class ListTasksArgs(
    @property:LLMDescription("완료된 할일도 포함할지 여부 (기본 false)") val showCompleted: Boolean = false,
)

@Serializable
data class CreateTaskArgs(
    @property:LLMDescription("할일 제목") val title: String,
    @property:LLMDescription("기한 (YYYY-MM-DD, 선택)") val dueDate: String = "",
    @property:LLMDescription("메모 (선택)") val notes: String = "",
)

@Serializable
data class CompleteTaskArgs(
    @property:LLMDescription("완료할 할일 ID") val taskId: String,
)

@Serializable
data class DeleteTaskArgs(
    @property:LLMDescription("삭제할 할일 ID") val taskId: String,
)

// ── 연락처/전화/SMS Factory ──

fun createSearchContactsTool(
    resolver: MockContactResolver,
    stateStore: MockStateStore,
): SimpleTool<SearchContactsArgs> = object : SimpleTool<SearchContactsArgs>(
    argsSerializer = SearchContactsArgs.serializer(),
    name = "search_contacts",
    description = "연락처에서 이름으로 검색한다. 전화나 문자를 보내기 전에 반드시 먼저 호출해야 한다.",
) {
    override suspend fun execute(args: SearchContactsArgs): String {
        stateStore.recordSearch(args.query)
        val contacts = resolver.searchContacts(args.query)
        return if (contacts.isEmpty()) {
            "연락처에서 '${args.query}'을(를) 찾을 수 없습니다."
        } else {
            contacts.joinToString("\n") { "${it.name}: ${it.phoneNumber}" }
        }
    }
}

fun createMakeCallTool(
    stateStore: MockStateStore,
): SimpleTool<MakeCallArgs> = object : SimpleTool<MakeCallArgs>(
    argsSerializer = MakeCallArgs.serializer(),
    name = "make_call",
    description = "전화번호로 전화를 건다. search_contacts로 번호를 먼저 확인한 후 호출해야 한다.",
) {
    override suspend fun execute(args: MakeCallArgs): String {
        stateStore.recordCall(args.contact, args.phoneNumber)
        return "${args.contact}(${args.phoneNumber})에게 전화를 걸었습니다."
    }
}

fun createSendSmsTool(
    stateStore: MockStateStore,
): SimpleTool<SendSmsArgs> = object : SimpleTool<SendSmsArgs>(
    argsSerializer = SendSmsArgs.serializer(),
    name = "send_sms",
    description = "문자 메시지를 보낸다. search_contacts로 번호를 먼저 확인한 후 호출해야 한다.",
) {
    override suspend fun execute(args: SendSmsArgs): String {
        stateStore.recordSms(args.contact, args.phoneNumber, args.message)
        return "${args.contact}(${args.phoneNumber})에게 '${args.message}'라고 문자를 보냈습니다."
    }
}

// ── 캘린더 Factory ──

fun createListEventsTool(
    stateStore: MockStateStore,
): SimpleTool<ListEventsArgs> = object : SimpleTool<ListEventsArgs>(
    argsSerializer = ListEventsArgs.serializer(),
    name = "list_events",
    description = "캘린더에서 일정을 조회한다.",
) {
    override suspend fun execute(args: ListEventsArgs): String {
        stateStore.recordListEvents(args.date, args.days)
        val filtered = stateStore.events.filter { event ->
            val eventDate = event.startDateTime.take(10)
            eventDate >= args.date && eventDate < addDays(args.date, args.days)
        }
        if (filtered.isEmpty()) return "조회 기간에 일정이 없습니다."
        return filtered.mapIndexed { i, e ->
            "${i + 1}. ${e.title} (${e.startDateTime} ~ ${e.endDateTime}) [id:${e.id}]"
        }.joinToString("\n")
    }
}

fun createCreateEventTool(
    stateStore: MockStateStore,
): SimpleTool<CreateEventArgs> = object : SimpleTool<CreateEventArgs>(
    argsSerializer = CreateEventArgs.serializer(),
    name = "create_event",
    description = "캘린더에 새 일정을 추가한다.",
) {
    override suspend fun execute(args: CreateEventArgs): String {
        val id = "evt-${UUID.randomUUID().toString().take(8)}"
        val end = args.endDateTime.ifBlank {
            args.startDateTime.replace("T(\\d{2})".toRegex()) {
                "T${(it.groupValues[1].toInt() + 1).toString().padStart(2, '0')}"
            }
        }
        stateStore.recordCreateEvent(id, args.title, args.startDateTime, end, args.description)
        return "'${args.title}' 일정을 추가했습니다. (${args.startDateTime})"
    }
}

fun createUpdateEventTool(
    stateStore: MockStateStore,
): SimpleTool<UpdateEventArgs> = object : SimpleTool<UpdateEventArgs>(
    argsSerializer = UpdateEventArgs.serializer(),
    name = "update_event",
    description = "캘린더 일정을 수정한다. list_events로 eventId를 먼저 확인해야 한다.",
) {
    override suspend fun execute(args: UpdateEventArgs): String {
        stateStore.recordUpdateEvent(
            args.eventId,
            args.title.ifBlank { null },
            args.startDateTime.ifBlank { null },
            args.endDateTime.ifBlank { null }
        )
        return "일정을 수정했습니다."
    }
}

fun createDeleteEventTool(
    stateStore: MockStateStore,
): SimpleTool<DeleteEventArgs> = object : SimpleTool<DeleteEventArgs>(
    argsSerializer = DeleteEventArgs.serializer(),
    name = "delete_event",
    description = "캘린더 일정을 삭제한다. list_events로 eventId를 먼저 확인해야 한다.",
) {
    override suspend fun execute(args: DeleteEventArgs): String {
        stateStore.recordDeleteEvent(args.eventId)
        return "일정을 삭제했습니다."
    }
}

// ── 할일 Factory ──

fun createListTasksTool(
    stateStore: MockStateStore,
): SimpleTool<ListTasksArgs> = object : SimpleTool<ListTasksArgs>(
    argsSerializer = ListTasksArgs.serializer(),
    name = "list_tasks",
    description = "할일 목록을 조회한다.",
) {
    override suspend fun execute(args: ListTasksArgs): String {
        stateStore.recordListTasks(args.showCompleted)
        val filtered = if (args.showCompleted) stateStore.tasks
        else stateStore.tasks.filter { it.status != "completed" }
        if (filtered.isEmpty()) return "할일이 없습니다."
        return filtered.mapIndexed { i, t ->
            val statusLabel = if (t.status == "completed") "완료" else "미완료"
            val dueLabel = if (t.dueDate.isNotBlank()) " (기한: ${t.dueDate})" else ""
            "${i + 1}. ${t.title} [$statusLabel]$dueLabel [id:${t.id}]"
        }.joinToString("\n")
    }
}

fun createCreateTaskTool(
    stateStore: MockStateStore,
): SimpleTool<CreateTaskArgs> = object : SimpleTool<CreateTaskArgs>(
    argsSerializer = CreateTaskArgs.serializer(),
    name = "create_task",
    description = "새 할일을 추가한다.",
) {
    override suspend fun execute(args: CreateTaskArgs): String {
        val id = "task-${UUID.randomUUID().toString().take(8)}"
        stateStore.recordCreateTask(id, args.title, args.dueDate, args.notes)
        val dueLabel = if (args.dueDate.isNotBlank()) " (기한: ${args.dueDate})" else ""
        return "할일 '${args.title}'을(를) 추가했습니다.$dueLabel"
    }
}

fun createCompleteTaskTool(
    stateStore: MockStateStore,
): SimpleTool<CompleteTaskArgs> = object : SimpleTool<CompleteTaskArgs>(
    argsSerializer = CompleteTaskArgs.serializer(),
    name = "complete_task",
    description = "할일을 완료 처리한다. list_tasks로 taskId를 먼저 확인해야 한다.",
) {
    override suspend fun execute(args: CompleteTaskArgs): String {
        stateStore.recordCompleteTask(args.taskId)
        return "할일을 완료 처리했습니다."
    }
}

fun createDeleteTaskTool(
    stateStore: MockStateStore,
): SimpleTool<DeleteTaskArgs> = object : SimpleTool<DeleteTaskArgs>(
    argsSerializer = DeleteTaskArgs.serializer(),
    name = "delete_task",
    description = "할일을 삭제한다. list_tasks로 taskId를 먼저 확인해야 한다.",
) {
    override suspend fun execute(args: DeleteTaskArgs): String {
        stateStore.recordDeleteTask(args.taskId)
        return "할일을 삭제했습니다."
    }
}

// ── 알림/카카오톡 Args ──

@Serializable
class ListNotificationsArgs

@Serializable
data class SendKakaoArgs(
    @property:LLMDescription("채팅방 또는 상대 이름") val roomName: String,
    @property:LLMDescription("보낼 메시지 내용") val message: String,
)

// ── 알림/카카오톡 Factory ──

fun createListNotificationsTool(
    stateStore: MockStateStore,
): SimpleTool<ListNotificationsArgs> = object : SimpleTool<ListNotificationsArgs>(
    argsSerializer = ListNotificationsArgs.serializer(),
    name = "list_notifications",
    description = "현재 알림 목록을 조회한다.",
) {
    override suspend fun execute(args: ListNotificationsArgs): String {
        stateStore.toolCallLog.add("list_notifications" to emptyMap())
        return "1. 카카오톡: 철수님이 메시지를 보냈습니다\n2. Gmail: 새 메일 2건"
    }
}

fun createSendKakaoTool(
    stateStore: MockStateStore,
): SimpleTool<SendKakaoArgs> = object : SimpleTool<SendKakaoArgs>(
    argsSerializer = SendKakaoArgs.serializer(),
    name = "send_kakao",
    description = "카카오톡으로 메시지를 보낸다. 최근 카톡 알림이 온 상대에게만 보낼 수 있다.",
) {
    override suspend fun execute(args: SendKakaoArgs): String {
        stateStore.toolCallLog.add("send_kakao" to mapOf("roomName" to args.roomName, "message" to args.message))
        return "${args.roomName}에게 '${args.message}'라고 카톡을 보냈습니다."
    }
}

// ── 유틸 ──

private fun addDays(date: String, days: Int): String {
    val parts = date.split("-").map { it.toInt() }
    val localDate = java.time.LocalDate.of(parts[0], parts[1], parts[2]).plusDays(days.toLong())
    return localDate.toString()
}
