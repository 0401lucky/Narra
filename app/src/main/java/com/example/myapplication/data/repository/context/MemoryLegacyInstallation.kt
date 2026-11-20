package com.example.myapplication.data.repository.context

import androidx.room.withTransaction
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryArchiveInvalidation
import com.example.myapplication.data.local.memory.MemoryEngineStateEntity
import com.example.myapplication.data.local.memory.MemoryRecordEntity
import com.example.myapplication.data.local.memory.MemorySourceEvidenceEntity
import com.example.myapplication.data.local.memory.MemorySpaceEntity
import com.example.myapplication.model.*

/** 切换前将仍由旧引擎维护的行接入档案；只做本地迁移，不建立历史提取队列。 */
internal class MemoryLegacyInstallation(
    private val database: ChatDatabase,
    private val now: () -> Long
) {
    suspend fun activate(): Int = database.withTransaction {
        val dao = database.memoryArchiveDao()
        val previousState = dao.getEngineState()
        val previousVersion = previousState?.engineVersion ?: MEMORY_ENGINE_COMPATIBILITY
        if (previousState != null && previousState.activationVersion >= MEMORY_ENGINE_CURRENT) return@withTransaction previousVersion
        if (previousState != null && previousVersion >= MEMORY_ENGINE_CURRENT) {
            dao.upsertEngineState(previousState.copy(activationVersion = MEMORY_ENGINE_CURRENT))
            return@withTransaction previousVersion
        }
        val storage = MemoryRecordStorage(database)
        val invalidation =
            MemoryArchiveInvalidation(dao, database.memoryDao(), database.memoryKnowledgeDao())
        database.memoryDao().listMemoryEntries().forEach { entry ->
            val existing = dao.getRecord(entry.id)
            if (existing?.status == "DELETED") {
                storage.projectLegacy(existing)
                return@forEach
            }
            val owner =
                entry.characterId.ifBlank { if (entry.scopeType == "assistant") entry.scopeId else MEMORY_UNASSIGNED_CHARACTER }
            val conversationId = if (entry.scopeType == "conversation") entry.scopeId else ""
            val spaceKey = RoomMemoryArchiveRepository.legacySpaceKey(owner, conversationId)
            val space = dao.findSpace(spaceKey) ?: MemorySpaceEntity(
                spaceKey, spaceKey, owner, MemoryPurpose.LEGACY_COMPAT.storageValue,
                "", "", 1, "ACTIVE", entry.createdAt, entry.updatedAt,
            ).also { dao.upsertSpace(it) }
            if (space.status == "DELETED") return@forEach
            val pristineLegacy = existing?.let {
                it.kind == MemoryRecordKind.LEGACY_UNCLASSIFIED.name && it.revision == 1L &&
                        it.spaceId == space.id && dao.listRevisions(it.id)
                    .singleOrNull()?.origin == MemoryChangeOrigin.MIGRATION.name
            } == true
            val changedLegacy = pristineLegacy && existing!!.legacyEntry()
                .copy(archiveRevision = null) != entry.let {
                MemoryEntry(
                    it.id,
                    MemoryScopeType.fromStorageValue(it.scopeType),
                    it.scopeId,
                    it.characterId,
                    it.content,
                    it.importance,
                    it.pinned,
                    it.sourceMessageId,
                    it.lastUsedAt,
                    it.createdAt,
                    it.updatedAt
                )
            }
            if (existing != null && !changedLegacy) {
                storage.projectLegacy(existing)
                return@forEach
            }
            if (existing != null) invalidation.record(existing, deleted = false, now())
            val record = MemoryRecordEntity(
                entry.id,
                space.id,
                owner,
                MemoryRecordKind.LEGACY_UNCLASSIFIED.name,
                MemoryCategory.DAILY.name,
                entry.content,
                (existing?.revision ?: 0) + 1,
                "ACTIVE",
                entry.importance,
                entry.pinned,
                null,
                null,
                "unknown",
                MEMORY_UNKNOWN_PERSONA,
                entry.createdAt,
                entry.updatedAt,
                entry.lastUsedAt,
                null,
                null,
                entry.scopeType,
                entry.scopeId,
                entry.sourceMessageId,
            )
            val originalMessage = entry.sourceMessageId.takeIf(String::isNotBlank)
                ?.let { database.conversationDao().getMessage(it) }
            val sourceId = "legacy-source:${entry.id}:${record.revision}"
            dao.insertSource(
                MemorySourceEvidenceEntity(
                    sourceId,
                    space.id,
                    owner,
                    "LEGACY_HINT",
                    conversationId.ifBlank { originalMessage?.conversationId.orEmpty() },
                    null,
                    0L,
                    "",
                    "",
                    MEMORY_UNKNOWN_PERSONA,
                    "[]",
                    "",
                    0,
                    if (originalMessage == null) "MISSING" else "UNKNOWN",
                    entry.createdAt,
                    entry.updatedAt,
                    "",
                )
            )
            dao.upsertRecord(record)
            storage.writeRevision(
                record,
                MemoryChangeOrigin.MIGRATION,
                listOf(MemorySourceRef(sourceId, 0, 0, 0)),
                entry.updatedAt
            )
        }
        dao.upsertEngineState(
            MemoryEngineStateEntity(
                engineVersion = MEMORY_ENGINE_CURRENT,
                updatedAt = now(),
                activationVersion = MEMORY_ENGINE_CURRENT
            )
        )
        MEMORY_ENGINE_CURRENT
    }
}
