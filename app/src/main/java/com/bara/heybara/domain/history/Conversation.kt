package com.bara.heybara.domain.history

data class Conversation(
    val id: Long = 0,
    val sessionId: String = "",
    val topic: String,
    val category: String,       // "call" | "sms" | "chat"
    val inputMode: String,      // "voice" | "text"
    val timestamp: Long,
    val transcript: String      // 대화 전문
)
