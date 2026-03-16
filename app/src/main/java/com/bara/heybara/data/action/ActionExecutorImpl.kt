package com.bara.heybara.data.action

import com.bara.heybara.domain.action.ActionExecutor
import com.bara.heybara.domain.agent.AgentAction

class ActionExecutorImpl(
    private val callExecutor: CallExecutor,
    private val smsExecutor: SmsExecutor
) : ActionExecutor {

    override suspend fun execute(action: AgentAction): Boolean {
        return when (action) {
            is AgentAction.Call -> {
                val number = action.phoneNumber ?: return false
                callExecutor.call(number)
            }
            is AgentAction.SendSms -> {
                val number = action.phoneNumber ?: return false
                smsExecutor.sendSms(number, action.message)
            }
        }
    }
}
