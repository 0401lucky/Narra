package com.example.myapplication.model

import androidx.compose.runtime.Immutable

enum class MessageRole {
    USER,
    ASSISTANT,
}

enum class MessageStatus {
    COMPLETED,
    LOADING,
    ERROR,
}

@Immutable
data class ChatMessage(
    val id: String,
    val conversationId: String = "",
    val role: MessageRole,
    val content: String,
    val status: MessageStatus = MessageStatus.COMPLETED,
    val createdAt: Long = 0L,
    val modelName: String = "",
    val reasoningContent: String = "",
    val attachments: List<MessageAttachment> = emptyList(),
    val parts: List<ChatMessagePart> = emptyList(),
)

fun ChatMessage.hasSendableContent(): Boolean {
    return content.isNotBlank() ||
        attachments.isNotEmpty() ||
        normalizeChatMessageParts(parts).isNotEmpty()
}
