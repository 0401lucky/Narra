package com.example.myapplication.data.repository.context

import androidx.room.withTransaction
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryCurrentStateEntity
import com.example.myapplication.data.local.memory.MemoryResidentEntryEntity
import com.example.myapplication.data.local.memory.MemorySourceEvidenceEntity
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import kotlinx.coroutines.CancellationException
import java.util.UUID

/** 人工修改使用独立 MANUAL 来源；原始消息改变不会抹掉用户的新修订。 */
internal class MemoryKnowledgeEditing(
    private val database: ChatDatabase,
    private val validContext: suspend (MemoryRuntimeContext) -> Boolean,
    validSource: suspend (MemoryRuntimeContext, MemorySourceRef) -> Boolean,
    private val now: () -> Long,
) {
    private val archive = database.memoryArchiveDao()
    private val dao = database.memoryKnowledgeDao()
    private val storage = MemoryKnowledgeStorage(database, validSource)

    suspend fun addResident(
        context: MemoryRuntimeContext,
        category: MemoryResidentCategory,
        content: String,
        subjective: Boolean
    ): MemoryKnowledgeWriteResult =
        database.withTransaction {
            if (!validContext(context)) return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
            if (!validContent(content)) return@withTransaction conflict(MemoryConflict.INVALID_INPUT)
            val timestamp = now()
            val row = MemoryResidentEntryEntity(
                UUID.randomUUID().toString(),
                context.spaceId,
                context.characterId,
                category.name,
                context.personaKey,
                content.trim(),
                1L,
                "READY",
                subjective,
                null,
                true,
                timestamp,
                timestamp
            )
            archive.upsertResidentEntry(row)
            saveResident(context, row, listOf(manualSource(context, row.content)), emptyList())
            MemoryKnowledgeWriteResult.Written(row.id, row.revision)
        }

    suspend fun editResident(
        context: MemoryRuntimeContext,
        id: String,
        expected: Long,
        content: String,
        subjective: Boolean
    ): MemoryKnowledgeWriteResult =
        database.withTransaction {
            if (!validContext(context)) return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
            val previous = dao.getResident(id)
                ?: return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
            if (!owned(
                    context,
                    previous
                ) || previous.revision != expected || previous.status == "DELETED"
            ) return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
            if (!validContent(content)) return@withTransaction conflict(MemoryConflict.INVALID_INPUT)
            storage.rememberResident(context, previous)
            val row = previous.copy(
                content = content.trim(), revision = previous.revision + 1, status = "READY",
                subjective = subjective, editedByUser = true, updatedAt = now()
            )
            archive.upsertResidentEntry(row)
            saveResident(context, row, listOf(manualSource(context, row.content)), emptyList())
            MemoryKnowledgeWriteResult.Written(row.id, row.revision)
        }

    suspend fun archiveResident(
        context: MemoryRuntimeContext,
        id: String,
        expected: Long,
        archived: Boolean
    ): MemoryKnowledgeWriteResult =
        database.withTransaction {
            if (!validContext(context)) return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
            val previous = dao.getResident(id)
                ?: return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
            if (!owned(
                    context,
                    previous
                ) || previous.revision != expected || previous.status == "DELETED"
            ) return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
            val evidence = storage.evidence(context, "RESIDENT", id, previous.revision)
            if (!archived && (evidence == null || previous.content.isBlank())) return@withTransaction conflict(
                MemoryConflict.SOURCE_CHANGED
            )
            // 归档只改变可用状态，不能把未核实的旧正文与它的真实来源分离。
            val retainedEvidence = evidence
                ?: storage.historicalEvidence(context, "RESIDENT", id, previous.revision)
            storage.rememberResident(context, previous)
            val row = previous.copy(
                status = if (archived) "ARCHIVED" else "READY", revision = previous.revision + 1,
                editedByUser = true, updatedAt = now()
            )
            archive.upsertResidentEntry(row)
            saveResident(context, row, retainedEvidence.sources, retainedEvidence.records)
            MemoryKnowledgeWriteResult.Written(row.id, row.revision)
        }

    suspend fun setState(
        context: MemoryRuntimeContext,
        key: MemoryStateKey,
        expected: Long,
        content: String,
        expiresAt: Long?
    ): MemoryKnowledgeWriteResult =
        database.withTransaction {
            if (!validContext(context)) return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
            if (!validContent(content) || expiresAt != null && expiresAt <= 0) return@withTransaction conflict(
                MemoryConflict.INVALID_INPUT
            )
            val previous = dao.listStates(context.spaceId, context.characterId, context.personaKey)
                .firstOrNull { it.stateKey.equals(key.name, true) }
            if ((previous?.revision ?: 0) != expected) return@withTransaction conflict(
                MemoryConflict.RECORD_CHANGED
            )
            previous?.let { storage.rememberState(context, it) }
            val timestamp = now()
            val expiry = expiresAt ?: timestamp + key.defaultLifetimeMillis
            val row = MemoryCurrentStateEntity(
                previous?.id ?: MemoryArchiveIdentity.id(
                    "state",
                    context.spaceId,
                    context.personaKey,
                    key.name
                ),
                context.spaceId,
                context.characterId,
                key.name.lowercase(),
                context.personaKey,
                content.trim(),
                expected + 1,
                if (expiry <= timestamp) "EXPIRED" else "ACTIVE",
                expiry,
                null,
                previous?.createdAt ?: timestamp,
                timestamp,
                true,
                timestamp
            )
            archive.upsertCurrentState(row)
            saveState(context, row, listOf(manualSource(context, row.content)))
            MemoryKnowledgeWriteResult.Written(row.id, row.revision)
        }

    suspend fun endState(
        context: MemoryRuntimeContext,
        id: String,
        expected: Long
    ): MemoryKnowledgeWriteResult = database.withTransaction {
        if (!validContext(context)) return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
        val previous =
            dao.getState(id) ?: return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
        if (!owned(
                context,
                previous
            ) || previous.revision != expected || previous.status == "DELETED"
        ) return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
        storage.rememberState(context, previous)
        val row = previous.copy(
            revision = expected + 1,
            status = "ENDED",
            endedAt = now(),
            observedAt = now(),
            editedByUser = true,
            updatedAt = now()
        )
        archive.upsertCurrentState(row)
        saveState(context, row, listOf(manualSource(context, "用户结束当前状态：${row.content}")))
        MemoryKnowledgeWriteResult.Written(row.id, row.revision)
    }

    suspend fun delete(
        context: MemoryRuntimeContext,
        kind: MemoryMaterialKind,
        id: String,
        expected: Long
    ): MemoryKnowledgeWriteResult = database.withTransaction {
        if (!validContext(context)) return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
        when (kind) {
            MemoryMaterialKind.RESIDENT -> {
                val row = dao.getResident(id)
                    ?: return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
                if (!owned(
                        context,
                        row
                    ) || row.revision != expected || row.status == "DELETED"
                ) return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
                archive.upsertResidentEntry(
                    row.copy(
                        content = "",
                        revision = expected + 1,
                        status = "DELETED",
                        editedByUser = true,
                        updatedAt = now()
                    )
                )
            }

            MemoryMaterialKind.STATE -> {
                val row = dao.getState(id)
                    ?: return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
                if (!owned(
                        context,
                        row
                    ) || row.revision != expected || row.status == "DELETED"
                ) return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
                archive.upsertCurrentState(
                    row.copy(
                        content = "",
                        revision = expected + 1,
                        status = "DELETED",
                        editedByUser = true,
                        endedAt = now(),
                        observedAt = now(),
                        updatedAt = now()
                    )
                )
            }

            else -> return@withTransaction conflict(MemoryConflict.INVALID_INPUT)
        }
        val revisions = storage.revisions(kind.name, id)
        val sourceIds = revisions.flatMap { it.sourceRefs }.map { it.sourceId }.toSet()
        dao.deleteRevisions(kind.name, id)
        dao.deleteOwnerLinks(kind.name, id)
        com.example.myapplication.data.local.memory.MemoryArchiveInvalidation(
            archive, database.memoryDao(), dao,
        ).scrubUnreferencedSources(sourceIds, now())
        scrubJobs(context.spaceId, kind, id, sourceIds)
        MemoryKnowledgeWriteResult.Written(id, expected + 1)
    }

    private suspend fun scrubJobs(
        spaceId: String,
        kind: MemoryMaterialKind,
        id: String,
        sourceIds: Set<String>
    ) {
        archive.listSpaceJobs(spaceId).forEach { job ->
            val targetReferenced = if (job.requestJson.isBlank()) false else try {
                val targets = AppJson.gson.fromJson(
                    job.requestJson,
                    MemoryExtractionSnapshot::class.java
                )?.knowledgeTargets
                when (kind) {
                    MemoryMaterialKind.RESIDENT -> targets?.residents?.any { it.id == id } == true
                    MemoryMaterialKind.STATE -> targets?.states?.any { it.id == id } == true
                    else -> false
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            val sourceReferenced = archive.listJobSources(job.id).any { it.sourceId in sourceIds }
            if (targetReferenced || sourceReferenced) archive.upsertJob(
                job.copy(
                    artifact = "",
                    requestJson = "",
                    status = if (job.status == "COMMITTED") "COMMITTED" else "INVALIDATED",
                    leaseOwner = "",
                    leaseUntil = 0,
                    failureCode = "KNOWLEDGE_DELETED",
                    updatedAt = now()
                )
            )
        }
    }

    private suspend fun saveResident(
        context: MemoryRuntimeContext,
        row: MemoryResidentEntryEntity,
        refs: List<MemorySourceRef>,
        records: List<MemoryRecordVersion>
    ) =
        storage.writeRevision(
            context, "RESIDENT", row.id, row.revision, row.content, row.status,
            MemoryChangeOrigin.MANUAL, row.subjective, null, null, refs, records, row.updatedAt
        )

    private suspend fun saveState(
        context: MemoryRuntimeContext,
        row: MemoryCurrentStateEntity,
        refs: List<MemorySourceRef>
    ) =
        storage.writeRevision(
            context,
            "STATE",
            row.id,
            row.revision,
            row.content,
            row.status,
            MemoryChangeOrigin.MANUAL,
            false,
            row.expiresAt,
            row.endedAt,
            refs,
            emptyList(),
            row.updatedAt
        )

    private suspend fun manualSource(
        context: MemoryRuntimeContext,
        content: String
    ): MemorySourceRef {
        val id = UUID.randomUUID().toString()
        archive.insertSource(
            MemorySourceEvidenceEntity(
                id,
                context.spaceId,
                context.characterId,
                "MANUAL",
                context.conversationId,
                null,
                1L,
                "user",
                "USER",
                context.personaKey,
                AppJson.gson.toJson(listOf(context.characterId)),
                content,
                content.length,
                "AVAILABLE",
                now(),
                now(),
                MemoryArchiveIdentity.fingerprint(content)
            )
        )
        return MemorySourceRef(id, 1L, 0, content.length)
    }

    private fun owned(context: MemoryRuntimeContext, row: MemoryResidentEntryEntity) =
        MemoryKnowledgeStore.sameOwner(context, row.spaceId, row.characterId, row.personaKey)

    private fun owned(context: MemoryRuntimeContext, row: MemoryCurrentStateEntity) =
        MemoryKnowledgeStore.sameOwner(context, row.spaceId, row.characterId, row.personaKey)

    private fun validContent(content: String) = content.isNotBlank() && content.length <= 12_000
    private fun conflict(reason: MemoryConflict) = MemoryKnowledgeWriteResult.Conflict(reason)
}
