package com.bara.evaluation.core

import org.junit.Assert.*
import org.junit.Test

class TaskManagerTest {
    @Test
    fun `loads tasks from resources`() {
        val manager = TaskManager("tasks")
        val tasks = manager.loadAll()
        assertTrue(tasks.isNotEmpty())
    }

    @Test
    fun `parses task yaml correctly`() {
        val manager = TaskManager("tasks")
        val tasks = manager.loadAll()
        val task = tasks.find { it.id == "voice-agent/test-task-001" }
        assertNotNull(task)
        assertEquals("엄마한테 전화해줘", task!!.input)
        assertEquals("superset", task.expected.toolCalls!!.mode)
    }
}
