package com.bara.heybara.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String = "",
    val topic: String,
    val category: String,
    val inputMode: String,
    val timestamp: Long,
    val transcript: String
)
