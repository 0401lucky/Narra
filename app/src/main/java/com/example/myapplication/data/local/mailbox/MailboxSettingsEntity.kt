package com.example.myapplication.data.local.mailbox

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mailbox_settings")
data class MailboxSettingsEntity(
    @PrimaryKey val scenarioId: String,
    val autoReplyToUserLetters: Boolean,
    val includeRecentChatByDefault: Boolean,
    val includePhoneCluesByDefault: Boolean,
    val allowMemoryByDefault: Boolean,
    val proactiveFrequency: String,
    val lastProactiveLetterAt: Long,
    val updatedAt: Long,
)
