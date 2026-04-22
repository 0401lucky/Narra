package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.RoundTripInitialPersistence
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.model.toMessageAttachments

sealed interface ChatRetryResolution {
    data object NoOp : ChatRetryResolution

    data class Error(
        val message: String,
    ) : ChatRetryResolution

    class Ready(
        val loadingMessage: ChatMessage,
        val retryMessages: List<ChatMessage>,
        val requestMessages: List<ChatMessage>,
        val retryPrompt: String,
        val retryImageAttachments: List<com.example.myapplication.model.MessageAttachment>,
        val initialPersistence: RoundTripInitialPersistence,
        private val finalMessagesBuilder: (ChatMessage) -> List<ChatMessage>,
    ) : ChatRetryResolution {
        fun buildFinalMessages(completedAssistant: ChatMessage): List<ChatMessage> {
            return finalMessagesBuilder(completedAssistant)
        }
    }
}

object ChatRetrySupport {
    fun resolveRetry(
        messageId: String,
        currentMessages: List<ChatMessage>,
        imageGenerationEnabled: Boolean,
    ): ChatRetryResolution {
        val failedIndex = currentMessages.indexOfFirst { it.id == messageId && it.status == MessageStatus.ERROR }
        if (failedIndex != -1) {
            val loadingMessage = currentMessages[failedIndex].toRetryLoadingMessage()
            val requestMessages = buildRequestMessages(
                messages = currentMessages,
                endExclusive = failedIndex,
            )
            val retryPrompt = resolveRetryPrompt(
                requestMessages = requestMessages,
                imageGenerationEnabled = imageGenerationEnabled,
            ) ?: return ChatRetryResolution.Error("未找到可重试的生图提示词")
            return ChatRetryResolution.Ready(
                loadingMessage = loadingMessage,
                retryMessages = currentMessages.toMutableList().apply {
                    this[failedIndex] = loadingMessage
                },
                requestMessages = requestMessages,
                retryPrompt = retryPrompt,
                retryImageAttachments = resolveRetryImageAttachments(
                    requestMessages = requestMessages,
                    imageGenerationEnabled = imageGenerationEnabled,
                ),
                initialPersistence = RoundTripInitialPersistence.Upsert(
                    messages = listOf(loadingMessage),
                ),
                finalMessagesBuilder = { completedAssistant ->
                    currentMessages.toMutableList().apply {
                        this[failedIndex] = completedAssistant
                    }
                },
            )
        }

        val assistantIndex = currentMessages.indexOfFirst {
            it.id == messageId &&
                it.role == MessageRole.ASSISTANT &&
                it.status == MessageStatus.COMPLETED
        }
        if (assistantIndex == -1) {
            return ChatRetryResolution.NoOp
        }

        val loadingMessage = currentMessages[assistantIndex].toRetryLoadingMessage()
        val retryMessages = currentMessages.take(assistantIndex) + loadingMessage
        val requestMessages = buildRequestMessages(
            messages = currentMessages,
            endExclusive = assistantIndex,
        )
        val retryPrompt = resolveRetryPrompt(
            requestMessages = requestMessages,
            imageGenerationEnabled = imageGenerationEnabled,
        ) ?: return ChatRetryResolution.Error("未找到可重试的生图提示词")
        return ChatRetryResolution.Ready(
            loadingMessage = loadingMessage,
            retryMessages = retryMessages,
            requestMessages = requestMessages,
            retryPrompt = retryPrompt,
            retryImageAttachments = resolveRetryImageAttachments(
                requestMessages = requestMessages,
                imageGenerationEnabled = imageGenerationEnabled,
            ),
            initialPersistence = RoundTripInitialPersistence.ReplaceSnapshot(
                messages = retryMessages,
            ),
            finalMessagesBuilder = { completedAssistant ->
                retryMessages.dropLast(1) + completedAssistant
            },
        )
    }

    private fun buildRequestMessages(
        messages: List<ChatMessage>,
        endExclusive: Int,
    ): List<ChatMessage> {
        return messages
            .take(endExclusive)
            .filter { it.status == MessageStatus.COMPLETED && it.hasSendableContent() }
    }

    private fun resolveRetryPrompt(
        requestMessages: List<ChatMessage>,
        imageGenerationEnabled: Boolean,
    ): String? {
        if (!imageGenerationEnabled) {
            return ""
        }
        return requestMessages.lastOrNull { it.role == MessageRole.USER }
            ?.content
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun resolveRetryImageAttachments(
        requestMessages: List<ChatMessage>,
        imageGenerationEnabled: Boolean,
    ): List<com.example.myapplication.model.MessageAttachment> {
        if (!imageGenerationEnabled) {
            return emptyList()
        }
        val lastUserMessage = requestMessages.lastOrNull { it.role == MessageRole.USER } ?: return emptyList()
        return if (lastUserMessage.parts.isNotEmpty()) {
            lastUserMessage.parts.toMessageAttachments()
        } else {
            lastUserMessage.attachments
        }
    }

    private fun ChatMessage.toRetryLoadingMessage(): ChatMessage {
        return copy(
            content = "",
            status = MessageStatus.LOADING,
            reasoningContent = "",
            reasoningSteps = emptyList(),
            attachments = emptyList(),
            parts = emptyList(),
        )
    }
}
