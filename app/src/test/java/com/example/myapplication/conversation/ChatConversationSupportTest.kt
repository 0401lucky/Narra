package com.example.myapplication.conversation

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.imageMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun buildConversationExcerpt_keepsLatestMessagesWithinLimit() {
        val expected = listOf(
            "用户: 最新进展一",
            "助手: 最新进展二",
        ).joinToString(separator = "\n")

        val excerpt = ChatConversationSupport.buildConversationExcerpt(
            messages = listOf(
                ChatMessage(id = "m1", conversationId = "conv-1", role = MessageRole.USER, content = "最早铺垫一", createdAt = 1L),
                ChatMessage(id = "m2", conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "最早铺垫二", createdAt = 2L),
                ChatMessage(id = "m3", conversationId = "conv-1", role = MessageRole.USER, content = "最新进展一", createdAt = 3L),
                ChatMessage(id = "m4", conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "最新进展二", createdAt = 4L),
            ),
            maxLength = expected.length,
            perMessageLimit = 200,
        )

        assertEquals(expected, excerpt)
        assertFalse(excerpt.contains("最早铺垫"))
    }

    @Test
    fun buildConversationExcerpt_keepsLatestTailWhenSingleMessageTooLong() {
        val excerpt = ChatConversationSupport.buildConversationExcerpt(
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "conv-1",
                    role = MessageRole.USER,
                    content = "前情铺垫很长很长，真正关键的是最后一句：周六聚会已经结束，现在已经正式交往了",
                    createdAt = 1L,
                ),
            ),
            maxLength = 20,
            perMessageLimit = 200,
        )

        assertTrue(excerpt.startsWith("用户: …"))
        assertTrue(excerpt.contains("现在已经正式交往了"))
        assertFalse(excerpt.contains("前情铺垫很长"))
    }

    @Test
    fun supportsImageGeneration_usesProviderAbilities() {
        val settings = AppSettings(
            providers = listOf(
                ProviderSettings(
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

    @Test
    fun validateOutgoingParts_allowsImageForEditableImageGenerationModel() {
        val result = ChatConversationSupport.validateOutgoingParts(
            settings = settingsWithSelectedModel("gpt-image-1"),
            userParts = ChatConversationSupport.buildUserMessageParts(
                text = "把这张图改成复古胶片风",
                pendingParts = listOf(imageMessagePart(uri = "content://image-1")),
            ),
        )

        assertEquals(null, result)
    }

    @Test
    fun validateOutgoingParts_requiresPromptForEditableImageGenerationModel() {
        val result = ChatConversationSupport.validateOutgoingParts(
            settings = settingsWithSelectedModel("gpt-image-1"),
            userParts = ChatConversationSupport.buildUserMessageParts(
                text = "",
                pendingParts = listOf(imageMessagePart(uri = "content://image-1")),
            ),
        )

        assertEquals("当前模型支持参考图改图，请先输入修改要求后再发送图片", result)
    }

    @Test
    fun prepareUserEdit_rewindsConversationAndRestoresPendingParts() {
        val prepared = ChatConversationSupport.prepareUserEdit(
            currentMessages = listOf(
                ChatMessage(
                    id = "user-1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "第一句",
                    status = MessageStatus.COMPLETED,
                    createdAt = 1L,
                ),
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "旧回复",
                    status = MessageStatus.COMPLETED,
                    createdAt = 2L,
                ),
                ChatMessage(
                    id = "user-2",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "图片已发送",
                    status = MessageStatus.COMPLETED,
                    createdAt = 3L,
                    parts = listOf(
                        imageMessagePart(
                            uri = "content://image-1",
                            fileName = "test.png",
                        ),
                    ),
                ),
                ChatMessage(
                    id = "assistant-2",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "后续回复",
                    status = MessageStatus.COMPLETED,
                    createdAt = 4L,
                ),
            ),
            sourceMessageId = "user-2",
        )

        assertEquals("图片已发送", prepared?.restoredInput)
        assertEquals(1, prepared?.restoredPendingParts?.size)
        assertEquals(ChatMessagePartType.IMAGE, prepared?.restoredPendingParts?.single()?.type)
        assertEquals(listOf("user-1", "assistant-1"), prepared?.rewoundMessages?.map { it.id })
    }

    @Test
    fun prepareUserEdit_returnsNullForAssistantMessage() {
        val prepared = ChatConversationSupport.prepareUserEdit(
            currentMessages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "旧回复",
                    status = MessageStatus.COMPLETED,
                    createdAt = 1L,
                ),
            ),
            sourceMessageId = "assistant-1",
        )

        assertFalse(prepared != null)
    }

    private fun settingsWithSelectedModel(modelId: String): AppSettings {
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = modelId,
        )
        return AppSettings(
            providers = listOf(provider),
            selectedProviderId = provider.id,
        )
    }
}
