package com.example.myapplication.viewmodel

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID

data class ObservedSettingsUpdate(
    val newAssistantId: String,
    val activeAssistant: Assistant?,
    val shouldSwitchAssistant: Boolean,
)

data class ObservedConversationCollection(
    val resolvedConversation: Conversation?,
    val shouldActivateConversation: Boolean,
)

object ChatObservationSupport {
    fun resolveObservedSettings(
        currentAssistantId: String,
        settings: AppSettings,
    ): ObservedSettingsUpdate {
        val newAssistantId = settings.selectedAssistantId.ifBlank { DEFAULT_ASSISTANT_ID }
        return ObservedSettingsUpdate(
            newAssistantId = newAssistantId,
            activeAssistant = settings.activeAssistant(),
            shouldSwitchAssistant = newAssistantId != currentAssistantId,
        )
    }

    fun resolveConversationCollection(
        conversations: List<Conversation>,
        currentConversationId: String?,
    ): ObservedConversationCollection {
        val resolvedConversation = conversations.firstOrNull { it.id == currentConversationId }
            ?: conversations.firstOrNull()
        return ObservedConversationCollection(
            resolvedConversation = resolvedConversation,
            shouldActivateConversation = resolvedConversation != null && resolvedConversation.id != currentConversationId,
        )
    }

    fun shouldIgnoreObservedMessages(
        current: ChatUiState,
        conversationId: String,
    ): Boolean {
        return current.isSending && current.currentConversationId == conversationId
    }
}
