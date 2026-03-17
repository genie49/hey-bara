package com.bara.heybara.domain.agent

interface AgentEngine {
    suspend fun process(text: String): String
    fun release()
}
