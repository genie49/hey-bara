package com.bara.evaluation.graders

import com.bara.evaluation.core.*
import com.bara.evaluation.mocks.MockStateStore
import org.junit.Assert.*
import org.junit.Test

class StateCheckGraderTest {
    private val grader = StateCheckGrader()

    @Test fun `passes when all checks match`() {
        val store = MockStateStore()
        store.recordCall("엄마", "010-1234-5678")
        assertTrue(grader.grade(listOf(StateCheckExpected("calls.last.contact", "엄마"), StateCheckExpected("calls.count", ">=1")), store).passed)
    }
    @Test fun `fails when value mismatch`() {
        val store = MockStateStore()
        store.recordCall("아빠", "010-2345-6789")
        assertFalse(grader.grade(listOf(StateCheckExpected("calls.last.contact", "엄마")), store).passed)
    }
    @Test fun `wildcard passes when value exists`() {
        val store = MockStateStore()
        store.recordSms("엄마", "010-1234-5678", "잘 잔다")
        assertTrue(grader.grade(listOf(StateCheckExpected("sms.last.message", "*")), store).passed)
    }
}
