package com.example.myapplication.data.repository.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplaySuggestionSupportTest {
    @Test
    fun buildRequestMessages_prefersArrayContractAndRejectsAxisWrapper() {
        val messages = RoleplaySuggestionSupport.buildRequestMessages(
            conversationExcerpt = "用户：你到底还瞒了我什么？",
            systemPrompt = "【场景设定】雨夜对峙",
            playerStyleReference = "- 你最好说清楚。",
            longformMode = false,
        )

        val systemPrompt = messages.first().content.toString()
        assertTrue(systemPrompt.contains("首选且默认只输出 JSON 数组"))
        assertTrue(systemPrompt.contains("唯一允许的包装形式是 {\"suggestions\":[...]}"))
        assertTrue(systemPrompt.contains("严禁输出 {\"plot\":{...},\"info\":{...},\"emotion\":{...}}"))
        assertTrue(systemPrompt.contains("完整正文必须全部写进 text 字段"))
    }
}
