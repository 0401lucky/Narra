package com.example.myapplication.viewmodel

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ProviderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatOutgoingMessageSupportTest {
    @Test
    fun resolveTextMessage_returnsErrorWhenNoTextOrAttachments() {
        val result = ChatOutgoingMessageSupport.resolveTextMessage(
            ChatUiState(input = "   "),
        )

        val error = result as ChatOutgoingMessageResolution.Error
        assertEquals("请输入消息内容或添加附件", error.message)
    }

    @Test
    fun resolveTextMessage_buildsPlanAndClearsDrafts() {
        val result = ChatOutgoingMessageSupport.resolveTextMessage(
            ChatUiState(
                input = "  你好  ",
                pendingParts = listOf(
                    com.example.myapplication.model.imageMessagePart(
                        uri = "content://image/1",
                        mimeType = "image/png",
                        fileName = "a.png",
                    ),
                ),
            ),
        )

        val ready = result as ChatOutgoingMessageResolution.Ready
        assertEquals("你好", ready.plan.imageGenerationPrompt)
        assertEquals("", ready.plan.nextInput)
        assertTrue(ready.plan.nextPendingParts.isEmpty())
        assertEquals(2, ready.plan.userParts.size)
        assertTrue(!ready.plan.forceChatRoundTrip)
    }

    @Test
    fun resolveTextMessage_rejectsImageWhenModelHasNoVision() {
        val result = ChatOutgoingMessageSupport.resolveTextMessage(
            ChatUiState(
                input = "帮我看看这张图",
                pendingParts = listOf(
                    com.example.myapplication.model.imageMessagePart(
                        uri = "content://image/2",
                        mimeType = "image/png",
                        fileName = "b.png",
                    ),
                ),
                settings = settingsWithSelectedModel("deepseek-chat"),
            ),
        )

        val error = result as ChatOutgoingMessageResolution.Error
        assertEquals("当前模型不支持图片理解，请切换到支持视觉的模型后再发送图片", error.message)
    }

    @Test
    fun resolveTextMessage_rejectsAttachmentsWhenModelIsImageGeneration() {
        val result = ChatOutgoingMessageSupport.resolveTextMessage(
            ChatUiState(
                input = "做成海报风格",
                pendingParts = listOf(
                    com.example.myapplication.model.fileMessagePart(
                        uri = "content://file/1",
                        mimeType = "text/plain",
                        fileName = "brief.txt",
                    ),
                ),
                settings = settingsWithSelectedModel("gpt-image-1"),
            ),
        )

        val error = result as ChatOutgoingMessageResolution.Error
        assertEquals("当前模型为生图模型，仅支持文本提示词。请切换到聊天模型后再发送附件", error.message)
    }

    @Test
    fun resolveTransferPlay_returnsErrorWhenAmountBlank() {
        val result = ChatOutgoingMessageSupport.resolveTransferPlay(
            state = ChatUiState(),
            counterparty = "Alice",
            amount = "   ",
            note = "备注",
        )

        val error = result as ChatOutgoingMessageResolution.Error
        assertEquals("请输入转账金额", error.message)
    }

    @Test
    fun resolveTransferPlay_rejectsImageGenerationModel() {
        val result = ChatOutgoingMessageSupport.resolveTransferPlay(
            state = ChatUiState(
                settings = settingsWithSelectedModel("gpt-image-1"),
            ),
            counterparty = "Alice",
            amount = "88.8",
            note = "备注",
        )

        val error = result as ChatOutgoingMessageResolution.Error
        assertEquals("当前模型为生图模型，不支持特殊玩法。请切换到聊天模型后再继续", error.message)
    }

    @Test
    fun resolveTransferPlay_usesAssistantNameAsCounterpartyFallback() {
        val result = ChatOutgoingMessageSupport.resolveTransferPlay(
            state = ChatUiState(
                input = "保留草稿",
                pendingParts = listOf(
                    com.example.myapplication.model.textMessagePart("附件说明"),
                ),
                currentAssistant = Assistant(id = "a1", name = "小助理"),
            ),
            counterparty = "   ",
            amount = " 88.8 ",
            note = " 午饭 ",
        )

        val ready = result as ChatOutgoingMessageResolution.Ready
        val transferPart = ready.plan.userParts.single()
        assertEquals("小助理", transferPart.specialCounterparty)
        assertEquals("88.8", transferPart.specialAmount)
        assertEquals("午饭", transferPart.specialNote)
        assertEquals("保留草稿", ready.plan.nextInput)
        assertEquals(1, ready.plan.nextPendingParts.size)
        assertTrue(ready.plan.forceChatRoundTrip)
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
