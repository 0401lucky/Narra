package com.example.myapplication.viewmodel

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_CONVERSATION_TITLE

object ChatViewModelUiUpdates {
    fun applyObservedSettings(
        current: ChatUiState,
        settings: AppSettings,
        currentAssistant: Assistant?,
    ): ChatUiState {
        return current.copy(
            settings = settings,
            currentAssistant = currentAssistant,
        )
    }

    fun clearConversationCollection(
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
        )
    }

    fun applyConversationCollection(
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

    fun beginRoundTrip(
        current: ChatUiState,
        messages: List<ChatMessage>,
        loadingMessageId: String,
        nextInput: String,
        nextPendingParts: List<ChatMessagePart>,
    ): ChatUiState {
        return current.copy(
            messages = messages,
            streamingMessageId = loadingMessageId,
            streamingContent = "",
            streamingReasoningContent = "",
            streamingParts = emptyList(),
            input = nextInput,
            pendingParts = nextPendingParts,
            isSending = true,
            errorMessage = null,
        )
    }

    fun applyStreamingFrame(
        current: ChatUiState,
        conversationId: String,
        loadingMessageId: String,
        content: String,
        reasoning: String,
        parts: List<ChatMessagePart>,
    ): ChatUiState {
        if (current.currentConversationId != conversationId || current.streamingMessageId != loadingMessageId) {
            return current
        }
        return current.copy(
            streamingContent = content,
            streamingReasoningContent = reasoning,
            streamingParts = parts,
        )
    }

    fun applyTitleGeneratedNotice(
        current: ChatUiState,
        titleModel: String,
    ): ChatUiState {
        return current.copy(noticeMessage = "标题已由 $titleModel 生成")
    }

    fun applyTransferReceiptNotice(
        current: ChatUiState,
        messages: List<ChatMessage>,
    ): ChatUiState {
        return current.copy(
            messages = messages,
            noticeMessage = "已收款",
        )
    }

    fun beginRetry(
        current: ChatUiState,
        messages: List<ChatMessage>,
        loadingMessageId: String,
    ): ChatUiState {
        return current.copy(
            messages = messages,
            streamingMessageId = loadingMessageId,
            streamingContent = "",
            streamingReasoningContent = "",
            streamingParts = emptyList(),
            isSending = true,
            errorMessage = null,
        )
    }
}
