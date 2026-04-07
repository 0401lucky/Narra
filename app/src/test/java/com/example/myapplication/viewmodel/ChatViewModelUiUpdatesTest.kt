package com.example.myapplication.viewmodel

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ChatReasoningStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatViewModelUiUpdatesTest {
    @Test
    fun applyTransferReceiptNotice_updatesMessagesAndNotice() {
        val updated = ChatViewModelUiUpdates.applyTransferReceiptNotice(
            current = ChatUiState(noticeMessage = null),
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "已收款",
                    createdAt = 1L,
                ),
            ),
        )

        assertEquals(1, updated.messages.size)
        assertEquals("已收款", updated.noticeMessage)
    }

    @Test
    fun beginRetry_entersSendingStateAndResetsStreamingBuffers() {
        val updated = ChatViewModelUiUpdates.beginRetry(
            current = ChatUiState(
                isSending = false,
                streamingContent = "old",
                streamingReasoningContent = "reason",
                errorMessage = "old error",
            ),
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "",
                    createdAt = 1L,
                ),
            ),
            loadingMessageId = "m1",
        )

        assertFalse(updated.messages.isEmpty())
        assertEquals("m1", updated.streamingMessageId)
        assertEquals("", updated.streamingContent)
        assertEquals("", updated.streamingReasoningContent)
        assertFalse(updated.errorMessage != null)
        assertEquals(true, updated.isSending)
    }

    @Test
    fun applyStreamingFrame_updatesReasoningSteps() {
        val updated = ChatViewModelUiUpdates.applyStreamingFrame(
            current = ChatUiState(
                currentConversationId = "c1",
                streamingMessageId = "m1",
            ),
            conversationId = "c1",
            loadingMessageId = "m1",
            content = "",
            reasoning = "**分析目标**\n先看输入。",
            reasoningSteps = listOf(
                ChatReasoningStep(
                    id = "reasoning-1",
                    text = "**分析目标**\n先看输入。",
                    createdAt = 10L,
                    finishedAt = null,
                ),
            ),
            parts = emptyList(),
        )

        assertEquals(1, updated.streamingReasoningSteps.size)
        assertEquals("reasoning-1", updated.streamingReasoningSteps.single().id)
        assertEquals("**分析目标**\n先看输入。", updated.streamingReasoningContent)
    }
}
