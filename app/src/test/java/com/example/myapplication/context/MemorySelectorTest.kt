package com.example.myapplication.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import org.junit.Assert.assertEquals
import org.junit.Test

class MemorySelectorTest {
    private val selector = MemorySelector()

    @Test
    fun select_returnsEmptyWhenAssistantMemoryDisabled() {
        val result = selector.select(
            entries = listOf(
                MemoryEntry(
                    id = "m1",
                    content = "记忆 A",
                    pinned = true,
                ),
            ),
            assistant = Assistant(id = "assistant-1", memoryEnabled = false),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
        )

        assertEquals(emptyList<MemoryEntry>(), result)
    }

    @Test
    fun select_prioritizesPinnedAndScopedEntries() {
        val result = selector.select(
            entries = listOf(
                MemoryEntry(
                    id = "global",
                    content = "全局记忆",
                    pinned = false,
                    importance = 50,
                    scopeType = MemoryScopeType.GLOBAL,
                ),
                MemoryEntry(
                    id = "assistant",
                    content = "助手记忆",
                    pinned = true,
                    importance = 40,
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                ),
                MemoryEntry(
                    id = "conversation",
                    content = "会话记忆",
                    pinned = true,
                    importance = 30,
                    scopeType = MemoryScopeType.CONVERSATION,
                    scopeId = "c1",
                ),
                MemoryEntry(
                    id = "other",
                    content = "其他助手记忆",
                    pinned = true,
                    importance = 100,
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-2",
                ),
            ),
            assistant = Assistant(
                id = "assistant-1",
                memoryEnabled = true,
                memoryMaxItems = 3,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
        )

        assertEquals(
            listOf("助手记忆", "会话记忆", "全局记忆"),
            result.map { it.content },
        )
    }
}
