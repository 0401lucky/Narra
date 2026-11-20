package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryCurrentStateEntity
import com.example.myapplication.data.local.memory.MemoryDerivedLinkEntity
import com.example.myapplication.data.local.memory.MemoryKnowledgeRevisionEntity
import com.example.myapplication.data.local.memory.MemoryKnowledgeSourceEntity
import com.example.myapplication.data.local.memory.MemoryResidentEntryEntity
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson

internal data class MemoryKnowledgeEvidence(
    val sources: List<MemorySourceRef>,
    val records: List<MemoryRecordVersion>,
)

/** 供维护和最终召回复用同一份来源证明；调用方持有主库事务。 */
internal class MemoryKnowledgeStorage(
    private val database: ChatDatabase,
    private val validSource: suspend (MemoryRuntimeContext, MemorySourceRef) -> Boolean,
) {
    private val archive = database.memoryArchiveDao()
    private val dao = database.memoryKnowledgeDao()

    suspend fun evidence(
        context: MemoryRuntimeContext,
        kind: String,
        id: String,
        revision: Long
    ): MemoryKnowledgeEvidence? {
        val direct = dao.listSources(kind, id, revision).map { it.ref() }
        if (direct.any { !validSource(context, it) }) return null
        val links = archive.listDerivedLinks(kind, id)
        val supportingSources = mutableListOf<MemorySourceRef>()
        for (link in links) {
            val record = archive.getRecord(link.recordId) ?: return null
            if (record.spaceId != context.spaceId || record.characterId != context.characterId ||
                record.personaKey != context.personaKey || record.revision != link.recordRevision ||
                record.status !in setOf("ACTIVE", "ARCHIVED") || record.content.isBlank()
            ) return null
            val refs = archive.listRecordSources(record.id, record.revision).map {
                MemorySourceRef(it.sourceId, it.sourceRevision, it.spanStart, it.spanEnd)
            }
            if (refs.isEmpty() || refs.any { !validSource(context, it) }) return null
            supportingSources += refs
        }
        val sources = (direct + supportingSources).distinct()
        if (sources.isEmpty()) return null
        return MemoryKnowledgeEvidence(
            sources,
            links.map { MemoryRecordVersion(it.recordId, it.recordRevision) })
    }

    suspend fun writeRevision(
        context: MemoryRuntimeContext,
        kind: String,
        id: String,
        revision: Long,
        content: String,
        status: String,
        origin: MemoryChangeOrigin,
        subjective: Boolean,
        expiresAt: Long?,
        endedAt: Long?,
        sources: List<MemorySourceRef>,
        records: List<MemoryRecordVersion>,
        now: Long,
    ) {
        dao.insertRevision(
            MemoryKnowledgeRevisionEntity(
                kind,
                id,
                revision,
                context.spaceId,
                content,
                status,
                origin.name,
                subjective,
                expiresAt,
                endedAt,
                AppJson.gson.toJson(records),
                now
            )
        )
        sources.distinct().forEach {
            dao.insertSource(
                MemoryKnowledgeSourceEntity(
                    kind,
                    id,
                    revision,
                    it.sourceId,
                    it.sourceRevision,
                    it.start,
                    it.end
                )
            )
        }
        dao.deleteOwnerLinks(kind, id)
        records.distinct().forEach {
            archive.upsertDerivedLink(MemoryDerivedLinkEntity(kind, id, it.recordId, it.revision))
        }
    }

    suspend fun revisions(kind: String, id: String): List<MemoryKnowledgeRevision> =
        dao.listRevisions(kind, id).map {
            MemoryKnowledgeRevision(
                MemoryMaterialKind.valueOf(it.ownerKind),
                it.ownerId,
                it.revision,
                it.content,
                it.status,
                MemoryChangeOrigin.valueOf(it.origin),
                it.subjective,
                it.expiresAt,
                it.endedAt,
                dao.listSources(kind, id, it.revision).map { source -> source.ref() },
                AppJson.gson.fromJson(it.recordRefsJson, Array<MemoryRecordVersion>::class.java)
                    .toList(),
                it.createdAt
            )
        }

    suspend fun sources(
        context: MemoryRuntimeContext,
        revisions: List<MemoryKnowledgeRevision>
    ): List<MemorySourceEvidence> =
        revisions.flatMap { it.sourceRefs }.map { it.sourceId }.distinct().mapNotNull {
            archive.getSource(it)?.takeIf { source ->
                MemoryKnowledgeStore.sameOwner(
                    context,
                    source.spaceId,
                    source.characterId,
                    source.personaKey
                )
            }?.toDomain()
        }

    suspend fun rememberResident(context: MemoryRuntimeContext, row: MemoryResidentEntryEntity) {
        if (dao.listRevisions("RESIDENT", row.id).isNotEmpty()) return
        val evidence = historicalEvidence(context, "RESIDENT", row.id, row.revision)
        writeRevision(
            context, "RESIDENT", row.id, row.revision, row.content, row.status,
            MemoryChangeOrigin.MIGRATION, row.subjective, null, null,
            evidence.sources, evidence.records, row.updatedAt
        )
    }

    suspend fun rememberState(context: MemoryRuntimeContext, row: MemoryCurrentStateEntity) {
        if (dao.listRevisions("STATE", row.id).isNotEmpty()) return
        val evidence = historicalEvidence(context, "STATE", row.id, row.revision)
        writeRevision(
            context, "STATE", row.id, row.revision, row.content, row.status,
            MemoryChangeOrigin.MIGRATION, false, row.expiresAt, row.endedAt,
            evidence.sources, evidence.records, row.updatedAt
        )
    }

    /** 历史证明保留 UNKNOWN/CHANGED 等真实状态，不借当前召回可用性丢掉原引用。 */
    suspend fun historicalEvidence(
        context: MemoryRuntimeContext,
        kind: String,
        id: String,
        revision: Long
    ): MemoryKnowledgeEvidence {
        val links = archive.listDerivedLinks(kind, id).filter { link ->
            archive.getRecord(link.recordId)?.let {
                MemoryKnowledgeStore.sameOwner(context, it.spaceId, it.characterId, it.personaKey)
            } == true
        }
        val refs = dao.listSources(kind, id, revision).map { it.ref() } + links.flatMap { link ->
            archive.listRecordSources(link.recordId, link.recordRevision).map {
                MemorySourceRef(it.sourceId, it.sourceRevision, it.spanStart, it.spanEnd)
            }
        }
        return MemoryKnowledgeEvidence(refs.distinct().filter { ref ->
            archive.getSource(ref.sourceId)?.let {
                MemoryKnowledgeStore.sameOwner(context, it.spaceId, it.characterId, it.personaKey)
            } == true
        }, links.map { MemoryRecordVersion(it.recordId, it.recordRevision) })
    }
}

internal fun MemoryKnowledgeSourceEntity.ref() =
    MemorySourceRef(sourceId, sourceRevision, spanStart, spanEnd)

internal fun MemoryResidentEntryEntity.toDomain() = MemoryResidentEntry(
    id,
    spaceId,
    characterId,
    personaKey,
    MemoryResidentCategory.valueOf(category),
    content,
    revision,
    status,
    subjective,
    editedByUser,
    supersedesId,
    createdAt,
    updatedAt,
)

internal fun MemoryCurrentStateEntity.toDomain() = MemoryCurrentState(
    id,
    spaceId,
    characterId,
    personaKey,
    MemoryStateKey.valueOf(stateKey.uppercase()),
    content,
    revision,
    status,
    expiresAt,
    endedAt,
    observedAt,
    editedByUser,
    createdAt,
    updatedAt,
)
