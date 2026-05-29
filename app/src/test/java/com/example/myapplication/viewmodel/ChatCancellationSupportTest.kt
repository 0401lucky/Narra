package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.StreamedAssistantPayload
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCancellationSupportTest {
    private fun loading(): ChatMessage = ChatMessage(
        id = "assistant-1",
        conversationId = "c1",
        role = MessageRole.ASSISTANT,
        content = "",
        status = MessageStatus.LOADING,
        createdAt = 1L,
    )

    @Test
    fun buildCancelledAssistantMessage_keepsPartialContentAsCompleted() {
        val payload = StreamedAssistantPayload(
            content = "已生成的部分内容",
            reasoning = "推理过程",
            reasoningSteps = emptyList(),
            parts = listOf(
                ChatMessagePart(type = ChatMessagePartType.TEXT, text = "已生成的部分内容"),
            ),
            citations = listOf(MessageCitation(title = "来源", url = "https://example.com")),
        )

        val result = buildCancelledAssistantMessage(payload, loading())

        assertEquals(MessageStatus.COMPLETED, result.status)
        assertEquals("已生成的部分内容", result.content)
        assertEquals("推理过程", result.reasoningContent)
        assertEquals(payload.parts, result.parts)
        assertEquals(payload.citations, result.citations)
    }

    @Test
    fun buildCancelledAssistantMessage_usesPartsMirrorWhenContentBlank() {
        val payload = StreamedAssistantPayload(
            content = "",
            reasoning = "",
            reasoningSteps = emptyList(),
            parts = listOf(
                ChatMessagePart(type = ChatMessagePartType.TEXT, text = "镜像文本内容"),
            ),
            citations = emptyList(),
        )

        val result = buildCancelledAssistantMessage(payload, loading())

        assertEquals(MessageStatus.COMPLETED, result.status)
        assertEquals("镜像文本内容", result.content)
    }

    @Test
    fun buildCancelledAssistantMessage_fallsBackToCancelledWhenEmpty() {
        val payload = StreamedAssistantPayload(
            content = "",
            reasoning = "",
            reasoningSteps = emptyList(),
            parts = emptyList(),
            citations = emptyList(),
        )

        val result = buildCancelledAssistantMessage(payload, loading())

        assertEquals(MessageStatus.COMPLETED, result.status)
        assertEquals("已取消", result.content)
    }
}
