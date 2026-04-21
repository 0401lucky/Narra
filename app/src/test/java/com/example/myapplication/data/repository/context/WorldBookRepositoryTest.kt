package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.worldbook.WorldBookDao
import com.example.myapplication.data.local.worldbook.WorldBookEntryEntity
import com.example.myapplication.model.WorldBookEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WorldBookRepositoryTest {
    @Test
    fun upsertEntry_preservesTrailingNewlineInTitleAndContent() = runTest {
        val dao = RecordingWorldBookDao()
        val repository = RoomWorldBookRepository(dao)

        repository.upsertEntry(
            WorldBookEntry(
                id = "entry-1",
                title = "标题带末尾换行\n",
                content = "正文\n\n\n",
                sourceBookName = "书",
            ),
        )

        val saved = dao.captured
        assertEquals("标题带末尾换行\n", saved?.title)
        assertEquals("正文\n\n\n", saved?.content)
    }

    @Test
    fun upsertEntry_stillTrimsSourceBookNameAndScopeId() = runTest {
        val dao = RecordingWorldBookDao()
        val repository = RoomWorldBookRepository(dao)

        repository.upsertEntry(
            WorldBookEntry(
                id = "entry-2",
                title = "x",
                content = "y",
                sourceBookName = "  带空格的书名  ",
                scopeId = "  conv-1  ",
            ),
        )

        val saved = dao.captured
        assertEquals("带空格的书名", saved?.sourceBookName)
        assertEquals("conv-1", saved?.scopeId)
    }

    private class RecordingWorldBookDao : WorldBookDao {
        var captured: WorldBookEntryEntity? = null
        private val stream = MutableStateFlow<List<WorldBookEntryEntity>>(emptyList())

        override fun observeEntries(): Flow<List<WorldBookEntryEntity>> = stream
        override suspend fun listEntries(): List<WorldBookEntryEntity> = stream.value
        override suspend fun listEnabledEntries(): List<WorldBookEntryEntity> = stream.value.filter { it.enabled }
        override suspend fun getEntry(entryId: String): WorldBookEntryEntity? = stream.value.firstOrNull { it.id == entryId }
        override suspend fun upsertEntry(entry: WorldBookEntryEntity) {
            captured = entry
            stream.value = stream.value.filterNot { it.id == entry.id } + entry
        }
        override suspend fun deleteEntry(entryId: String) {
            stream.value = stream.value.filterNot { it.id == entryId }
        }
        override suspend fun updateBookName(bookId: String, newName: String, updatedAt: Long): Int {
            var changed = 0
            stream.value = stream.value.map { entity ->
                if (entity.bookId == bookId) {
                    changed++
                    entity.copy(sourceBookName = newName, updatedAt = updatedAt)
                } else {
                    entity
                }
            }
            return changed
        }
        override suspend fun deleteByBookId(bookId: String): Int {
            val before = stream.value.size
            stream.value = stream.value.filterNot { it.bookId == bookId }
            return before - stream.value.size
        }
    }
}
