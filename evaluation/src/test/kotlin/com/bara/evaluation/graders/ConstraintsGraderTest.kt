package com.bara.evaluation.graders

import com.bara.evaluation.core.*
import org.junit.Assert.*
import org.junit.Test

class ConstraintsGraderTest {
    private val grader = ConstraintsGrader()

    @Test fun `passes when within all constraints`() {
        assertTrue(grader.grade(ConstraintsExpected(maxTurns = 5, maxToolcalls = 3, timeout = 10000), Metrics(turns = 3, toolCallCount = 2, durationMs = 5000)).passed)
    }
    @Test fun `fails when turns exceeded`() {
        assertFalse(grader.grade(ConstraintsExpected(maxTurns = 3), Metrics(turns = 5)).passed)
    }
    @Test fun `fails when timeout exceeded`() {
        assertFalse(grader.grade(ConstraintsExpected(timeout = 5000), Metrics(durationMs = 6000)).passed)
    }
}
