package com.example.myapplication.conversation

import com.example.myapplication.data.repository.TransferUpdateDirective
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.giftMessagePart
import com.example.myapplication.model.giftImageStatus
import com.example.myapplication.model.GiftImageStatus
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.model.withGiftImageGenerating
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
    fun applyTransferUpdates_supportsRejectedTransferStatus() {
        val original = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "c1",
                role = MessageRole.USER,
                content = "待收款",
                parts = listOf(
                    transferMessagePart(
                        id = "transfer-1",
                        direction = TransferDirection.USER_TO_ASSISTANT,
                        status = TransferStatus.PENDING,
                        counterparty = "陆宴清",
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
                    status = TransferStatus.REJECTED,
                ),
            ),
        )

        assertEquals(TransferStatus.REJECTED, updated.single().parts.single().specialStatus)
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
    fun applyGiftImageUpdate_updatesMatchingGiftPart() {
        val original = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "c1",
                role = MessageRole.USER,
                content = "礼物：黑胶唱片",
                parts = listOf(
                    giftMessagePart(
                        id = "gift-1",
                        target = "陆宴清",
                        item = "黑胶唱片",
                    ),
                ),
            ),
        )

        val updated = ConversationMessageTransforms.applyGiftImageUpdate(
            messages = original,
            specialId = "gift-1",
        ) { part ->
            part.withGiftImageGenerating()
        }

        assertEquals(GiftImageStatus.GENERATING, updated.single().parts.single().giftImageStatus())
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
            completedMessageCount = messages.size,
            summaryCoveredMessageCount = 3,
            recentWindow = 2,
        )

        assertEquals(listOf("m4", "m5"), trimmed.map { it.id })
    }

    @Test
    fun trimRequestMessagesWithSummary_keepsAllMessagesWithoutSummaryCoverage() {
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
            completedMessageCount = messages.size,
            summaryCoveredMessageCount = 0,
            recentWindow = 1,
        )

        assertEquals(messages, trimmed)
    }

    @Test
    fun trimRequestMessagesWithSummary_keepsAllMessagesWhenWindowIsLargerThanMessageCount() {
        val messages = (1..2).map { index ->
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
            completedMessageCount = messages.size,
            summaryCoveredMessageCount = 1,
            recentWindow = 4,
        )

        assertEquals(messages, trimmed)
    }

    @Test
    fun trimRequestMessagesWithSummary_keepsAllMessagesWhenSummaryCoverageIsStale() {
        val messages = (1..6).map { index ->
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
            completedMessageCount = messages.size,
            summaryCoveredMessageCount = 2,
            recentWindow = 2,
        )

        assertEquals(messages, trimmed)
    }

    @Test
    fun hasSufficientSummaryCoverage_returnsTrueOnlyWhenSummaryCoversOlderMessages() {
        assertEquals(
            true,
            ConversationMessageTransforms.hasSufficientSummaryCoverage(
                completedMessageCount = 10,
                recentWindow = 4,
                summaryCoveredMessageCount = 6,
            ),
        )
        assertEquals(
            false,
            ConversationMessageTransforms.hasSufficientSummaryCoverage(
                completedMessageCount = 10,
                recentWindow = 4,
                summaryCoveredMessageCount = 5,
            ),
        )
    }
}
