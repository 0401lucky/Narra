package com.example.myapplication.model

import java.util.UUID

enum class MemoryScopeType(
    val storageValue: String,
    val label: String,
) {
    GLOBAL("global", "全局"),
    ASSISTANT("assistant", "助手"),
    CONVERSATION("conversation", "会话");

    companion object {
        fun fromStorageValue(value: String): MemoryScopeType {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() } ?: GLOBAL
        }
    }
}

data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val scopeType: MemoryScopeType = MemoryScopeType.GLOBAL,
    val scopeId: String = "",
    val content: String = "",
    val importance: Int = 0,
    val pinned: Boolean = false,
    val sourceMessageId: String = "",
    val lastUsedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    fun resolvedScopeId(): String {
        return scopeId.trim()
    }
}
