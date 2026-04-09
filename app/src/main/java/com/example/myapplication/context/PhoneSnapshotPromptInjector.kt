package com.example.myapplication.context

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.toPlainText

class PhoneSnapshotPromptInjector {
    fun selectRelevantItems(
        snapshot: PhoneSnapshot,
        userInputText: String,
        recentMessages: List<ChatMessage>,
        maxItems: Int = 3,
    ): List<String> {
        if (!snapshot.hasContent()) {
            return emptyList()
        }
        val queryTerms = extractQueryTerms(userInputText, recentMessages)
        if (queryTerms.isEmpty()) {
            return emptyList()
        }
        return snapshot.summaryCandidates()
            .map { candidate ->
                candidate to calculateRelevance(candidate, queryTerms)
            }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .map { (candidate, _) -> candidate }
            .distinct()
            .take(maxItems.coerceAtLeast(1))
    }

    private fun extractQueryTerms(
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

    private fun calculateRelevance(
        candidate: String,
        queryTerms: Set<String>,
    ): Int {
        if (candidate.isBlank() || queryTerms.isEmpty()) {
            return 0
        }
        val candidateTerms = extractRelevanceTerms(candidate)
        if (candidateTerms.isEmpty()) {
            return 0
        }
        return queryTerms.count { term -> term in candidateTerms }
    }

    private fun extractRelevanceTerms(text: String): Set<String> {
        val normalizedText = text.lowercase()
        val baseTokens = Regex("""[\p{L}\p{N}\u4e00-\u9fff]{2,}""")
            .findAll(normalizedText)
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

    private fun PhoneSnapshot.summaryCandidates(): List<String> {
        return buildList {
            relationshipHighlights.forEach { item ->
                add(
                    buildString {
                        append(item.name)
                        if (item.relationLabel.isNotBlank()) {
                            append("（")
                            append(item.relationLabel)
                            append("）")
                        }
                        if (item.stance.isNotBlank()) {
                            append("：")
                            append(item.stance)
                        }
                        if (item.note.isNotBlank()) {
                            append("，")
                            append(item.note)
                        }
                    },
                )
            }
            messageThreads.forEach { thread ->
                add(
                    buildString {
                        append("消息：")
                        append(thread.contactName)
                        if (thread.relationLabel.isNotBlank()) {
                            append("（")
                            append(thread.relationLabel)
                            append("）")
                        }
                        if (thread.preview.isNotBlank()) {
                            append("：")
                            append(thread.preview)
                        }
                    },
                )
            }
            notes.forEach { note ->
                add("备忘录：${note.title}；${note.summary}")
            }
            gallery.forEach { entry ->
                add("相册：${entry.title}；${entry.summary}")
            }
            shoppingRecords.forEach { record ->
                add("购物：${record.title}；${record.note}")
            }
            searchHistory.forEach { search ->
                add("搜索：${search.query}")
                search.detail?.summary
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add("搜索详情：${search.query}；$it") }
            }
        }
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
