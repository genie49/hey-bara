package com.bara.evaluation.core

import org.junit.Assert.*
import org.junit.Test

class CacheTest {
    @Test
    fun `generates consistent hash for same task`() {
        val task = Task(id = "test-001", input = "hello")
        assertEquals(Cache.computeHash(task), Cache.computeHash(task))
    }

    @Test
    fun `generates different hash for different input`() {
        val task1 = Task(id = "test-001", input = "hello")
        val task2 = Task(id = "test-001", input = "world")
        assertNotEquals(Cache.computeHash(task1), Cache.computeHash(task2))
    }
}
