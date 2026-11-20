package com.example.myapplication.data.repository.context

import com.example.myapplication.model.MemoryCommitBatch
import com.example.myapplication.model.MemoryConversationBinding
import com.example.myapplication.model.MemoryIngressEvent
import com.example.myapplication.model.MemoryJobStatus
import com.example.myapplication.model.MemoryProcessingCursor
import com.example.myapplication.model.MemoryProcessingJob
import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.model.MemoryRecordDetails
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemorySourceEvidence
import com.example.myapplication.model.MemorySourceRef
import com.example.myapplication.model.MemorySpace
import com.example.myapplication.model.MemoryWriteResult
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.MemorySemanticText
import com.example.myapplication.model.MemoryManualSelectionResult
import com.example.myapplication.model.MemoryMessageSelection
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.MemoryMaterialRef
import com.example.myapplication.model.MemoryRecallSnapshot
import com.example.myapplication.model.MemoryRecallReceipt
import com.example.myapplication.model.MemoryRecallRequest
import com.example.myapplication.model.MemoryRecallOmission
import com.example.myapplication.model.MemoryEventBox
import com.example.myapplication.model.MemoryEventDetails
import com.example.myapplication.model.MemoryEventConclusion
import com.example.myapplication.model.MemoryEventMutationResult
import com.example.myapplication.model.MemoryEventRelationProgress
import com.example.myapplication.model.MemoryEventPlanResult
import com.example.myapplication.model.MemoryEventSummaryOptions
import com.example.myapplication.model.MemoryRecordVersion
import com.example.myapplication.model.PromptCompletionResult
import com.example.myapplication.model.MemoryResidentEntry
import com.example.myapplication.model.MemoryResidentDetails
import com.example.myapplication.model.MemoryResidentCategory
import com.example.myapplication.model.MemoryCurrentState
import com.example.myapplication.model.MemoryCurrentStateDetails
import com.example.myapplication.model.MemoryStateKey
import com.example.myapplication.model.MemoryKnowledgeTargets
import com.example.myapplication.model.MemoryKnowledgeProgress
import com.example.myapplication.model.MemoryKnowledgeWriteResult
import com.example.myapplication.model.MemoryMaterialKind
import com.example.myapplication.model.MemoryArchiveConversation
import com.example.myapplication.model.MemoryArchiveItem
import com.example.myapplication.model.MemoryArchiveMessage
import com.example.myapplication.model.MemoryArchivePage
import com.example.myapplication.model.MemoryArchivePersona
import com.example.myapplication.model.MemoryArchiveQuery
import com.example.myapplication.model.MemoryRecordRevisionDetails
import com.example.myapplication.model.ManualMemoryRecordDraft
import com.example.myapplication.model.MemoryProcessingControl
import com.example.myapplication.model.MemoryProcessingControlResult
import com.example.myapplication.model.MemorySelectionInspection
import kotlinx.coroutines.flow.Flow

interface MemoryArchiveRepository {
    suspend fun listArchiveCharacterIds(): List<String> = error("当前存储不支持档案角色查询")

    suspend fun ensureDailySpace(
        characterId: String,
        personaKey: String = com.example.myapplication.model.MEMORY_UNKNOWN_PERSONA,
    ): MemoryRuntimeContext = error("当前存储不支持创建日常档案")

    suspend fun getManagementContext(
        characterId: String, spaceId: String,
        personaKey: String = com.example.myapplication.model.MEMORY_UNKNOWN_PERSONA,
    ): MemoryRuntimeContext? {
        val space = getSpace(spaceId)?.takeIf {
            it.characterId == characterId && it.status != com.example.myapplication.model.MemorySpaceStatus.DELETED
        } ?: return null
        if (personaKey.isBlank()) return null
        return MemoryRuntimeContext(
            characterId,
            spaceId,
            "",
            space.scenarioId,
            space.runId,
            personaKey,
            space.generation
        )
    }

    suspend fun listArchivePersonas(context: MemoryRuntimeContext): List<MemoryArchivePersona> =
        error("当前存储不支持档案身份查询")

    suspend fun listArchiveConversations(context: MemoryRuntimeContext): List<MemoryArchiveConversation> =
        error("当前存储不支持档案对话查询")

    suspend fun listArchiveMessages(
        context: MemoryRuntimeContext, conversationId: String, limit: Int = 100, offset: Int = 0,
    ): MemoryArchivePage<MemoryArchiveMessage> = error("当前存储不支持档案原文查询")

    suspend fun getLatestArchiveAssistantMessage(
        context: MemoryRuntimeContext, conversationId: String,
    ): MemoryArchiveMessage? = error("当前存储不支持当前回合定位")

    suspend fun queryArchive(
        context: MemoryRuntimeContext, query: MemoryArchiveQuery,
    ): MemoryArchivePage<MemoryArchiveItem> = error("当前存储不支持档案查询")

    suspend fun countArchiveItems(characterId: String): Int = error("当前存储不支持档案计数")

    suspend fun getRecordRevisionDetails(
        context: MemoryRuntimeContext, recordId: String, revision: Long,
    ): MemoryRecordRevisionDetails? = error("当前存储不支持历史来源查询")

    suspend fun getArchivedRecallReceipt(
        context: MemoryRuntimeContext, conversationId: String, messageId: String, requestId: String,
    ): MemoryRecallReceipt? = error("当前存储不支持历史回执查询")

    suspend fun addRecord(
        context: MemoryRuntimeContext,
        draft: ManualMemoryRecordDraft
    ): MemoryWriteResult =
        error("当前存储不支持手动新增经历")

    suspend fun upsertLegacyEntry(entry: com.example.myapplication.model.MemoryEntry): MemoryWriteResult =
        error("当前存储不支持兼容记忆编辑")

    suspend fun setRecordPinned(
        context: MemoryRuntimeContext, recordId: String, expectedRevision: Long,
        pinned: Boolean, pinnedUntil: Long? = null,
    ): MemoryWriteResult = error("当前存储不支持经历置顶")

    suspend fun setRecordArchived(
        context: MemoryRuntimeContext, recordId: String, expectedRevision: Long, archived: Boolean,
    ): MemoryWriteResult = error("当前存储不支持经历归档")

    suspend fun setSpaceArchived(
        context: MemoryRuntimeContext,
        archived: Boolean
    ): MemoryWriteResult =
        error("当前存储不支持空间归档")

    suspend fun getProcessingControl(
        context: MemoryRuntimeContext, jobId: String = "",
    ): MemoryProcessingControl? = error("当前存储不支持处理控制")

    suspend fun setProcessingPaused(
        context: MemoryRuntimeContext,
        expectedControlVersion: Long,
        paused: Boolean,
        jobId: String = "",
    ): MemoryProcessingControlResult = error("当前存储不支持处理控制")

    suspend fun isProcessingPaused(context: MemoryRuntimeContext, jobId: String = ""): Boolean =
        false

    suspend fun inspectSelection(
        context: MemoryRuntimeContext, selection: MemoryMessageSelection,
    ): MemorySelectionInspection = error("当前存储不支持选区预检")

    fun observeSpaces(characterId: String): Flow<List<MemorySpace>>
    suspend fun listSpaces(characterId: String): List<MemorySpace>
    suspend fun getSpace(spaceId: String): MemorySpace?
    suspend fun getRuntimeContext(
        conversationId: String,
        characterId: String
    ): MemoryRuntimeContext?

    suspend fun listScenarioBindings(scenarioId: String): List<MemoryConversationBinding>

    suspend fun bindConversation(
        scenario: RoleplayScenario,
        conversationId: String,
        characterIds: List<String>,
        runId: String,
    ): List<MemoryRuntimeContext>

    suspend fun archiveConversation(conversationId: String)
    suspend fun closeCharacterSpaces(characterId: String)
    suspend fun deleteScenarioArchives(scenarioId: String)
    suspend fun deleteSpace(context: MemoryRuntimeContext): MemoryWriteResult
    fun observeRecords(context: MemoryRuntimeContext): Flow<List<MemoryRecord>>
    suspend fun listRecords(
        context: MemoryRuntimeContext,
        limit: Int = 100,
        offset: Int = 0
    ): List<MemoryRecord>

    suspend fun listLegacyCompatibleRecords(
        characterId: String,
        conversationId: String,
        useGlobalMemory: Boolean = false
    ): List<MemoryRecord>

    suspend fun getRecordDetails(
        context: MemoryRuntimeContext,
        recordId: String
    ): MemoryRecordDetails?

    suspend fun getSource(context: MemoryRuntimeContext, sourceId: String): MemorySourceEvidence?
    suspend fun getSourceSegments(
        context: MemoryRuntimeContext,
        sourceId: String
    ): List<MemorySemanticText>

    suspend fun listCommittedSpans(sourceId: String): List<MemorySourceRef>
    suspend fun captureSelectedMessages(
        context: MemoryRuntimeContext,
        selection: MemoryMessageSelection
    ): MemoryManualSelectionResult

    suspend fun listPendingIngress(
        context: MemoryRuntimeContext,
        limit: Int = 100
    ): List<MemoryIngressEvent>

    suspend fun getCursor(spaceId: String): MemoryProcessingCursor?

    suspend fun planJob(
        context: MemoryRuntimeContext,
        idempotencyKey: String,
        sources: List<MemorySourceRef>,
        targetRevisions: Map<String, Long> = emptyMap(),
        requestJson: String = "",
    ): MemoryProcessingJob

    suspend fun planSelectedJob(
        context: MemoryRuntimeContext,
        idempotencyKey: String,
        sources: List<MemorySourceRef>,
        targetRevisions: Map<String, Long> = emptyMap(),
        requestJson: String = "",
    ): MemoryProcessingJob

    suspend fun planToolJob(
        context: MemoryRuntimeContext,
        idempotencyKey: String,
        sources: List<MemorySourceRef>,
    ): MemoryProcessingJob

    suspend fun getJob(jobId: String): MemoryProcessingJob?

    /** 只替换未请求且未持有有效 lease 的目标快照；原任务及来源保持可追溯。 */
    suspend fun replanUnrequestedJob(
        jobId: String,
        idempotencyKey: String,
        targetRevisions: Map<String, Long>,
        requestJson: String,
    ): MemoryProcessingJob? = null

    suspend fun listRecoverableJobs(spaceId: String): List<MemoryProcessingJob>
    suspend fun listUnresolvedAutomaticJobs(context: MemoryRuntimeContext): List<MemoryProcessingJob> =
        emptyList()

    suspend fun listJobHistory(
        context: MemoryRuntimeContext,
        limit: Int = 100,
        offset: Int = 0
    ): List<MemoryProcessingJob>

    suspend fun listCommittedExtractionJobs(
        context: MemoryRuntimeContext,
        limit: Int = 100,
        offset: Int = 0
    ): List<MemoryProcessingJob>

    suspend fun getJobRecordIds(jobId: String): Map<String, String>
    fun observePendingProposal(conversationId: String): Flow<PendingMemoryProposal?>
    fun observeProposalHistory(conversationId: String): Flow<List<MemoryProposalHistoryItem>>
    suspend fun getPendingProposal(proposalId: String): PendingMemoryProposal?
    suspend fun saveProposal(proposal: PendingMemoryProposal)
    suspend fun approveStoredProposal(proposalId: String): MemoryWriteResult
    suspend fun rejectStoredProposal(proposalId: String)
    suspend fun clearStoredProposals(conversationId: String)
    suspend fun claimJob(
        jobId: String,
        owner: String,
        leaseMillis: Long = 120_000L
    ): MemoryProcessingJob?

    suspend fun renewLease(jobId: String, owner: String, leaseMillis: Long = 120_000L): Boolean
    suspend fun markRequestStarted(jobId: String, owner: String): Boolean

    suspend fun saveExtraction(jobId: String, owner: String, artifact: String): Boolean
    suspend fun releaseJob(
        jobId: String,
        owner: String,
        status: MemoryJobStatus,
        failureCode: String = ""
    ): Boolean

    suspend fun commitJob(jobId: String, owner: String, batch: MemoryCommitBatch): MemoryWriteResult

    suspend fun editRecord(
        context: MemoryRuntimeContext,
        recordId: String,
        expectedRevision: Long,
        content: String,
    ): MemoryWriteResult

    suspend fun deleteRecord(
        context: MemoryRuntimeContext,
        recordId: String,
        expectedRevision: Long
    ): MemoryWriteResult

    suspend fun getEngineVersion(): Int

    suspend fun keepCompatibilityEngine(): Unit = error("当前存储不支持引擎兼容控制")

    suspend fun listResidentEntries(
        context: MemoryRuntimeContext,
        limit: Int = 100,
        offset: Int = 0
    ): List<MemoryResidentEntry> = emptyList()

    suspend fun listCurrentStates(context: MemoryRuntimeContext): List<MemoryCurrentState> =
        emptyList()

    suspend fun getResidentDetails(
        context: MemoryRuntimeContext,
        id: String
    ): MemoryResidentDetails? = null

    suspend fun getCurrentStateDetails(
        context: MemoryRuntimeContext,
        id: String
    ): MemoryCurrentStateDetails? = null

    suspend fun getKnowledgeTargets(context: MemoryRuntimeContext): MemoryKnowledgeTargets =
        MemoryKnowledgeTargets()

    suspend fun consumeCommittedKnowledge(context: MemoryRuntimeContext): MemoryKnowledgeProgress =
        MemoryKnowledgeProgress()

    suspend fun addResidentEntry(
        context: MemoryRuntimeContext,
        category: MemoryResidentCategory,
        content: String,
        subjective: Boolean
    ): MemoryKnowledgeWriteResult =
        MemoryKnowledgeWriteResult.Conflict(com.example.myapplication.model.MemoryConflict.SPACE_CHANGED)

    suspend fun editResidentEntry(
        context: MemoryRuntimeContext,
        id: String,
        expectedRevision: Long,
        content: String,
        subjective: Boolean
    ): MemoryKnowledgeWriteResult =
        MemoryKnowledgeWriteResult.Conflict(com.example.myapplication.model.MemoryConflict.SPACE_CHANGED)

    suspend fun setResidentArchived(
        context: MemoryRuntimeContext,
        id: String,
        expectedRevision: Long,
        archived: Boolean
    ): MemoryKnowledgeWriteResult =
        MemoryKnowledgeWriteResult.Conflict(com.example.myapplication.model.MemoryConflict.SPACE_CHANGED)

    suspend fun setCurrentState(
        context: MemoryRuntimeContext,
        key: MemoryStateKey,
        expectedRevision: Long,
        content: String,
        expiresAt: Long? = null
    ): MemoryKnowledgeWriteResult =
        MemoryKnowledgeWriteResult.Conflict(com.example.myapplication.model.MemoryConflict.SPACE_CHANGED)

    suspend fun endCurrentState(
        context: MemoryRuntimeContext,
        id: String,
        expectedRevision: Long
    ): MemoryKnowledgeWriteResult =
        MemoryKnowledgeWriteResult.Conflict(com.example.myapplication.model.MemoryConflict.SPACE_CHANGED)

    suspend fun deleteKnowledge(
        context: MemoryRuntimeContext,
        kind: MemoryMaterialKind,
        id: String,
        expectedRevision: Long
    ): MemoryKnowledgeWriteResult =
        MemoryKnowledgeWriteResult.Conflict(com.example.myapplication.model.MemoryConflict.SPACE_CHANGED)

    suspend fun listEvents(context: MemoryRuntimeContext): List<MemoryEventBox> = emptyList()
    suspend fun getEventDetails(
        context: MemoryRuntimeContext,
        eventId: String
    ): MemoryEventDetails? = null

    suspend fun listEventConclusions(context: MemoryRuntimeContext): List<MemoryEventConclusion> =
        emptyList()

    suspend fun consumeEventRelations(context: MemoryRuntimeContext): MemoryEventRelationProgress =
        MemoryEventRelationProgress()

    suspend fun linkEventMembers(
        context: MemoryRuntimeContext,
        from: MemoryRecordVersion,
        target: MemoryRecordVersion,
        title: String = "",
    ): MemoryEventMutationResult =
        MemoryEventMutationResult.Conflict(com.example.myapplication.model.MemoryEventConflict.SCOPE_CHANGED)

    suspend fun unlinkEventMember(
        context: MemoryRuntimeContext, eventId: String, recordId: String, expectedGeneration: Long,
    ): MemoryEventMutationResult =
        MemoryEventMutationResult.Conflict(com.example.myapplication.model.MemoryEventConflict.SCOPE_CHANGED)

    suspend fun setEventArchived(
        context: MemoryRuntimeContext, eventId: String, expectedGeneration: Long, archived: Boolean,
    ): MemoryEventMutationResult =
        MemoryEventMutationResult.Conflict(com.example.myapplication.model.MemoryEventConflict.SCOPE_CHANGED)

    suspend fun planEventSummary(
        context: MemoryRuntimeContext,
        eventId: String,
        options: MemoryEventSummaryOptions = MemoryEventSummaryOptions(),
    ): MemoryEventPlanResult = MemoryEventPlanResult.NotNeeded

    suspend fun beginEventSummaryBatch(jobId: String, owner: String, batchIndex: Int): Boolean =
        false

    suspend fun saveEventSummaryBatch(
        jobId: String,
        owner: String,
        batchIndex: Int,
        completion: PromptCompletionResult
    ): Boolean = false

    suspend fun commitEventSummary(jobId: String, owner: String): MemoryWriteResult =
        MemoryWriteResult.Conflict(com.example.myapplication.model.MemoryConflict.JOB_CHANGED)

    suspend fun listRecallRecords(context: MemoryRuntimeContext): List<MemoryRecord> = emptyList()
    suspend fun listActiveRecallContexts(characterId: String): List<MemoryRuntimeContext> =
        emptyList()

    suspend fun getRecallSnapshot(context: MemoryRuntimeContext): MemoryRecallSnapshot? = null
    suspend fun validateRecallRecords(
        context: MemoryRuntimeContext,
        recordRevisions: Map<String, Long>
    ): Boolean = false

    suspend fun validateRecallMaterials(
        context: MemoryRuntimeContext,
        materials: List<MemoryMaterialRef>
    ): Boolean = false

    suspend fun archiveRecallOverflow(context: MemoryRuntimeContext, activeCapacity: Int) = Unit
    suspend fun prepareRecallReceipt(request: MemoryRecallRequest): Boolean = false
    suspend fun prepareRecallTransmission(
        requestId: String,
        payloadFingerprint: String,
        materials: List<MemoryMaterialRef>,
        omitted: List<MemoryRecallOmission>
    ): Int? = null

    suspend fun confirmRecallTransmission(requestId: String, sequence: Int): Boolean = false
    suspend fun failRecallRequest(requestId: String, failureCode: String) = Unit
    suspend fun getRecallReceipt(
        context: MemoryRuntimeContext,
        requestId: String
    ): MemoryRecallReceipt? = null
}
