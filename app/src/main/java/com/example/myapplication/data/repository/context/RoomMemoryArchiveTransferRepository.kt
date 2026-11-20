package com.example.myapplication.data.repository.context

import androidx.room.withTransaction
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.*
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

class RoomMemoryArchiveTransferRepository(
    private val database: ChatDatabase,
    private val archiveRepository: MemoryArchiveRepository,
    private val vectorRepository: RoomMemoryVectorRepository? = null,
    private val isCharacterAvailable: suspend (String) -> Boolean,
    private val onImported: (MemoryRuntimeContext) -> Unit = {},
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MemoryArchiveTransferRepository {
    private val dao = database.memoryTransferDao()
    private val archive = database.memoryArchiveDao()
    private val knowledge = database.memoryKnowledgeDao()

    override suspend fun exportArchive(includeVectors: Boolean): MemoryArchiveTransferData =
        withContext(dispatcher) {
            database.withTransaction {
                val spaces = dao.listSpaces()
                var result = MemoryArchiveTransferData(spaces = spaces.map { it.toDomain() })
                for (space in spaces) {
                    currentCoroutineContext().ensureActive()
                    val revisions = knowledge.listSpaceRevisions(space.id).map { revision ->
                        MemoryKnowledgeRevision(
                            MemoryMaterialKind.valueOf(revision.ownerKind),
                            revision.ownerId,
                            revision.revision,
                            revision.content,
                            revision.status,
                            MemoryChangeOrigin.valueOf(revision.origin),
                            revision.subjective,
                            revision.expiresAt,
                            revision.endedAt,
                            knowledge.listSources(
                                revision.ownerKind,
                                revision.ownerId,
                                revision.revision
                            ).map {
                                MemorySourceRef(
                                    it.sourceId,
                                    it.sourceRevision,
                                    it.spanStart,
                                    it.spanEnd
                                )
                            },
                            AppJson.gson.fromJson(
                                revision.recordRefsJson,
                                Array<MemoryRecordVersion>::class.java
                            ).toList(),
                            revision.createdAt,
                        )
                    }
                    result = result.copy(
                        records = result.records + dao.listRecords(space.id).map { it.toDomain() },
                        recordRevisions = result.recordRevisions + dao.listRecordRevisions(space.id)
                            .map { it.transferDomain() },
                        sources = result.sources + dao.listSources(space.id).map { it.toDomain() },
                        recordSources = result.recordSources + dao.listRecordSources(space.id)
                            .map { it.transferDomain() },
                        events = result.events + dao.listEvents(space.id)
                            .map { it.transferDomain() },
                        eventMembers = result.eventMembers + dao.listMembers(space.id)
                            .map { it.transferDomain() },
                        residents = result.residents + dao.listResidents(space.id)
                            .map { it.transferDomain() },
                        states = result.states + dao.listStates(space.id)
                            .map { it.transferDomain() },
                        knowledgeRevisions = result.knowledgeRevisions + revisions,
                        derivedLinks = result.derivedLinks + dao.listDerivedLinks(space.id)
                            .map { it.transferDomain() },
                        origins = result.origins + dao.listOrigins(space.id)
                            .map { it.transferDomain() },
                    )
                }
                if (includeVectors) result =
                    result.copy(vectors = vectorRepository?.exportTransferIndexes(result).orEmpty())
                result.also(MemoryArchiveTransferValidation::validate)
            }
        }

    override suspend fun listTargetOptions(characterId: String): List<MemoryImportTargetOption> =
        withContext(dispatcher) {
            if (!isCharacterAvailable(characterId)) return@withContext emptyList()
            database.withTransaction {
                val spaces = archive.listSpaces(characterId).filter { it.status != "DELETED" }
                buildList {
                    for (space in spaces) {
                        val personas = buildSet {
                            add(MEMORY_UNKNOWN_PERSONA)
                            dao.listBindings(space.id).forEach { add(it.personaKey) }
                            dao.listRecords(space.id).forEach { add(it.personaKey) }
                            dao.listSources(space.id).forEach { add(it.personaKey) }
                            dao.listResidents(space.id).forEach { add(it.personaKey) }
                            dao.listStates(space.id).forEach { add(it.personaKey) }
                        }
                        for (persona in personas.sorted()) add(
                            MemoryImportTargetOption(
                                "${MemoryPurpose.fromStorageValue(space.purpose).label} · ${
                                    space.id.take(
                                        8
                                    )
                                } · ${personaLabel(persona)}",
                                MemoryImportTarget(
                                    MemoryImportTargetMode.EXISTING,
                                    characterId,
                                    space.id,
                                    persona,
                                    space.generation
                                ),
                            )
                        )
                    }
                    if (archive.findSpace(MemoryArchiveIdentity.dailySpaceKey(characterId))?.status.let { it == null || it == "DELETED" }) add(
                        MemoryImportTargetOption(
                            "新建日常空间 · 未设置扮演身份",
                            MemoryImportTarget(MemoryImportTargetMode.NEW_DAILY, characterId),
                        )
                    )
                    add(
                        MemoryImportTargetOption(
                            "新建隔离档案 · 保留为历史资料",
                            MemoryImportTarget(MemoryImportTargetMode.NEW_ARCHIVE, characterId)
                        )
                    )
                }
            }
        }

    override suspend fun prepareImport(
        bundle: ContextDataBundle,
        targets: Map<String, MemoryImportTarget>
    ): MemoryImportPlan = withContext(dispatcher) {
        val data = MemoryTransferLegacyConversion.materialize(bundle)
        database.withTransaction {
            validateTargets(data, targets)
            MemoryImportPlan(
                UUID.randomUUID().toString(),
                fingerprint(bundle, data),
                targets.toMap(),
                stateConflicts(data, targets)
            )
        }
    }

    override suspend fun importArchive(
        bundle: ContextDataBundle,
        plan: MemoryImportPlan
    ): MemoryArchiveImportResult = withContext(dispatcher) {
        val data = MemoryTransferLegacyConversion.materialize(bundle)
        if (plan.localPlanId.isBlank() || plan.bundleFingerprint != fingerprint(
                bundle,
                data
            )
        ) return@withContext MemoryArchiveImportResult.Conflict("资料包已改变，请重新预览")
        val contexts = mutableSetOf<MemoryRuntimeContext>()
        try {
            val result = database.withTransaction {
                val targetsFingerprint = targetsFingerprint(plan)
                dao.getReceipt(plan.localPlanId)?.let { receipt ->
                    if (receipt.bundleFingerprint != plan.bundleFingerprint || receipt.targetsFingerprint != targetsFingerprint) conflict(
                        "确认计划已改变，请重新预览"
                    )
                    return@withTransaction AppJson.gson.fromJson(
                        receipt.resultJson,
                        MemoryArchiveImportResult.Imported::class.java
                    ).copy(alreadyImported = true)
                }
                validateTargets(data, plan.targets)
                val currentConflicts = stateConflicts(data, plan.targets)
                if (currentConflicts != plan.stateConflicts) conflict("目标状态已改变，请重新预览")
                if (currentConflicts.any { it.multipleImportedHeads }) conflict("多个导入状态使用同一目标，请分别选择空间或身份")
                if (currentConflicts.isNotEmpty() && !plan.replaceExistingStates) conflict("目标已有当前状态，请明确选择替换或更换目标")
                val resolved = resolveTargets(data, plan.targets)
                contexts += resolved.values
                val writer = MemoryTransferWriter(database, nowProvider)
                val written = writer.write(bundle, data, plan, resolved)
                var restoredVectors = 0
                var rebuildVectors = 0
                val recordIds = written.filter { it.kind == MemoryTransferObjectKind.RECORD }
                    .associate { it.originalId to it.localId }
                for (vector in data.vectors) {
                    currentCoroutineContext().ensureActive()
                    val context = resolved[MemoryTransferLegacyConversion.groupKey(
                        vector.spaceId,
                        vector.personaKey
                    )]
                    val runtime = context?.let { management ->
                        dao.listBindings(management.spaceId)
                            .filter { !it.archived && it.personaKey == management.personaKey }
                            .firstNotNullOfOrNull { binding ->
                                archiveRepository.getRuntimeContext(
                                    binding.conversationId,
                                    management.characterId
                                )
                            }
                    }
                    if (runtime != null && vectorRepository?.restoreTransferIndex(
                            runtime,
                            vector,
                            recordIds
                        ) == true
                    ) restoredVectors++ else rebuildVectors++
                }
                for (context in contexts) if (!isCharacterAvailable(context.characterId)) conflict("目标角色已移除")
                val imported = MemoryArchiveImportResult.Imported(
                    data.itemCount,
                    data.sources.size,
                    restoredVectors,
                    rebuildVectors,
                    written,
                    contexts = contexts.toList()
                )
                dao.insertReceipt(
                    MemoryTransferReceiptEntity(
                        plan.localPlanId,
                        plan.bundleFingerprint,
                        targetsFingerprint,
                        AppJson.gson.toJson(imported),
                        nowProvider()
                    )
                )
                currentCoroutineContext().ensureActive()
                imported
            }
            result
        } catch (conflict: MemoryTransferConflict) {
            MemoryArchiveImportResult.Conflict(requireNotNull(conflict.message))
        }
    }

    override suspend fun notifyImportCommitted(plan: MemoryImportPlan) = withContext(dispatcher) {
        check(!database.inTransaction()) { "资料事务尚未提交" }
        val receipt = dao.getReceipt(plan.localPlanId) ?: return@withContext
        if (receipt.bundleFingerprint != plan.bundleFingerprint || receipt.targetsFingerprint != targetsFingerprint(
                plan
            )
        ) return@withContext
        AppJson.gson.fromJson(
            receipt.resultJson,
            MemoryArchiveImportResult.Imported::class.java
        ).contexts.forEach(onImported)
    }

    override suspend fun getOrigins(
        context: MemoryRuntimeContext,
        kind: MemoryTransferObjectKind,
        id: String
    ): List<MemoryTransferOriginLink> = withContext(dispatcher) {
        database.withTransaction {
            val space = archive.getSpace(context.spaceId) ?: return@withTransaction emptyList()
            if (space.characterId != context.characterId || space.generation != context.generation || space.status == "DELETED") return@withTransaction emptyList()
            if (context.conversationId.isNotBlank() && archiveRepository.getRuntimeContext(
                    context.conversationId,
                    context.characterId
                ) != context
            ) return@withTransaction emptyList()
            val samePersona = when (kind) {
                MemoryTransferObjectKind.SPACE -> id == space.id
                MemoryTransferObjectKind.RECORD -> archive.getRecord(id)
                    ?.let { it.spaceId == space.id && it.personaKey == context.personaKey } == true

                MemoryTransferObjectKind.SOURCE -> archive.getSource(id)
                    ?.let { it.spaceId == space.id && it.personaKey == context.personaKey } == true

                MemoryTransferObjectKind.EVENT -> dao.listEvents(space.id)
                    .any { it.id == id && it.personaKey == context.personaKey }

                MemoryTransferObjectKind.RESIDENT -> knowledge.getResident(id)
                    ?.let { it.spaceId == space.id && it.personaKey == context.personaKey } == true

                MemoryTransferObjectKind.STATE -> knowledge.getState(id)
                    ?.let { it.spaceId == space.id && it.personaKey == context.personaKey } == true
            }
            if (!samePersona) return@withTransaction emptyList()
            dao.listOrigins(space.id).map { it.transferDomain() }
                .filter { it.kind == kind && it.localId == id }
        }
    }

    private suspend fun validateTargets(
        data: MemoryArchiveTransferData,
        targets: Map<String, MemoryImportTarget>
    ) {
        val groups = MemoryTransferLegacyConversion.sourceGroups(data)
        if (groups.isEmpty() || groups.map { it.key }
                .toSet() != targets.keys) conflict("请为每个原空间和身份选择本机目标")
        for (target in targets.values) {
            if (!isCharacterAvailable(target.characterId) || target.personaKey.isBlank()) conflict("请选择有效的本机角色与身份")
            when (target.mode) {
                MemoryImportTargetMode.EXISTING -> {
                    val space = archive.getSpace(target.spaceId) ?: conflict("目标空间不存在")
                    if (space.characterId != target.characterId || space.status == "DELETED" || space.generation != target.expectedGeneration) conflict(
                        "目标空间已改变，请重新选择"
                    )
                    if (listTargetOptions(target.characterId).none { it.target == target }) conflict(
                        "目标身份已改变，请重新选择"
                    )
                }

                MemoryImportTargetMode.NEW_DAILY -> if (target.spaceId.isNotEmpty() || target.expectedGeneration != 0L ||
                    target.personaKey != MEMORY_UNKNOWN_PERSONA || archive.findSpace(
                        MemoryArchiveIdentity.dailySpaceKey(target.characterId)
                    )?.status.let { it != null && it != "DELETED" }
                ) conflict("日常空间已存在，请选择现有空间")

                MemoryImportTargetMode.NEW_ARCHIVE -> if (target.spaceId.isNotEmpty() || target.expectedGeneration != 0L || target.personaKey != MEMORY_UNKNOWN_PERSONA) conflict(
                    "新档案目标无效"
                )
            }
        }
    }

    private suspend fun stateConflicts(
        data: MemoryArchiveTransferData,
        targets: Map<String, MemoryImportTarget>
    ): List<MemoryImportStateConflict> {
        val grouped = data.states.groupBy { state ->
            val sourceKey = MemoryTransferLegacyConversion.groupKey(state.spaceId, state.personaKey)
            val target = targets.getValue(sourceKey)
            Triple(targetScopeKey(sourceKey, target), target.personaKey, state.key)
        }
        return grouped.flatMap { (slot, states) ->
            val sourceKey = MemoryTransferLegacyConversion.groupKey(
                states.first().spaceId,
                states.first().personaKey
            )
            val target = targets.getValue(sourceKey)
            val existing =
                if (target.mode == MemoryImportTargetMode.EXISTING) dao.listStates(target.spaceId)
                    .singleOrNull { it.personaKey == target.personaKey && it.stateKey == states.first().key.name.lowercase() } else null
            if (states.size == 1 && existing == null) emptyList() else states.map {
                MemoryImportStateConflict(
                    it.id,
                    slot.first,
                    slot.second,
                    it.key,
                    existing?.id,
                    existing?.revision,
                    states.size > 1
                )
            }
        }.sortedBy { it.sourceStateId }
    }

    private suspend fun resolveTargets(
        data: MemoryArchiveTransferData,
        targets: Map<String, MemoryImportTarget>
    ): Map<String, MemoryRuntimeContext> {
        val created = mutableMapOf<String, MemorySpaceEntity>()
        return MemoryTransferLegacyConversion.sourceGroups(data).associate { group ->
            val target = targets.getValue(group.key)
            val space = if (target.mode == MemoryImportTargetMode.EXISTING) requireNotNull(
                archive.getSpace(target.spaceId)
            ) else if (target.mode == MemoryImportTargetMode.NEW_DAILY) {
                val context =
                    archiveRepository.ensureDailySpace(target.characterId, target.personaKey)
                requireNotNull(archive.getSpace(context.spaceId))
            } else {
                val key = targetScopeKey(group.key, target)
                created.getOrPut(key) {
                    MemorySpaceEntity(
                        UUID.randomUUID().toString(),
                        "import:${UUID.randomUUID()}",
                        target.characterId,
                        MemoryPurpose.INDEPENDENT.storageValue,
                        "",
                        "",
                        1L,
                        "ARCHIVED",
                        nowProvider(),
                        nowProvider()
                    )
                }.also { archive.upsertSpace(it) }
            }
            group.key to MemoryRuntimeContext(
                target.characterId,
                space.id,
                "",
                space.scenarioId,
                space.runId,
                if (target.mode == MemoryImportTargetMode.NEW_ARCHIVE) "imported:${group.key}" else target.personaKey,
                space.generation
            )
        }
    }

    private fun targetScopeKey(group: String, target: MemoryImportTarget): String =
        when (target.mode) {
            MemoryImportTargetMode.EXISTING -> target.spaceId
            MemoryImportTargetMode.NEW_DAILY -> "new-daily:${target.characterId}"
            MemoryImportTargetMode.NEW_ARCHIVE -> "new-archive:$group:${target.characterId}"
        }

    private fun fingerprint(bundle: ContextDataBundle, data: MemoryArchiveTransferData) =
        MemoryArchiveIdentity.fingerprint(
            "${bundle.version}:${bundle.exportedAt}:" + AppJson.gson.toJson(data.copy(vectors = emptyList())) + data.vectors.toString(),
        )

    private fun targetsFingerprint(plan: MemoryImportPlan) = MemoryArchiveIdentity.fingerprint(
        AppJson.gson.toJson(plan.targets.toSortedMap()) + AppJson.gson.toJson(plan.stateConflicts) + ":${plan.replaceExistingStates}",
    )

    private fun personaLabel(persona: String) = when {
        persona == MEMORY_UNKNOWN_PERSONA -> "未设置扮演身份"
        persona.startsWith("imported:") -> "导入的隔离身份 ${persona.takeLast(8)}"
        else -> persona
    }

    private fun conflict(message: String): Nothing = throw MemoryTransferConflict(message)
}

internal class MemoryTransferConflict(message: String) : IllegalStateException(message)
