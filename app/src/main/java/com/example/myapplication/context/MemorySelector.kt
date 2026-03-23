package com.example.myapplication.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_MEMORY_MAX_ITEMS
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.toPlainText

class MemorySelector {
    fun select(
        entries: List<MemoryEntry>,
        assistant: Assistant?,
        conversation: Conversation,
        promptMode: PromptMode = PromptMode.CHAT,
        userInputText: String = "",
        recentMessages: List<ChatMessage> = emptyList(),
    ): List<MemoryEntry> {
        if (assistant?.memoryEnabled != true) {
            return emptyList()
        }

        val maxItems = assistant.memoryMaxItems.takeIf { it > 0 } ?: DEFAULT_MEMORY_MAX_ITEMS
        val scopedEntries = entries.filter { matchesScope(it, assistant, conversation) }
        if (promptMode != PromptMode.ROLEPLAY) {
            return scopedEntries
                .sortedWith(
                    compareByDescending<MemoryEntry> { it.pinned }
                        .thenByDescending { it.importance }
                        .thenByDescending { it.lastUsedAt }
                        .thenByDescending { it.updatedAt },
                )
                .take(maxItems)
                .toList()
        }
        val queryTerms = buildQueryTerms(
            userInputText = userInputText,
            recentMessages = recentMessages,
        )
        val conversationEntries = scopedEntries
            .filter { it.scopeType == MemoryScopeType.CONVERSATION }
            .sortedWith(roleplayComparator(queryTerms))
        val assistantEntries = scopedEntries
            .filter { it.scopeType == MemoryScopeType.ASSISTANT }
            .sortedWith(roleplayComparator(queryTerms))
        val globalEntries = scopedEntries
            .filter { it.scopeType == MemoryScopeType.GLOBAL }
            .sortedWith(roleplayComparator(queryTerms))

        val selected = mutableListOf<MemoryEntry>()
        val usedIds = mutableSetOf<String>()
        allocateRoleplayQuota(
            source = conversationEntries,
            quota = minOf(3, maxItems),
            selected = selected,
            usedIds = usedIds,
        )
        allocateRoleplayQuota(
            source = assistantEntries,
            quota = minOf(2, (maxItems - selected.size).coerceAtLeast(0)),
            selected = selected,
            usedIds = usedIds,
        )
        allocateRoleplayQuota(
            source = globalEntries,
            quota = minOf(1, (maxItems - selected.size).coerceAtLeast(0)),
            selected = selected,
            usedIds = usedIds,
        )
        if (selected.size < maxItems) {
            (conversationEntries + assistantEntries + globalEntries)
                .forEach { entry ->
                    if (selected.size >= maxItems || entry.id in usedIds) {
                        return@forEach
                    }
                    selected += entry
                    usedIds += entry.id
                }
        }
        return selected
    }

    private fun matchesScope(
        entry: MemoryEntry,
        assistant: Assistant,
        conversation: Conversation,
    ): Boolean {
        return when (entry.scopeType) {
            MemoryScopeType.GLOBAL -> true
            MemoryScopeType.ASSISTANT -> !assistant.useGlobalMemory && entry.resolvedScopeId() == assistant.id
            MemoryScopeType.CONVERSATION -> entry.resolvedScopeId() == conversation.id
        }
    }

    private fun roleplayComparator(queryTerms: Set<String>): Comparator<MemoryEntry> {
        return compareByDescending<MemoryEntry> { entry ->
            calculateRelevanceScore(entry, queryTerms)
        }
            .thenByDescending { it.pinned }
            .thenByDescending { it.importance }
            .thenByDescending { it.lastUsedAt }
            .thenByDescending { it.updatedAt }
    }

    private fun calculateRelevanceScore(
        entry: MemoryEntry,
        queryTerms: Set<String>,
    ): Int {
        if (queryTerms.isEmpty()) {
            return 0
        }
        val memoryTerms = extractRelevanceTerms(entry.content)
        if (memoryTerms.isEmpty()) {
            return 0
        }
        return queryTerms.count { token -> token in memoryTerms }
    }

    private fun buildQueryTerms(
        userInputText: String,
        recentMessages: List<ChatMessage>,
    ): Set<String> {
        val sourceText = buildString {
            appendLine(userInputText)
            recentMessages.takeLast(6).forEach { message ->
                appendLine(message.parts.toPlainText().ifBlank { message.content })
            }
        }
        return extractRelevanceTerms(sourceText)
    }

    private fun extractRelevanceTerms(text: String): Set<String> {
        val normalizedText = text.lowercase()
        val baseTokens = Regex("""[\p{L}\p{N}\u4e00-\u9fff]{2,}""")
            .findAll(text.lowercase())
            .map { it.value }
            .filter { token -> token.length >= 2 }
            .toSet()
        return baseTokens + buildCjkBigrams(normalizedText)
    }

    private fun buildCjkBigrams(text: String): Set<String> {
        val characters = text.filter { character ->
            character in '\u4e00'..'\u9fff'
        }
        if (characters.length < 2) {
            return emptySet()
        }
        return (0 until characters.length - 1)
            .map { index -> characters.substring(index, index + 2) }
            .toSet()
    }

    private fun allocateRoleplayQuota(
        source: List<MemoryEntry>,
        quota: Int,
        selected: MutableList<MemoryEntry>,
        usedIds: MutableSet<String>,
    ) {
        if (quota <= 0) {
            return
        }
        var taken = 0
        source.forEach { entry ->
            if (taken >= quota || entry.id in usedIds) {
                return@forEach
            }
            selected += entry
            usedIds += entry.id
            taken++
        }
    }
}
