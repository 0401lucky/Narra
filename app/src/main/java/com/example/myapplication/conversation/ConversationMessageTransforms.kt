package com.example.myapplication.conversation

import com.example.myapplication.data.repository.TransferUpdateDirective
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.isGiftPart
import com.example.myapplication.model.isTransferPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.toContentMirror

object ConversationMessageTransforms {
    fun applyTransferUpdates(
        messages: List<ChatMessage>,
        updates: List<TransferUpdateDirective>,
    ): List<ChatMessage> {
        if (updates.isEmpty()) {
            return messages
        }

        var changed = false
        val updatedMessages = messages.map { message ->
            val updatedParts = message.parts.map { part ->
                val update = updates.firstOrNull { it.refId == part.specialId } ?: return@map part
                if (!part.isTransferPart() || part.specialStatus == update.status) {
                    return@map part
                }
                changed = true
                part.copy(specialStatus = update.status)
            }
            if (updatedParts == message.parts) {
                message
            } else {
                val normalizedUpdatedParts = normalizeChatMessageParts(updatedParts)
                changed = true
                message.copy(
                    parts = normalizedUpdatedParts,
                    content = normalizedUpdatedParts.toContentMirror(
                        specialFallback = "特殊玩法",
                    ).ifBlank { message.content },
                )
            }
        }
        return if (changed) updatedMessages else messages
    }

    fun applyGiftImageUpdate(
        messages: List<ChatMessage>,
        specialId: String,
        transform: (ChatMessagePart) -> ChatMessagePart,
    ): List<ChatMessage> {
        if (specialId.isBlank()) {
            return messages
        }

        var changed = false
        val updatedMessages = messages.map { message ->
            val updatedParts = message.parts.map { part ->
                if (!part.isGiftPart() || part.specialId != specialId) {
                    return@map part
                }
                val updatedPart = transform(part)
                if (updatedPart != part) {
                    changed = true
                }
                updatedPart
            }
            if (updatedParts == message.parts) {
                message
            } else {
                val normalizedUpdatedParts = normalizeChatMessageParts(updatedParts)
                message.copy(
                    parts = normalizedUpdatedParts,
                    content = normalizedUpdatedParts.toContentMirror(
                        specialFallback = "特殊玩法",
                    ).ifBlank { message.content },
                )
            }
        }
        return if (changed) updatedMessages else messages
    }

    fun trimRequestMessagesWithSummary(
        requestMessages: List<ChatMessage>,
        completedMessageCount: Int,
        summaryCoveredMessageCount: Int,
        recentWindow: Int,
    ): List<ChatMessage> {
        if (!hasSufficientSummaryCoverage(
                completedMessageCount = completedMessageCount,
                recentWindow = recentWindow,
                summaryCoveredMessageCount = summaryCoveredMessageCount,
            ) || requestMessages.size <= recentWindow
        ) {
            return requestMessages
        }
        return requestMessages.takeLast(recentWindow)
    }

    fun hasSufficientSummaryCoverage(
        completedMessageCount: Int,
        recentWindow: Int,
        summaryCoveredMessageCount: Int,
    ): Boolean {
        if (summaryCoveredMessageCount <= 0) {
            return false
        }
        val olderCompletedMessageCount = (completedMessageCount - recentWindow).coerceAtLeast(0)
        return summaryCoveredMessageCount >= olderCompletedMessageCount
    }
}
