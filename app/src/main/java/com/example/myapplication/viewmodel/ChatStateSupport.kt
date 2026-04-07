package com.example.myapplication.viewmodel

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.DEFAULT_CONVERSATION_TITLE
import com.example.myapplication.model.normalizeChatMessageParts

object ChatStateSupport {
    fun applySettings(
        current: ChatUiState,
        settings: AppSettings,
        currentAssistant: Assistant?,
    ): ChatUiState {
        return current.copy(
            settings = settings,
            currentAssistant = currentAssistant,
        )
    }

    fun applyErrorMessage(
        current: ChatUiState,
        message: String,
    ): ChatUiState {
        return current.copy(errorMessage = message)
    }

    fun applyNoticeMessage(
        current: ChatUiState,
        message: String,
    ): ChatUiState {
        return current.copy(noticeMessage = message)
    }

    fun clearErrorMessage(current: ChatUiState): ChatUiState {
        return current.copy(errorMessage = null)
    }

    fun clearNoticeMessage(current: ChatUiState): ChatUiState {
        return current.copy(noticeMessage = null)
    }

    fun updateInput(current: ChatUiState, value: String): ChatUiState {
        return current.copy(
            input = value,
            errorMessage = null,
        )
    }

    fun addPendingParts(
        current: ChatUiState,
        normalizedParts: List<ChatMessagePart>,
    ): ChatUiState {
        return current.copy(
            pendingParts = normalizeChatMessageParts(current.pendingParts + normalizedParts),
            errorMessage = null,
        )
    }

    fun removePendingPart(
        current: ChatUiState,
        index: Int,
    ): ChatUiState {
        return current.copy(
            pendingParts = current.pendingParts.filterIndexed { i, _ -> i != index },
            errorMessage = null,
        )
    }

    fun clearPendingParts(current: ChatUiState): ChatUiState {
        return current.copy(
            pendingParts = emptyList(),
            errorMessage = null,
        )
    }

    fun beginTranslation(
        current: ChatUiState,
        sourceText: String,
        sourceLabel: String,
        modelName: String,
    ): ChatUiState {
        return current.copy(
            translation = TranslationUiState(
                isVisible = true,
                isLoading = true,
                sourceText = sourceText,
                sourceLabel = sourceLabel,
                modelName = modelName,
            ),
            errorMessage = null,
        )
    }

    fun completeTranslation(
        current: ChatUiState,
        translatedText: String,
    ): ChatUiState {
        return current.copy(
            translation = current.translation.copy(
                isVisible = true,
                isLoading = false,
                translatedText = translatedText,
            ),
        )
    }

    fun failTranslation(
        current: ChatUiState,
        errorMessage: String,
    ): ChatUiState {
        return current.copy(
            translation = TranslationUiState(),
            errorMessage = errorMessage,
        )
    }

    fun dismissTranslation(current: ChatUiState): ChatUiState {
        return current.copy(translation = TranslationUiState())
    }

    fun applyTranslationToInput(
        current: ChatUiState,
        translatedText: String,
        replace: Boolean,
    ): ChatUiState {
        val updatedInput = if (replace || current.input.isBlank()) {
            translatedText
        } else {
            current.input.trimEnd() + "\n\n" + translatedText
        }
        return current.copy(
            input = updatedInput,
            translation = current.translation.copy(isVisible = false),
        )
    }

    fun prepareTranslatedMessageInput(
        current: ChatUiState,
        translatedText: String,
    ): ChatUiState {
        return current.copy(
            input = translatedText,
            translation = current.translation.copy(isVisible = false),
        )
    }

    fun clearConversationSelection(
        current: ChatUiState,
        conversations: List<Conversation>,
    ): ChatUiState {
        return current.copy(
            conversations = conversations,
            currentConversationId = "",
            displayedConversationId = "",
            currentConversationTitle = DEFAULT_CONVERSATION_TITLE,
            messages = emptyList(),
            streamingMessageId = "",
            streamingContent = "",
            streamingReasoningContent = "",
            streamingParts = emptyList(),
            isConversationReady = false,
            hasConversationSummary = false,
            summaryCoveredMessageCount = 0,
            latestPromptDebugDump = "",
            contextGovernance = null,
        )
    }

    fun applyConversationSelection(
        current: ChatUiState,
        conversations: List<Conversation>,
        resolvedConversation: Conversation,
    ): ChatUiState {
        return current.copy(
            conversations = conversations,
            currentConversationId = resolvedConversation.id,
            currentConversationTitle = resolvedConversation.title,
            displayedConversationId = current.displayedConversationId.ifBlank { resolvedConversation.id },
        )
    }

    fun applyObservedMessages(
        current: ChatUiState,
        conversationId: String,
        messages: List<ChatMessage>,
    ): ChatUiState {
        if (current.currentConversationId != conversationId) {
            return current
        }
        return current.copy(
            displayedConversationId = conversationId,
            messages = messages,
            streamingMessageId = "",
            streamingContent = "",
            streamingReasoningContent = "",
            streamingParts = emptyList(),
            isConversationReady = true,
        )
    }

    fun updateRememberedMessageIds(
        current: ChatUiState,
        rememberedIds: Set<String>,
    ): ChatUiState {
        return current.copy(rememberedMessageIds = rememberedIds)
    }

    fun updateConversationSummaryStatus(
        current: ChatUiState,
        hasSummary: Boolean,
        coveredMessageCount: Int,
    ): ChatUiState {
        return current.copy(
            hasConversationSummary = hasSummary,
            summaryCoveredMessageCount = coveredMessageCount,
        )
    }

    fun selectAssistant(
        current: ChatUiState,
        currentAssistant: Assistant?,
    ): ChatUiState {
        return current.copy(currentAssistant = currentAssistant)
    }

    fun applyPromptDebugDump(
        current: ChatUiState,
        conversationId: String,
        debugDump: String,
    ): ChatUiState {
        if (current.currentConversationId != conversationId) {
            return current
        }
        return current.copy(latestPromptDebugDump = debugDump)
    }

    fun applyContextGovernance(
        current: ChatUiState,
        conversationId: String,
        snapshot: ContextGovernanceSnapshot?,
        debugDump: String,
    ): ChatUiState {
        if (current.currentConversationId != conversationId) {
            return current
        }
        return current.copy(
            hasConversationSummary = snapshot?.summaryCoveredMessageCount?.let { it > 0 } ?: current.hasConversationSummary,
            summaryCoveredMessageCount = snapshot?.summaryCoveredMessageCount ?: current.summaryCoveredMessageCount,
            latestPromptDebugDump = debugDump,
            contextGovernance = snapshot,
        )
    }

    fun applyChatSuggestions(
        current: ChatUiState,
        suggestions: List<String>,
        modelName: String,
    ): ChatUiState {
        return current.copy(
            chatSuggestions = suggestions,
            chatSuggestionsModelName = modelName,
        )
    }

    fun finishSending(
        current: ChatUiState,
        messages: List<ChatMessage>,
        errorMessage: String?,
    ): ChatUiState {
        return current.copy(
            messages = messages,
            streamingMessageId = "",
            streamingContent = "",
            streamingReasoningContent = "",
            streamingParts = emptyList(),
            isSending = false,
            errorMessage = errorMessage,
        )
    }

    fun activateConversation(
        current: ChatUiState,
        conversationId: String,
        title: String,
    ): ChatUiState {
        return current.copy(
            currentConversationId = conversationId,
            displayedConversationId = conversationId,
            currentConversationTitle = title,
            messages = emptyList(),
            streamingMessageId = "",
            streamingContent = "",
            streamingReasoningContent = "",
            streamingParts = emptyList(),
            input = "",
            pendingParts = emptyList(),
            isConversationReady = false,
            errorMessage = null,
            noticeMessage = null,
            chatSuggestions = emptyList(),
            chatSuggestionsModelName = "",
            hasConversationSummary = false,
            summaryCoveredMessageCount = 0,
            latestPromptDebugDump = "",
            contextGovernance = null,
            translation = TranslationUiState(),
        )
    }
}
