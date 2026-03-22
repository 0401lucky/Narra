package com.example.myapplication.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_MEMORY_MAX_ITEMS
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.PromptMode

class MemorySelector {
    fun select(
        entries: List<MemoryEntry>,
        assistant: Assistant?,
        conversation: Conversation,
        promptMode: PromptMode = PromptMode.CHAT,
    ): List<MemoryEntry> {
        if (assistant?.memoryEnabled != true) {
            return emptyList()
        }

        val maxItems = assistant.memoryMaxItems.takeIf { it > 0 } ?: DEFAULT_MEMORY_MAX_ITEMS

        return entries
            .asSequence()
            .filter { matchesScope(it, assistant, conversation) }
            .sortedWith(
                compareByDescending<MemoryEntry> {
                    if (promptMode == PromptMode.ROLEPLAY) {
                        memoryScopePriority(it.scopeType)
                    } else {
                        0
                    }
                }
                    .thenByDescending { it.pinned }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.lastUsedAt }
                    .thenByDescending { it.updatedAt },
            )
            .take(maxItems)
            .toList()
    }

    private fun matchesScope(
        entry: MemoryEntry,
        assistant: Assistant,
        conversation: Conversation,
    ): Boolean {
        return when (entry.scopeType) {
            MemoryScopeType.GLOBAL -> true
            MemoryScopeType.ASSISTANT -> entry.resolvedScopeId() == assistant.id
            MemoryScopeType.CONVERSATION -> entry.resolvedScopeId() == conversation.id
        }
    }

    private fun memoryScopePriority(scopeType: MemoryScopeType): Int {
        return when (scopeType) {
            MemoryScopeType.CONVERSATION -> 3
            MemoryScopeType.ASSISTANT -> 2
            MemoryScopeType.GLOBAL -> 1
        }
    }
}
