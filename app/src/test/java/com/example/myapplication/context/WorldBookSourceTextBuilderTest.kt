package com.example.myapplication.context

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldBookSourceTextBuilderTest {
    private fun msg(id: String, role: MessageRole, text: String): ChatMessage {
        return ChatMessage(
            id = id,
            conversationId = "c1",
            role = role,
            content = text,
            createdAt = 1L,
        )
    }

    @Test
    fun scanDepthZero_returnsOnlyCurrentInput() {
        val result = buildWorldBookSourceText(
            userInputText = "当前输入",
            recentMessages = listOf(
                msg("m1", MessageRole.USER, "上条用户"),
                msg("m2", MessageRole.ASSISTANT, "上条助手"),
            ),
            scanDepth = 0,
            maxChars = 2000,
        )
        assertEquals("当前输入", result)
    }

    @Test
    fun scanDepthTwo_appendsLastTwoMessagesInChronologicalOrder() {
        val result = buildWorldBookSourceText(
            userInputText = "当前输入",
            recentMessages = listOf(
                msg("m1", MessageRole.USER, "较早用户"),
                msg("m2", MessageRole.ASSISTANT, "较早助手"),
                msg("m3", MessageRole.USER, "最近用户"),
            ),
            scanDepth = 2,
            maxChars = 2000,
        )
        assertTrue("result=$result", result.contains("当前输入"))
        assertTrue("result=$result", result.contains("较早助手"))
        assertTrue("result=$result", result.contains("最近用户"))
        assertTrue("不应包含 scanDepth 范围外的 m1", !result.contains("较早用户"))
    }

    @Test
    fun emptyUserInput_fallsBackToRecentMessages() {
        val result = buildWorldBookSourceText(
            userInputText = "",
            recentMessages = listOf(
                msg("m1", MessageRole.USER, "回退输入"),
            ),
            scanDepth = 2,
            maxChars = 2000,
        )
        assertTrue(result.contains("回退输入"))
    }

    @Test
    fun maxChars_truncatesFromHead_keepsLatestInputIntact() {
        val longHistory = (1..50).joinToString(" ") { "历史块$it" }
        val result = buildWorldBookSourceText(
            userInputText = "当前输入",
            recentMessages = listOf(
                msg("m1", MessageRole.ASSISTANT, longHistory),
            ),
            scanDepth = 2,
            maxChars = 40,
        )
        assertTrue("result.len=${result.length}", result.length <= 40)
        assertTrue("当前输入必须保留", result.contains("当前输入"))
    }

    @Test
    fun negativeScanDepth_treatedAsZero() {
        val result = buildWorldBookSourceText(
            userInputText = "当前",
            recentMessages = listOf(msg("m1", MessageRole.USER, "历史")),
            scanDepth = -5,
            maxChars = 2000,
        )
        assertEquals("当前", result)
    }
}
