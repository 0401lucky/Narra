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
            // 角色隔离：如果记忆绑定了特定角色，只对匹配的 assistant 可见
            // characterId 为空代表全局记忆（渐进迁移兼容），任何角色可见
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
