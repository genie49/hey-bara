package com.bara.evaluation.mocks

import org.junit.Assert.*
import org.junit.Test

class MockStateStoreTest {
    @Test
    fun `get calls count returns number of calls`() {
        val store = MockStateStore()
        store.recordCall("엄마", "010-1234-5678")
        store.recordCall("아빠", "010-2345-6789")
        assertEquals(2, store.get("calls.count"))
    }

    @Test
    fun `get calls last contact returns last call contact`() {
        val store = MockStateStore()
        store.recordCall("엄마", "010-1234-5678")
        store.recordCall("아빠", "010-2345-6789")
        assertEquals("아빠", store.get("calls.last.contact"))
    }

    @Test
    fun `get calls first contact returns first call contact`() {
        val store = MockStateStore()
        store.recordCall("엄마", "010-1234-5678")
        store.recordCall("아빠", "010-2345-6789")
        assertEquals("엄마", store.get("calls.first.contact"))
    }

    @Test
    fun `get calls by index`() {
        val store = MockStateStore()
        store.recordCall("엄마", "010-1234-5678")
        store.recordCall("아빠", "010-2345-6789")
        assertEquals("엄마", store.get("calls.0.contact"))
        assertEquals("아빠", store.get("calls.1.contact"))
    }

    @Test
    fun `get sms last message`() {
        val store = MockStateStore()
        store.recordSms("엄마", "010-1234-5678", "잘 잔다")
        assertEquals("잘 잔다", store.get("sms.last.message"))
    }

    @Test
    fun `get nonexistent path returns null`() {
        val store = MockStateStore()
        assertNull(store.get("calls.last.contact"))
    }

    // === Events 테스트 ===

    @Test
    fun `loadEvents and get events count`() {
        val store = MockStateStore()
        store.loadEvents(listOf(
            EventRecord("evt-001", "팀 미팅", "2026-03-17T10:00:00+09:00", "2026-03-17T11:00:00+09:00"),
            EventRecord("evt-002", "점심 약속", "2026-03-17T12:30:00+09:00", "2026-03-17T13:30:00+09:00")
        ))
        assertEquals(2, store.get("events.count"))
    }

    @Test
    fun `get events first title`() {
        val store = MockStateStore()
        store.loadEvents(listOf(
            EventRecord("evt-001", "팀 미팅", "2026-03-17T10:00:00+09:00", "2026-03-17T11:00:00+09:00")
        ))
        assertEquals("팀 미팅", store.get("events.first.title"))
    }

    @Test
    fun `recordCreateEvent adds event`() {
        val store = MockStateStore()
        store.recordCreateEvent("evt-new", "새 일정", "2026-03-18T10:00:00+09:00", "2026-03-18T11:00:00+09:00")
        assertEquals(1, store.get("events.count"))
        assertEquals("새 일정", store.get("events.last.title"))
    }

    @Test
    fun `recordDeleteEvent removes event`() {
        val store = MockStateStore()
        store.loadEvents(listOf(
            EventRecord("evt-001", "팀 미팅", "2026-03-17T10:00:00+09:00", "2026-03-17T11:00:00+09:00")
        ))
        store.recordDeleteEvent("evt-001")
        assertEquals(0, store.get("events.count"))
    }

    @Test
    fun `recordUpdateEvent modifies event`() {
        val store = MockStateStore()
        store.loadEvents(listOf(
            EventRecord("evt-001", "팀 미팅", "2026-03-17T10:00:00+09:00", "2026-03-17T11:00:00+09:00")
        ))
        store.recordUpdateEvent("evt-001", "변경된 미팅", null, null)
        assertEquals("변경된 미팅", store.get("events.first.title"))
    }

    // === Tasks 테스트 ===

    @Test
    fun `loadTasks and get tasks count`() {
        val store = MockStateStore()
        store.loadTasks(listOf(
            TaskRecord("task-001", "장보기", "needsAction", "2026-03-18"),
            TaskRecord("task-002", "세탁소", "needsAction")
        ))
        assertEquals(2, store.get("tasks.count"))
    }

    @Test
    fun `recordCreateTask adds task`() {
        val store = MockStateStore()
        store.recordCreateTask("task-new", "새 할일", "2026-03-20")
        assertEquals(1, store.get("tasks.count"))
        assertEquals("새 할일", store.get("tasks.last.title"))
        assertEquals("needsAction", store.get("tasks.last.status"))
    }

    @Test
    fun `recordCompleteTask changes status`() {
        val store = MockStateStore()
        store.loadTasks(listOf(TaskRecord("task-001", "장보기", "needsAction")))
        store.recordCompleteTask("task-001")
        assertEquals("completed", store.get("tasks.first.status"))
    }

    @Test
    fun `recordDeleteTask removes task`() {
        val store = MockStateStore()
        store.loadTasks(listOf(TaskRecord("task-001", "장보기", "needsAction")))
        store.recordDeleteTask("task-001")
        assertEquals(0, store.get("tasks.count"))
    }

    // === toolCallLog 테스트 ===

    @Test
    fun `toolCallLog records all operations`() {
        val store = MockStateStore()
        store.recordSearch("엄마")
        store.recordCall("엄마", "010-1234-5678")
        store.recordCreateEvent("evt-1", "미팅", "start", "end")
        store.recordCreateTask("task-1", "할일")
        assertEquals(4, store.toolCallLog.size)
        assertEquals("search_contacts", store.toolCallLog[0].first)
        assertEquals("make_call", store.toolCallLog[1].first)
        assertEquals("create_event", store.toolCallLog[2].first)
        assertEquals("create_task", store.toolCallLog[3].first)
    }

    @Test
    fun `reset clears everything`() {
        val store = MockStateStore()
        store.recordCall("엄마", "010")
        store.recordCreateEvent("evt-1", "미팅", "s", "e")
        store.recordCreateTask("t-1", "할일")
        store.reset()
        assertEquals(0, store.get("calls.count"))
        assertEquals(0, store.get("events.count"))
        assertEquals(0, store.get("tasks.count"))
        assertTrue(store.toolCallLog.isEmpty())
    }
}
