package com.example.myapplication.conversation

import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.model.ChatMessage

sealed interface RoundTripInitialPersistence {
    data class Append(
        val messages: List<ChatMessage>,
    ) : RoundTripInitialPersistence

    data class Upsert(
        val messages: List<ChatMessage>,
    ) : RoundTripInitialPersistence

    data class ReplaceSnapshot(
        val messages: List<ChatMessage>,
    ) : RoundTripInitialPersistence
}

suspend fun ConversationRepository.persistInitialRoundTripState(
    conversationId: String,
    selectedModel: String,
    persistence: RoundTripInitialPersistence,
) {
    when (persistence) {
        is RoundTripInitialPersistence.Append -> {
            appendMessages(
                conversationId = conversationId,
                messages = persistence.messages,
                selectedModel = selectedModel,
            )
        }

        is RoundTripInitialPersistence.Upsert -> {
            upsertMessages(
                conversationId = conversationId,
                messages = persistence.messages,
                selectedModel = selectedModel,
            )
        }

        is RoundTripInitialPersistence.ReplaceSnapshot -> {
            replaceConversationSnapshot(
                conversationId = conversationId,
                messages = persistence.messages,
                selectedModel = selectedModel,
            )
        }
    }
}
