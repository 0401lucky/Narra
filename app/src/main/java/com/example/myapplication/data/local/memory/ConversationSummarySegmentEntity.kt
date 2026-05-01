package com.example.myapplication.data.local.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.myapplication.data.local.chat.ConversationEntity

@Entity(
    tableName = "conversation_summary_segments",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["assistantId"]),
        Index(value = ["conversationId", "startCreatedAt"]),
        Index(value = ["conversationId", "endCreatedAt"]),
        Index(value = ["conversationId", "startMessageId", "endMessageId"], unique = true),
    ],
)
data class ConversationSummarySegmentEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val assistantId: String = "",
    val startMessageId: String = "",
    val endMessageId: String = "",
    val startCreatedAt: Long = 0L,
    val endCreatedAt: Long = 0L,
    val messageCount: Int = 0,
    val summary: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
