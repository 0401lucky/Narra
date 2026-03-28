package com.example.myapplication.viewmodel

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.toPlainText

data class ChatTranslationRequest(
    val sourceText: String,
    val sourceLabel: String,
)

sealed interface ChatTranslationResolution {
    data object NoOp : ChatTranslationResolution

    data class Error(
        val message: String,
    ) : ChatTranslationResolution

    data class Ready(
        val request: ChatTranslationRequest,
    ) : ChatTranslationResolution
}

sealed interface ChatConversationValidationResult {
    data class Error(
        val message: String,
        val shouldEnsureConversation: Boolean = false,
    ) : ChatConversationValidationResult

    data class Ready(
        val conversationId: String,
    ) : ChatConversationValidationResult
}

object ChatInteractionSupport {
    fun resolveDraftTranslation(state: ChatUiState): ChatTranslationResolution {
        val sourceText = state.input.trim()
        if (sourceText.isBlank()) {
            return ChatTranslationResolution.Error("请先输入要翻译的内容")
        }
        return ChatTranslationResolution.Ready(
            request = ChatTranslationRequest(
                sourceText = sourceText,
                sourceLabel = "输入框内容",
            ),
        )
    }

    fun resolveMessageTranslation(
        messages: List<ChatMessage>,
        messageId: String,
    ): ChatTranslationResolution {
        val message = messages.firstOrNull { it.id == messageId } ?: return ChatTranslationResolution.NoOp
        val sourceText = message.parts.toPlainText()
            .ifBlank { message.content.trim() }
        if (sourceText.isBlank()) {
            return ChatTranslationResolution.Error("当前消息没有可翻译的文本内容")
        }
        return ChatTranslationResolution.Ready(
            request = ChatTranslationRequest(
                sourceText = sourceText,
                sourceLabel = if (message.role == MessageRole.USER) "用户消息" else "助手回复",
            ),
        )
    }

    fun validateConversationReadyForSend(
        state: ChatUiState,
    ): ChatConversationValidationResult {
        if (!state.settings.hasRequiredConfig()) {
            return ChatConversationValidationResult.Error("请先前往设置页完成配置")
        }
        if (!state.isConversationReady) {
            return ChatConversationValidationResult.Error("会话切换中，请稍后再发送")
        }
        val conversationId = state.currentConversationId
        if (conversationId.isBlank()) {
            return ChatConversationValidationResult.Error(
                message = "会话初始化中，请稍后重试",
                shouldEnsureConversation = true,
            )
        }
        return ChatConversationValidationResult.Ready(conversationId)
    }
}
