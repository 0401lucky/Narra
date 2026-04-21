package com.example.myapplication.testutil

import com.example.myapplication.context.WorldBookScopeSupport
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.WorldBookEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeWorldBookRepository(
    initialEntries: List<WorldBookEntry> = emptyList(),
) : WorldBookRepository {
    private val entriesState = MutableStateFlow(initialEntries)
    var failNextBookMutation: Boolean = false

    override fun observeEntries(): Flow<List<WorldBookEntry>> = entriesState

    override suspend fun listEntries(): List<WorldBookEntry> = entriesState.value

    override suspend fun listEnabledEntries(): List<WorldBookEntry> {
        return entriesState.value.filter { it.enabled }
    }

    override suspend fun listAccessibleEnabledEntries(
        assistant: Assistant?,
        conversation: Conversation?,
    ): List<WorldBookEntry> {
        return WorldBookScopeSupport.filterAccessibleEntries(
            entries = entriesState.value.filter { it.enabled },
            assistant = assistant,
            conversation = conversation,
        )
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

    override suspend fun renameBook(bookId: String, newBookName: String) {
        if (failNextBookMutation) {
            failNextBookMutation = false
            throw RuntimeException("模拟重命名失败")
        }
        val normalizedBookId = bookId.trim()
        val normalizedName = newBookName.trim()
        entriesState.value = entriesState.value.map { entry ->
            if (entry.resolvedBookId() == normalizedBookId) {
                entry.copy(sourceBookName = normalizedName, updatedAt = System.currentTimeMillis())
            } else {
                entry
            }
        }
    }

    override suspend fun deleteBook(bookId: String) {
        if (failNextBookMutation) {
            failNextBookMutation = false
            throw RuntimeException("模拟删除失败")
        }
        val normalizedBookId = bookId.trim()
        entriesState.value = entriesState.value.filterNot { it.resolvedBookId() == normalizedBookId }
    }
}
