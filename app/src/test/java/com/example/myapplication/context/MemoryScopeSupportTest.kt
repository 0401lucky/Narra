package com.example.myapplication.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 固化 [MemoryScopeSupport.filterAccessibleEntries] 的「全局记忆按角色隔离」语义。
 *
 * 产品决策：useGlobalMemory 表示「全局作用域，但仍按角色隔离」——
 * 每个角色只能看到自己的 GLOBAL 记忆（写入时带 characterId = assistant.id）。
 * characterId 为空仅是历史迁移数据的兼容，这类旧数据对任何角色可见。
 */
class MemoryScopeSupportTest {
    private fun assistant(id: String, useGlobalMemory: Boolean = false): Assistant =
        Assistant(id = id, memoryEnabled = true, useGlobalMemory = useGlobalMemory)

    private fun conversation(id: String): Conversation =
        Conversation(id = id, createdAt = 1L, updatedAt = 1L)

    @Test
    fun globalEntryBoundToCharacterA_visibleToAssistantA() {
        val entry = MemoryEntry(
            id = "g-a",
            content = "A 的全局记忆",
            scopeType = MemoryScopeType.GLOBAL,
            characterId = "A",
        )

        val result = MemoryScopeSupport.filterAccessibleEntries(
            entries = listOf(entry),
            assistant = assistant("A"),
            conversation = conversation("c1"),
        )

        assertEquals(listOf("A 的全局记忆"), result.map { it.content })
    }

    @Test
    fun globalEntryBoundToCharacterA_notVisibleToAssistantB() {
        val entry = MemoryEntry(
            id = "g-a",
            content = "A 的全局记忆",
            scopeType = MemoryScopeType.GLOBAL,
            characterId = "A",
        )

        val result = MemoryScopeSupport.filterAccessibleEntries(
            entries = listOf(entry),
            assistant = assistant("B"),
            conversation = conversation("c1"),
        )

        // 核心断言：GLOBAL 记忆按角色隔离，B 看不到 A 的全局记忆。
        assertEquals(emptyList<String>(), result.map { it.content })
    }

    @Test
    fun globalEntryWithBlankCharacterId_visibleToAnyAssistant() {
        val entry = MemoryEntry(
            id = "g-legacy",
            content = "迁移遗留的全局记忆",
            scopeType = MemoryScopeType.GLOBAL,
            characterId = "",
        )

        val visibleToA = MemoryScopeSupport.filterAccessibleEntries(
            entries = listOf(entry),
            assistant = assistant("A"),
            conversation = conversation("c1"),
        )
        val visibleToB = MemoryScopeSupport.filterAccessibleEntries(
            entries = listOf(entry),
            assistant = assistant("B"),
            conversation = conversation("c1"),
        )

        // characterId 为空的历史迁移数据没有归属角色，对任何角色可见。
        assertEquals(listOf("迁移遗留的全局记忆"), visibleToA.map { it.content })
        assertEquals(listOf("迁移遗留的全局记忆"), visibleToB.map { it.content })
    }

    @Test
    fun assistantScopedEntry_visibleToOwnerWhenNotUsingGlobalMemory() {
        val entry = MemoryEntry(
            id = "a-scope",
            content = "A 的助手记忆",
            scopeType = MemoryScopeType.ASSISTANT,
            scopeId = "A",
        )

        val result = MemoryScopeSupport.filterAccessibleEntries(
            entries = listOf(entry),
            assistant = assistant("A", useGlobalMemory = false),
            conversation = conversation("c1"),
        )

        assertEquals(listOf("A 的助手记忆"), result.map { it.content })
    }

    @Test
    fun assistantScopedEntry_hiddenFromOwnerWhenUsingGlobalMemory() {
        val entry = MemoryEntry(
            id = "a-scope",
            content = "A 的助手记忆",
            scopeType = MemoryScopeType.ASSISTANT,
            scopeId = "A",
        )

        val result = MemoryScopeSupport.filterAccessibleEntries(
            entries = listOf(entry),
            assistant = assistant("A", useGlobalMemory = true),
            conversation = conversation("c1"),
        )

        // useGlobalMemory=true 时不再读取 ASSISTANT 作用域的隔离记忆。
        assertEquals(emptyList<String>(), result.map { it.content })
    }
}
