package com.bara.heybara.data.agent

import com.bara.heybara.domain.agent.AgentAction
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Method

// KoogAgentEngine의 parseResponse 로직을 테스트
// parseResponse는 private이므로 reflection으로 접근
class KoogAgentEngineParseTest {

    private val engine = KoogAgentEngine("dummy-key")

    private fun callParseResponse(input: String, result: String): Any? {
        val method: Method = KoogAgentEngine::class.java.getDeclaredMethod(
            "parseResponse", String::class.java, String::class.java
        )
        method.isAccessible = true
        return method.invoke(engine, input, result)
    }

    @Test
    fun `parseResponse detects call action`() {
        val response = callParseResponse(
            "엄마한테 전화해",
            "엄마(010-1234-5678)한테 전화를 겁니다."
        ) as com.bara.heybara.domain.agent.AgentResponse

        assertTrue(response.requiresConfirmation)
        assertNotNull(response.action)
        val call = response.action as AgentAction.Call
        assertEquals("엄마", call.contact)
        assertEquals("010-1234-5678", call.phoneNumber)
    }

    @Test
    fun `parseResponse detects sms action`() {
        val response = callParseResponse(
            "철수한테 밥 먹자고 문자 보내줘",
            "철수(010-9876-5432)한테 '밥 먹자'라고 문자를 보냅니다."
        ) as com.bara.heybara.domain.agent.AgentResponse

        assertTrue(response.requiresConfirmation)
        assertNotNull(response.action)
        val sms = response.action as AgentAction.SendSms
        assertEquals("철수", sms.contact)
        assertEquals("010-9876-5432", sms.phoneNumber)
        assertEquals("밥 먹자", sms.message)
    }

    @Test
    fun `parseResponse returns plain response for general chat`() {
        val response = callParseResponse(
            "오늘 날씨 어때?",
            "오늘 서울은 맑고 기온은 15도입니다."
        ) as com.bara.heybara.domain.agent.AgentResponse

        assertFalse(response.requiresConfirmation)
        assertNull(response.action)
        assertEquals("오늘 서울은 맑고 기온은 15도입니다.", response.text)
    }

    @Test
    fun `parseResponse handles unmatched tool result as plain response`() {
        val response = callParseResponse(
            "뭐해?",
            "저는 바라에요! 무엇을 도와드릴까요?"
        ) as com.bara.heybara.domain.agent.AgentResponse

        assertFalse(response.requiresConfirmation)
        assertNull(response.action)
    }
}
