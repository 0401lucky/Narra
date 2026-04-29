package com.example.myapplication.model

data class ContextDataBundle(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val assistants: List<Assistant> = emptyList(),
    val worldBookEntries: List<WorldBookEntry> = emptyList(),
    val memoryEntries: List<MemoryEntry> = emptyList(),
    val conversationSummaries: List<ConversationSummary> = emptyList(),
    val presets: List<Preset> = emptyList(),
)
