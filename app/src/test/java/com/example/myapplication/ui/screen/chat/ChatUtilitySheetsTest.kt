package com.example.myapplication.ui.screen.chat

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatReasoningStep
import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.textMessagePart
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
                        id = "cite001",
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
    fun buildSearchResultPreviewPayload_parsesItemsAndAnswer() {
        val payload = buildSearchResultPreviewPayload(
            ChatMessage(
                id = "m1",
                role = MessageRole.ASSISTANT,
                content = "",
                parts = listOf(
                    textMessagePart(
                        """
                        {
                          "query": "今天天气",
                          "answer": "今天整体晴朗。",
                          "items": [
                            {
                              "id": "w001",
                              "title": "天气网",
                              "url": "https://weather.example.com",
                              "text": "今日晴天",
                              "sourceLabel": "LLM 搜索"
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
                ),
                modelName = "grok-4.20-reasoning",
            ),
        )

        assertNotNull(payload)
        assertEquals("今天天气", payload?.query)
        assertEquals("今天整体晴朗。", payload?.answer)
        assertEquals("w001", payload?.items?.single()?.id)
    }

    @Test
    fun buildSearchResultPreviewPayload_returnsNullForNormalMessage() {
        val payload = buildSearchResultPreviewPayload(
            ChatMessage(
                id = "m1",
                role = MessageRole.ASSISTANT,
                content = "普通回复",
            ),
        )

        assertEquals(null, payload)
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
