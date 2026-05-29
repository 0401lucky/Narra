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
    fun beginRetry_entersSendingStateAndClearsError() {
        val updated = ChatViewModelUiUpdates.beginRetry(
            current = ChatUiState(
                isSending = false,
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
        )

        assertFalse(updated.messages.isEmpty())
        assertFalse(updated.errorMessage != null)
        assertEquals(true, updated.isSending)
    }

    @Test
    fun applyStreamingFrame_updatesReasoningSteps() {
        val updated = ChatViewModelUiUpdates.applyStreamingFrame(
            current = ChatStreamingState(streamingMessageId = "m1"),
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

    @Test
    fun applyStreamingFrame_ignoresFrameForDifferentLoadingMessage() {
        val current = ChatStreamingState(
            streamingMessageId = "m1",
            streamingContent = "已有内容",
        )

        val updated = ChatViewModelUiUpdates.applyStreamingFrame(
            current = current,
            loadingMessageId = "m2",
            content = "迟到的旧帧",
            reasoning = "",
            reasoningSteps = emptyList(),
            parts = emptyList(),
        )

        assertEquals(current, updated)
    }
}
