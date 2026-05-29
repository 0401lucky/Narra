package com.example.myapplication.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType

object MemoryScopeSupport {
    fun filterAccessibleEntries(
        entries: List<MemoryEntry>,
        assistant: Assistant?,
        conversation: Conversation?,
    ): List<MemoryEntry> {
        val resolvedConversationId = conversation?.id.orEmpty()
        val resolvedAssistantId = assistant?.id.orEmpty()
        return entries.filter { entry ->
            // 角色隔离（有意为之，勿改成真全局共享）：记忆按 characterId 隔离，
            // 包括 GLOBAL 作用域——每个角色只能看到自己的全局记忆（写入时已带上
            // characterId = assistant.id）。characterId 为空仅为兼容历史迁移数据，
            // 这类旧数据没有归属角色，故对任何角色可见。
            if (entry.characterId.isNotBlank() && entry.characterId != resolvedAssistantId) {
                return@filter false
            }
            when (entry.scopeType) {
                MemoryScopeType.GLOBAL -> true
                MemoryScopeType.ASSISTANT -> {
                    val resolvedAssistant = assistant ?: return@filter false
                    !resolvedAssistant.useGlobalMemory &&
                        entry.resolvedScopeId() == resolvedAssistant.id
                }

                MemoryScopeType.CONVERSATION -> {
                    resolvedConversationId.isNotBlank() &&
                        entry.resolvedScopeId() == resolvedConversationId
                }
            }
        }
    }

    fun sortByPriority(
        entries: List<MemoryEntry>,
    ): List<MemoryEntry> {
        return entries.sortedWith(
            compareByDescending<MemoryEntry> { it.pinned }
                .thenByDescending { it.importance }
                .thenByDescending { it.lastUsedAt }
                .thenByDescending { it.updatedAt },
        )
    }
}
