package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.SavedImageFile
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatImageGenerationSupportTest {
    @Test
    fun buildCompletedAssistant_prefersRevisedPromptAndBuildsAttachments() = runBlocking {
        val support = ChatImageGenerationSupport(
            imageSaver = {
                SavedImageFile(
                    path = "/tmp/generated.png",
                    mimeType = "image/png",
                    fileName = "generated.png",
                )
            },
        )
        val loadingMessage = ChatMessage(
            id = "assistant-1",
            conversationId = "c1",
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.LOADING,
            createdAt = 1L,
        )

        val completed = support.buildCompletedAssistant(
            loadingMessage = loadingMessage,
            results = listOf(
                ImageGenerationResult(
                    b64Data = "base64-data",
                    url = "",
                    revisedPrompt = "修订后的提示词",
                ),
                ImageGenerationResult(
                    b64Data = "",
                    url = "https://cdn.example.com/generated/remote",
                    revisedPrompt = "",
                ),
            ),
        )

        assertEquals(MessageStatus.COMPLETED, completed.status)
        assertEquals("修订后的提示词", completed.content)
        assertEquals(
            listOf("/tmp/generated.png", "https://cdn.example.com/generated/remote"),
            completed.attachments.map { it.uri },
        )
        assertEquals(3, completed.parts.size)
    }

    @Test
    fun buildCompletedAssistant_fallsBackToDefaultTextWhenPromptMissing() = runBlocking {
        val support = ChatImageGenerationSupport(
            imageSaver = {
                SavedImageFile(
                    path = "/tmp/generated.png",
                    mimeType = "image/png",
                    fileName = "generated.png",
                )
            },
        )
        val loadingMessage = ChatMessage(
            id = "assistant-1",
            conversationId = "c1",
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.LOADING,
            createdAt = 1L,
        )

        val completed = support.buildCompletedAssistant(
            loadingMessage = loadingMessage,
            results = listOf(
                ImageGenerationResult(
                    b64Data = "",
                    url = "https://cdn.example.com/generated/remote",
                    revisedPrompt = "",
                ),
            ),
        )

        assertEquals("图片已生成", completed.content)
        assertTrue(completed.parts.first().text.contains("图片已生成"))
    }
}
