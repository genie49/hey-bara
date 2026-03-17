package com.bara.heybara.domain.history

interface ConversationRepository {
    suspend fun save(conversation: Conversation)
    suspend fun getAll(): List<Conversation>
    suspend fun deleteAll()
}
