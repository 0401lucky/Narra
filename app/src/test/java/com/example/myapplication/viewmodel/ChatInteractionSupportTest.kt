package com.example.myapplication.viewmodel

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.textMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInteractionSupportTest {
    @Test
    fun resolveDraftTranslation_returnsErrorWhenInputBlank() {
        val result = ChatInteractionSupport.resolveDraftTranslation(
            ChatUiState(input = "   "),
        )

        val error = result as ChatTranslationResolution.Error
        assertEquals("请先输入要翻译的内容", error.message)
    }

    @Test
    fun resolveMessageTranslation_prefersPartsPlainTextAndUsesRoleLabel() {
        val result = ChatInteractionSupport.resolveMessageTranslation(
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "fallback",
                    parts = listOf(textMessagePart("你好")),
                    status = MessageStatus.COMPLETED,
                    createdAt = 1L,
                ),
            ),
            messageId = "m1",
        )

        val ready = result as ChatTranslationResolution.Ready
        assertEquals("你好", ready.request.sourceText)
        assertEquals("用户消息", ready.request.sourceLabel)
    }

    @Test
    fun validateConversationReadyForSend_returnsEnsureConversationErrorWhenIdMissing() {
        val result = ChatInteractionSupport.validateConversationReadyForSend(
            ChatUiState(
                currentConversationId = "",
                isConversationReady = true,
                settings = AppSettings(
                    baseUrl = "https://example.com/v1/",
                    apiKey = "key",
                    selectedModel = "deepseek-chat",
                ),
            ),
        )

        val error = result as ChatConversationValidationResult.Error
        assertEquals("会话初始化中，请稍后重试", error.message)
        assertTrue(error.shouldEnsureConversation)
    }

    @Test
    fun validateConversationReadyForSend_returnsConversationIdWhenReady() {
        val result = ChatInteractionSupport.validateConversationReadyForSend(
            ChatUiState(
                currentConversationId = "c1",
                isConversationReady = true,
                settings = AppSettings(
                    baseUrl = "https://example.com/v1/",
                    apiKey = "key",
                    selectedModel = "deepseek-chat",
                ),
            ),
        )

        val ready = result as ChatConversationValidationResult.Ready
        assertEquals("c1", ready.conversationId)
    }
}
