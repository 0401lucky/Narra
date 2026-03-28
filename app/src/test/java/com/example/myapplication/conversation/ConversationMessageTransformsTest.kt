package com.example.myapplication.conversation

import com.example.myapplication.data.repository.TransferUpdateDirective
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.transferMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ConversationMessageTransformsTest {
    @Test
    fun applyTransferUpdates_updatesMatchingTransferPart() {
        val original = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "c1",
                role = MessageRole.ASSISTANT,
                content = "待收款",
                parts = listOf(
                    transferMessagePart(
                        id = "transfer-1",
                        direction = TransferDirection.ASSISTANT_TO_USER,
                        status = TransferStatus.PENDING,
                        counterparty = "用户",
                        amount = "88.00",
                    ),
                ),
            ),
        )

        val updated = ConversationMessageTransforms.applyTransferUpdates(
            messages = original,
            updates = listOf(
                TransferUpdateDirective(
                    refId = "transfer-1",
                    status = TransferStatus.RECEIVED,
                ),
            ),
        )

        assertEquals(TransferStatus.RECEIVED, updated.single().parts.single().specialStatus)
    }

    @Test
    fun applyTransferUpdates_returnsSameListWhenNothingMatches() {
        val original = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "c1",
                role = MessageRole.ASSISTANT,
                content = "普通文本",
            ),
        )

        val updated = ConversationMessageTransforms.applyTransferUpdates(
            messages = original,
            updates = listOf(
                TransferUpdateDirective(
                    refId = "missing",
                    status = TransferStatus.RECEIVED,
                ),
            ),
        )

        assertSame(original, updated)
    }

    @Test
    fun trimRequestMessagesWithSummary_keepsRecentWindowWhenSummaryExists() {
        val messages = (1..5).map { index ->
            ChatMessage(
                id = "m$index",
                conversationId = "c1",
                role = MessageRole.USER,
                content = "消息$index",
                createdAt = index.toLong(),
            )
        }

        val trimmed = ConversationMessageTransforms.trimRequestMessagesWithSummary(
            requestMessages = messages,
            recentWindow = 2,
            hasSummary = true,
        )

        assertEquals(listOf("m4", "m5"), trimmed.map { it.id })
    }

    @Test
    fun trimRequestMessagesWithSummary_keepsAllMessagesWithoutSummary() {
        val messages = (1..3).map { index ->
            ChatMessage(
                id = "m$index",
                conversationId = "c1",
                role = MessageRole.USER,
                content = "消息$index",
                createdAt = index.toLong(),
            )
        }

        val trimmed = ConversationMessageTransforms.trimRequestMessagesWithSummary(
            requestMessages = messages,
            recentWindow = 1,
            hasSummary = false,
        )

        assertEquals(messages, trimmed)
    }
}
