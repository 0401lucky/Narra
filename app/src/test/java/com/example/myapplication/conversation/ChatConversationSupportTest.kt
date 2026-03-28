package com.example.myapplication.conversation

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.imageMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatConversationSupportTest {
    @Test
    fun buildUserMessageParts_mergesTextAndKeepsNonTextParts() {
        val parts = ChatConversationSupport.buildUserMessageParts(
            text = "你好",
            pendingParts = listOf(
                imageMessagePart(uri = "content://image"),
            ),
        )

        assertEquals(2, parts.size)
        assertEquals(ChatMessagePartType.TEXT, parts[0].type)
        assertEquals("你好", parts[0].text)
        assertEquals(ChatMessagePartType.IMAGE, parts[1].type)
    }

    @Test
    fun buildPromptAssemblyInput_usesLatestUserMessageText() {
        val input = ChatConversationSupport.buildPromptAssemblyInput(
            settings = AppSettings(
                assistants = listOf(
                    Assistant(id = "assistant-1", name = "陆宴清"),
                ),
                selectedAssistantId = "assistant-1",
            ),
            currentAssistant = null,
            currentConversations = listOf(
                Conversation(
                    id = "conv-1",
                    title = "会话",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = "assistant-1",
                ),
            ),
            fallbackAssistantId = "assistant-1",
            conversationId = "conv-1",
            requestMessages = listOf(
                ChatMessage(id = "m1", conversationId = "conv-1", role = MessageRole.USER, content = "第一句", createdAt = 1L),
                ChatMessage(id = "m2", conversationId = "conv-1", role = MessageRole.USER, content = "第二句", createdAt = 2L),
            ),
            nowProvider = { 10L },
        )

        assertEquals("第二句", input.userInputText)
        assertEquals("conv-1", input.conversation.id)
        assertEquals("assistant-1", input.assistant?.id)
    }

    @Test
    fun supportsImageGeneration_usesProviderAbilities() {
        val settings = AppSettings(
            providers = listOf(
                com.example.myapplication.model.ProviderSettings(
                    id = "provider-1",
                    name = "Provider",
                    baseUrl = "https://example.com/v1/",
                    apiKey = "key",
                    selectedModel = "image-model",
                    models = listOf(
                        com.example.myapplication.model.ModelInfo(
                            modelId = "image-model",
                            abilities = setOf(com.example.myapplication.model.ModelAbility.IMAGE_GENERATION),
                            abilitiesCustomized = true,
                        ),
                    ),
                ),
            ),
            selectedProviderId = "provider-1",
        )

        assertTrue(ChatConversationSupport.supportsImageGeneration(settings, "image-model"))
    }
}
