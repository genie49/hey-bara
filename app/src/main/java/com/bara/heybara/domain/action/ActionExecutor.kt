package com.bara.heybara.domain.action

import com.bara.heybara.domain.agent.AgentAction

interface ActionExecutor {
    suspend fun execute(action: AgentAction): Boolean
}
