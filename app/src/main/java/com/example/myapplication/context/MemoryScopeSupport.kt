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
        return entries.filter { entry ->
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
