package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.worldbook.WorldBookDao
import com.example.myapplication.data.local.worldbook.WorldBookEntryEntity
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.WORLD_BOOK_MAX_PRIMARY_KEYWORDS
import com.example.myapplication.model.WORLD_BOOK_MAX_SECONDARY_KEYWORDS
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookMatchMode
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.model.deriveWorldBookBookId
import com.example.myapplication.model.normalizedForContextImport
import com.example.myapplication.model.normalizeWorldBookKeywords
import com.example.myapplication.system.json.AppJson
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface WorldBookRepository {
    fun observeEntries(): Flow<List<WorldBookEntry>>

    suspend fun listEntries(): List<WorldBookEntry>

    suspend fun listEnabledEntries(): List<WorldBookEntry>

    /**
     * 直接在 SQL 层按"对当前 assistant + conversation 可访问"过滤，
     * 减少 Matcher / 工具热路径把全量条目拉到内存再过滤的开销。
     *
     * 与 `WorldBookScopeSupport.filterAccessibleEntries` 语义一致：
     *   GLOBAL 全部返回；
     *   ATTACHABLE 只返回 assistant.linkedWorldBookIds / linkedWorldBookBookIds 命中的条目；
     *   ASSISTANT 只返回 scopeId == assistant.id 的条目；
     *   CONVERSATION 只返回 scopeId == conversation.id 的条目。
     */
    suspend fun listAccessibleEnabledEntries(
        assistant: Assistant?,
        conversation: Conversation?,
    ): List<WorldBookEntry>

    suspend fun getEntry(entryId: String): WorldBookEntry?

    suspend fun upsertEntry(entry: WorldBookEntry)

    suspend fun deleteEntry(entryId: String)

    suspend fun renameBook(bookId: String, newBookName: String)

    suspend fun deleteBook(bookId: String)
}

class RoomWorldBookRepository(
    private val worldBookDao: WorldBookDao,
) : WorldBookRepository {
    private val gson = AppJson.gson
    private val stringListType = object : TypeToken<List<String>>() {}.type

    override fun observeEntries(): Flow<List<WorldBookEntry>> {
        return worldBookDao.observeEntries().map { entries ->
            entries.map(::toDomain)
        }
    }

    override suspend fun listEntries(): List<WorldBookEntry> {
        return worldBookDao.listEntries().map(::toDomain)
    }

    override suspend fun listEnabledEntries(): List<WorldBookEntry> {
        return worldBookDao.listEnabledEntries().map(::toDomain)
    }

    override suspend fun listAccessibleEnabledEntries(
        assistant: Assistant?,
        conversation: Conversation?,
    ): List<WorldBookEntry> {
        val linkedEntryIds = assistant?.linkedWorldBookIds
            ?.mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() } }
            .orEmpty()
        val linkedBookIds = assistant?.linkedWorldBookBookIds
            ?.mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() } }
            .orEmpty()
        val assistantId = assistant?.id?.trim().orEmpty()
        val conversationId = conversation?.id?.trim().orEmpty()
        return worldBookDao.listAccessibleEnabledEntries(
            assistantId = assistantId,
            conversationId = conversationId,
            linkedEntryIds = linkedEntryIds,
            linkedBookIds = linkedBookIds,
        ).map(::toDomain)
    }

    override suspend fun getEntry(entryId: String): WorldBookEntry? {
        return worldBookDao.getEntry(entryId)?.let(::toDomain)
    }

    override suspend fun upsertEntry(entry: WorldBookEntry) {
        worldBookDao.upsertEntry(toEntity(entry))
    }

    override suspend fun deleteEntry(entryId: String) {
        worldBookDao.deleteEntry(entryId)
    }

    override suspend fun renameBook(bookId: String, newBookName: String) {
        val normalizedBookId = bookId.trim()
        val normalizedName = newBookName.trim()
        if (normalizedBookId.isBlank() || normalizedName.isBlank()) return
        worldBookDao.updateBookName(
            bookId = normalizedBookId,
            newName = normalizedName,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun deleteBook(bookId: String) {
        val normalizedBookId = bookId.trim()
        if (normalizedBookId.isBlank()) return
        worldBookDao.deleteByBookId(normalizedBookId)
    }

    private fun toDomain(entity: WorldBookEntryEntity): WorldBookEntry {
        return WorldBookEntry(
            id = entity.id,
            bookId = entity.bookId,
            title = entity.title,
            content = entity.content,
            keywords = decodeStringList(entity.keywordsJson),
            aliases = decodeStringList(entity.aliasesJson),
            secondaryKeywords = decodeStringList(entity.secondaryKeywordsJson),
            enabled = entity.enabled,
            alwaysActive = entity.alwaysActive,
            selective = entity.selective,
            caseSensitive = entity.caseSensitive,
            matchMode = WorldBookMatchMode.fromStorageValue(entity.matchMode),
            priority = entity.priority,
            insertionOrder = entity.insertionOrder,
            probability = entity.probability.coerceIn(0, 100),
            sourceBookName = entity.sourceBookName,
            scopeType = WorldBookScopeType.fromStorageValue(entity.scopeType),
            scopeId = entity.scopeId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            extrasJson = entity.extrasJson.ifBlank { "{}" },
        )
    }

    private fun toEntity(entry: WorldBookEntry): WorldBookEntryEntity {
        val normalizedEntry = entry.normalizedForContextImport(gson)
        val normalizedSourceBookName = normalizedEntry.sourceBookName.trim()
        val resolvedBookId = normalizedEntry.bookId.trim().ifBlank {
            normalizedSourceBookName
                .takeIf { it.isNotBlank() }
                ?.let(::deriveWorldBookBookId)
                .orEmpty()
        }
        val now = System.currentTimeMillis()
        val resolvedCreatedAt = entry.createdAt.takeIf { it > 0L } ?: now
        val resolvedUpdatedAt = entry.updatedAt.takeIf { it > 0L } ?: resolvedCreatedAt
        return WorldBookEntryEntity(
            id = normalizedEntry.id,
            bookId = resolvedBookId,
            title = normalizedEntry.title,
            content = normalizedEntry.content,
            keywordsJson = gson.toJson(
                normalizeWorldBookKeywords(
                    values = normalizedEntry.keywords,
                    matchMode = normalizedEntry.matchMode,
                    maxItems = WORLD_BOOK_MAX_PRIMARY_KEYWORDS,
                ),
            ),
            aliasesJson = gson.toJson(
                normalizeWorldBookKeywords(
                    values = normalizedEntry.aliases,
                    matchMode = normalizedEntry.matchMode,
                    maxItems = WORLD_BOOK_MAX_PRIMARY_KEYWORDS,
                ),
            ),
            secondaryKeywordsJson = gson.toJson(
                normalizeWorldBookKeywords(
                    values = normalizedEntry.secondaryKeywords,
                    matchMode = normalizedEntry.matchMode,
                    maxItems = WORLD_BOOK_MAX_SECONDARY_KEYWORDS,
                ),
            ),
            enabled = normalizedEntry.enabled,
            alwaysActive = normalizedEntry.alwaysActive,
            selective = normalizedEntry.selective,
            caseSensitive = normalizedEntry.caseSensitive,
            matchMode = normalizedEntry.matchMode.storageValue,
            priority = normalizedEntry.priority,
            insertionOrder = normalizedEntry.insertionOrder,
            probability = normalizedEntry.probability.coerceIn(0, 100),
            sourceBookName = normalizedSourceBookName,
            scopeType = normalizedEntry.scopeType.storageValue,
            scopeId = normalizedEntry.resolvedScopeId(),
            createdAt = resolvedCreatedAt,
            updatedAt = resolvedUpdatedAt,
            extrasJson = normalizedEntry.extrasJson.ifBlank { "{}" },
        )
    }

    private fun decodeStringList(rawJson: String): List<String> {
        return runCatching {
            gson.fromJson<List<String>>(rawJson, stringListType).orEmpty()
        }.getOrDefault(emptyList())
            .mapNotNull { value ->
                value.trim().takeIf { it.isNotEmpty() }
            }
    }

    private fun normalizeStringList(values: List<String>): List<String> {
        return values.mapNotNull { value ->
            value.trim().takeIf { it.isNotEmpty() }
        }
    }
}

object EmptyWorldBookRepository : WorldBookRepository {
    override fun observeEntries(): Flow<List<WorldBookEntry>> = flowOf(emptyList())

    override suspend fun listEntries(): List<WorldBookEntry> = emptyList()

    override suspend fun listEnabledEntries(): List<WorldBookEntry> = emptyList()

    override suspend fun listAccessibleEnabledEntries(
        assistant: Assistant?,
        conversation: Conversation?,
    ): List<WorldBookEntry> = emptyList()

    override suspend fun getEntry(entryId: String): WorldBookEntry? = null

    override suspend fun upsertEntry(entry: WorldBookEntry) = Unit

    override suspend fun deleteEntry(entryId: String) = Unit

    override suspend fun renameBook(bookId: String, newBookName: String) = Unit

    override suspend fun deleteBook(bookId: String) = Unit
}
