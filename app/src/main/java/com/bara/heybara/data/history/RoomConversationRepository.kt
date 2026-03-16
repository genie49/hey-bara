package com.bara.heybara.data.history

import com.bara.heybara.domain.history.Conversation
import com.bara.heybara.domain.history.ConversationRepository

class RoomConversationRepository(
    private val dao: ConversationDao
) : ConversationRepository {

    override suspend fun save(conversation: Conversation) {
        dao.insert(
            ConversationEntity(
                topic = conversation.topic,
                category = conversation.category,
                inputMode = conversation.inputMode,
                timestamp = conversation.timestamp,
                transcript = conversation.transcript
            )
        )
    }

    override suspend fun getAll(): List<Conversation> {
        return dao.getAll().map {
            Conversation(
                id = it.id,
                topic = it.topic,
                category = it.category,
                inputMode = it.inputMode,
                timestamp = it.timestamp,
                transcript = it.transcript
            )
        }
    }
}
