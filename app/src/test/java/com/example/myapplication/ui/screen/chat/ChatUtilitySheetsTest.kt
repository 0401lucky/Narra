package com.example.myapplication.ui.screen.chat

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import org.junit.Assert.assertFalse
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
}
