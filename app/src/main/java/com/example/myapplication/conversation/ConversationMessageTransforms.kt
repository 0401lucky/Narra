package com.example.myapplication.conversation

import com.example.myapplication.data.repository.TransferUpdateDirective
import com.example.myapplication.model.ChatMessage
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
                        specialFallback = "转账",
                    ).ifBlank { message.content },
                )
            }
        }
        return if (changed) updatedMessages else messages
    }

    fun trimRequestMessagesWithSummary(
        requestMessages: List<ChatMessage>,
        recentWindow: Int,
        hasSummary: Boolean,
    ): List<ChatMessage> {
        if (!hasSummary || requestMessages.size <= recentWindow) {
            return requestMessages
        }
        return requestMessages.takeLast(recentWindow)
    }
}
