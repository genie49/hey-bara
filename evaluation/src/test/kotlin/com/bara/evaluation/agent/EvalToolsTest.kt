package com.bara.evaluation.agent

import com.bara.evaluation.datasets.DEFAULT_CONTACTS
import com.bara.evaluation.mocks.MockContactResolver
import com.bara.evaluation.mocks.MockStateStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EvalToolsTest {
    private lateinit var stateStore: MockStateStore
    private lateinit var resolver: MockContactResolver

    @Before
    fun setup() {
        stateStore = MockStateStore()
        resolver = MockContactResolver(DEFAULT_CONTACTS)
    }

    @Test
    fun `SearchContactsTool records query and returns results`() = runTest {
        val tool = createSearchContactsTool(resolver, stateStore)
        val result = tool.execute(SearchContactsArgs("엄마"))
        assertTrue(result.contains("010-1234-5678"))
        assertEquals(listOf("엄마"), stateStore.searchQueries)
    }

    @Test
    fun `MakeCallTool records call in state store`() = runTest {
        val tool = createMakeCallTool(stateStore)
        tool.execute(MakeCallArgs("엄마", "010-1234-5678"))
        assertEquals(1, stateStore.calls.size)
        assertEquals("엄마", stateStore.calls[0].contact)
    }

    @Test
    fun `SendSmsTool records sms in state store`() = runTest {
        val tool = createSendSmsTool(stateStore)
        tool.execute(SendSmsArgs("엄마", "010-1234-5678", "잘 잔다"))
        assertEquals(1, stateStore.sms.size)
        assertEquals("잘 잔다", stateStore.sms[0].message)
    }
}
