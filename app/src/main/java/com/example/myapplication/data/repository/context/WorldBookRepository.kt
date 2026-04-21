package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.worldbook.WorldBookDao
import com.example.myapplication.data.local.worldbook.WorldBookEntryEntity
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookMatchMode
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.model.deriveWorldBookBookId
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
            sourceBookName = entity.sourceBookName,
            scopeType = WorldBookScopeType.fromStorageValue(entity.scopeType),
            scopeId = entity.scopeId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            extrasJson = entity.extrasJson.ifBlank { "{}" },
        )
    }

    private fun toEntity(entry: WorldBookEntry): WorldBookEntryEntity {
        val normalizedSourceBookName = entry.sourceBookName.trim()
        val resolvedBookId = entry.bookId.trim().ifBlank {
            normalizedSourceBookName
                .takeIf { it.isNotBlank() }
                ?.let(::deriveWorldBookBookId)
                .orEmpty()
        }
        return WorldBookEntryEntity(
            id = entry.id,
            bookId = resolvedBookId,
            title = entry.title,
            content = entry.content,
            keywordsJson = gson.toJson(normalizeStringList(entry.keywords)),
            aliasesJson = gson.toJson(normalizeStringList(entry.aliases)),
            secondaryKeywordsJson = gson.toJson(normalizeStringList(entry.secondaryKeywords)),
            enabled = entry.enabled,
            alwaysActive = entry.alwaysActive,
            selective = entry.selective,
            caseSensitive = entry.caseSensitive,
            matchMode = entry.matchMode.storageValue,
            priority = entry.priority,
            insertionOrder = entry.insertionOrder,
            sourceBookName = normalizedSourceBookName,
            scopeType = entry.scopeType.storageValue,
            scopeId = entry.resolvedScopeId(),
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
            extrasJson = entry.extrasJson.ifBlank { "{}" },
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

    override suspend fun getEntry(entryId: String): WorldBookEntry? = null

    override suspend fun upsertEntry(entry: WorldBookEntry) = Unit

    override suspend fun deleteEntry(entryId: String) = Unit

    override suspend fun renameBook(bookId: String, newBookName: String) = Unit

    override suspend fun deleteBook(bookId: String) = Unit
}
