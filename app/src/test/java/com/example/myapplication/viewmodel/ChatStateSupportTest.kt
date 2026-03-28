package com.example.myapplication.viewmodel

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStateSupportTest {
    @Test
    fun finishSending_resetsStreamingAndSendingState() {
        val updated = ChatStateSupport.finishSending(
            current = ChatUiState(
                streamingMessageId = "m1",
                streamingContent = "流式内容",
                streamingReasoningContent = "思考中",
                isSending = true,
            ),
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "完成回复",
                    createdAt = 1L,
                ),
            ),
            errorMessage = "发送失败",
        )

        assertFalse(updated.isSending)
        assertEquals("", updated.streamingMessageId)
        assertEquals("", updated.streamingContent)
        assertEquals("", updated.streamingReasoningContent)
        assertEquals("发送失败", updated.errorMessage)
        assertEquals(1, updated.messages.size)
    }

    @Test
    fun activateConversation_resetsConversationScopedUiState() {
        val updated = ChatStateSupport.activateConversation(
            current = ChatUiState(
                currentConversationId = "old",
                displayedConversationId = "old",
                currentConversationTitle = "旧标题",
                input = "旧输入",
                pendingParts = listOf(),
                isConversationReady = true,
                isSending = true,
                noticeMessage = "旧提示",
                chatSuggestions = listOf("建议"),
                latestPromptDebugDump = "旧调试",
                translation = TranslationUiState(isVisible = true, translatedText = "译文"),
            ),
            conversationId = "new",
            title = "新标题",
        )

        assertEquals("new", updated.currentConversationId)
        assertEquals("new", updated.displayedConversationId)
        assertEquals("新标题", updated.currentConversationTitle)
        assertEquals("", updated.input)
        assertTrue(updated.pendingParts.isEmpty())
        assertFalse(updated.isConversationReady)
        assertEquals(null, updated.noticeMessage)
        assertTrue(updated.chatSuggestions.isEmpty())
        assertEquals("", updated.latestPromptDebugDump)
        assertFalse(updated.translation.isVisible)
    }

    @Test
    fun beginTranslation_marksSheetVisibleAndLoading() {
        val updated = ChatStateSupport.beginTranslation(
            current = ChatUiState(errorMessage = "旧错误"),
            sourceText = "hello",
            sourceLabel = "输入框内容",
            modelName = "translate-model",
        )

        assertTrue(updated.translation.isVisible)
        assertTrue(updated.translation.isLoading)
        assertEquals("hello", updated.translation.sourceText)
        assertEquals("输入框内容", updated.translation.sourceLabel)
        assertEquals("translate-model", updated.translation.modelName)
        assertEquals(null, updated.errorMessage)
    }

    @Test
    fun applyConversationSelection_updatesConversationPointers() {
        val resolvedConversation = Conversation(
            id = "c2",
            title = "会话二",
            model = "model-b",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val updated = ChatStateSupport.applyConversationSelection(
            current = ChatUiState(displayedConversationId = "c1"),
            conversations = listOf(resolvedConversation),
            resolvedConversation = resolvedConversation,
        )

        assertEquals("c2", updated.currentConversationId)
        assertEquals("会话二", updated.currentConversationTitle)
        assertEquals("c1", updated.displayedConversationId)
        assertEquals(1, updated.conversations.size)
    }

    @Test
    fun applySettings_updatesSettingsAndAssistant() {
        val assistant = Assistant(id = "assistant-1", name = "测试助手")
        val updated = ChatStateSupport.applySettings(
            current = ChatUiState(),
            settings = com.example.myapplication.model.AppSettings(selectedModel = "chat-model"),
            currentAssistant = assistant,
        )

        assertEquals("chat-model", updated.settings.selectedModel)
        assertEquals("assistant-1", updated.currentAssistant?.id)
    }
}
