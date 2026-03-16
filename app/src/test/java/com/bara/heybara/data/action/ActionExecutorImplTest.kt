package com.bara.heybara.data.action

import com.bara.heybara.domain.agent.AgentAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ActionExecutorImplTest {

    private lateinit var mockCallExecutor: CallExecutor
    private lateinit var mockSmsExecutor: SmsExecutor
    private lateinit var executor: ActionExecutorImpl

    @Before
    fun setup() {
        mockCallExecutor = mock()
        mockSmsExecutor = mock()
        executor = ActionExecutorImpl(mockCallExecutor, mockSmsExecutor)
    }

    @Test
    fun `execute Call action calls CallExecutor`() = runTest {
        whenever(mockCallExecutor.call("010-1234-5678")).thenReturn(true)
        val result = executor.execute(AgentAction.Call("엄마", "010-1234-5678"))
        assertTrue(result)
        verify(mockCallExecutor).call("010-1234-5678")
    }

    @Test
    fun `execute Call action returns false when no phoneNumber`() = runTest {
        val result = executor.execute(AgentAction.Call("엄마", null))
        assertFalse(result)
        verifyNoInteractions(mockCallExecutor)
    }

    @Test
    fun `execute Call action returns false when call fails`() = runTest {
        whenever(mockCallExecutor.call("010-1234-5678")).thenReturn(false)
        val result = executor.execute(AgentAction.Call("엄마", "010-1234-5678"))
        assertFalse(result)
    }

    @Test
    fun `execute SendSms action calls SmsExecutor`() = runTest {
        whenever(mockSmsExecutor.sendSms("010-1234-5678", "밥 먹자")).thenReturn(true)
        val result = executor.execute(AgentAction.SendSms("철수", "010-1234-5678", "밥 먹자"))
        assertTrue(result)
        verify(mockSmsExecutor).sendSms("010-1234-5678", "밥 먹자")
    }

    @Test
    fun `execute SendSms action returns false when no phoneNumber`() = runTest {
        val result = executor.execute(AgentAction.SendSms("철수", null, "밥 먹자"))
        assertFalse(result)
        verifyNoInteractions(mockSmsExecutor)
    }

    @Test
    fun `execute SendSms action returns false when sms fails`() = runTest {
        whenever(mockSmsExecutor.sendSms("010-1234-5678", "밥 먹자")).thenReturn(false)
        val result = executor.execute(AgentAction.SendSms("철수", "010-1234-5678", "밥 먹자"))
        assertFalse(result)
    }
}
