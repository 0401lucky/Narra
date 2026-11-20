package com.example.myapplication.data.local.memory

import com.example.myapplication.model.MemoryRecordVersion
import com.example.myapplication.system.json.AppJson

/** 派生内容的旧版本也按真实来源擦除，人工新修订独立的 MANUAL 来源不受牵连。 */
internal class MemoryKnowledgeInvalidation(
    private val archive: MemoryArchiveDao,
    private val dao: MemoryKnowledgeDao,
) {
    suspend fun source(sourceId: String, now: Long) {
        dao.listSourceOwners(sourceId)
            .distinctBy { Triple(it.ownerKind, it.ownerId, it.ownerRevision) }
            .forEach { owner ->
                dao.scrubRevision(owner.ownerKind, owner.ownerId, owner.ownerRevision)
                when (owner.ownerKind) {
                    "RESIDENT" -> dao.getResident(owner.ownerId)
                        ?.takeIf { it.revision == owner.ownerRevision }?.let {
                        archive.upsertResidentEntry(
                            it.copy(
                                content = "",
                                status = "STALE",
                                updatedAt = now
                            )
                        )
                    }

                    "STATE" -> dao.getState(owner.ownerId)
                        ?.takeIf { it.revision == owner.ownerRevision }?.let {
                        archive.upsertCurrentState(
                            it.copy(
                                content = "",
                                status = "STALE",
                                updatedAt = now
                            )
                        )
                    }
                }
            }
    }

    suspend fun recordDeleted(record: MemoryRecordEntity) {
        dao.listSpaceRevisions(record.spaceId).forEach { revision ->
            val records = AppJson.gson.fromJson(
                revision.recordRefsJson,
                Array<MemoryRecordVersion>::class.java
            )
            if (records.any { it.recordId == record.id }) {
                dao.upsertRevision(
                    revision.copy(
                        content = "",
                        status = "STALE",
                        recordRefsJson = "[]"
                    )
                )
            }
        }
    }
}
