package com.example.myapplication.ui.screen.chat

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatReasoningStep
import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.MessageRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUtilitySheetsTest {
    @Test
    fun buildConversationMarkdown_includesReasoningAndModelWhenEnabled() {
        val markdown = buildConversationMarkdown(
            title = "测试会话",
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    role = MessageRole.ASSISTANT,
                    content = "最终答案",
                    reasoningContent = "先分析，再回答",
                    modelName = "gpt-4o-mini",
                ),
            ),
            options = ConversationExportOptions(
                includeReasoning = true,
                includeModelName = true,
                includeImageLinks = false,
            ),
        )

        assertTrue(markdown.contains("## 助手 · gpt-4o-mini"))
        assertTrue(markdown.contains("> 思考内容"))
        assertTrue(markdown.contains("先分析，再回答"))
    }

    @Test
    fun buildConversationPlainText_omitsOptionalSectionsWhenDisabled() {
        val plainText = buildConversationPlainText(
            title = "测试会话",
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    role = MessageRole.ASSISTANT,
                    content = "最终答案",
                    reasoningContent = "先分析，再回答",
                    modelName = "gpt-4o-mini",
                ),
            ),
            options = ConversationExportOptions(
                includeReasoning = false,
                includeModelName = false,
                includeImageLinks = false,
            ),
        )

        assertTrue(plainText.contains("助手"))
        assertFalse(plainText.contains("gpt-4o-mini"))
        assertFalse(plainText.contains("思考内容"))
    }

    @Test
    fun buildMessageMarkdown_includesReasoningStepsAndCitations() {
        val markdown = buildMessageMarkdown(
            message = ChatMessage(
                id = "m1",
                role = MessageRole.ASSISTANT,
                content = "最终答案",
                modelName = "gpt-4o-mini",
                reasoningSteps = listOf(
                    ChatReasoningStep(
                        id = "step-1",
                        text = "**分析目标**\n先看输入。",
                        createdAt = 1L,
                        finishedAt = 2L,
                    ),
                ),
                citations = listOf(
                    MessageCitation(
                        title = "OpenAI",
                        url = "https://openai.com",
                        sourceLabel = "官网",
                    ),
                ),
            ),
        )

        assertTrue(markdown.contains("### 思考过程"))
        assertTrue(markdown.contains("**分析目标**"))
        assertTrue(markdown.contains("### 引用来源"))
        assertTrue(markdown.contains("[OpenAI](https://openai.com)"))
    }

    @Test
    fun messageHasPreviewableText_returnsFalseForPureImageMessage() {
        val message = ChatMessage(
            id = "m1",
            role = MessageRole.USER,
            content = "图片已发送",
            parts = listOf(
                com.example.myapplication.model.imageMessagePart(uri = "content://image"),
            ),
        )

        assertFalse(messageHasPreviewableText(message))
    }

    @Test
    fun resolveMessageActionAvailability_allowsEditOnlyForUserMessage() {
        val userAvailability = resolveMessageActionAvailability(
            ChatMessage(
                id = "u1",
                role = MessageRole.USER,
                content = "你好",
            ),
        )
        val assistantAvailability = resolveMessageActionAvailability(
            ChatMessage(
                id = "a1",
                role = MessageRole.ASSISTANT,
                content = "你好",
            ),
        )

        assertTrue(userAvailability.canEditUserMessage)
        assertFalse(assistantAvailability.canEditUserMessage)
        assertTrue(assistantAvailability.canRegenerate)
        assertEquals(true, assistantAvailability.canPreview)
    }
}
