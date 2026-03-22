package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.worldbook.WorldBookDao
import com.example.myapplication.data.local.worldbook.WorldBookEntryEntity
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType
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
}

class RoomWorldBookRepository(
    private val worldBookDao: WorldBookDao,
) : WorldBookRepository {
    private val gson = Gson()
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

    private fun toDomain(entity: WorldBookEntryEntity): WorldBookEntry {
        return WorldBookEntry(
            id = entity.id,
            title = entity.title,
            content = entity.content,
            keywords = decodeStringList(entity.keywordsJson),
            aliases = decodeStringList(entity.aliasesJson),
            secondaryKeywords = decodeStringList(entity.secondaryKeywordsJson),
            enabled = entity.enabled,
            alwaysActive = entity.alwaysActive,
            selective = entity.selective,
            caseSensitive = entity.caseSensitive,
            priority = entity.priority,
            insertionOrder = entity.insertionOrder,
            sourceBookName = entity.sourceBookName,
            scopeType = WorldBookScopeType.fromStorageValue(entity.scopeType),
            scopeId = entity.scopeId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    private fun toEntity(entry: WorldBookEntry): WorldBookEntryEntity {
        return WorldBookEntryEntity(
            id = entry.id,
            title = entry.title.trim(),
            content = entry.content.trim(),
            keywordsJson = gson.toJson(normalizeStringList(entry.keywords)),
            aliasesJson = gson.toJson(normalizeStringList(entry.aliases)),
            secondaryKeywordsJson = gson.toJson(normalizeStringList(entry.secondaryKeywords)),
            enabled = entry.enabled,
            alwaysActive = entry.alwaysActive,
            selective = entry.selective,
            caseSensitive = entry.caseSensitive,
            priority = entry.priority,
            insertionOrder = entry.insertionOrder,
            sourceBookName = entry.sourceBookName.trim(),
            scopeType = entry.scopeType.storageValue,
            scopeId = entry.resolvedScopeId(),
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
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
}
