package com.example.myapplication.testutil

import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.model.WorldBookEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeWorldBookRepository(
    initialEntries: List<WorldBookEntry> = emptyList(),
) : WorldBookRepository {
    private val entriesState = MutableStateFlow(initialEntries)

    override fun observeEntries(): Flow<List<WorldBookEntry>> = entriesState

    override suspend fun listEntries(): List<WorldBookEntry> = entriesState.value

    override suspend fun listEnabledEntries(): List<WorldBookEntry> {
        return entriesState.value.filter { it.enabled }
    }

    override suspend fun getEntry(entryId: String): WorldBookEntry? {
        return entriesState.value.firstOrNull { it.id == entryId }
    }

    override suspend fun upsertEntry(entry: WorldBookEntry) {
        entriesState.value = entriesState.value
            .filterNot { it.id == entry.id } + entry
    }

    override suspend fun deleteEntry(entryId: String) {
        entriesState.value = entriesState.value.filterNot { it.id == entryId }
    }
}
