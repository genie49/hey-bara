package com.bara.heybara.domain.agent

data class AgentResponse(
    val text: String,
    val action: AgentAction?,
    val requiresConfirmation: Boolean
)

sealed class AgentAction {
    data class Call(val contact: String) : AgentAction()
    // Phase 3에서 추가: Sms, Kakao, Calendar, Task, Notification
}
