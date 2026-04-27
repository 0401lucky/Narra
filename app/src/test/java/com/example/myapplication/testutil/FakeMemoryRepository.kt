package com.example.myapplication.testutil

import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMemoryRepository(
    initialEntries: List<MemoryEntry> = emptyList(),
) : MemoryRepository {
    private val entriesState = MutableStateFlow(initialEntries)

    var pruneCallCount: Int = 0
        private set
    var lastPruneCapacity: Int? = null
        private set

    override fun observeEntries(): Flow<List<MemoryEntry>> = entriesState

    override suspend fun listEntries(): List<MemoryEntry> = entriesState.value

    override suspend fun findEntryBySourceMessage(
        scopeType: MemoryScopeType,
        scopeId: String,
        sourceMessageId: String,
    ): MemoryEntry? {
        return entriesState.value.firstOrNull { entry ->
            entry.scopeType == scopeType &&
                entry.resolvedScopeId() == scopeId.trim() &&
                entry.sourceMessageId == sourceMessageId
        }
    }

    override suspend fun upsertEntry(entry: MemoryEntry) {
        entriesState.value = entriesState.value
            .filterNot { it.id == entry.id } + entry
    }

    override suspend fun deleteEntry(entryId: String) {
        entriesState.value = entriesState.value.filterNot { it.id == entryId }
    }

    override suspend fun pruneToCapacity(capacity: Int) {
        pruneCallCount += 1
        lastPruneCapacity = capacity
        val safeCapacity = capacity.coerceAtLeast(1)
        val sorted = entriesState.value.sortedWith(
            compareByDescending<MemoryEntry> { it.pinned }
                .thenByDescending { it.importance }
                .thenByDescending { it.updatedAt },
        )
        if (sorted.size > safeCapacity) {
            entriesState.value = sorted.take(safeCapacity)
        }
    }

    override suspend fun markEntriesUsed(entryIds: List<String>, timestamp: Long) {
        if (entryIds.isEmpty()) return
        entriesState.value = entriesState.value.map { entry ->
            if (entry.id in entryIds) {
                entry.copy(lastUsedAt = timestamp, updatedAt = timestamp)
            } else {
                entry
            }
        }
    }

    suspend fun currentEntries(): List<MemoryEntry> = entriesState.value
}
