package com.example.myapplication.data.local.memory

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation_summaries",
    indices = [
        Index(value = ["assistantId"]),
        Index(value = ["updatedAt"]),
    ],
)
data class ConversationSummaryEntity(
    @PrimaryKey val conversationId: String,
    val assistantId: String = "",
    val summary: String = "",
    val coveredMessageCount: Int = 0,
    val updatedAt: Long = 0L,
)
