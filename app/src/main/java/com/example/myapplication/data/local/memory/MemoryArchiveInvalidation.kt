package com.example.myapplication.data.local.memory

import com.example.myapplication.model.MemorySourceRef
import com.example.myapplication.system.json.AppJson

/** 调用者必须位于 ChatDatabase.withTransaction；所有派生正文在同一提交中失效。 */
internal class MemoryArchiveInvalidation(
    private val dao: MemoryArchiveDao,
    private val legacyDao: MemoryDao,
    private val knowledgeDao: MemoryKnowledgeDao,
) {
    private val knowledge = MemoryKnowledgeInvalidation(dao, knowledgeDao)

    suspend fun scrubUnreferencedSources(sourceIds: Collection<String>, now: Long) {
        sourceIds.distinct().forEach { sourceId ->
            val evidence = dao.getSource(sourceId) ?: return@forEach
            if (evidence.kind == "MESSAGE" || dao.hasAnyRecordSourceReference(sourceId) ||
                knowledgeDao.listSourceOwners(sourceId).isNotEmpty()
            ) return@forEach
            // 任何正文版本仍引用的共享导入源都保留；最后一个引用解除才清除快照与可执行产物。
            source(evidence, deleted = true, now)
        }
    }

    suspend fun record(record: MemoryRecordEntity, deleted: Boolean, now: Long) {
        dao.invalidateRecordEventJobs(record.id, now)
        dao.invalidateEventSummaries(record.id, now)
        dao.invalidateEventMember(record.id)
        dao.invalidateResidentEntries(record.id, now)
        dao.invalidateCurrentStates(record.id, now)
        dao.invalidateRelations(record.id, deleted)
        dao.deleteRecordEmbeddings(record.id)
        if (deleted) {
            knowledge.recordDeleted(record)
            dao.listSpaceJobs(record.spaceId).forEach { job ->
                val targets = AppJson.gson.fromJson(
                    job.targetRevisionsJson,
                    com.google.gson.JsonObject::class.java
                )
                val outputs =
                    AppJson.gson.fromJson(job.resultRecordIdsJson, Array<String>::class.java)
                if (job.id == record.jobId || targets.has(record.id) || record.id in outputs) {
                    dao.upsertJob(
                        job.copy(
                            artifact = "",
                            requestJson = "",
                            targetRevisionsJson = "{}",
                            status = if (job.status == "COMMITTED") "COMMITTED" else "INVALIDATED",
                            leaseOwner = "",
                            leaseUntil = 0L,
                            failureCode = "RECORD_DELETED",
                            updatedAt = now
                        )
                    )
                }
            }
            if (record.legacyScopeType.isNotBlank()) legacyDao.deleteMemoryEntry(record.id)
            dao.scrubEventTitles(record.id)
            dao.scrubRecordProposals(record.id)
            dao.deleteRevisions(record.id)
            dao.deleteEventMemberships(record.id)
            dao.deleteDerivedLinks(record.id)
            // 唯一键与 ID 留作无正文墓碑，旧 job/localItemId 不能重新生成这条记忆。
            dao.upsertRecord(
                record.copy(
                    content = "",
                    revision = record.revision + 1,
                    status = "DELETED",
                    updatedAt = now
                )
            )
        }
    }

    suspend fun source(source: MemorySourceEvidenceEntity, deleted: Boolean, now: Long) {
        knowledge.source(source.id, now)
        dao.upsertSource(source.copy(content = "", status = if (deleted) "DELETED" else "CHANGED"))
        dao.invalidateSourceJobs(source.id, "SOURCE_CHANGED", now)
        dao.scrubSourceArtifacts(source.id)
        dao.updateIngressStatus(source.id, "INVALIDATED")
        dao.listProposals(source.spaceId).filter { it.characterId == source.characterId }
            .forEach { proposal ->
                val refs = AppJson.gson.fromJson(
                    proposal.sourceRefsJson,
                    Array<MemorySourceRef>::class.java
                )
                if (refs.any { it.sourceId == source.id }) {
                    dao.upsertProposal(
                        proposal.copy(
                            content = "",
                            reason = "",
                            sourceRefsJson = "[]",
                            status = "INVALIDATED",
                            decidedAt = now,
                        )
                    )
                }
            }
        dao.listCurrentRecordsForSource(source.id).forEach { current ->
            record(current, deleted, now)
            if (!deleted) {
                dao.upsertRecord(current.copy(status = "STALE", updatedAt = now))
            }
        }
    }
}
