package com.bara.evaluation.core

import org.junit.Assert.*
import org.junit.Test

class TypesTest {
    @Test
    fun `Outcome passed when all graders pass`() {
        val results = listOf(
            GraderResult("tool_calls", passed = true, score = 1.0),
            GraderResult("output_contains", passed = true, score = 1.0),
        )
        val outcome = Outcome(taskId = "test-001", graderResults = results)
        assertEquals("passed", outcome.status)
    }

    @Test
    fun `Outcome failed when any grader fails`() {
        val results = listOf(
            GraderResult("tool_calls", passed = true, score = 1.0),
            GraderResult("output_contains", passed = false, score = 0.0),
        )
        val outcome = Outcome(taskId = "test-001", graderResults = results)
        assertEquals("failed", outcome.status)
    }

    @Test
    fun `Outcome error when exception present`() {
        val outcome = Outcome(taskId = "test-001", graderResults = emptyList(), error = "API timeout")
        assertEquals("error", outcome.status)
    }

    @Test
    fun `Outcome cached when cached flag set`() {
        val outcome = Outcome(taskId = "test-001", graderResults = emptyList(), cached = true)
        assertEquals("cached", outcome.status)
    }
}
