package com.bara.heybara.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ConversationDao {
    @Insert
    suspend fun insert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC")
    suspend fun getAll(): List<ConversationEntity>

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("SELECT * FROM conversations WHERE sessionId = :sessionId LIMIT 1")
    suspend fun findBySessionId(sessionId: String): ConversationEntity?

    @Query("UPDATE conversations SET topic = :topic, category = :category, transcript = :transcript, timestamp = :timestamp WHERE sessionId = :sessionId")
    suspend fun updateBySessionId(sessionId: String, topic: String, category: String, transcript: String, timestamp: Long)
}
