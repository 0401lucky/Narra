package com.example.myapplication.conversation

import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.TransferUpdateDirective
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.TransferStatus

class ConversationTransferCoordinator(
    private val conversationRepository: ConversationRepository,
) {
    suspend fun confirmReceipt(
        conversationId: String,
        specialId: String,
        selectedModel: String,
        currentMessages: List<ChatMessage>,
    ): List<ChatMessage>? {
        if (conversationId.isBlank() || specialId.isBlank()) {
            return null
        }
        val updatedMessages = conversationRepository.applyTransferUpdates(
            conversationId = conversationId,
            updates = listOf(
                TransferUpdateDirective(
                    refId = specialId,
                    status = TransferStatus.RECEIVED,
                ),
            ),
            selectedModel = selectedModel,
        )
        return updatedMessages.takeIf { it != currentMessages }
    }
}
