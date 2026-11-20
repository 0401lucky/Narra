package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryEntryEntity
import com.example.myapplication.data.local.memory.MemoryRecordEntity
import com.example.myapplication.data.local.memory.MemoryRecordRevisionEntity
import com.example.myapplication.data.local.memory.MemoryRecordSourceEntity
import com.example.myapplication.model.MEMORY_UNASSIGNED_CHARACTER
import com.example.myapplication.model.MemoryChangeOrigin
import com.example.myapplication.model.MemorySourceRef

/** 记录版本和旧格式投影只在主库事务内单向写入。 */
internal class MemoryRecordStorage(private val database: ChatDatabase) {
    private val dao = database.memoryArchiveDao()

    suspend fun writeRevision(
        record: MemoryRecordEntity,
        origin: MemoryChangeOrigin,
        sources: List<MemorySourceRef>,
        now: Long
    ) {
        dao.insertRevision(
            MemoryRecordRevisionEntity(
                record.id,
                record.revision,
                record.content,
                origin.name,
                now
            )
        )
        sources.forEach {
            dao.insertRecordSource(
                MemoryRecordSourceEntity(
                    record.id,
                    record.revision,
                    it.sourceId,
                    it.sourceRevision,
                    it.start,
                    it.end
                )
            )
        }
        projectLegacy(record)
    }

    suspend fun projectLegacy(record: MemoryRecordEntity) {
        if (record.legacyScopeType.isBlank()) return
        if (record.status == "DELETED") {
            database.memoryDao().deleteMemoryEntry(record.id)
            return
        }
        database.memoryDao().upsertMemoryEntry(
            MemoryEntryEntity(
                id = record.id, scopeType = record.legacyScopeType, scopeId = record.legacyScopeId,
                characterId = record.characterId.takeUnless { it == MEMORY_UNASSIGNED_CHARACTER }
                    .orEmpty(),
                content = record.content, importance = record.importance, pinned = record.pinned,
                sourceMessageId = record.legacySourceMessageId, lastUsedAt = record.lastUsedAt,
                createdAt = record.createdAt, updatedAt = record.updatedAt,
            )
        )
    }
}
