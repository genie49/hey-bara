package com.bara.heybara.data.history

import com.bara.heybara.domain.history.Conversation
import com.bara.heybara.domain.history.ConversationRepository

class RoomConversationRepository(
    private val dao: ConversationDao
) : ConversationRepository {

    override suspend fun save(conversation: Conversation) {
        dao.insert(
            ConversationEntity(
                sessionId = conversation.sessionId,
                topic = conversation.topic,
                category = conversation.category,
                inputMode = conversation.inputMode,
                timestamp = conversation.timestamp,
                transcript = conversation.transcript
            )
        )
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }

    override suspend fun getAll(): List<Conversation> {
        return dao.getAll().map {
            Conversation(
                id = it.id,
                sessionId = it.sessionId,
                topic = it.topic,
                category = it.category,
                inputMode = it.inputMode,
                timestamp = it.timestamp,
                transcript = it.transcript
            )
        }
    }

    // sessionId 기준 upsert: 같은 세션이면 업데이트, 없으면 새로 생성
    suspend fun upsertBySession(
        sessionId: String,
        topic: String,
        category: String,
        inputMode: String,
        transcript: String
    ) {
        val existing = dao.findBySessionId(sessionId)
        if (existing != null) {
            dao.updateBySessionId(sessionId, topic, category, transcript, System.currentTimeMillis())
        } else {
            dao.insert(
                ConversationEntity(
                    sessionId = sessionId,
                    topic = topic,
                    category = category,
                    inputMode = inputMode,
                    timestamp = System.currentTimeMillis(),
                    transcript = transcript
                )
            )
        }
    }
}
