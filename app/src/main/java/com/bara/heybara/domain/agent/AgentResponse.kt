package com.bara.heybara.domain.agent

data class AgentResponse(
    val text: String,
    val action: AgentAction?,
    val requiresConfirmation: Boolean
)

sealed class AgentAction {
    data class Call(val contact: String, val phoneNumber: String?) : AgentAction()
    data class SendSms(val contact: String, val phoneNumber: String?, val message: String) : AgentAction()
}
