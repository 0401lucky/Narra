package com.example.myapplication.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType

object WorldBookScopeSupport {
    fun filterAccessibleEntries(
        entries: List<WorldBookEntry>,
        assistant: Assistant?,
        conversation: Conversation?,
    ): List<WorldBookEntry> {
        val linkedIds = assistant?.linkedWorldBookIds
            ?.mapNotNull { value ->
                value.trim().takeIf { it.isNotEmpty() }
            }
            ?.toSet()
            .orEmpty()
        val assistantId = assistant?.id?.trim().orEmpty()
        val conversationId = conversation?.id.orEmpty()

        return entries.filter { entry ->
            when (entry.scopeType) {
                WorldBookScopeType.GLOBAL -> true
                WorldBookScopeType.ATTACHABLE -> linkedIds.contains(entry.id)
                WorldBookScopeType.ASSISTANT -> {
                    assistantId.isNotBlank() && entry.resolvedScopeId() == assistantId
                }

                WorldBookScopeType.CONVERSATION -> {
                    conversationId.isNotBlank() && entry.resolvedScopeId() == conversationId
                }
            }
        }
    }

    fun sortByPriority(
        entries: List<WorldBookEntry>,
    ): List<WorldBookEntry> {
        return entries.sortedWith(priorityComparator())
    }

    fun priorityComparator(): Comparator<WorldBookEntry> {
        return compareByDescending<WorldBookEntry> { it.alwaysActive }
            .thenByDescending { it.priority }
            .thenBy { it.insertionOrder }
            .thenBy { it.createdAt }
            .thenByDescending { it.updatedAt }
    }
}
