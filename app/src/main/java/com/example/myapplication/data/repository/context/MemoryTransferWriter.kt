package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.*
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

/** 调用者持有唯一主库事务；不通过人工编辑/模型任务恢复，避免丢历史或制造任务凭据。 */
internal class MemoryTransferWriter(
    private val database: ChatDatabase,
    private val now: () -> Long,
) {
    private val dao = database.memoryTransferDao()
    private val archive = database.memoryArchiveDao()
    private val knowledge = database.memoryKnowledgeDao()

    suspend fun write(
        bundle: ContextDataBundle,
        data: MemoryArchiveTransferData,
        plan: MemoryImportPlan,
        targets: Map<String, MemoryRuntimeContext>,
    ): List<MemoryTransferObjectMapping> {
        fun target(spaceId: String, persona: String) =
            targets.getValue(MemoryTransferLegacyConversion.groupKey(spaceId, persona))

        fun ids(values: List<String>) = values.associateWith { UUID.randomUUID().toString() }
        val recordIds = ids(data.records.map { it.id })
        val sourceIds = ids(data.sources.map { it.id })
        val eventIds = ids(data.events.map { it.id })
        val residentIds = ids(data.residents.map { it.id })
        val stateIds = ids(data.states.map { it.id }).toMutableMap()
        val stateRevisions = mutableMapOf<Pair<String, Long>, Long>()
        val replacedStates = mutableSetOf<String>()
        for (state in data.states) {
            val replacement =
                plan.stateConflicts.singleOrNull { it.sourceStateId == state.id }?.targetStateId
            val revisions =
                (data.knowledgeRevisions.filter { it.kind == MemoryMaterialKind.STATE && it.ownerId == state.id }
                    .map { it.revision } +
                        data.origins.filter { it.kind == MemoryTransferObjectKind.STATE && it.localId == state.id }
                            .map { it.localRevision } + state.revision).distinct().sorted()
            if (replacement == null) revisions.forEach {
                stateRevisions[state.id to it] = it
            } else {
                val previous = knowledge.getState(replacement)
                    ?: throw MemoryTransferConflict("目标状态已移除")
                val expected =
                    plan.stateConflicts.single { it.sourceStateId == state.id }.expectedRevision
                if (previous.revision != expected) throw MemoryTransferConflict("目标状态已改变")
                val existing = knowledge.listRevisions("STATE", previous.id)
                if (existing.none { it.revision == previous.revision }) {
                    // 旧安装未保存知识历史时，只记录当前已存在头，不补造原始聊天证明。
                    knowledge.insertRevision(
                        MemoryKnowledgeRevisionEntity(
                            "STATE",
                            previous.id,
                            previous.revision,
                            previous.spaceId,
                            previous.content,
                            previous.status,
                            MemoryChangeOrigin.MIGRATION.name,
                            false,
                            previous.expiresAt,
                            previous.endedAt,
                            AppJson.gson.toJson(
                                archive.listDerivedLinks("STATE", previous.id)
                                    .map { MemoryRecordVersion(it.recordId, it.recordRevision) }),
                            previous.updatedAt,
                        )
                    )
                }
                val base = maxOf(previous.revision, existing.maxOfOrNull { it.revision } ?: 0L)
                if (base >= Long.MAX_VALUE - revisions.size - 1L) throw MemoryTransferConflict("目标状态版本已达到上限")
                revisions.forEachIndexed { index, revision ->
                    stateRevisions[state.id to revision] = base + index + 1L
                }
                stateIds[state.id] = replacement
                replacedStates += state.id
                knowledge.deleteOwnerLinks("STATE", replacement)
            }
        }
        fun ownerId(kind: MemoryMaterialKind, id: String) = when (kind) {
            MemoryMaterialKind.RESIDENT -> residentIds.getValue(id)
            MemoryMaterialKind.STATE -> stateIds.getValue(id)
            else -> error("认知类型无效")
        }

        fun revision(kind: MemoryMaterialKind, id: String, value: Long) =
            if (kind == MemoryMaterialKind.STATE) stateRevisions.getValue(id to value) else value

        fun knowledgeTarget(kind: MemoryMaterialKind, id: String): MemoryRuntimeContext =
            if (kind == MemoryMaterialKind.RESIDENT) {
                data.residents.single { it.id == id }.let { target(it.spaceId, it.personaKey) }
            } else data.states.single { it.id == id }.let { target(it.spaceId, it.personaKey) }

        currentCoroutineContext().ensureActive()
        dao.insertSources(data.sources.map {
            it.importedEntity(
                sourceIds.getValue(it.id),
                target(it.spaceId, it.personaKey)
            )
        })
        dao.insertRecords(data.records.map {
            it.importedEntity(
                recordIds.getValue(it.id),
                target(it.spaceId, it.personaKey)
            )
        })
        dao.insertRecordRevisions(data.recordRevisions.map {
            MemoryRecordRevisionEntity(
                recordIds.getValue(it.recordId),
                it.revision,
                it.content,
                it.origin.name,
                it.createdAt
            )
        })
        dao.insertRecordSources(data.recordSources.map {
            MemoryRecordSourceEntity(
                recordIds.getValue(it.recordId),
                it.recordRevision,
                sourceIds.getValue(it.source.sourceId),
                it.source.sourceRevision,
                it.source.start,
                it.source.end
            )
        })
        val recordById = data.records.associateBy { it.id }
        val sourceById = data.sources.associateBy { it.id }
        dao.insertEvents(data.events.map { event ->
            val context = target(event.spaceId, event.personaKey)
            val members = data.eventMembers.filter { it.eventId == event.id }
            val canUseSummary =
                members.isNotEmpty() && members.any { it.coveredRevision != null } &&
                        members.all { member ->
                            val record = recordById.getValue(member.recordId)
                            val refs =
                                data.recordSources.filter { it.recordId == record.id && it.recordRevision == record.revision }
                            record.status in setOf(
                                MemoryRecordStatus.ACTIVE,
                                MemoryRecordStatus.ARCHIVED
                            ) &&
                                    (member.coveredRevision == null || member.coveredRevision == record.revision) &&
                                    refs.isNotEmpty() && refs.all { sourceById.getValue(it.source.sourceId).status == MemorySourceStatus.AVAILABLE }
                        }
            MemoryEventBoxEntity(
                eventIds.getValue(event.id),
                context.spaceId,
                context.characterId,
                event.title,
                event.summary,
                event.summaryRevision,
                if (event.summaryStatus == MemoryEventSummaryStatus.READY && !canUseSummary) MemoryEventSummaryStatus.STALE.name else event.summaryStatus.name,
                event.status.name,
                event.currentState,
                1L,
                event.createdAt,
                event.updatedAt,
                context.personaKey
            )
        })
        dao.insertMembers(data.eventMembers.map {
            MemoryEventMemberEntity(
                eventIds.getValue(it.eventId),
                recordIds.getValue(it.recordId),
                it.coveredRevision,
                it.status.name,
                it.addedAt
            )
        })
        dao.insertResidents(data.residents.map {
            it.importedEntity(
                residentIds.getValue(it.id),
                target(it.spaceId, it.personaKey),
                it.supersedesId?.let(residentIds::getValue)
            )
        })
        for (state in data.states) {
            var row = state.importedEntity(
                stateIds.getValue(state.id),
                target(state.spaceId, state.personaKey),
                stateRevisions.getValue(state.id to state.revision)
            )
            if (state.id in replacedStates) row =
                row.copy(createdAt = requireNotNull(knowledge.getState(row.id)).createdAt)
            if (state.id in replacedStates) archive.upsertCurrentState(row) else dao.insertStates(
                listOf(row)
            )
        }
        currentCoroutineContext().ensureActive()
        val importedKnowledge = data.knowledgeRevisions.toMutableList()
        data.states.filter { it.id in replacedStates }.forEach { state ->
            if (importedKnowledge.none { it.kind == MemoryMaterialKind.STATE && it.ownerId == state.id && it.revision == state.revision }) {
                importedKnowledge += MemoryKnowledgeRevision(
                    MemoryMaterialKind.STATE,
                    state.id,
                    state.revision,
                    state.content,
                    state.status,
                    MemoryChangeOrigin.IMPORTED,
                    false,
                    state.expiresAt,
                    state.endedAt,
                    emptyList(),
                    data.derivedLinks.filter { it.kind == MemoryMaterialKind.STATE && it.ownerId == state.id }
                        .map { it.record },
                    state.updatedAt
                )
            }
        }
        dao.insertKnowledgeRevisions(importedKnowledge.map { entry ->
            MemoryKnowledgeRevisionEntity(
                entry.kind.name,
                ownerId(entry.kind, entry.ownerId),
                revision(entry.kind, entry.ownerId, entry.revision),
                knowledgeTarget(entry.kind, entry.ownerId).spaceId,
                entry.content,
                entry.status,
                entry.origin.name,
                entry.subjective,
                entry.expiresAt,
                entry.endedAt,
                AppJson.gson.toJson(entry.supportingRecords.map {
                    it.copy(recordId = recordIds.getValue(it.recordId))
                }),
                entry.createdAt
            )
        })
        dao.insertKnowledgeSources(importedKnowledge.flatMap { entry ->
            entry.sourceRefs.map {
                MemoryKnowledgeSourceEntity(
                    entry.kind.name,
                    ownerId(entry.kind, entry.ownerId),
                    revision(entry.kind, entry.ownerId, entry.revision),
                    sourceIds.getValue(it.sourceId),
                    it.sourceRevision,
                    it.start,
                    it.end
                )
            }
        })
        dao.insertDerivedLinks(data.derivedLinks.map {
            MemoryDerivedLinkEntity(
                it.kind.name,
                ownerId(it.kind, it.ownerId),
                recordIds.getValue(it.record.recordId),
                it.record.revision
            )
        })

        val mappings = buildList {
            MemoryTransferLegacyConversion.sourceGroups(data).forEach {
                add(
                    MemoryTransferObjectMapping(
                        MemoryTransferObjectKind.SPACE,
                        it.spaceId,
                        targets.getValue(it.key).spaceId
                    )
                )
            }
            recordIds.forEach { (old, local) ->
                add(
                    MemoryTransferObjectMapping(
                        MemoryTransferObjectKind.RECORD,
                        old,
                        local
                    )
                )
            }
            sourceIds.forEach { (old, local) ->
                add(
                    MemoryTransferObjectMapping(
                        MemoryTransferObjectKind.SOURCE,
                        old,
                        local
                    )
                )
            }
            eventIds.forEach { (old, local) ->
                add(
                    MemoryTransferObjectMapping(
                        MemoryTransferObjectKind.EVENT,
                        old,
                        local
                    )
                )
            }
            residentIds.forEach { (old, local) ->
                add(
                    MemoryTransferObjectMapping(
                        MemoryTransferObjectKind.RESIDENT,
                        old,
                        local
                    )
                )
            }
            stateIds.forEach { (old, local) ->
                add(
                    MemoryTransferObjectMapping(
                        MemoryTransferObjectKind.STATE,
                        old,
                        local
                    )
                )
            }
            stateRevisions.forEach { (old, local) ->
                add(
                    MemoryTransferObjectMapping(
                        MemoryTransferObjectKind.STATE,
                        old.first,
                        stateIds.getValue(old.first),
                        old.second,
                        local
                    )
                )
            }
        }.distinct()
        val originRows = mutableListOf<MemoryTransferOriginEntity>()
        fun origin(
            id: String?,
            space: String?,
            character: String?,
            persona: String?,
            revision: Long? = null
        ) = MemoryTransferOrigin(
            plan.localPlanId,
            bundle.version,
            bundle.exportedAt,
            now(),
            id,
            space,
            character,
            persona,
            revision,
        )

        fun save(
            kind: MemoryTransferObjectKind,
            id: String,
            context: MemoryRuntimeContext,
            localRevision: Long,
            original: MemoryTransferOrigin
        ) {
            originRows += MemoryTransferOriginLink(
                kind,
                id,
                localRevision,
                original
            ).entity(UUID.randomUUID().toString(), context.spaceId)
        }
        MemoryTransferLegacyConversion.sourceGroups(data).forEach { group ->
            val space = data.spaces.single { it.id == group.spaceId }
            val context = targets.getValue(group.key)
            save(
                MemoryTransferObjectKind.SPACE, context.spaceId, context, 0L,
                origin(
                    space.id.takeIf { bundle.memoryArchive?.spaces?.any { saved -> saved.id == space.id } == true },
                    space.id.takeIf { bundle.memoryArchive?.spaces?.any { saved -> saved.id == space.id } == true },
                    space.characterId.takeUnless { it == MEMORY_UNASSIGNED_CHARACTER },
                    group.personaKey
                ).copy(
                    originalPurpose = space.purpose,
                    originalScenarioId = space.scenarioId,
                    originalRunId = space.runId,
                    originalGeneration = space.generation,
                    originalSpaceStatus = space.status
                )
            )
        }
        val syntheticSourceIds =
            data.origins.filter { it.kind == MemoryTransferObjectKind.SOURCE && it.origin.importBatchId.isEmpty() }
                .map { it.localId }.toSet()
        data.sources.filterNot { it.id in syntheticSourceIds }.forEach {
            save(
                MemoryTransferObjectKind.SOURCE,
                sourceIds.getValue(it.id),
                target(it.spaceId, it.personaKey),
                it.messageRevision,
                origin(it.id, it.spaceId, it.characterId, it.personaKey, it.messageRevision).copy(
                    originalSourceKind = it.kind,
                    originalSourceStatus = it.status,
                    originalConversationId = it.conversationId,
                    originalMessageId = it.messageId.ifBlank { null },
                    originalSpeakerId = it.speakerId,
                    originalSpeakerRole = it.speakerRole,
                    originalVisibleCharacterIds = it.visibleCharacterIds
                )
            )
        }
        val syntheticRecordIds =
            data.origins.filter { it.kind == MemoryTransferObjectKind.RECORD && it.origin.importBatchId.isEmpty() }
                .map { it.localId }.toSet()
        data.records.filterNot { it.id in syntheticRecordIds }.forEach { record ->
            val context = target(record.spaceId, record.personaKey)
            val recordRevisions =
                data.recordRevisions.filter { it.recordId == record.id }.map { it.revision }
                    .ifEmpty { listOf(record.revision) }
            recordRevisions.forEach { value ->
                save(
                    MemoryTransferObjectKind.RECORD, recordIds.getValue(record.id), context, value,
                    origin(
                        record.id,
                        record.spaceId,
                        record.characterId,
                        record.personaKey,
                        value
                    ).copy(
                        originalCreatedAt = record.createdAt,
                        originalUpdatedAt = record.updatedAt,
                        originalLegacyScopeType = record.legacyScopeType,
                        originalLegacyScopeId = record.legacyScopeId,
                        originalLegacySourceMessageId = record.legacySourceMessageId
                    )
                )
            }
        }
        data.events.forEach {
            save(
                MemoryTransferObjectKind.EVENT,
                eventIds.getValue(it.id),
                target(it.spaceId, it.personaKey),
                it.summaryRevision,
                origin(it.id, it.spaceId, it.characterId, it.personaKey, it.summaryRevision).copy(
                    originalGeneration = it.generation
                )
            )
        }
        data.residents.forEach { entry ->
            val values =
                data.knowledgeRevisions.filter { it.kind == MemoryMaterialKind.RESIDENT && it.ownerId == entry.id }
                    .map { it.revision }.ifEmpty { listOf(entry.revision) }
            values.forEach { value ->
                save(
                    MemoryTransferObjectKind.RESIDENT,
                    residentIds.getValue(entry.id),
                    target(entry.spaceId, entry.personaKey),
                    value,
                    origin(entry.id, entry.spaceId, entry.characterId, entry.personaKey, value)
                )
            }
        }
        data.states.forEach { state ->
            stateRevisions.filterKeys { it.first == state.id }.forEach { (old, local) ->
                save(
                    MemoryTransferObjectKind.STATE,
                    stateIds.getValue(state.id),
                    target(state.spaceId, state.personaKey),
                    local,
                    origin(
                        state.id,
                        state.spaceId,
                        state.characterId,
                        state.personaKey,
                        old.second
                    ).copy(originalCreatedAt = state.createdAt, originalUpdatedAt = state.updatedAt)
                )
            }
        }
        // 再次导出/导入时保留最初出处；本次 ID 与原始 ID 分属独立字段。
        data.origins.forEach { link ->
            val localIds =
                mappings.filter { it.kind == link.kind && it.originalId == link.localId && it.originalRevision == null }
                    .map { it.localId }.distinct()
            for (localId in localIds) {
                val context = when (link.kind) {
                    MemoryTransferObjectKind.SPACE -> targets.values.first { it.spaceId == localId }
                    MemoryTransferObjectKind.RECORD -> data.records.single { it.id == link.localId }
                        .let { target(it.spaceId, it.personaKey) }

                    MemoryTransferObjectKind.SOURCE -> data.sources.single { it.id == link.localId }
                        .let { target(it.spaceId, it.personaKey) }

                    MemoryTransferObjectKind.EVENT -> data.events.single { it.id == link.localId }
                        .let { target(it.spaceId, it.personaKey) }

                    MemoryTransferObjectKind.RESIDENT -> data.residents.single { it.id == link.localId }
                        .let { target(it.spaceId, it.personaKey) }

                    MemoryTransferObjectKind.STATE -> data.states.single { it.id == link.localId }
                        .let { target(it.spaceId, it.personaKey) }
                }
                val localRevision =
                    if (link.kind == MemoryTransferObjectKind.STATE && link.localRevision > 0L) stateRevisions[link.localId to link.localRevision]
                        ?: throw MemoryTransferConflict("原始状态出处版本缺失") else link.localRevision
                val original = if (link.origin.importBatchId.isEmpty()) link.origin.copy(
                    importBatchId = plan.localPlanId,
                    importedAt = now()
                ) else link.origin
                save(link.kind, localId, context, localRevision, original)
            }
        }
        dao.insertOrigins(originRows)
        currentCoroutineContext().ensureActive()
        return mappings
    }
}
