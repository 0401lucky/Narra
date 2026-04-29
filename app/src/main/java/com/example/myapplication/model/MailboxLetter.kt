package com.example.myapplication.model

import java.util.UUID

data class MailboxLetter(
    val id: String = UUID.randomUUID().toString(),
    val scenarioId: String = "",
    val conversationId: String = "",
    val assistantId: String = "",
    val senderType: MailboxSenderType = MailboxSenderType.USER,
    val box: MailboxBox = MailboxBox.DRAFT,
    val subject: String = "",
    val content: String = "",
    val excerpt: String = "",
    val tags: List<String> = emptyList(),
    val mood: String = "",
    val replyToLetterId: String = "",
    val isRead: Boolean = true,
    val isStarred: Boolean = false,
    val allowMemory: Boolean = true,
    val memoryCandidate: String = "",
    val linkedMemoryId: String = "",
    val source: MailboxSource = MailboxSource.MANUAL,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val sentAt: Long = 0L,
    val readAt: Long = 0L,
)

data class MailboxSettings(
    val scenarioId: String = "",
    val autoReplyToUserLetters: Boolean = true,
    val includeRecentChatByDefault: Boolean = true,
    val includePhoneCluesByDefault: Boolean = true,
    val allowMemoryByDefault: Boolean = true,
    val proactiveFrequency: MailboxProactiveFrequency = MailboxProactiveFrequency.OFF,
    val lastProactiveLetterAt: Long = 0L,
    val updatedAt: Long = 0L,
)

enum class MailboxProactiveFrequency(
    val storageValue: String,
    val label: String,
    val description: String,
    val cooldownMillis: Long,
) {
    OFF("off", "关闭", "只在你寄信后回信", Long.MAX_VALUE),
    LOW("low", "偶尔想起", "大约几天一封", 72L * 60L * 60L * 1000L),
    NORMAL("normal", "有事会写", "关系有动静时会来信", 24L * 60L * 60L * 1000L),
    HIGH("high", "常常惦记", "更容易收到主动来信", 6L * 60L * 60L * 1000L);

    companion object {
        fun fromStorageValue(value: String): MailboxProactiveFrequency {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: OFF
        }
    }
}

enum class MailboxSenderType(
    val storageValue: String,
    val label: String,
) {
    USER("user", "我"),
    CHARACTER("character", "角色"),
    SYSTEM("system", "系统");

    companion object {
        fun fromStorageValue(value: String): MailboxSenderType {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: USER
        }
    }
}

enum class MailboxBox(
    val storageValue: String,
    val label: String,
) {
    INBOX("inbox", "收件"),
    DRAFT("draft", "草稿"),
    SENT("sent", "已寄"),
    ARCHIVE("archive", "归档"),
    TRASH("trash", "删除");

    companion object {
        fun fromStorageValue(value: String): MailboxBox {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: INBOX
        }
    }
}

enum class MailboxSource(
    val storageValue: String,
) {
    MANUAL("manual"),
    AI_REPLY("ai_reply"),
    ROLEPLAY_EVENT("roleplay_event"),
    IMPORTED("imported");

    companion object {
        fun fromStorageValue(value: String): MailboxSource {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: MANUAL
        }
    }
}
