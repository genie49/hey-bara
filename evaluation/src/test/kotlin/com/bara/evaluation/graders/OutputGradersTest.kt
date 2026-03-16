package com.bara.evaluation.graders

import org.junit.Assert.*
import org.junit.Test

class OutputGradersTest {
    @Test fun `OutputContains passes when all keywords present`() {
        assertTrue(OutputContainsGrader().grade(listOf("전화", "엄마"), "엄마한테 전화를 걸까요?").passed)
    }
    @Test fun `OutputContains fails when keyword missing`() {
        assertFalse(OutputContainsGrader().grade(listOf("전화", "문자"), "엄마한테 전화를 걸까요?").passed)
    }
    @Test fun `OutputContainsAny passes when at least one present`() {
        assertTrue(OutputContainsAnyGrader().grade(listOf("걸까요?", "할까요?"), "엄마한테 전화를 걸까요?").passed)
    }
    @Test fun `OutputContainsAny fails when none present`() {
        assertFalse(OutputContainsAnyGrader().grade(listOf("문자", "SMS"), "엄마한테 전화를 걸까요?").passed)
    }
}
