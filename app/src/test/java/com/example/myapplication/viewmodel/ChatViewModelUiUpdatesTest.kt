package com.example.myapplication.viewmodel

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
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
}
