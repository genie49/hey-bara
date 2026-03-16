package com.bara.evaluation.agent

data class AgentConfig(
    val apiKey: String,
    val modelId: String = "gemini-3.1-flash-lite-preview",
    val maxIterations: Int = 10,
)
