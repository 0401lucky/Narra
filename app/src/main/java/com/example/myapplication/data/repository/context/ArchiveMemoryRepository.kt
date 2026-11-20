package com.example.myapplication.data.repository.context

import androidx.room.withTransaction
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryRecordEntity
import com.example.myapplication.model.MEMORY_ENGINE_CURRENT
import com.example.myapplication.model.MEMORY_UNASSIGNED_CHARACTER
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.MemoryWriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 旧管理入口只读主档案投影，写入必须携带用户实际看到的修订。 */
class ArchiveMemoryRepository(
    private val database: ChatDatabase,
    private val legacy: MemoryRepository,
    private val archive: MemoryArchiveRepository,
) : MemoryRepository {
    private val dao = database.memoryArchiveDao()

    override fun observeEntries(): Flow<List<MemoryEntry>> =
        combine(legacy.observeEntries(), dao.observeLegacyRecords()) { old, _ ->
            if (enabled()) dao.listLegacyRecords().map { it.legacyEntry() } else old
        }

    override suspend fun listEntries(): List<MemoryEntry> =
        if (enabled()) dao.listLegacyRecords().map { it.legacyEntry() } else legacy.listEntries()

    override suspend fun findEntryBySourceMessage(
        scopeType: MemoryScopeType,
        scopeId: String,
        sourceMessageId: String
    ): MemoryEntry? =
        listEntries().firstOrNull { it.scopeType == scopeType && it.scopeId == scopeId.trim() && it.sourceMessageId == sourceMessageId }

    override suspend fun upsertEntry(entry: MemoryEntry) {
        if (!enabled()) return legacy.upsertEntry(entry)
        check(archive.upsertLegacyEntry(entry) is MemoryWriteResult.Committed) { "记忆已经更新，请刷新后重新编辑" }
    }

    override suspend fun deleteEntry(entryId: String) {
        if (!enabled()) return legacy.deleteEntry(entryId)
        error("删除记忆需要当前查看的版本，请刷新后重试")
    }

    override suspend fun deleteEntry(entryId: String, expectedRevision: Long) {
        if (!enabled()) return legacy.deleteEntry(entryId)
        val record = dao.getRecord(entryId)?.takeIf { it.legacyScopeType.isNotBlank() }
            ?: error("该兼容记忆已不存在")
        val context =
            archive.getManagementContext(record.characterId, record.spaceId, record.personaKey)
                ?: error("该记忆档案已不可用")
        check(
            archive.deleteRecord(
                context,
                entryId,
                expectedRevision
            ) is MemoryWriteResult.Committed
        ) { "记忆已经更新，请刷新后重新删除" }
    }

    override suspend fun pruneToCapacity(capacity: Int) {
        if (!enabled()) legacy.pruneToCapacity(capacity)
    }

    override suspend fun markEntriesUsed(entryIds: List<String>, timestamp: Long) {
        if (!enabled()) return legacy.markEntriesUsed(entryIds, timestamp)
        if (entryIds.isEmpty()) return
        database.withTransaction {
            dao.markRecordsUsed(entryIds, timestamp)
            val projection = MemoryRecordStorage(database)
            entryIds.forEach { dao.getRecord(it)?.let { row -> projection.projectLegacy(row) } }
        }
    }

    private suspend fun enabled(): Boolean {
        val engine = archive.getEngineVersion()
        return engine >= MEMORY_ENGINE_CURRENT || (dao.getEngineState()?.activationVersion
            ?: 0) >= MEMORY_ENGINE_CURRENT
    }
}

internal fun MemoryRecordEntity.legacyEntry() = MemoryEntry(
    id = id,
    scopeType = MemoryScopeType.fromStorageValue(legacyScopeType),
    scopeId = legacyScopeId,
    characterId = characterId.takeUnless { it == MEMORY_UNASSIGNED_CHARACTER }.orEmpty(),
    content = content,
    importance = importance,
    pinned = pinned,
    sourceMessageId = legacySourceMessageId,
    lastUsedAt = lastUsedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archiveRevision = revision,
)
