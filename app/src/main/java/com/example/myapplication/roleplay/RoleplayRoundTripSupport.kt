package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.model.toPlainText

data class PreparedRoleplayRoundTrip(
    val baseMessages: List<ChatMessage>,
    val userMessage: ChatMessage,
    val loadingMessage: ChatMessage,
    val initialMessages: List<ChatMessage>,
    val requestMessages: List<ChatMessage>,
)

data class PreparedRoleplayRetry(
    val baseMessages: List<ChatMessage>,
    val loadingMessage: ChatMessage,
    val initialMessages: List<ChatMessage>,
    val requestMessages: List<ChatMessage>,
)

data class PreparedRoleplayEdit(
    val restoredInput: String,
    val rewoundMessages: List<ChatMessage>,
)

object RoleplayRoundTripSupport {
    fun currentConversationMessages(
        messages: List<ChatMessage>,
        conversationId: String,
    ): List<ChatMessage> {
        return messages.filter { it.conversationId == conversationId }
    }

    fun prepareOutgoingRoundTrip(
        baseMessages: List<ChatMessage>,
        conversationId: String,
        userParts: List<ChatMessagePart>,
        selectedModel: String,
        roleplayOutputFormat: RoleplayOutputFormat,
        nowProvider: () -> Long,
        messageIdProvider: () -> String,
    ): PreparedRoleplayRoundTrip {
        val userMessage = buildMessage(
            conversationId = conversationId,
            role = MessageRole.USER,
            content = userParts.toContentMirror(specialFallback = "特殊玩法").ifBlank { "剧情互动" },
            nowProvider = nowProvider,
            messageIdProvider = messageIdProvider,
            parts = userParts,
        )
        val loadingMessage = buildMessage(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            nowProvider = nowProvider,
            messageIdProvider = messageIdProvider,
            status = MessageStatus.LOADING,
            modelName = selectedModel,
            roleplayOutputFormat = roleplayOutputFormat,
        )
        return PreparedRoleplayRoundTrip(
            baseMessages = baseMessages,
            userMessage = userMessage,
            loadingMessage = loadingMessage,
            initialMessages = baseMessages + userMessage + loadingMessage,
            requestMessages = baseMessages.filter {
                it.status == MessageStatus.COMPLETED && it.hasSendableContent()
            } + userMessage,
        )
    }

    fun prepareRetryTurn(
        currentMessages: List<ChatMessage>,
        sourceMessageId: String,
    ): PreparedRoleplayRetry? {
        val targetIndex = currentMessages.indexOfFirst { message ->
            message.id == sourceMessageId &&
                message.role == MessageRole.ASSISTANT &&
                (message.status == MessageStatus.COMPLETED || message.status == MessageStatus.ERROR)
        }
        if (targetIndex == -1) {
            return null
        }
        val targetMessage = currentMessages[targetIndex]
        val loadingMessage = targetMessage.copy(
            content = "",
            status = MessageStatus.LOADING,
            reasoningContent = "",
            parts = emptyList(),
        )
        val baseMessages = currentMessages.take(targetIndex)
        return PreparedRoleplayRetry(
            baseMessages = baseMessages,
            loadingMessage = loadingMessage,
            initialMessages = baseMessages + loadingMessage,
            requestMessages = baseMessages.filter {
                it.status == MessageStatus.COMPLETED && it.hasSendableContent()
            },
        )
    }

    fun prepareUserEdit(
        currentMessages: List<ChatMessage>,
        sourceMessageId: String,
    ): PreparedRoleplayEdit? {
        val targetIndex = currentMessages.indexOfFirst { message ->
            message.id == sourceMessageId &&
                message.role == MessageRole.USER &&
                message.status == MessageStatus.COMPLETED &&
                message.hasSendableContent()
        }
        if (targetIndex == -1) {
            return null
        }
        val targetMessage = currentMessages[targetIndex]
        return PreparedRoleplayEdit(
            restoredInput = normalizeChatMessageParts(targetMessage.parts)
                .toPlainText()
                .ifBlank { targetMessage.content }
                .trim(),
            rewoundMessages = currentMessages.take(targetIndex),
        )
    }

    private fun buildMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        nowProvider: () -> Long,
        messageIdProvider: () -> String,
        status: MessageStatus = MessageStatus.COMPLETED,
        modelName: String = "",
        reasoningContent: String = "",
        parts: List<ChatMessagePart> = emptyList(),
        roleplayOutputFormat: RoleplayOutputFormat = RoleplayOutputFormat.UNSPECIFIED,
    ): ChatMessage {
        return ChatMessage(
            id = messageIdProvider(),
            conversationId = conversationId,
            role = role,
            content = content,
            status = status,
            createdAt = nowProvider(),
            modelName = modelName,
            reasoningContent = reasoningContent,
            parts = normalizeChatMessageParts(parts),
            roleplayOutputFormat = roleplayOutputFormat,
        )
    }
}
