package com.bara.evaluation.graders

import com.bara.evaluation.core.*
import org.junit.Assert.*
import org.junit.Test

class ToolCallsGraderTest {
    private val grader = ToolCallsGrader()

    @Test fun `superset mode passes when expected calls are subset of actual`() {
        val expected = ToolCallsExpected(mode = "superset", calls = listOf(ToolCallExpected("search_contacts", mapOf("query" to "엄마"))))
        val actual = listOf(ToolCall("search_contacts", mapOf("query" to "엄마")), ToolCall("make_call", mapOf("contact" to "엄마", "phoneNumber" to "010-1234-5678")))
        assertTrue(grader.grade(expected, actual).passed)
    }

    @Test fun `superset mode fails when expected call missing`() {
        val expected = ToolCallsExpected(mode = "superset", calls = listOf(ToolCallExpected("search_contacts", mapOf("query" to "엄마")), ToolCallExpected("make_call", mapOf("contact" to "엄마"))))
        val actual = listOf(ToolCall("search_contacts", mapOf("query" to "엄마")))
        assertFalse(grader.grade(expected, actual).passed)
    }

    @Test fun `strict mode fails on wrong order`() {
        val expected = ToolCallsExpected(mode = "strict", calls = listOf(ToolCallExpected("search_contacts"), ToolCallExpected("make_call")))
        val actual = listOf(ToolCall("make_call", emptyMap()), ToolCall("search_contacts", emptyMap()))
        assertFalse(grader.grade(expected, actual).passed)
    }

    @Test fun `wildcard param matches any value`() {
        val expected = ToolCallsExpected(mode = "superset", calls = listOf(ToolCallExpected("make_call", mapOf("contact" to "*"))))
        val actual = listOf(ToolCall("make_call", mapOf("contact" to "아무나", "phoneNumber" to "010-0000-0000")))
        assertTrue(grader.grade(expected, actual).passed)
    }
}
