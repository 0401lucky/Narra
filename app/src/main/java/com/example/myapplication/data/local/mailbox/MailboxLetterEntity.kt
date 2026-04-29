package com.example.myapplication.data.local.mailbox

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mailbox_letters",
    indices = [
        Index(value = ["scenarioId", "box", "updatedAt"]),
        Index(value = ["conversationId"]),
        Index(value = ["assistantId"]),
        Index(value = ["replyToLetterId"]),
    ],
)
data class MailboxLetterEntity(
    @PrimaryKey val id: String,
    val scenarioId: String,
    val conversationId: String,
    val assistantId: String,
    val senderType: String,
    val box: String,
    val subject: String,
    val content: String,
    val excerpt: String,
    val tagsCsv: String,
    val mood: String,
    val replyToLetterId: String,
    val isRead: Boolean,
    val isStarred: Boolean,
    val allowMemory: Boolean,
    val memoryCandidate: String,
    val linkedMemoryId: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sentAt: Long,
    val readAt: Long,
)
