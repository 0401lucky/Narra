package com.example.myapplication.ui.screen.settings.memory

import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryManagementScreenFilterTest {
    @Test
    fun filterForAssistant_usesCharacterIdAndAssistantScopeFallback() {
        val entries = listOf(
            MemoryEntry(
                id = "m1",
                scopeType = MemoryScopeType.ASSISTANT,
                scopeId = "assistant-1",
                characterId = "assistant-1",
                content = "角色一长期记忆",
            ),
            MemoryEntry(
                id = "m2",
                scopeType = MemoryScopeType.ASSISTANT,
                scopeId = "assistant-1",
                characterId = "",
                content = "旧数据助手记忆",
            ),
            MemoryEntry(
                id = "m3",
                scopeType = MemoryScopeType.GLOBAL,
                content = "全局记忆",
            ),
            MemoryEntry(
                id = "m4",
                scopeType = MemoryScopeType.ASSISTANT,
                scopeId = "assistant-2",
                characterId = "assistant-2",
                content = "角色二长期记忆",
            ),
        )

        val filtered = entries.filterMemoriesForAssistant("assistant-1")

        assertEquals(listOf("m1", "m2"), filtered.map { it.id })
    }

    @Test
    fun filterSummariesForAssistant_matchesAssistantIdAndSupportsAll() {
        val summaries = listOf(
            ConversationSummary(
                conversationId = "c1",
                assistantId = "assistant-1",
                summary = "角色一摘要",
            ),
            ConversationSummary(
                conversationId = "c2",
                assistantId = "assistant-2",
                summary = "角色二摘要",
            ),
        )

        assertEquals(listOf("c1"), summaries.filterSummariesForAssistant("assistant-1").map { it.conversationId })
        assertEquals(listOf("c1", "c2"), summaries.filterSummariesForAssistant("__all__").map { it.conversationId })
    }
}
