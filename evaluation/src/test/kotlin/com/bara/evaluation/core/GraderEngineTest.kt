package com.bara.evaluation.core

import com.bara.evaluation.mocks.MockStateStore
import org.junit.Assert.*
import org.junit.Test

class GraderEngineTest {
    @Test
    fun `runs only specified graders`() {
        val engine = GraderEngine(apiKey = "")
        val expected = Expected(
            outputContains = listOf("전화"),
            constraints = ConstraintsExpected(maxTurns = 5),
        )
        val transcript = Transcript(
            messages = listOf(Message("assistant", "엄마한테 전화를 걸까요?")),
            toolCalls = emptyList(),
            metrics = Metrics(turns = 3),
        )
        val results = engine.gradeSync(
            expected = expected,
            transcript = transcript,
            stateStore = MockStateStore(),
            input = "엄마한테 전화해줘",
        )
        assertEquals(2, results.size)
        assertTrue(results.all { it.passed })
    }
}
