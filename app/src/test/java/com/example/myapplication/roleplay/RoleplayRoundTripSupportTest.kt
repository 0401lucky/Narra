package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.textMessagePart
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplayRoundTripSupportTest {
    @Test
    fun prepareOutgoingRoundTrip_ordersAssistantAfterUserWhenClockDoesNotAdvance() {
        val prepared = RoleplayRoundTripSupport.prepareOutgoingRoundTrip(
            baseMessages = emptyList(),
            conversationId = "conv-1",
            userParts = listOf(textMessagePart("先发一句")),
            replyToMessageId = "",
            replyToPreview = "",
            replyToSpeakerName = "",
            selectedModel = "chat-model",
            roleplayOutputFormat = RoleplayOutputFormat.LONGFORM,
            roleplayInteractionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
            nowProvider = { 100L },
            messageIdProvider = idProviderOf("z-user", "a-assistant"),
        )

        assertEquals(100L, prepared.userMessage.createdAt)
        assertEquals(101L, prepared.loadingMessage.createdAt)
        assertEquals(
            listOf("z-user", "a-assistant"),
            prepared.initialMessages
                .sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
                .map { it.id },
        )
    }

    @Test
    fun buildAssistantLoadingMessage_canBePlacedAfterLatestTimelineMessage() {
        val loading = RoleplayRoundTripSupport.buildAssistantLoadingMessage(
            conversationId = "conv-1",
            nowProvider = { 200L },
            messageIdProvider = { "a-assistant" },
            modelName = "chat-model",
            roleplayOutputFormat = RoleplayOutputFormat.LONGFORM,
            roleplayInteractionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
            afterCreatedAt = 300L,
        )

        assertEquals(301L, loading.createdAt)
    }

    private fun idProviderOf(vararg ids: String): () -> String {
        val values = ArrayDeque(ids.toList())
        return {
            values.removeFirstOrNull() ?: error("测试消息 ID 不足")
        }
    }
}
