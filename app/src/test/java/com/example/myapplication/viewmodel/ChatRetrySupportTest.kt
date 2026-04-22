package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.RoundTripInitialPersistence
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.imageMessagePart
import com.example.myapplication.model.textMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRetrySupportTest {
    @Test
    fun resolveRetry_forFailedMessage_buildsUpsertPlan() {
        val userMessage = ChatMessage(
            id = "user-1",
            conversationId = "c1",
            role = MessageRole.USER,
            content = "你好",
            status = MessageStatus.COMPLETED,
            createdAt = 1L,
        )
        val failedAssistant = ChatMessage(
            id = "assistant-1",
            conversationId = "c1",
            role = MessageRole.ASSISTANT,
            content = "发送失败",
            status = MessageStatus.ERROR,
            createdAt = 2L,
        )

        val result = ChatRetrySupport.resolveRetry(
            messageId = failedAssistant.id,
            currentMessages = listOf(userMessage, failedAssistant),
            imageGenerationEnabled = false,
        )

        val ready = result as ChatRetryResolution.Ready
        assertEquals(MessageStatus.LOADING, ready.loadingMessage.status)
        assertEquals("", ready.loadingMessage.content)
        assertEquals(listOf(MessageStatus.COMPLETED, MessageStatus.LOADING), ready.retryMessages.map { it.status })
        assertEquals(listOf(userMessage), ready.requestMessages)
        assertEquals("", ready.retryPrompt)
        assertTrue(ready.initialPersistence is RoundTripInitialPersistence.Upsert)
        assertEquals(
            listOf(userMessage.id, failedAssistant.id),
            ready.buildFinalMessages(failedAssistant.copy(status = MessageStatus.COMPLETED)).map { it.id },
        )
    }

    @Test
    fun resolveRetry_forCompletedAssistant_buildsReplaceSnapshotPlan() {
        val userMessage = ChatMessage(
            id = "user-1",
            conversationId = "c1",
            role = MessageRole.USER,
            content = "你好",
            status = MessageStatus.COMPLETED,
            createdAt = 1L,
        )
        val assistantMessage = ChatMessage(
            id = "assistant-1",
            conversationId = "c1",
            role = MessageRole.ASSISTANT,
            content = "旧回答",
            status = MessageStatus.COMPLETED,
            createdAt = 2L,
        )

        val result = ChatRetrySupport.resolveRetry(
            messageId = assistantMessage.id,
            currentMessages = listOf(userMessage, assistantMessage),
            imageGenerationEnabled = false,
        )

        val ready = result as ChatRetryResolution.Ready
        assertEquals(listOf(userMessage.id, assistantMessage.id), ready.retryMessages.map { it.id })
        assertEquals(MessageStatus.LOADING, ready.retryMessages.last().status)
        assertEquals(listOf(userMessage), ready.requestMessages)
        assertTrue(ready.initialPersistence is RoundTripInitialPersistence.ReplaceSnapshot)
        assertEquals(
            listOf("你好", "新回答"),
            ready.buildFinalMessages(assistantMessage.copy(content = "新回答")).map { it.content },
        )
    }

    @Test
    fun resolveRetry_forImageGenerationWithoutPrompt_returnsError() {
        val failedAssistant = ChatMessage(
            id = "assistant-1",
            conversationId = "c1",
            role = MessageRole.ASSISTANT,
            content = "发送失败",
            status = MessageStatus.ERROR,
            createdAt = 2L,
        )

        val result = ChatRetrySupport.resolveRetry(
            messageId = failedAssistant.id,
            currentMessages = listOf(failedAssistant),
            imageGenerationEnabled = true,
        )

        val error = result as ChatRetryResolution.Error
        assertEquals("未找到可重试的生图提示词", error.message)
    }

    @Test
    fun resolveRetry_forUnknownMessage_returnsNoOp() {
        val result = ChatRetrySupport.resolveRetry(
            messageId = "missing",
            currentMessages = emptyList(),
            imageGenerationEnabled = false,
        )

        assertTrue(result is ChatRetryResolution.NoOp)
    }

    @Test
    fun resolveRetry_forImageEditingPreservesReferenceImages() {
        val userMessage = ChatMessage(
            id = "user-1",
            conversationId = "c1",
            role = MessageRole.USER,
            content = "把它改成漫画风",
            status = MessageStatus.COMPLETED,
            createdAt = 1L,
            parts = listOf(
                textMessagePart("把它改成漫画风"),
                imageMessagePart(
                    uri = "content://image/1",
                    mimeType = "image/png",
                    fileName = "ref.png",
                ),
            ),
        )
        val failedAssistant = ChatMessage(
            id = "assistant-1",
            conversationId = "c1",
            role = MessageRole.ASSISTANT,
            content = "发送失败",
            status = MessageStatus.ERROR,
            createdAt = 2L,
        )

        val result = ChatRetrySupport.resolveRetry(
            messageId = failedAssistant.id,
            currentMessages = listOf(userMessage, failedAssistant),
            imageGenerationEnabled = true,
        )

        val ready = result as ChatRetryResolution.Ready
        val getter = ready.javaClass.getMethod("getRetryImageAttachments")
        @Suppress("UNCHECKED_CAST")
        val retryImages = getter.invoke(ready) as List<com.example.myapplication.model.MessageAttachment>
        assertEquals(1, retryImages.size)
        assertEquals("ref.png", retryImages.single().fileName)
        assertEquals("把它改成漫画风", ready.retryPrompt)
    }
}
