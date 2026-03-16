package com.bara.heybara.domain.agent

import org.junit.Assert.*
import org.junit.Test

class AgentResponseTest {

    @Test
    fun `Call action stores contact and phoneNumber`() {
        val action = AgentAction.Call("엄마", "010-1234-5678")
        assertEquals("엄마", action.contact)
        assertEquals("010-1234-5678", action.phoneNumber)
    }

    @Test
    fun `Call action allows null phoneNumber`() {
        val action = AgentAction.Call("엄마", null)
        assertNull(action.phoneNumber)
    }

    @Test
    fun `SendSms action stores all fields`() {
        val action = AgentAction.SendSms("철수", "010-9876-5432", "밥 먹자")
        assertEquals("철수", action.contact)
        assertEquals("010-9876-5432", action.phoneNumber)
        assertEquals("밥 먹자", action.message)
    }

    @Test
    fun `AgentResponse with confirmation required`() {
        val response = AgentResponse(
            text = "엄마한테 전화를 걸까요?",
            action = AgentAction.Call("엄마", "010-1234-5678"),
            requiresConfirmation = true
        )
        assertTrue(response.requiresConfirmation)
        assertNotNull(response.action)
    }

    @Test
    fun `AgentResponse without action`() {
        val response = AgentResponse(
            text = "오늘 날씨는 맑아요",
            action = null,
            requiresConfirmation = false
        )
        assertFalse(response.requiresConfirmation)
        assertNull(response.action)
    }

    @Test
    fun `Call and SendSms are distinct AgentAction subtypes`() {
        val call: AgentAction = AgentAction.Call("엄마", "010-1234-5678")
        val sms: AgentAction = AgentAction.SendSms("철수", "010-9876-5432", "밥 먹자")
        assertTrue(call is AgentAction.Call)
        assertTrue(sms is AgentAction.SendSms)
        assertFalse(call is AgentAction.SendSms)
    }
}
