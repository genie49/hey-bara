package com.bara.evaluation.mocks

/** 전화 기록 */
data class CallRecord(val contact: String, val phoneNumber: String)

/** 문자 기록 */
data class SmsRecord(val contact: String, val phoneNumber: String, val message: String)

/** 캘린더 일정 기록 */
data class EventRecord(
    val id: String, val title: String,
    val startDateTime: String, val endDateTime: String,
    val description: String = ""
)

/** 할일 기록 */
data class TaskRecord(
    val id: String, val title: String,
    val status: String,  // "needsAction" | "completed"
    val dueDate: String = "", val notes: String = ""
)

/**
 * Mock 상태 저장소 — 평가 중 에이전트의 부수효과를 기록하고 조회
 *
 * 경로 문법: "calls.last.contact", "events.0.title", "tasks.count" 등
 */
class MockStateStore {
    val calls = mutableListOf<CallRecord>()
    val sms = mutableListOf<SmsRecord>()
    val searchQueries = mutableListOf<String>()
    val events = mutableListOf<EventRecord>()
    val tasks = mutableListOf<TaskRecord>()
    val toolCallLog = mutableListOf<Pair<String, Map<String, String>>>()

    fun recordCall(contact: String, phoneNumber: String) {
        calls.add(CallRecord(contact, phoneNumber))
        toolCallLog.add("make_call" to mapOf("contact" to contact, "phoneNumber" to phoneNumber))
    }

    fun recordSms(contact: String, phoneNumber: String, message: String) {
        sms.add(SmsRecord(contact, phoneNumber, message))
        toolCallLog.add("send_sms" to mapOf("contact" to contact, "phoneNumber" to phoneNumber, "message" to message))
    }

    fun recordSearch(query: String) {
        searchQueries.add(query)
        toolCallLog.add("search_contacts" to mapOf("query" to query))
    }

    // 데이터셋 로드
    fun loadEvents(list: List<EventRecord>) { events.clear(); events.addAll(list) }
    fun loadTasks(list: List<TaskRecord>) { tasks.clear(); tasks.addAll(list) }

    // 캘린더 CRUD
    fun recordListEvents(date: String, days: Int) {
        toolCallLog.add("list_events" to mapOf("date" to date, "days" to days.toString()))
    }

    fun recordCreateEvent(id: String, title: String, start: String, end: String, desc: String = "") {
        events.add(EventRecord(id, title, start, end, desc))
        toolCallLog.add("create_event" to mapOf("title" to title, "startDateTime" to start))
    }

    fun recordUpdateEvent(id: String, title: String?, start: String?, end: String?) {
        val idx = events.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = events[idx]
            events[idx] = old.copy(
                title = title ?: old.title,
                startDateTime = start ?: old.startDateTime,
                endDateTime = end ?: old.endDateTime
            )
        }
        toolCallLog.add("update_event" to mapOf("eventId" to id))
    }

    fun recordDeleteEvent(id: String) {
        events.removeAll { it.id == id }
        toolCallLog.add("delete_event" to mapOf("eventId" to id))
    }

    // 할일 CRUD
    fun recordListTasks(showCompleted: Boolean) {
        toolCallLog.add("list_tasks" to mapOf("showCompleted" to showCompleted.toString()))
    }

    fun recordCreateTask(id: String, title: String, dueDate: String = "", notes: String = "") {
        tasks.add(TaskRecord(id, title, "needsAction", dueDate, notes))
        toolCallLog.add("create_task" to mapOf("title" to title))
    }

    fun recordCompleteTask(id: String) {
        val idx = tasks.indexOfFirst { it.id == id }
        if (idx >= 0) tasks[idx] = tasks[idx].copy(status = "completed")
        toolCallLog.add("complete_task" to mapOf("taskId" to id))
    }

    fun recordDeleteTask(id: String) {
        tasks.removeAll { it.id == id }
        toolCallLog.add("delete_task" to mapOf("taskId" to id))
    }

    /** 점(.) 구분 경로로 상태 조회 */
    fun get(path: String): Any? {
        val parts = path.split(".")
        if (parts.isEmpty()) return null
        val collection: List<Any> = when (parts[0]) {
            "calls" -> calls
            "sms" -> sms
            "searchQueries" -> searchQueries
            "events" -> events
            "tasks" -> tasks
            else -> return null
        }
        return resolvePath(collection, parts.drop(1))
    }

    private fun resolvePath(collection: List<Any>, parts: List<String>): Any? {
        if (parts.isEmpty()) return collection
        val accessor = parts[0]
        val element: Any? = when (accessor) {
            "count" -> return collection.size
            "last" -> collection.lastOrNull()
            "first" -> collection.firstOrNull()
            else -> {
                val index = accessor.toIntOrNull() ?: return null
                collection.getOrNull(index)
            }
        }
        if (element == null) return null
        if (parts.size == 1) return element
        val field = parts[1]
        return when (element) {
            is CallRecord -> when (field) {
                "contact" -> element.contact
                "phoneNumber" -> element.phoneNumber
                else -> null
            }
            is SmsRecord -> when (field) {
                "contact" -> element.contact
                "phoneNumber" -> element.phoneNumber
                "message" -> element.message
                else -> null
            }
            is EventRecord -> when (field) {
                "id" -> element.id
                "title" -> element.title
                "startDateTime" -> element.startDateTime
                "endDateTime" -> element.endDateTime
                "description" -> element.description
                else -> null
            }
            is TaskRecord -> when (field) {
                "id" -> element.id
                "title" -> element.title
                "status" -> element.status
                "dueDate" -> element.dueDate
                "notes" -> element.notes
                else -> null
            }
            is String -> element
            else -> null
        }
    }

    /** 모든 기록 초기화 */
    fun reset() {
        calls.clear()
        sms.clear()
        searchQueries.clear()
        events.clear()
        tasks.clear()
        toolCallLog.clear()
    }
}
