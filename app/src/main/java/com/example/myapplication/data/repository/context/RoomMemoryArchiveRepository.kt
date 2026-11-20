package com.example.myapplication.data.repository.context

import androidx.room.withTransaction
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryArchiveInvalidation
import com.example.myapplication.data.local.memory.MemoryConversationBindingEntity
import com.example.myapplication.data.local.memory.MemoryJobSourceEntity
import com.example.myapplication.data.local.memory.MemoryProcessingCursorEntity
import com.example.myapplication.data.local.memory.MemoryProcessingJobEntity
import com.example.myapplication.data.local.memory.MemoryRecordEntity
import com.example.myapplication.data.local.memory.MemoryRecordRevisionEntity
import com.example.myapplication.data.local.memory.MemoryRecordSourceEntity
import com.example.myapplication.data.local.memory.MemoryRelationIntentEntity
import com.example.myapplication.data.local.memory.MemorySourceEvidenceEntity
import com.example.myapplication.data.local.memory.MemorySpaceEntity
import com.example.myapplication.data.local.memory.MemorySourceStorage
import com.example.myapplication.model.MEMORY_ENGINE_COMPATIBILITY
import com.example.myapplication.model.MEMORY_UNASSIGNED_CHARACTER
import com.example.myapplication.model.MemoryCategory
import com.example.myapplication.model.MemoryArchivePersona
import com.example.myapplication.model.MemoryArchiveConversation
import com.example.myapplication.model.MemoryArchiveMessage
import com.example.myapplication.model.MemoryArchiveQuery
import com.example.myapplication.model.MemoryArchiveItem
import com.example.myapplication.model.MemoryArchivePage
import com.example.myapplication.model.MemoryRecordRevisionDetails
import com.example.myapplication.model.ManualMemoryRecordDraft
import com.example.myapplication.model.MemoryProcessingControl
import com.example.myapplication.model.MemoryProcessingControlResult
import com.example.myapplication.model.MemorySelectionInspection
import com.example.myapplication.model.MemoryChangeOrigin
import com.example.myapplication.model.MemoryCommitBatch
import com.example.myapplication.model.MemoryConflict
import com.example.myapplication.model.MemoryConversationBinding
import com.example.myapplication.model.MemoryIngressEvent
import com.example.myapplication.model.MemoryJobStatus
import com.example.myapplication.model.MemoryProcessingCursor
import com.example.myapplication.model.MemoryProcessingJob
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.model.MemoryRecordDetails
import com.example.myapplication.model.MemoryRecordKind
import com.example.myapplication.model.MemoryRecordRevision
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemorySourceEvidence
import com.example.myapplication.model.MemorySourceRef
import com.example.myapplication.model.MemorySpace
import com.example.myapplication.model.MemoryWriteResult
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.MemoryJobPurpose
import com.example.myapplication.model.MemoryRequestState
import com.example.myapplication.model.MemorySemanticKind
import com.example.myapplication.model.MemorySemanticText
import com.example.myapplication.model.MemoryManualSelectionResult
import com.example.myapplication.model.MemoryMessageSelection
import com.example.myapplication.model.MEMORY_UNKNOWN_PERSONA
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.memoryRequestMayHaveStarted
import com.example.myapplication.roleplay.RoleplayTranscriptFormatter
import com.example.myapplication.system.json.AppJson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomMemoryArchiveRepository(
    private val database: ChatDatabase,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val isCharacterAvailable: suspend (String) -> Boolean = { it.isNotBlank() && it != MEMORY_UNASSIGNED_CHARACTER },
    private val observeCharacterAvailability: (String) -> Flow<Boolean> = { characterId ->
        flow { emit(isCharacterAvailable(characterId)) }
    },
    private val onRecordsChanged: (MemoryRuntimeContext) -> Unit = {},
    private val usesLegacyGlobalMemory: suspend (String) -> Boolean = { false },
    private val onRecallReceiptChanged: (com.example.myapplication.model.MemoryRecallReceipt) -> Unit = {},
    private val residentLimits: suspend (String) -> com.example.myapplication.model.MemoryResidentLimits = { com.example.myapplication.model.MemoryResidentLimits() },
) : MemoryArchiveRepository {
    private val dao = database.memoryArchiveDao()
    private val companionSources =
        com.example.myapplication.data.local.companion.CompanionSourceStorage(database)
    private val invalidation =
        MemoryArchiveInvalidation(dao, database.memoryDao(), database.memoryKnowledgeDao())
    private val gson = AppJson.gson
    private val revisionMapType = object : TypeToken<Map<String, Long>>() {}.type
    private val recordStorage = MemoryRecordStorage(database)
    private val management by lazy {
        MemoryArchiveManagementStore(
            database,
            this,
            ::validContext,
            recall::receiptForPublication,
            nowProvider
        )
    }

    override suspend fun listArchiveCharacterIds() = dao.listArchiveCharacterIds()
    override suspend fun ensureDailySpace(
        characterId: String,
        personaKey: String
    ): MemoryRuntimeContext = database.withTransaction {
        require(isCharacterAvailable(characterId) && personaKey.isNotBlank()) { "角色或扮演身份不可用" }
        val key = MemoryArchiveIdentity.dailySpaceKey(characterId)
        val existing = dao.findSpace(key)
        val now = nowProvider()
        if (existing?.status == "DELETED") {
            dao.upsertSpace(existing.copy(identityKey = "$key:deleted:${existing.id}"))
        }
        val space = existing?.takeUnless { it.status == "DELETED" } ?: MemorySpaceEntity(
            UUID.randomUUID().toString(), key, characterId, MemoryPurpose.CONTINUOUS.storageValue,
            "", "", 1L, "ACTIVE", now, now,
        ).also { dao.upsertSpace(it) }
        MemoryRuntimeContext(
            characterId,
            space.id,
            "",
            space.scenarioId,
            space.runId,
            personaKey,
            space.generation
        )
    }

    override suspend fun listArchivePersonas(context: MemoryRuntimeContext): List<MemoryArchivePersona> =
        management.personas(context)

    override suspend fun listArchiveConversations(context: MemoryRuntimeContext): List<MemoryArchiveConversation> =
        management.conversations(context)

    override suspend fun listArchiveMessages(
        context: MemoryRuntimeContext,
        conversationId: String,
        limit: Int,
        offset: Int
    ): MemoryArchivePage<MemoryArchiveMessage> =
        management.messages(context, conversationId, limit, offset)

    override suspend fun getLatestArchiveAssistantMessage(
        context: MemoryRuntimeContext,
        conversationId: String
    ): MemoryArchiveMessage? =
        management.latestAssistant(context, conversationId)

    override suspend fun queryArchive(
        context: MemoryRuntimeContext,
        query: MemoryArchiveQuery
    ): MemoryArchivePage<MemoryArchiveItem> = management.query(context, query)

    override suspend fun countArchiveItems(characterId: String): Int =
        dao.countArchiveItems(characterId)

    override suspend fun getRecordRevisionDetails(
        context: MemoryRuntimeContext,
        recordId: String,
        revision: Long
    ): MemoryRecordRevisionDetails? = management.revision(context, recordId, revision)

    override suspend fun getArchivedRecallReceipt(
        context: MemoryRuntimeContext,
        conversationId: String,
        messageId: String,
        requestId: String
    ) =
        management.archivedReceipt(context, conversationId, messageId, requestId)

    override suspend fun addRecord(
        context: MemoryRuntimeContext,
        draft: ManualMemoryRecordDraft
    ): MemoryWriteResult =
        management.add(context, draft)
            .also { if (it is MemoryWriteResult.Committed) onRecordsChanged(context) }

    override suspend fun upsertLegacyEntry(entry: com.example.myapplication.model.MemoryEntry): MemoryWriteResult {
        val result = management.editLegacy(entry)
        if (result is MemoryWriteResult.Committed && !result.alreadyCommitted) {
            dao.getRecord(entry.id)?.let { record ->
                getManagementContext(record.characterId, record.spaceId, record.personaKey)?.let(
                    onRecordsChanged
                )
            }
        }
        return result
    }

    override suspend fun setRecordPinned(
        context: MemoryRuntimeContext,
        recordId: String,
        expectedRevision: Long,
        pinned: Boolean,
        pinnedUntil: Long?
    ): MemoryWriteResult =
        management.metadata(context, recordId, expectedRevision) {
            it.copy(
                pinned = pinned,
                pinnedUntil = if (pinned) pinnedUntil else null
            )
        }
            .also { if (it is MemoryWriteResult.Committed) onRecordsChanged(context) }

    override suspend fun setRecordArchived(
        context: MemoryRuntimeContext,
        recordId: String,
        expectedRevision: Long,
        archived: Boolean
    ): MemoryWriteResult =
        management.metadata(
            context,
            recordId,
            expectedRevision
        ) { it.copy(status = if (archived) "ARCHIVED" else "ACTIVE") }
            .also { if (it is MemoryWriteResult.Committed) onRecordsChanged(context) }

    override suspend fun setSpaceArchived(
        context: MemoryRuntimeContext,
        archived: Boolean
    ): MemoryWriteResult = management.archiveSpace(context, archived)

    override suspend fun getProcessingControl(
        context: MemoryRuntimeContext,
        jobId: String
    ): MemoryProcessingControl? = management.control(context, jobId)

    override suspend fun setProcessingPaused(
        context: MemoryRuntimeContext,
        expectedControlVersion: Long,
        paused: Boolean,
        jobId: String
    ): MemoryProcessingControlResult =
        management.pause(context, expectedControlVersion, paused, jobId)

    override suspend fun isProcessingPaused(context: MemoryRuntimeContext, jobId: String): Boolean =
        dao.isProcessingPaused(context.spaceId, jobId)

    override suspend fun inspectSelection(
        context: MemoryRuntimeContext,
        selection: MemoryMessageSelection
    ): MemorySelectionInspection = management.inspect(context, selection)

    private val events by lazy {
        MemoryEventStore(
            database, ::validContext, ::validRuntimeContext, isCharacterAvailable,
            ::validSource, { it.toDomain() }, nowProvider
        )
    }
    private val recall by lazy {
        MemoryRecallStore(
            database,
            ::validRuntimeContext,
            ::validSource,
            nowProvider,
            usesLegacyGlobalMemory,
            residentLimits,
        )
    }
    private val proposals by lazy {
        MemoryArchiveProposalStore(
            database,
            this,
            ::validRuntimeContext,
            ::validSource,
            nowProvider
        )
    }

    private val knowledge by lazy {
        MemoryKnowledgeStore(
            database,
            ::validContext,
            ::validRuntimeContext,
            ::validSource,
            residentLimits,
            nowProvider
        )
    }
    private val knowledgeEditing by lazy {
        MemoryKnowledgeEditing(database, ::validContext, ::validSource, nowProvider)
    }

    override suspend fun listResidentEntries(
        context: MemoryRuntimeContext,
        limit: Int,
        offset: Int
    ) = knowledge.residents(context, limit, offset)

    override suspend fun listCurrentStates(context: MemoryRuntimeContext) =
        knowledge.states(context)

    override suspend fun getResidentDetails(context: MemoryRuntimeContext, id: String) =
        knowledge.residentDetails(context, id)

    override suspend fun getCurrentStateDetails(context: MemoryRuntimeContext, id: String) =
        knowledge.stateDetails(context, id)

    override suspend fun getKnowledgeTargets(context: MemoryRuntimeContext) =
        knowledge.targets(context)

    override suspend fun consumeCommittedKnowledge(context: MemoryRuntimeContext) =
        knowledge.consume(context)

    override suspend fun addResidentEntry(
        context: MemoryRuntimeContext,
        category: com.example.myapplication.model.MemoryResidentCategory,
        content: String,
        subjective: Boolean
    ) =
        knowledgeEditing.addResident(context, category, content, subjective)

    override suspend fun editResidentEntry(
        context: MemoryRuntimeContext,
        id: String,
        expectedRevision: Long,
        content: String,
        subjective: Boolean
    ) =
        knowledgeEditing.editResident(context, id, expectedRevision, content, subjective)

    override suspend fun setResidentArchived(
        context: MemoryRuntimeContext,
        id: String,
        expectedRevision: Long,
        archived: Boolean
    ) =
        knowledgeEditing.archiveResident(context, id, expectedRevision, archived)

    override suspend fun setCurrentState(
        context: MemoryRuntimeContext,
        key: com.example.myapplication.model.MemoryStateKey,
        expectedRevision: Long,
        content: String,
        expiresAt: Long?
    ) =
        knowledgeEditing.setState(context, key, expectedRevision, content, expiresAt)

    override suspend fun endCurrentState(
        context: MemoryRuntimeContext,
        id: String,
        expectedRevision: Long
    ) =
        knowledgeEditing.endState(context, id, expectedRevision)

    override suspend fun deleteKnowledge(
        context: MemoryRuntimeContext,
        kind: com.example.myapplication.model.MemoryMaterialKind,
        id: String,
        expectedRevision: Long
    ) =
        knowledgeEditing.delete(context, kind, id, expectedRevision)

    override fun observePendingProposal(conversationId: String): Flow<PendingMemoryProposal?> =
        proposals.observeProposal(conversationId)

    override fun observeProposalHistory(conversationId: String): Flow<List<MemoryProposalHistoryItem>> =
        proposals.observeHistory(conversationId)

    override suspend fun getPendingProposal(proposalId: String): PendingMemoryProposal? =
        proposals.getProposal(proposalId)

    override suspend fun saveProposal(proposal: PendingMemoryProposal) = proposals.save(proposal)
    override suspend fun approveStoredProposal(proposalId: String): MemoryWriteResult =
        proposals.approve(proposalId)

    override suspend fun rejectStoredProposal(proposalId: String) = proposals.reject(proposalId)
    override suspend fun clearStoredProposals(conversationId: String) =
        proposals.clear(conversationId)

    override fun observeSpaces(characterId: String): Flow<List<MemorySpace>> =
        dao.observeSpaces(characterId).map { spaces -> spaces.map { it.toDomain() } }

    override suspend fun listSpaces(characterId: String): List<MemorySpace> =
        dao.listSpaces(characterId).map { it.toDomain() }

    override suspend fun getSpace(spaceId: String): MemorySpace? = dao.getSpace(spaceId)?.toDomain()

    override suspend fun getRuntimeContext(
        conversationId: String,
        characterId: String
    ): MemoryRuntimeContext? {
        val binding = dao.getBinding(conversationId, characterId) ?: return null
        val space = dao.getSpace(binding.spaceId) ?: return null
        if (binding.archived || space.status != "ACTIVE" || !isCharacterAvailable(characterId)) return null
        return binding.context(space.generation)
    }

    override suspend fun listScenarioBindings(scenarioId: String): List<MemoryConversationBinding> =
        dao.listScenarioBindings(scenarioId).map { it.toDomain() }

    override suspend fun bindConversation(
        scenario: RoleplayScenario,
        conversationId: String,
        characterIds: List<String>,
        runId: String,
    ): List<MemoryRuntimeContext> = database.withTransaction {
        require(conversationId.isNotBlank() && runId.isNotBlank()) { "会话与剧情运行不能为空" }
        require(
            database.conversationDao().getConversation(conversationId) != null
        ) { "当前会话不存在" }
        val now = nowProvider()
        val characterSet = characterIds.map(String::trim).distinct()
        dao.listBindings(conversationId).filter { !it.archived && it.characterId !in characterSet }
            .forEach { old ->
                dao.upsertBinding(old.copy(archived = true))
                dao.invalidateBindingJobs(conversationId, old.characterId, now)
                dao.invalidateBindingProposals(conversationId, old.characterId, now)
                dao.getSpace(old.spaceId)
                    ?.takeIf { it.purpose == MemoryPurpose.INDEPENDENT.storageValue }
                    ?.let { space ->
                        dao.upsertSpace(
                            space.copy(
                                status = "ARCHIVED",
                                generation = space.generation + 1,
                                updatedAt = now
                            )
                        )
                    }
            }
        characterSet.map { characterId ->
            require(isCharacterAvailable(characterId)) { "角色已不可用，无法绑定记忆空间" }
            val existing = dao.getBinding(conversationId, characterId)
            if (existing != null) {
                require(existing.purpose == scenario.memoryPurpose.storageValue && existing.runId == runId) {
                    "对话记忆归属已固定，请创建新的对话绑定"
                }
                var space = requireNotNull(dao.getSpace(existing.spaceId))
                require(space.status != "DELETED") { "记忆空间已删除" }
                val personaKey = MemoryArchiveIdentity.personaKey(scenario)
                val next = existing.copy(archived = false, personaKey = personaKey)
                if (existing.archived || existing.personaKey != personaKey) {
                    space = space.copy(
                        status = "ACTIVE",
                        generation = space.generation + 1,
                        updatedAt = now
                    )
                    dao.upsertSpace(space)
                    dao.upsertBinding(next)
                    dao.invalidateSpaceJobs(space.id, "BINDING_CHANGED", now)
                    dao.invalidateSpaceProposals(space.id, now)
                }
                return@map next.context(space.generation)
            }
            val key = when (scenario.memoryPurpose) {
                MemoryPurpose.CONTINUOUS -> MemoryArchiveIdentity.dailySpaceKey(characterId)
                MemoryPurpose.INDEPENDENT -> MemoryArchiveIdentity.id("story", runId, characterId)
                MemoryPurpose.LEGACY_COMPAT -> legacySpaceKey(characterId, conversationId)
            }
            val space = dao.findSpace(key) ?: MemorySpaceEntity(
                id = UUID.randomUUID().toString(), identityKey = key, characterId = characterId,
                purpose = scenario.memoryPurpose.storageValue,
                scenarioId = if (scenario.memoryPurpose == MemoryPurpose.INDEPENDENT) scenario.id else "",
                runId = if (scenario.memoryPurpose == MemoryPurpose.INDEPENDENT) runId else "",
                generation = 1, status = "ACTIVE", createdAt = now, updatedAt = now,
            ).also { dao.upsertSpace(it) }
            require(space.status == "ACTIVE") { "记忆空间已归档或删除" }
            val binding = MemoryConversationBindingEntity(
                conversationId, characterId, space.id, scenario.memoryPurpose.storageValue,
                scenario.id, runId, MemoryArchiveIdentity.personaKey(scenario), false, now,
            )
            dao.upsertBinding(binding)
            if (dao.getCursor(space.id) == null) {
                dao.upsertCursor(MemoryProcessingCursorEntity(space.id, 0L, false, now))
            }
            binding.context(space.generation)
        }
    }

    override suspend fun archiveConversation(conversationId: String) = database.withTransaction {
        val now = nowProvider()
        dao.listBindings(conversationId).filterNot { it.archived }.forEach { binding ->
            dao.invalidateBindingJobs(conversationId, binding.characterId, now)
            dao.invalidateBindingProposals(conversationId, binding.characterId, now)
            val space = dao.getSpace(binding.spaceId) ?: return@forEach
            if (space.purpose == MemoryPurpose.INDEPENDENT.storageValue) {
                dao.upsertSpace(
                    space.copy(
                        status = "ARCHIVED",
                        generation = space.generation + 1,
                        updatedAt = now
                    )
                )
                dao.invalidateSpaceJobs(space.id, "SPACE_CHANGED", now)
            }
        }
        dao.archiveBindings(conversationId)
    }

    override suspend fun closeCharacterSpaces(characterId: String) = database.withTransaction {
        val now = nowProvider()
        dao.listSpaces(characterId).filter { it.status != "DELETED" }.forEach { space ->
            dao.upsertSpace(
                space.copy(
                    status = "ARCHIVED",
                    generation = space.generation + 1,
                    updatedAt = now
                )
            )
            dao.invalidateSpaceJobs(space.id, "CHARACTER_REMOVED", now)
            dao.invalidateSpaceProposals(space.id, now)
            dao.archiveSpaceBindings(space.id)
        }
    }

    override suspend fun deleteScenarioArchives(scenarioId: String) = database.withTransaction {
        require(scenarioId.isNotBlank()) { "场景不能为空" }
        val now = nowProvider()
        dao.listIndependentScenarioSpaces(scenarioId).filter { it.status != "DELETED" }
            .forEach { deleteSpaceContents(it, now) }
        dao.listScenarioBindings(scenarioId).map { it.conversationId }.distinct()
            .forEach { dao.archiveBindings(it) }
    }

    override fun observeRecords(context: MemoryRuntimeContext): Flow<List<MemoryRecord>> =
        combine(
            dao.observeRecords(
                context.spaceId,
                context.characterId,
                context.conversationId,
                context.personaKey,
                context.generation
            ),
            if (context.conversationId.isBlank()) flowOf(true)
            else observeCharacterAvailability(context.characterId),
        ) { records, characterAvailable ->
            if (characterAvailable && readableSpace(context)) records.map { it.toDomain() }
            else emptyList()
        }

    override suspend fun listRecords(
        context: MemoryRuntimeContext,
        limit: Int,
        offset: Int
    ): List<MemoryRecord> {
        if (!readableSpace(context)) return emptyList()
        return dao.listScopedRecords(
            context.spaceId, context.characterId, context.personaKey,
            context.conversationId.isBlank(), limit.coerceIn(1, 1000), offset.coerceAtLeast(0)
        ).map { it.toDomain() }
    }

    override suspend fun listLegacyCompatibleRecords(
        characterId: String,
        conversationId: String,
        useGlobalMemory: Boolean,
    ): List<MemoryRecord> = dao.listLegacyRecords().filter { record ->
        (record.characterId == characterId || record.characterId == MEMORY_UNASSIGNED_CHARACTER) &&
                when (record.legacyScopeType) {
                    "global" -> true
                    "assistant" -> !useGlobalMemory && record.legacyScopeId == characterId
                    "conversation" -> conversationId.isNotBlank() && record.legacyScopeId == conversationId
                    else -> false
                }
    }.map { it.toDomain() }

    override suspend fun getRecordDetails(
        context: MemoryRuntimeContext,
        recordId: String
    ): MemoryRecordDetails? =
        database.withTransaction {
            if (!readableSpace(context)) return@withTransaction null
            val record = dao.getRecord(recordId)?.takeIf {
                it.spaceId == context.spaceId && it.characterId == context.characterId && (context.conversationId.isBlank() || it.personaKey == context.personaKey)
            }
                ?: return@withTransaction null
            val refs = dao.listRecordSources(recordId, record.revision).map {
                MemorySourceRef(it.sourceId, it.sourceRevision, it.spanStart, it.spanEnd)
            }
            MemoryRecordDetails(
                record.toDomain(),
                dao.listRevisions(recordId).map {
                    MemoryRecordRevision(
                        it.recordId,
                        it.revision,
                        it.content,
                        MemoryChangeOrigin.valueOf(it.origin),
                        it.createdAt
                    )
                },
                refs.map { it.sourceId }.distinct().mapNotNull { dao.getSource(it)?.toDomain() },
                refs,
            )
        }

    override suspend fun getSource(
        context: MemoryRuntimeContext,
        sourceId: String
    ): MemorySourceEvidence? {
        if (!readableSpace(context)) return null
        return dao.getSource(sourceId)?.takeIf {
            it.spaceId == context.spaceId && it.characterId == context.characterId && (context.conversationId.isBlank() || it.personaKey == context.personaKey)
        }?.toDomain()
    }

    override suspend fun listPendingIngress(
        context: MemoryRuntimeContext,
        limit: Int
    ): List<MemoryIngressEvent> {
        if (!readableSpace(context)) return emptyList()
        return dao.listPendingIngress(
            context.spaceId,
            context.personaKey,
            context.conversationId.isBlank(),
            limit.coerceIn(1, 1000)
        ).mapNotNull { event ->
            dao.getSource(event.sourceId)?.takeIf { it.characterId == context.characterId }?.let {
                MemoryIngressEvent(event.sequence, it.toDomain(), event.status)
            }
        }
    }

    override suspend fun getSourceSegments(
        context: MemoryRuntimeContext,
        sourceId: String
    ): List<MemorySemanticText> {
        val source = getSource(context, sourceId) ?: return emptyList()
        if (!validSource(context, source.fullRef())) return emptyList()
        val message = source.messageId.takeIf(String::isNotBlank)
            ?.let { database.conversationDao().getMessage(it) }
        if (message == null) return listOf(
            MemorySemanticText(
                source.content,
                MemorySemanticKind.UNKNOWN
            )
        )
        val segments = RoleplayTranscriptFormatter.memorySemanticSegments(
            MemorySourceStorage.semanticMessage(
                message,
                com.example.myapplication.model.CompanionSourceEligibility.TEXT_ALLOWED
            )
        )
        if (source.contentFormatVersion == 2) return segments
        // 旧来源没有分段映射，含私有心声的混合正文保守限制给说话者。
        return listOf(
            MemorySemanticText(
                source.content,
                MemorySemanticKind.UNKNOWN,
                segments.any { it.privateToSpeaker },
                if (segments.isNotEmpty() && segments.all { it.skipReason.isNotBlank() }) "MEDIA_WITHOUT_SEMANTICS" else ""
            )
        )
    }

    override suspend fun listCommittedSpans(sourceId: String): List<MemorySourceRef> =
        dao.listCommittedSpans(sourceId).map { it.ref() }

    override suspend fun captureSelectedMessages(
        context: MemoryRuntimeContext,
        selection: MemoryMessageSelection,
    ): MemoryManualSelectionResult = database.withTransaction {
        require(validContext(context) && isCharacterAvailable(context.characterId)) { "记忆档案已改变" }
        require(selection.messageIds.isNotEmpty() && selection.messageIds.size <= 1000 && selection.messageIds.distinct().size == selection.messageIds.size) { "请选择明确的消息范围" }
        val binding = dao.getBinding(selection.conversationId, context.characterId)
        require(binding?.spaceId == context.spaceId) { "所选对话不属于当前档案" }
        val unavailable = mutableListOf<String>()
        val sources = selection.messageIds.mapNotNull { id ->
            val message = database.conversationDao().getMessage(id)
            if (message == null || message.conversationId != selection.conversationId || message.status != "COMPLETED" || message.isRecalled ||
                companionSources.eligibility(message) != com.example.myapplication.model.CompanionSourceEligibility.TEXT_ALLOWED
            ) {
                unavailable += id
                return@mapNotNull null
            }
            val existing = dao.listMessageSources(selection.conversationId, id)
                .filter { it.spaceId == context.spaceId && it.messageRevision == message.memoryRevision }
                .maxByOrNull { it.ingestedAt }
            if (existing != null) return@mapNotNull existing.toDomain()
                .takeIf { it.status == com.example.myapplication.model.MemorySourceStatus.AVAILABLE }
                .also { if (it == null) unavailable += id }
            // 历史身份/可见性无法证明，明确标为 unknown，不用当前绑定补造过去事实。
            val content = MemorySourceStorage.contentForSource(
                message,
                eligibility = com.example.myapplication.model.CompanionSourceEligibility.TEXT_ALLOWED
            )
            if (content.isBlank()) {
                unavailable += id; return@mapNotNull null
            }
            val source = MemorySourceEvidenceEntity(
                MemoryArchiveIdentity.id(
                    "source",
                    context.spaceId,
                    id,
                    message.memoryRevision.toString()
                ),
                context.spaceId,
                context.characterId,
                "MESSAGE",
                selection.conversationId,
                id,
                message.memoryRevision,
                message.speakerId.ifBlank { if (message.role == "USER") "user" else "" },
                message.role,
                MEMORY_UNKNOWN_PERSONA,
                "[]",
                content,
                content.length,
                "AVAILABLE",
                message.createdAt,
                nowProvider(),
                MemoryArchiveIdentity.fingerprint(content),
                contentFormatVersion = 2,
            )
            dao.insertSource(source)
            source.toDomain()
        }
        MemoryManualSelectionResult(sources, unavailable)
    }

    override suspend fun getCursor(spaceId: String): MemoryProcessingCursor? =
        dao.getCursor(spaceId)?.toDomain()

    override suspend fun planJob(
        context: MemoryRuntimeContext,
        idempotencyKey: String,
        sources: List<MemorySourceRef>,
        targetRevisions: Map<String, Long>,
        requestJson: String,
    ): MemoryProcessingJob = planJobInternal(
        context,
        idempotencyKey,
        sources,
        targetRevisions,
        requestJson,
        MemoryJobPurpose.AUTOMATIC_EXTRACTION
    )

    override suspend fun planSelectedJob(
        context: MemoryRuntimeContext,
        idempotencyKey: String,
        sources: List<MemorySourceRef>,
        targetRevisions: Map<String, Long>,
        requestJson: String
    ): MemoryProcessingJob =
        planJobInternal(
            context,
            idempotencyKey,
            sources,
            targetRevisions,
            requestJson,
            MemoryJobPurpose.USER_SELECTION
        )

    override suspend fun planToolJob(
        context: MemoryRuntimeContext,
        idempotencyKey: String,
        sources: List<MemorySourceRef>
    ): MemoryProcessingJob =
        planJobInternal(
            context,
            idempotencyKey,
            sources,
            emptyMap(),
            "",
            MemoryJobPurpose.TOOL_WRITE
        )

    private suspend fun planJobInternal(
        context: MemoryRuntimeContext, idempotencyKey: String, sources: List<MemorySourceRef>,
        targetRevisions: Map<String, Long>, requestJson: String, purpose: MemoryJobPurpose,
    ): MemoryProcessingJob = database.withTransaction {
        require(!dao.isProcessingPaused(context.spaceId, "")) { "记忆整理已暂停，请显式继续" }
        require(
            if (purpose == MemoryJobPurpose.USER_SELECTION) validContext(context) && isCharacterAvailable(
                context.characterId
            ) else validRuntimeContext(context)
        ) { "记忆归属已改变，请重新整理" }
        require(requestJson.length <= 1_000_000) { "整理快照过大" }
        require(idempotencyKey.isNotBlank() && sources.isNotEmpty() && sources.size <= 1000 && sources.distinct().size == sources.size) { "整理任务缺少有效来源" }
        dao.findJob(context.spaceId, idempotencyKey)?.let { existing ->
            val result = existing.toDomain()
            require(result.sources.toSet() == sources.toSet() && result.targetRevisions == targetRevisions && result.context == context && result.purpose == purpose && result.requestJson == requestJson) {
                "同一整理任务不能替换来源或归属"
            }
            return@withTransaction result
        }
        require(sources.all {
            validSource(
                context,
                it
            )
        }) { "整理来源已改变或不可访问" }
        require(targetRevisions.all { (id, revision) ->
            validJobTarget(context, id, revision)
        }) { "目标记忆已更新或不属于来源身份" }
        val now = nowProvider()
        val job = MemoryProcessingJobEntity(
            UUID.randomUUID().toString(),
            context.spaceId,
            context.characterId,
            context.conversationId,
            context.scenarioId,
            context.runId,
            context.personaKey,
            context.generation,
            idempotencyKey,
            gson.toJson(targetRevisions),
            "PLANNED",
            "",
            "",
            0L,
            "",
            now,
            now,
            purpose = purpose.name,
            requestJson = requestJson,
            requestState = MemoryRequestState.NOT_STARTED.name,
        )
        dao.upsertJob(job)
        dao.insertJobSources(sources.map {
            MemoryJobSourceEntity(
                job.id,
                it.sourceId,
                it.sourceRevision,
                it.start,
                it.end
            )
        })
        job.toDomain()
    }

    override suspend fun getJob(jobId: String): MemoryProcessingJob? = dao.getJob(jobId)?.toDomain()

    override suspend fun replanUnrequestedJob(
        jobId: String,
        idempotencyKey: String,
        targetRevisions: Map<String, Long>,
        requestJson: String,
    ): MemoryProcessingJob? = database.withTransaction {
        val previous = dao.getJob(jobId) ?: return@withTransaction null
        val now = nowProvider()
        if (previous.status !in setOf("PLANNED", "PAUSED", "FAILED", "EXTRACTING") ||
            dao.isProcessingPaused(previous.spaceId, previous.id) ||
            previous.artifact.isNotBlank() || previous.requestJson.isBlank() ||
            memoryRequestMayHaveStarted(previous.requestState, previous.failureCode) ||
            previous.leaseOwner.isNotBlank() && previous.leaseUntil > now ||
            previous.purpose !in setOf(
                MemoryJobPurpose.AUTOMATIC_EXTRACTION.name,
                MemoryJobPurpose.USER_SELECTION.name
            ) ||
            !validJobContext(previous)
        ) return@withTransaction null
        require(idempotencyKey != previous.idempotencyKey) { "重新规划必须保留原冻结任务" }
        val next = planJobInternal(
            previous.context(), idempotencyKey, dao.listJobSources(jobId).map { it.ref() },
            targetRevisions, requestJson, MemoryJobPurpose.valueOf(previous.purpose),
        )
        dao.getJob(next.id)?.let { replacement ->
            dao.upsertJob(replacement.copy(controlJobId = previous.controlJobId.ifBlank { previous.id }))
        }
        dao.upsertJob(
            previous.copy(
                status = "INVALIDATED", leaseOwner = "", leaseUntil = 0L,
                failureCode = "TARGETS_CHANGED_BEFORE_REQUEST", updatedAt = now,
            )
        )
        requireNotNull(dao.getJob(next.id)).toDomain()
    }

    override suspend fun listRecoverableJobs(spaceId: String): List<MemoryProcessingJob> =
        dao.listRecoverableJobs(spaceId).map { it.toDomain() }

    override suspend fun listUnresolvedAutomaticJobs(context: MemoryRuntimeContext): List<MemoryProcessingJob> {
        if (!validContext(context)) return emptyList()
        return dao.listUnresolvedAutomaticJobs(context.spaceId, context.personaKey)
            .map { it.toDomain() }
    }

    override suspend fun listJobHistory(
        context: MemoryRuntimeContext,
        limit: Int,
        offset: Int
    ): List<MemoryProcessingJob> {
        if (!validContext(context)) return emptyList()
        return dao.listJobHistory(
            context.spaceId,
            context.characterId,
            context.personaKey,
            false,
            limit.coerceIn(1, 1000),
            offset.coerceAtLeast(0)
        ).map { it.toDomain() }
    }

    override suspend fun listCommittedExtractionJobs(
        context: MemoryRuntimeContext,
        limit: Int,
        offset: Int
    ): List<MemoryProcessingJob> {
        if (!validContext(context)) return emptyList()
        return dao.listCommittedExtractionJobs(
            context.spaceId,
            context.personaKey,
            limit.coerceIn(1, 1000),
            offset.coerceAtLeast(0)
        ).map { it.toDomain() }
    }

    override suspend fun getJobRecordIds(jobId: String): Map<String, String> =
        dao.listJobRecords(jobId)
            .filter { it.status != "DELETED" }.associate { requireNotNull(it.localItemId) to it.id }

    override suspend fun claimJob(
        jobId: String,
        owner: String,
        leaseMillis: Long
    ): MemoryProcessingJob? = database.withTransaction {
        require(owner.isNotBlank()) { "整理任务执行者不能为空" }
        val now = nowProvider()
        val job = dao.getJob(jobId) ?: return@withTransaction null
        if (job.status in setOf(
                "COMMITTED",
                "INVALIDATED"
            ) || dao.isProcessingPaused(job.spaceId, job.id) || !validJobContext(job)
        ) return@withTransaction null
        if (job.leaseOwner.isNotBlank() && job.leaseOwner != owner && job.leaseUntil > now) return@withTransaction null
        if (dao.countOtherLeases(job.spaceId, job.id, now) != 0) return@withTransaction null
        val next = job.copy(
            status = if (job.artifact.isNotBlank()) "EXTRACTED" else "EXTRACTING",
            leaseOwner = owner,
            leaseUntil = now + leaseMillis.coerceIn(1_000L, 3_600_000L),
            failureCode = job.failureCode,
            updatedAt = now,
        )
        dao.upsertJob(next)
        next.toDomain()
    }

    override suspend fun renewLease(jobId: String, owner: String, leaseMillis: Long): Boolean =
        database.withTransaction {
            val job = dao.getJob(jobId) ?: return@withTransaction false
            if (!ownsLease(job, owner) || !validJobContext(job)) return@withTransaction false
            dao.upsertJob(
                job.copy(
                    leaseUntil = nowProvider() + leaseMillis.coerceIn(
                        1_000L,
                        3_600_000L
                    ), updatedAt = nowProvider()
                )
            )
            true
        }

    override suspend fun markRequestStarted(jobId: String, owner: String): Boolean =
        database.withTransaction {
            val job = dao.getJob(jobId) ?: return@withTransaction false
            if (MemoryEventStore.isEventJob(job)) return@withTransaction false
            if (dao.isProcessingPaused(job.spaceId, job.id)) return@withTransaction false
            if (!ownsLease(
                    job,
                    owner
                ) || job.status != "EXTRACTING" || job.requestState != MemoryRequestState.NOT_STARTED.name || !validJobContext(
                    job
                )
            ) return@withTransaction false
            if (!dao.listJobSources(job.id)
                    .all { validSource(job.context(), it.ref()) }
            ) return@withTransaction false
            val targets: Map<String, Long> = gson.fromJson(job.targetRevisionsJson, revisionMapType)
            if (!targets.all { (id, revision) ->
                    validJobTarget(
                        job.context(),
                        id,
                        revision
                    )
                }) return@withTransaction false
            dao.upsertJob(
                job.copy(
                    failureCode = "REQUEST_STARTED", requestState = MemoryRequestState.STARTED.name,
                    updatedAt = nowProvider(),
                )
            )
            true
        }

    override suspend fun saveExtraction(jobId: String, owner: String, artifact: String): Boolean =
        database.withTransaction {
            require(artifact.isNotBlank()) { "提取产物不能为空，空记忆应保存明确的空结构" }
            val job = dao.getJob(jobId) ?: return@withTransaction false
            if (MemoryEventStore.isEventJob(job)) return@withTransaction false
            if (!ownsLease(
                    job,
                    owner
                ) || job.status != "EXTRACTING" || !validJobContext(job)
            ) return@withTransaction false
            if (!dao.listJobSources(jobId)
                    .all { validSource(job.context(), it.ref()) }
            ) return@withTransaction false
            dao.upsertJob(
                job.copy(
                    status = "EXTRACTED",
                    artifact = artifact,
                    failureCode = "",
                    updatedAt = nowProvider()
                )
            )
            true
        }

    override suspend fun releaseJob(
        jobId: String,
        owner: String,
        status: MemoryJobStatus,
        failureCode: String
    ): Boolean =
        database.withTransaction {
            require(
                status in setOf(
                    MemoryJobStatus.FAILED,
                    MemoryJobStatus.PAUSED
                )
            ) { "整理任务不能跳过提交阶段" }
            require(failureCode.matches(Regex("[A-Z0-9_]{0,64}"))) { "失败原因应使用安全的状态代码" }
            val job = dao.getJob(jobId) ?: return@withTransaction false
            if (!ownsLease(job, owner)) return@withTransaction false
            dao.upsertJob(
                job.copy(
                    status = status.name,
                    leaseOwner = "",
                    leaseUntil = 0L,
                    failureCode = failureCode,
                    updatedAt = nowProvider()
                )
            )
            true
        }

    override suspend fun commitJob(
        jobId: String,
        owner: String,
        batch: MemoryCommitBatch
    ): MemoryWriteResult {
        val result = commitJobTransaction(jobId, owner, batch)
        if (result is MemoryWriteResult.Committed && !result.alreadyCommitted) {
            dao.getJob(jobId)?.context()?.let(onRecordsChanged)
        }
        return result
    }

    private suspend fun commitJobTransaction(
        jobId: String,
        owner: String,
        batch: MemoryCommitBatch
    ): MemoryWriteResult =
        database.withTransaction {
            val job =
                dao.getJob(jobId) ?: return@withTransaction conflict(MemoryConflict.JOB_CHANGED)
            if (MemoryEventStore.isEventJob(job)) return@withTransaction conflict(MemoryConflict.INVALID_INPUT)
            if (job.status == "COMMITTED") {
                return@withTransaction MemoryWriteResult.Committed(
                    gson.fromJson(job.resultRecordIdsJson, Array<String>::class.java).toList(),
                    job.cursorAfterCommit, alreadyCommitted = true,
                )
            }
            if (!ownsLease(
                    job,
                    owner
                ) || job.status != "EXTRACTED"
            ) return@withTransaction conflict(MemoryConflict.JOB_CHANGED)
            val context = job.context()
            if (dao.isProcessingPaused(job.spaceId, job.id)) return@withTransaction conflict(
                MemoryConflict.JOB_CHANGED
            )
            if (!validJobContext(job)) return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
            val sourceSnapshot = dao.listJobSources(job.id).map { it.ref() }
            if (!sourceSnapshot.all { validSource(context, it) }) return@withTransaction conflict(
                MemoryConflict.SOURCE_CHANGED
            )
            val targetSnapshot: Map<String, Long> =
                gson.fromJson(job.targetRevisionsJson, revisionMapType)
            if (!targetSnapshot.all { (id, revision) -> validJobTarget(context, id, revision) } ||
                !batch.corrections.all { targetSnapshot[it.recordId] == it.expectedRevision }) {
                return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
            }
            if (!validBatch(
                    batch,
                    sourceSnapshot
                )
            ) return@withTransaction conflict(MemoryConflict.INVALID_INPUT)
            if (!knowledge.validActions(
                    job,
                    batch
                )
            ) return@withTransaction conflict(MemoryConflict.INVALID_INPUT)
            if (!(batch.records.flatMap { it.sources } + batch.corrections.flatMap { it.sources }).all {
                    validSource(
                        context,
                        it
                    )
                }) {
                return@withTransaction conflict(MemoryConflict.SOURCE_CHANGED)
            }
            if (!batch.relations.filter { it.targetRecordId.isNotBlank() }.all {
                    val revision = targetSnapshot[it.targetRecordId]
                    revision != null && validJobTarget(context, it.targetRecordId, revision)
                }) return@withTransaction conflict(MemoryConflict.RECORD_CHANGED)
            val now = nowProvider()
            val localIds = batch.records.associate {
                it.localId to MemoryArchiveIdentity.id(
                    "record",
                    job.id,
                    it.localId
                )
            }
            val resultIds = mutableListOf<String>()
            batch.records.forEach { draft ->
                val id = localIds.getValue(draft.localId)
                // 墓碑也占用同一 ID，不能通过重试 upsert 复活。
                check(dao.getRecord(id) == null) { "整理结果唯一键冲突" }
                val record = MemoryRecordEntity(
                    id,
                    context.spaceId,
                    context.characterId,
                    draft.kind.name,
                    draft.category.name,
                    draft.content.trim(),
                    1L,
                    "ACTIVE",
                    draft.importance.coerceIn(0, 100),
                    false,
                    null,
                    draft.occurredAt,
                    draft.datePrecision,
                    context.personaKey,
                    now,
                    now,
                    0L,
                    job.id,
                    draft.localId,
                    "",
                    "",
                    "",
                )
                dao.upsertRecord(record)
                writeRevision(record, MemoryChangeOrigin.EXTRACTION, draft.sources, now)
                resultIds += id
            }
            batch.corrections.forEach { correction ->
                val previous = requireNotNull(dao.getRecord(correction.recordId))
                invalidation.record(previous, deleted = false, now)
                val record = previous.copy(
                    content = correction.content.trim(),
                    revision = previous.revision + 1,
                    status = "ACTIVE",
                    updatedAt = now
                )
                dao.upsertRecord(record)
                writeRevision(record, MemoryChangeOrigin.CORRECTION, correction.sources, now)
                resultIds += record.id
            }
            batch.relations.forEach { relation ->
                val fromId = localIds.getValue(relation.fromLocalId)
                val targetId =
                    relation.targetRecordId.ifBlank { localIds.getValue(relation.targetLocalId) }
                val target = requireNotNull(dao.getRecord(targetId))
                dao.insertRelation(
                    MemoryRelationIntentEntity(
                        MemoryArchiveIdentity.id("relation", fromId, targetId, relation.label),
                        context.spaceId,
                        context.characterId,
                        fromId,
                        1L,
                        targetId,
                        target.revision,
                        relation.label,
                        relation.eventName.trim(),
                        "PENDING",
                        now,
                    ),
                )
            }
            knowledge.apply(job, context, batch)
            dao.upsertJob(
                job.copy(
                    status = "COMMITTED",
                    leaseOwner = "",
                    leaseUntil = 0L,
                    resultRecordIdsJson = gson.toJson(resultIds),
                    updatedAt = now
                )
            )
            if (job.purpose == MemoryJobPurpose.AUTOMATIC_EXTRACTION.name) sourceSnapshot.map { it.sourceId }
                .distinct().forEach { sourceId ->
                    val source = requireNotNull(dao.getSource(sourceId))
                    val spans = dao.listCommittedSpans(sourceId)
                    var covered = 0
                    for (span in spans) {
                        if (span.sourceRevision != source.messageRevision || span.spanStart > covered) break
                        covered = maxOf(covered, span.spanEnd)
                    }
                    if (covered >= source.contentLength) dao.updateIngressStatus(
                        sourceId,
                        "COMMITTED"
                    )
                }
            val cursor =
                if (job.purpose == MemoryJobPurpose.AUTOMATIC_EXTRACTION.name) advanceCursor(
                    context.spaceId,
                    now
                )
                else dao.getCursor(context.spaceId)?.committedSequence ?: 0L
            dao.upsertJob(requireNotNull(dao.getJob(job.id)).copy(cursorAfterCommit = cursor))
            MemoryWriteResult.Committed(resultIds, cursor)
        }

    override suspend fun editRecord(
        context: MemoryRuntimeContext,
        recordId: String,
        expectedRevision: Long,
        content: String
    ): MemoryWriteResult {
        val result = editRecordTransaction(context, recordId, expectedRevision, content)
        if (result is MemoryWriteResult.Committed) onRecordsChanged(context)
        return result
    }

    private suspend fun editRecordTransaction(
        context: MemoryRuntimeContext,
        recordId: String,
        expectedRevision: Long,
        content: String,
    ): MemoryWriteResult = database.withTransaction {
        if (!validContext(context)) return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
        if (!validTarget(context, recordId, expectedRevision)) return@withTransaction conflict(
            MemoryConflict.RECORD_CHANGED
        )
        if (content.isBlank()) return@withTransaction conflict(MemoryConflict.INVALID_INPUT)
        val now = nowProvider()
        val previous = requireNotNull(dao.getRecord(recordId))
        invalidation.record(previous, deleted = false, now)
        val sourceId = UUID.randomUUID().toString()
        val normalized = content.trim()
        dao.insertSource(
            MemorySourceEvidenceEntity(
                sourceId,
                context.spaceId,
                context.characterId,
                "MANUAL",
                context.conversationId,
                null,
                1L,
                "user",
                "USER",
                previous.personaKey,
                gson.toJson(listOf(context.characterId)),
                normalized,
                normalized.length,
                "AVAILABLE",
                now,
                now,
                MemoryArchiveIdentity.fingerprint(normalized),
            ),
        )
        val record = previous.copy(
            content = normalized,
            revision = previous.revision + 1,
            status = "ACTIVE",
            updatedAt = now
        )
        dao.upsertRecord(record)
        writeRevision(
            record,
            MemoryChangeOrigin.MANUAL,
            listOf(MemorySourceRef(sourceId, 1L, 0, normalized.length)),
            now
        )
        MemoryWriteResult.Committed(listOf(recordId))
    }

    override suspend fun deleteRecord(
        context: MemoryRuntimeContext,
        recordId: String,
        expectedRevision: Long
    ): MemoryWriteResult =
        database.withTransaction {
            if (!validContext(context)) return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
            if (!validTarget(context, recordId, expectedRevision)) return@withTransaction conflict(
                MemoryConflict.RECORD_CHANGED
            )
            val now = nowProvider()
            val record = requireNotNull(dao.getRecord(recordId))
            val sourceIds =
                dao.listRevisions(recordId).flatMap { dao.listRecordSources(recordId, it.revision) }
                    .map { it.sourceId }.distinct()
            invalidation.record(record, deleted = true, now)
            // 人工/导入来源的正文也属于该记忆；原聊天来源保留，避免删除原始对话。
            invalidation.scrubUnreferencedSources(sourceIds, now)
            dao.advanceGeneration(context.spaceId, now)
            dao.invalidateSpaceJobs(context.spaceId, "RECORD_DELETED", now)
            dao.invalidateSpaceProposals(context.spaceId, now)
            MemoryWriteResult.Committed(listOf(recordId))
        }

    override suspend fun deleteSpace(context: MemoryRuntimeContext): MemoryWriteResult =
        database.withTransaction {
            if (!validContext(context)) return@withTransaction conflict(MemoryConflict.SPACE_CHANGED)
            val now = nowProvider()
            val space = requireNotNull(dao.getSpace(context.spaceId))
            deleteSpaceContents(space, now)
            MemoryWriteResult.Committed(emptyList())
        }

    private suspend fun deleteSpaceContents(space: MemorySpaceEntity, now: Long) {
        while (true) {
            val records = dao.listRecords(space.id, 200, 0)
            if (records.isEmpty()) break
            records.forEach {
                invalidation.record(it, deleted = true, now)
            }
            // 查询排除墓碑，因此下一页仍从 0 开始。
        }
        dao.scrubSpace(space.id, now)
        dao.invalidateSpaceJobs(space.id, "SPACE_DELETED", now)
        dao.upsertSpace(
            space.copy(
                identityKey = "deleted:${space.id}",
                status = "DELETED",
                generation = space.generation + 1,
                updatedAt = now
            )
        )
    }

    override suspend fun getEngineVersion(): Int {
        val state = dao.getEngineState()
        return if (state != null && state.activationVersion >= com.example.myapplication.model.MEMORY_ENGINE_CURRENT) state.engineVersion else activateCurrentEngine()
    }

    suspend fun activateCurrentEngine(): Int =
        MemoryLegacyInstallation(database, nowProvider).activate()

    override suspend fun keepCompatibilityEngine() {
        activateCurrentEngine()
        database.withTransaction {
            val state = requireNotNull(dao.getEngineState())
            dao.upsertEngineState(
                state.copy(
                    engineVersion = MEMORY_ENGINE_COMPATIBILITY,
                    updatedAt = nowProvider()
                )
            )
        }
    }

    override suspend fun listEvents(context: MemoryRuntimeContext) = events.list(context)
    override suspend fun getEventDetails(context: MemoryRuntimeContext, eventId: String) =
        events.details(context, eventId)

    override suspend fun listEventConclusions(context: MemoryRuntimeContext) =
        events.conclusions(context)

    override suspend fun consumeEventRelations(context: MemoryRuntimeContext) =
        events.consume(context)

    override suspend fun linkEventMembers(
        context: MemoryRuntimeContext, from: com.example.myapplication.model.MemoryRecordVersion,
        target: com.example.myapplication.model.MemoryRecordVersion, title: String
    ): com.example.myapplication.model.MemoryEventMutationResult {
        val result = events.link(context, from, target, title)
        if (result is com.example.myapplication.model.MemoryEventMutationResult.Updated && !result.alreadyApplied) onRecordsChanged(
            context
        )
        return result
    }

    override suspend fun unlinkEventMember(
        context: MemoryRuntimeContext,
        eventId: String,
        recordId: String,
        expectedGeneration: Long
    ) =
        events.unlink(context, eventId, recordId, expectedGeneration)

    override suspend fun setEventArchived(
        context: MemoryRuntimeContext,
        eventId: String,
        expectedGeneration: Long,
        archived: Boolean
    ) =
        events.archive(context, eventId, expectedGeneration, archived)

    override suspend fun planEventSummary(
        context: MemoryRuntimeContext,
        eventId: String,
        options: com.example.myapplication.model.MemoryEventSummaryOptions
    ) =
        events.plan(context, eventId, options)

    override suspend fun beginEventSummaryBatch(jobId: String, owner: String, batchIndex: Int) =
        events.begin(jobId, owner, batchIndex)

    override suspend fun saveEventSummaryBatch(
        jobId: String,
        owner: String,
        batchIndex: Int,
        completion: com.example.myapplication.model.PromptCompletionResult
    ) =
        events.save(jobId, owner, batchIndex, completion)

    override suspend fun commitEventSummary(jobId: String, owner: String) =
        events.commit(jobId, owner)

    override suspend fun listRecallRecords(context: MemoryRuntimeContext) =
        recall.listRecords(context)

    override suspend fun listActiveRecallContexts(characterId: String): List<MemoryRuntimeContext> =
        database.withTransaction {
            dao.listActiveCharacterBindings(characterId).mapNotNull { binding ->
                dao.getSpace(binding.spaceId)?.let { binding.context(it.generation) }
                    ?.takeIf { validRuntimeContext(it) }
            }
        }

    override suspend fun getRecallSnapshot(context: MemoryRuntimeContext) = recall.snapshot(context)
    override suspend fun validateRecallRecords(
        context: MemoryRuntimeContext,
        recordRevisions: Map<String, Long>
    ) = recall.validateRecords(context, recordRevisions)

    override suspend fun validateRecallMaterials(
        context: MemoryRuntimeContext,
        materials: List<com.example.myapplication.model.MemoryMaterialRef>
    ) = recall.validateMaterials(context, materials)

    override suspend fun archiveRecallOverflow(context: MemoryRuntimeContext, activeCapacity: Int) =
        recall.archiveOverflow(context, activeCapacity)

    override suspend fun prepareRecallReceipt(request: com.example.myapplication.model.MemoryRecallRequest) =
        recall.prepare(request).also {
            if (it) publishRecallReceipt(request.requestId)
        }

    override suspend fun prepareRecallTransmission(
        requestId: String,
        payloadFingerprint: String,
        materials: List<com.example.myapplication.model.MemoryMaterialRef>,
        omitted: List<com.example.myapplication.model.MemoryRecallOmission>
    ) = recall.prepareTransmission(requestId, payloadFingerprint, materials, omitted).also {
        if (it != null) publishRecallReceipt(requestId)
    }

    override suspend fun confirmRecallTransmission(requestId: String, sequence: Int) =
        recall.confirmTransmission(requestId, sequence).also {
            if (it) publishRecallReceipt(requestId)
        }

    override suspend fun failRecallRequest(requestId: String, failureCode: String) {
        recall.fail(requestId, failureCode)
        publishRecallReceipt(requestId)
    }

    override suspend fun getRecallReceipt(context: MemoryRuntimeContext, requestId: String) =
        recall.get(context, requestId)

    private suspend fun publishRecallReceipt(requestId: String) {
        recall.receiptForPublication(requestId)?.let(onRecallReceiptChanged)
    }

    private fun validBatch(batch: MemoryCommitBatch, snapshot: List<MemorySourceRef>): Boolean {
        if (batch.records.size + batch.corrections.size > 200 || batch.relations.size > 500 || batch.residents.size > 40 || batch.states.size > 40) return false
        val localIds = batch.records.map { it.localId }
        if (localIds.any { it.isBlank() } || localIds.distinct().size != localIds.size) return false
        if (batch.corrections.map { it.recordId }
                .distinct().size != batch.corrections.size) return false
        fun validRefs(refs: List<MemorySourceRef>): Boolean = refs.isNotEmpty() && refs.all { ref ->
            snapshot.any { it.sourceId == ref.sourceId && it.sourceRevision == ref.sourceRevision && it.start <= ref.start && it.end >= ref.end && ref.end > ref.start }
        }
        if (batch.records.any { it.content.isBlank() || !validRefs(it.sources) }) return false
        if (batch.corrections.any { it.content.isBlank() || !validRefs(it.sources) }) return false
        if (batch.residents.any {
                it.content.isBlank() || it.content.length > 12_000 || !validRefs(
                    it.sources
                )
            }) return false
        if (batch.states.any { it.content.isBlank() || it.content.length > 12_000 || !validRefs(it.sources) }) return false
        return batch.relations.all {
            it.fromLocalId in localIds && (it.targetRecordId.isBlank() xor it.targetLocalId.isBlank()) &&
                    (it.targetLocalId.isBlank() || it.targetLocalId in localIds && it.targetLocalId != it.fromLocalId) &&
                    it.label == "same_event" && it.eventName.length <= 200
        }
    }

    private suspend fun writeRevision(
        record: MemoryRecordEntity,
        origin: MemoryChangeOrigin,
        sources: List<MemorySourceRef>,
        now: Long
    ) = recordStorage.writeRevision(record, origin, sources.distinct(), now)

    private suspend fun advanceCursor(spaceId: String, now: Long): Long {
        val previous =
            dao.getCursor(spaceId) ?: MemoryProcessingCursorEntity(spaceId, 0L, false, now)
        var sequence = previous.committedSequence
        while (true) {
            val page = dao.listIngress(spaceId, sequence, 200)
            if (page.isEmpty()) break
            for (event in page) {
                if (event.status !in setOf("COMMITTED", "INVALIDATED")) {
                    dao.upsertCursor(previous.copy(committedSequence = sequence, updatedAt = now))
                    return sequence
                }
                sequence = event.sequence
            }
        }
        dao.upsertCursor(previous.copy(committedSequence = sequence, updatedAt = now))
        return sequence
    }

    private fun conflict(reason: MemoryConflict) = MemoryWriteResult.Conflict(reason)

    private suspend fun readableSpace(context: MemoryRuntimeContext): Boolean {
        return validContext(context)
    }

    // 自动任务必须持有实际运行绑定，档案管理上下文不能充当任务凭证。
    private suspend fun validRuntimeContext(context: MemoryRuntimeContext): Boolean =
        context.conversationId.isNotBlank() && validContext(context)

    private suspend fun validJobContext(job: MemoryProcessingJobEntity): Boolean =
        when (job.purpose) {
            MemoryJobPurpose.USER_SELECTION.name -> validContext(job.context()) && isCharacterAvailable(
                job.characterId
            )

            MemoryJobPurpose.AUTOMATIC_EXTRACTION.name, MemoryJobPurpose.TOOL_WRITE.name -> validRuntimeContext(
                job.context()
            )

            MemoryJobPurpose.EVENT_SUMMARY.name, MemoryJobPurpose.EVENT_REBUILD.name -> events.current(
                job
            )

            else -> false
        }

    private suspend fun validContext(context: MemoryRuntimeContext): Boolean {
        val space = dao.getSpace(context.spaceId) ?: return false
        if (space.characterId != context.characterId || space.generation != context.generation || space.status == "DELETED") return false
        if (context.conversationId.isBlank()) return true // 用户在档案管理内的显式操作。
        if (!isCharacterAvailable(context.characterId)) return false
        val binding = dao.getBinding(context.conversationId, context.characterId) ?: return false
        return !binding.archived && space.status == "ACTIVE" && binding.context(space.generation) == context
    }

    private suspend fun validSource(context: MemoryRuntimeContext, ref: MemorySourceRef): Boolean {
        val source = dao.getSource(ref.sourceId) ?: return false
        if (source.spaceId != context.spaceId || source.characterId != context.characterId || source.personaKey != context.personaKey) return false
        if (source.status != "AVAILABLE" || source.messageRevision != ref.sourceRevision) return false
        if (source.kind == "MESSAGE") {
            val message =
                source.messageId?.let { database.conversationDao().getMessage(it) } ?: return false
            if (companionSources.eligibility(message) != com.example.myapplication.model.CompanionSourceEligibility.TEXT_ALLOWED) return false
            if (message.conversationId != source.conversationId || message.memoryRevision != source.messageRevision || message.status != "COMPLETED" || message.isRecalled || message.createdAt != source.occurredAt || message.role != source.speakerRole) return false
            if (MemoryArchiveIdentity.fingerprint(
                    MemorySourceStorage.contentForSource(
                        message,
                        source.contentFormatVersion,
                        com.example.myapplication.model.CompanionSourceEligibility.TEXT_ALLOWED,
                    )
                ) != source.contentFingerprint
            ) return false
        }
        if (ref.start < 0 || ref.end <= ref.start || ref.end > source.content.length) return false
        fun splitsSurrogate(offset: Int) = offset in 1 until source.content.length &&
                source.content[offset - 1].isHighSurrogate() && source.content[offset].isLowSurrogate()
        return !splitsSurrogate(ref.start) && !splitsSurrogate(ref.end)
    }

    private suspend fun validTarget(
        context: MemoryRuntimeContext,
        id: String,
        revision: Long
    ): Boolean {
        val record = dao.getRecord(id) ?: return false
        return record.spaceId == context.spaceId && record.characterId == context.characterId && record.revision == revision && record.status != "DELETED" &&
                (context.conversationId.isBlank() || record.personaKey == context.personaKey)
    }

    private suspend fun validJobTarget(
        context: MemoryRuntimeContext,
        id: String,
        revision: Long
    ): Boolean {
        if (!validTarget(context, id, revision)) return false
        val record = dao.getRecord(id) ?: return false
        return record.personaKey == context.personaKey && record.status in setOf(
            "ACTIVE",
            "ARCHIVED"
        )
    }

    private fun ownsLease(job: MemoryProcessingJobEntity, owner: String): Boolean =
        owner.isNotBlank() && job.leaseOwner == owner && job.leaseUntil > nowProvider() && job.status in setOf(
            "EXTRACTING",
            "EXTRACTED"
        )

    private fun MemoryConversationBindingEntity.context(generation: Long) = MemoryRuntimeContext(
        characterId, spaceId, conversationId, scenarioId, runId, personaKey, generation,
    )

    private fun MemoryProcessingJobEntity.context() = MemoryRuntimeContext(
        characterId, spaceId, conversationId, scenarioId, runId, personaKey, generation,
    )

    private fun MemoryJobSourceEntity.ref() =
        MemorySourceRef(sourceId, sourceRevision, spanStart, spanEnd)

    private suspend fun MemoryProcessingJobEntity.toDomain() = MemoryProcessingJob(
        id,
        context(),
        idempotencyKey,
        dao.listJobSources(id).map { it.ref() },
        gson.fromJson(targetRevisionsJson, revisionMapType),
        MemoryJobStatus.valueOf(status),
        artifact,
        leaseOwner,
        leaseUntil,
        failureCode,
        createdAt,
        updatedAt,
        MemoryJobPurpose.valueOf(purpose),
        requestJson,
        MemoryRequestState.entries.firstOrNull { it.name == requestState }
            ?: MemoryRequestState.UNKNOWN,
        eventId,
        controlJobId,
    )

    companion object {
        fun legacySpaceKey(characterId: String, conversationId: String = ""): String {
            fun hex(value: String) =
                value.toByteArray(Charsets.UTF_8).joinToString("") { "%02X".format(it) }
            return "legacy:${hex(characterId)}:${hex(conversationId)}"
        }
    }
}
