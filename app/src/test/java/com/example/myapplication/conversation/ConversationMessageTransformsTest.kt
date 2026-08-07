package com.example.myapplication.conversation

import com.example.myapplication.data.repository.TransferUpdateDirective
import com.example.myapplication.model.AiPhotoImageStatus
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.aiPhotoImageStatus
import com.example.myapplication.model.aiPhotoImageUri
import com.example.myapplication.model.aiPhotoMessagePart
import com.example.myapplication.model.fileMessagePart
import com.example.myapplication.model.giftMessagePart
import com.example.myapplication.model.giftImageStatus
import com.example.myapplication.model.GiftImageStatus
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.model.withAiPhotoImageSuccess
import com.example.myapplication.model.withGiftImageGenerating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMessageTransformsTest {
    @Test
    fun deleteMessageAndClearReferences_removesTargetAndClearsMatchingReferences() {
        val target = ChatMessage(
            id = "target",
            conversationId = "c1",
            role = MessageRole.ASSISTANT,
            content = "会映射成多个 RP 气泡",
            parts = listOf(
                textMessagePart(text = "第一段"),
                textMessagePart(text = "第二段"),
            ),
            createdAt = 2L,
        )
        val topLevelReply = ChatMessage(
            id = "reply-top",
            conversationId = "c1",
            role = MessageRole.USER,
            content = "顶层引用",
            replyToMessageId = target.id,
            replyToPreview = "预览",
            replyToSpeakerName = "陆宴清",
            attachments = listOf(
                MessageAttachment(
                    uri = "content://attachment/1",
                    mimeType = "image/png",
                    fileName = "图片.png",
                ),
            ),
            createdAt = 3L,
        )
        val partReply = ChatMessage(
            id = "reply-part",
            conversationId = "c1",
            role = MessageRole.ASSISTANT,
            content = "分段引用",
            parts = listOf(
                textMessagePart(
                    text = "命中引用",
                    replyToMessageId = target.id,
                    replyToPreview = "分段预览",
                    replyToSpeakerName = "林晚",
                ),
                fileMessagePart(
                    uri = "content://attachment/2",
                    mimeType = "text/plain",
                    fileName = "记录.txt",
                ),
            ),
            createdAt = 4L,
        )
        val unrelatedReply = ChatMessage(
            id = "unrelated",
            conversationId = "c1",
            role = MessageRole.USER,
            content = "其他引用",
            replyToMessageId = "other",
            replyToPreview = "保持",
            replyToSpeakerName = "其他人",
            parts = listOf(
                textMessagePart(
                    text = "保持分段引用",
                    replyToMessageId = "other",
                    replyToPreview = "保持分段预览",
                    replyToSpeakerName = "其他人",
                ),
            ),
            createdAt = 1L,
        )

        val result = ConversationMessageTransforms.deleteMessageAndClearReferences(
            messages = listOf(unrelatedReply, target, topLevelReply, partReply),
            targetMessageId = target.id,
        )

        val deleted = result as MessageDeletionResult.Deleted
        assertEquals(
            listOf("unrelated", "reply-top", "reply-part"),
            deleted.updatedMessages.map(ChatMessage::id),
        )
        assertSame(unrelatedReply, deleted.updatedMessages[0])
        assertEquals(
            topLevelReply.copy(
                replyToMessageId = "",
                replyToPreview = "",
                replyToSpeakerName = "",
            ),
            deleted.updatedMessages[1],
        )
        assertEquals(topLevelReply.attachments, deleted.updatedMessages[1].attachments)
        assertEquals("", deleted.updatedMessages[2].parts.first().replyToMessageId)
        assertEquals("", deleted.updatedMessages[2].parts.first().replyToPreview)
        assertEquals("", deleted.updatedMessages[2].parts.first().replyToSpeakerName)
        assertEquals(partReply.parts[1], deleted.updatedMessages[2].parts[1])
    }

    @Test
    fun deleteMessageAndClearReferences_returnsNotFoundWithoutChangingMessages() {
        val original = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "c1",
                role = MessageRole.USER,
                content = "保留",
                replyToMessageId = "other",
                replyToPreview = "预览",
                replyToSpeakerName = "角色",
            ),
        )

        val result = ConversationMessageTransforms.deleteMessageAndClearReferences(
            messages = original,
            targetMessageId = "missing",
        )

        assertTrue(result === MessageDeletionResult.NotFound)
        assertEquals("other", original.single().replyToMessageId)
    }

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
    fun applyAiPhotoUpdate_updatesMatchingPhotoAction() {
        val original = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "c1",
                role = MessageRole.ASSISTANT,
                content = "照片",
                parts = listOf(
                    aiPhotoMessagePart(
                        description = "窗边的阳光",
                        id = "photo-1",
                    ),
                ),
            ),
        )

        val updated = ConversationMessageTransforms.applyAiPhotoUpdate(
            messages = original,
            messageId = "m1",
            actionId = "photo-1",
        ) { part ->
            part.withAiPhotoImageSuccess(
                imageUri = "/tmp/photo.png",
                mimeType = "image/png",
                fileName = "photo.png",
            )
        }
        val updatedPart = updated.single().parts.single()

        assertEquals(AiPhotoImageStatus.READY, updatedPart.aiPhotoImageStatus())
        assertEquals("/tmp/photo.png", updatedPart.aiPhotoImageUri())
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
