package com.example.myapplication.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldBookMatcherTest {
    private val matcher = WorldBookMatcher()

    @Test
    fun match_hitsKeywordAndAlwaysActiveEntries() {
        val result = matcher.match(
            entries = listOf(
                WorldBookEntry(
                    id = "entry-1",
                    title = "白塔城",
                    content = "北境最大的贸易都会。",
                    keywords = listOf("白塔城"),
                    priority = 4,
                ),
                WorldBookEntry(
                    id = "entry-2",
                    title = "王都礼仪",
                    content = "进入王都前需持有通行印记。",
                    alwaysActive = true,
                    priority = 1,
                ),
            ),
            assistant = Assistant(id = "assistant-1", worldBookMaxEntries = 8),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "我准备去白塔城做生意",
            recentMessages = emptyList(),
        )

        assertEquals(listOf("王都礼仪", "白塔城"), result.entries.map { it.title })
        assertTrue(result.sourceText.contains("白塔城"))
    }

    @Test
    fun match_filtersByScope() {
        val result = matcher.match(
            entries = listOf(
                WorldBookEntry(
                    id = "global-entry",
                    title = "通用设定",
                    content = "所有会话可用。",
                    alwaysActive = true,
                    scopeType = WorldBookScopeType.GLOBAL,
                ),
                WorldBookEntry(
                    id = "assistant-entry",
                    title = "专属助手设定",
                    content = "仅某个助手可用。",
                    alwaysActive = true,
                    scopeType = WorldBookScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                ),
                WorldBookEntry(
                    id = "conversation-entry",
                    title = "专属会话设定",
                    content = "仅某个会话可用。",
                    alwaysActive = true,
                    scopeType = WorldBookScopeType.CONVERSATION,
                    scopeId = "c1",
                ),
                WorldBookEntry(
                    id = "other-assistant-entry",
                    title = "其他助手设定",
                    content = "不应命中。",
                    alwaysActive = true,
                    scopeType = WorldBookScopeType.ASSISTANT,
                    scopeId = "assistant-2",
                ),
            ),
            assistant = Assistant(id = "assistant-1"),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "",
            recentMessages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "继续当前剧情",
                    createdAt = 1L,
                ),
            ),
        )

        assertEquals(
            listOf("通用设定", "专属助手设定", "专属会话设定"),
            result.entries.map { it.title },
        )
    }

    @Test
    fun match_respectsAssistantLimitAndPriority() {
        val result = matcher.match(
            entries = listOf(
                WorldBookEntry(id = "entry-1", title = "低优先级", content = "A", alwaysActive = true, priority = 1),
                WorldBookEntry(id = "entry-2", title = "高优先级", content = "B", alwaysActive = true, priority = 9),
                WorldBookEntry(id = "entry-3", title = "中优先级", content = "C", alwaysActive = true, priority = 5),
            ),
            assistant = Assistant(id = "assistant-1", worldBookMaxEntries = 2),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "",
            recentMessages = emptyList(),
        )

        assertEquals(listOf("高优先级", "中优先级"), result.entries.map { it.title })
    }

    @Test
    fun match_supportsRegexAndSelectiveSecondaryKeywords() {
        val result = matcher.match(
            entries = listOf(
                WorldBookEntry(
                    id = "entry-1",
                    title = "璃珠都市",
                    content = "璃珠都市位于北部沿海。",
                    keywords = listOf("/璃珠(都|城)市/i"),
                    insertionOrder = 10,
                ),
                WorldBookEntry(
                    id = "entry-2",
                    title = "夜巡守则",
                    content = "午夜前后不要靠近旧钟楼。",
                    keywords = listOf("夜巡"),
                    secondaryKeywords = listOf("午夜", "钟楼"),
                    selective = true,
                    insertionOrder = 20,
                ),
            ),
            assistant = Assistant(id = "assistant-1", worldBookMaxEntries = 8),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "今晚夜巡时我会经过璃珠都市的旧钟楼，已经接近午夜。",
            recentMessages = emptyList(),
        )

        assertEquals(listOf("璃珠都市", "夜巡守则"), result.entries.map { it.title })
    }
}
