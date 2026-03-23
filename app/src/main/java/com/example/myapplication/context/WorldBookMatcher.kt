package com.example.myapplication.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_WORLD_BOOK_MAX_ENTRIES
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.model.toPlainText

data class WorldBookMatchResult(
    val entries: List<WorldBookEntry>,
    val sourceText: String,
)

class WorldBookMatcher {
    fun match(
        entries: List<WorldBookEntry>,
        assistant: Assistant?,
        conversation: Conversation,
        userInputText: String,
        recentMessages: List<ChatMessage>,
    ): WorldBookMatchResult {
        val sourceText = buildSourceText(userInputText, recentMessages)
        val linkedIds = assistant?.linkedWorldBookIds
            ?.mapNotNull { value ->
                value.trim().takeIf { it.isNotEmpty() }
            }
            ?.toSet()
            .orEmpty()
        val maxEntries = assistant?.worldBookMaxEntries
            ?.takeIf { it > 0 }
            ?: DEFAULT_WORLD_BOOK_MAX_ENTRIES

        val matchedEntries = entries
            .asSequence()
            .filter { it.enabled }
            .filter { entry ->
                val explicitlyLinked = linkedIds.contains(entry.id)
                explicitlyLinked || matchesScope(entry, assistant, conversation)
            }
            .filter { entry ->
                entry.alwaysActive || hasKeywordHit(entry, sourceText)
            }
            .sortedWith(
                compareByDescending<WorldBookEntry> { it.alwaysActive }
                    .thenByDescending { it.priority }
                    .thenBy { it.insertionOrder }
                    .thenBy { it.createdAt }
                    .thenByDescending { it.updatedAt },
            )
            .take(maxEntries)
            .toList()

        return WorldBookMatchResult(
            entries = matchedEntries,
            sourceText = sourceText,
        )
    }

    private fun buildSourceText(
        userInputText: String,
        recentMessages: List<ChatMessage>,
    ): String {
        val latestUserInput = userInputText.trim()
        if (latestUserInput.isNotBlank()) {
            return latestUserInput
        }

        return recentMessages
            .asReversed()
            .mapNotNull { message ->
                if (message.role != MessageRole.USER) {
                    return@mapNotNull null
                }
                message.parts.toPlainText()
                    .ifBlank { message.content.trim() }
                    .trim()
                    .takeIf { it.isNotEmpty() }
            }
            .firstOrNull()
            .orEmpty()
    }

    private fun matchesScope(
        entry: WorldBookEntry,
        assistant: Assistant?,
        conversation: Conversation,
    ): Boolean {
        return when (entry.scopeType) {
            WorldBookScopeType.GLOBAL -> true
            WorldBookScopeType.ASSISTANT -> {
                val assistantId = assistant?.id?.trim().orEmpty()
                assistantId.isNotEmpty() && entry.resolvedScopeId() == assistantId
            }

            WorldBookScopeType.CONVERSATION -> entry.resolvedScopeId() == conversation.id
        }
    }

    private fun hasKeywordHit(
        entry: WorldBookEntry,
        sourceText: String,
    ): Boolean {
        if (sourceText.isBlank()) {
            return false
        }
        val primaryMatched = expandKeywordPatterns(entry.keywords + entry.aliases)
            .any { keyword ->
                matchesPattern(
                    pattern = keyword,
                    sourceText = sourceText,
                    caseSensitive = entry.caseSensitive,
                )
            }
        if (!primaryMatched) {
            return false
        }
        if (!entry.selective || entry.secondaryKeywords.isEmpty()) {
            return true
        }
        return expandKeywordPatterns(entry.secondaryKeywords)
            .any { keyword ->
                matchesPattern(
                    pattern = keyword,
                    sourceText = sourceText,
                    caseSensitive = entry.caseSensitive,
                )
            }
    }

    private fun expandKeywordPatterns(rawPatterns: List<String>): List<String> {
        return rawPatterns.flatMap { rawPattern ->
            val normalized = rawPattern.trim()
            when {
                normalized.isBlank() -> emptyList()
                parseRegexLiteral(normalized) != null -> listOf(normalized)
                else -> normalized
                    .split(KeywordDelimiterRegex)
                    .mapNotNull { token ->
                        token.trim().takeIf { it.isNotEmpty() }
                    }
            }
        }
    }

    private fun matchesPattern(
        pattern: String,
        sourceText: String,
        caseSensitive: Boolean,
    ): Boolean {
        parseRegexLiteral(pattern)?.let { regex ->
            return regex.containsMatchIn(sourceText)
        }
        return sourceText.contains(pattern, ignoreCase = !caseSensitive)
    }

    private fun parseRegexLiteral(pattern: String): Regex? {
        if (pattern.length < 2 || !pattern.startsWith('/')) {
            return null
        }
        var escaped = false
        var endIndex = -1
        for (index in 1 until pattern.length) {
            val current = pattern[index]
            if (current == '/' && !escaped) {
                endIndex = index
                break
            }
            escaped = current == '\\' && !escaped
            if (current != '\\') {
                escaped = false
            }
        }
        if (endIndex <= 1) {
            return null
        }
        val body = pattern.substring(1, endIndex)
        val flags = pattern.substring(endIndex + 1)
        val options = mutableSetOf<RegexOption>()
        for (flag in flags) {
            when (flag.lowercaseChar()) {
                'i' -> options.add(RegexOption.IGNORE_CASE)
                'm' -> options.add(RegexOption.MULTILINE)
                's' -> options.add(RegexOption.DOT_MATCHES_ALL)
                'g', 'u' -> Unit
                else -> return null
            }
        }
        return runCatching {
            Regex(body, options)
        }.getOrNull()
    }

    private companion object {
        val KeywordDelimiterRegex = Regex("""\s*[,，]\s*""")
    }
}
