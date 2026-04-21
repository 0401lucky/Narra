package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.worldbook.WorldBookDao
import com.example.myapplication.data.local.worldbook.WorldBookEntryEntity
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun upsertEntry_persistsExtrasJsonAsIs() = runTest {
        val dao = RecordingWorldBookDao()
        val repository = RoomWorldBookRepository(dao)

        repository.upsertEntry(
            WorldBookEntry(
                id = "entry-extras",
                title = "t",
                content = "c",
                sourceBookName = "书",
                extrasJson = """{"probability":80,"depth":3}""",
            ),
        )

        assertEquals("""{"probability":80,"depth":3}""", dao.captured?.extrasJson)
    }

    @Test
    fun toDomain_fallsBackToEmptyObjectWhenExtrasBlank() = runTest {
        val dao = RecordingWorldBookDao()
        val repository = RoomWorldBookRepository(dao)

        repository.upsertEntry(
            WorldBookEntry(
                id = "entry-empty",
                title = "t",
                content = "c",
                sourceBookName = "书",
                extrasJson = "",
            ),
        )

        val loaded = repository.getEntry("entry-empty")!!
        assertEquals("{}", loaded.extrasJson)
    }

    @Test
    fun upsertEntry_fillsTimestampsWhenZero() = runTest {
        val dao = RecordingWorldBookDao()
        val repository = RoomWorldBookRepository(dao)
        val before = System.currentTimeMillis()

        repository.upsertEntry(
            WorldBookEntry(
                id = "entry-ts",
                title = "t",
                content = "c",
                sourceBookName = "书",
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )

        val saved = dao.captured!!
        assertTrue("createdAt 应被兜底为当前时间", saved.createdAt >= before)
        assertEquals(saved.createdAt, saved.updatedAt)
    }

    @Test
    fun upsertEntry_preservesExplicitTimestamps() = runTest {
        val dao = RecordingWorldBookDao()
        val repository = RoomWorldBookRepository(dao)

        repository.upsertEntry(
            WorldBookEntry(
                id = "entry-ts-2",
                title = "t",
                content = "c",
                sourceBookName = "书",
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_001_000L,
            ),
        )

        assertEquals(1_700_000_000_000L, dao.captured?.createdAt)
        assertEquals(1_700_000_001_000L, dao.captured?.updatedAt)
    }

    @Test
    fun upsertEntry_usesCreatedAtAsFallbackForUpdatedAt() = runTest {
        val dao = RecordingWorldBookDao()
        val repository = RoomWorldBookRepository(dao)

        repository.upsertEntry(
            WorldBookEntry(
                id = "entry-ts-3",
                title = "t",
                content = "c",
                sourceBookName = "书",
                createdAt = 1_700_000_000_000L,
                updatedAt = 0L,
            ),
        )

        assertEquals(1_700_000_000_000L, dao.captured?.createdAt)
        assertEquals(
            "updatedAt 为 0 时应沿用 createdAt，而不是覆盖为当前时间",
            1_700_000_000_000L,
            dao.captured?.updatedAt,
        )
    }

    @Test
    fun upsertEntry_persistsProbabilityWithinRange() = runTest {
        val dao = RecordingWorldBookDao()
        val repository = RoomWorldBookRepository(dao)

        repository.upsertEntry(
            WorldBookEntry(
                id = "entry-prob",
                title = "t",
                content = "c",
                sourceBookName = "书",
                probability = 45,
            ),
        )

        assertEquals(45, dao.captured?.probability)
        assertEquals(45, repository.getEntry("entry-prob")?.probability)
    }

    @Test
    fun upsertEntry_clampsProbabilityToValidRange() = runTest {
        val dao = RecordingWorldBookDao()
        val repository = RoomWorldBookRepository(dao)

        repository.upsertEntry(
            WorldBookEntry(
                id = "entry-over",
                title = "t",
                content = "c",
                sourceBookName = "书",
                probability = 150,
            ),
        )
        repository.upsertEntry(
            WorldBookEntry(
                id = "entry-neg",
                title = "t",
                content = "c",
                sourceBookName = "书",
                probability = -10,
            ),
        )

        assertEquals(100, repository.getEntry("entry-over")?.probability)
        assertEquals(0, repository.getEntry("entry-neg")?.probability)
    }

    @Test
    fun listAccessibleEnabledEntries_returnsGlobalAlwaysAndScopedEntries() = runTest {
        val dao = RecordingWorldBookDao()
        val repository = RoomWorldBookRepository(dao)
        val now = 1_700_000_000_000L
        dao.seed(
            listOf(
                entryEntity(id = "g1", enabled = true, scopeType = WorldBookScopeType.GLOBAL, now = now),
                entryEntity(id = "g-off", enabled = false, scopeType = WorldBookScopeType.GLOBAL, now = now),
                entryEntity(
                    id = "att-by-id",
                    enabled = true,
                    scopeType = WorldBookScopeType.ATTACHABLE,
                    bookId = "book-att",
                    now = now,
                ),
                entryEntity(
                    id = "att-by-book",
                    enabled = true,
                    scopeType = WorldBookScopeType.ATTACHABLE,
                    bookId = "book-att",
                    now = now,
                ),
                entryEntity(
                    id = "att-other",
                    enabled = true,
                    scopeType = WorldBookScopeType.ATTACHABLE,
                    bookId = "book-other",
                    now = now,
                ),
                entryEntity(
                    id = "assist-match",
                    enabled = true,
                    scopeType = WorldBookScopeType.ASSISTANT,
                    scopeId = "asst-1",
                    now = now,
                ),
                entryEntity(
                    id = "assist-other",
                    enabled = true,
                    scopeType = WorldBookScopeType.ASSISTANT,
                    scopeId = "asst-99",
                    now = now,
                ),
                entryEntity(
                    id = "conv-match",
                    enabled = true,
                    scopeType = WorldBookScopeType.CONVERSATION,
                    scopeId = "conv-1",
                    now = now,
                ),
                entryEntity(
                    id = "conv-other",
                    enabled = true,
                    scopeType = WorldBookScopeType.CONVERSATION,
                    scopeId = "conv-99",
                    now = now,
                ),
            ),
        )

        val result = repository.listAccessibleEnabledEntries(
            assistant = Assistant(
                id = "asst-1",
                name = "A",
                linkedWorldBookIds = listOf("att-by-id"),
                linkedWorldBookBookIds = listOf("book-att"),
            ),
            conversation = Conversation(id = "conv-1", title = "c", createdAt = now, updatedAt = now),
        )

        val ids = result.map { it.id }.sorted()
        assertEquals(
            "只返回：global + 匹配的 attachable（id 或 bookId）+ 匹配的 assistant + 匹配的 conversation",
            listOf("assist-match", "att-by-book", "att-by-id", "conv-match", "g1"),
            ids,
        )
    }

    @Test
    fun listAccessibleEnabledEntries_withoutAssistantOnlyReturnsGlobal() = runTest {
        val dao = RecordingWorldBookDao()
        val repository = RoomWorldBookRepository(dao)
        val now = 1_700_000_000_000L
        dao.seed(
            listOf(
                entryEntity(id = "g1", enabled = true, scopeType = WorldBookScopeType.GLOBAL, now = now),
                entryEntity(
                    id = "att",
                    enabled = true,
                    scopeType = WorldBookScopeType.ATTACHABLE,
                    bookId = "book-a",
                    now = now,
                ),
                entryEntity(
                    id = "assist",
                    enabled = true,
                    scopeType = WorldBookScopeType.ASSISTANT,
                    scopeId = "asst-1",
                    now = now,
                ),
            ),
        )

        val result = repository.listAccessibleEnabledEntries(
            assistant = null,
            conversation = null,
        )

        assertEquals(listOf("g1"), result.map { it.id })
    }

    private fun entryEntity(
        id: String,
        enabled: Boolean,
        scopeType: WorldBookScopeType,
        now: Long,
        bookId: String = "",
        scopeId: String = "",
    ): WorldBookEntryEntity = WorldBookEntryEntity(
        id = id,
        bookId = bookId,
        title = id,
        content = "content",
        scopeType = scopeType.storageValue,
        scopeId = scopeId,
        enabled = enabled,
        createdAt = now,
        updatedAt = now,
    )

    private class RecordingWorldBookDao : WorldBookDao {
        var captured: WorldBookEntryEntity? = null
        private val stream = MutableStateFlow<List<WorldBookEntryEntity>>(emptyList())

        fun seed(entities: List<WorldBookEntryEntity>) {
            stream.value = entities
        }

        override fun observeEntries(): Flow<List<WorldBookEntryEntity>> = stream
        override suspend fun listEntries(): List<WorldBookEntryEntity> = stream.value
        override suspend fun listEnabledEntries(): List<WorldBookEntryEntity> = stream.value.filter { it.enabled }
        override suspend fun listAccessibleEnabledEntries(
            assistantId: String,
            conversationId: String,
            linkedEntryIds: List<String>,
            linkedBookIds: List<String>,
        ): List<WorldBookEntryEntity> = stream.value.filter { entity ->
            if (!entity.enabled) return@filter false
            when (entity.scopeType) {
                WorldBookScopeType.GLOBAL.storageValue -> true
                WorldBookScopeType.ATTACHABLE.storageValue ->
                    entity.id in linkedEntryIds || entity.bookId in linkedBookIds
                WorldBookScopeType.ASSISTANT.storageValue ->
                    assistantId.isNotEmpty() && entity.scopeId == assistantId
                WorldBookScopeType.CONVERSATION.storageValue ->
                    conversationId.isNotEmpty() && entity.scopeId == conversationId
                else -> false
            }
        }
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
