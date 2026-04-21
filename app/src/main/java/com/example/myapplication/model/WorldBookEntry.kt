package com.example.myapplication.model

import java.nio.charset.StandardCharsets
import java.util.UUID

enum class WorldBookScopeType(
    val storageValue: String,
    val label: String,
) {
    GLOBAL("global", "全局"),
    ATTACHABLE("attachable", "可挂载"),
    ASSISTANT("assistant", "助手"),
    CONVERSATION("conversation", "会话");

    companion object {
        fun fromStorageValue(value: String): WorldBookScopeType {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: GLOBAL
        }
    }
}

data class WorldBookEntry(
    val id: String = UUID.randomUUID().toString(),
    val bookId: String = "",
    val title: String = "",
    val content: String = "",
    val keywords: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val secondaryKeywords: List<String> = emptyList(),
    val enabled: Boolean = true,
    val alwaysActive: Boolean = false,
    val selective: Boolean = false,
    val caseSensitive: Boolean = false,
    val matchMode: WorldBookMatchMode = WorldBookMatchMode.WORD_CJK,
    val priority: Int = 0,
    val insertionOrder: Int = 0,
    val sourceBookName: String = "",
    val scopeType: WorldBookScopeType = WorldBookScopeType.GLOBAL,
    val scopeId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    fun matchesSearch(query: String): Boolean {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            return true
        }
        return title.contains(normalizedQuery, ignoreCase = true) ||
            content.contains(normalizedQuery, ignoreCase = true) ||
            keywords.any { it.contains(normalizedQuery, ignoreCase = true) } ||
            aliases.any { it.contains(normalizedQuery, ignoreCase = true) } ||
            secondaryKeywords.any { it.contains(normalizedQuery, ignoreCase = true) } ||
            sourceBookName.contains(normalizedQuery, ignoreCase = true) ||
            scopeId.contains(normalizedQuery, ignoreCase = true)
    }

    fun resolvedScopeId(): String {
        return scopeId.trim()
    }

    fun resolvedBookId(): String {
        return bookId.trim().ifBlank {
            sourceBookName.trim()
                .takeIf { it.isNotBlank() }
                ?.let(::deriveWorldBookBookId)
                .orEmpty()
        }
    }
}

fun deriveWorldBookBookId(bookName: String): String {
    val normalizedBookName = bookName.trim()
        .replace(Regex("""\s+"""), " ")
        .lowercase()
    if (normalizedBookName.isBlank()) {
        return ""
    }
    return "worldbook-" + UUID.nameUUIDFromBytes(
        normalizedBookName.toByteArray(StandardCharsets.UTF_8),
    ).toString()
}
