package com.example.myapplication.model

/** 只包含可保存的档案图；运行绑定、任务、请求与设置不属于资料包。 */
data class MemoryArchiveTransferData(
    val spaces: List<MemorySpace> = emptyList(),
    val records: List<MemoryRecord> = emptyList(),
    val recordRevisions: List<MemoryRecordRevision> = emptyList(),
    val sources: List<MemorySourceEvidence> = emptyList(),
    val recordSources: List<MemoryTransferRecordSource> = emptyList(),
    val events: List<MemoryEventBox> = emptyList(),
    val eventMembers: List<MemoryTransferEventMember> = emptyList(),
    val residents: List<MemoryResidentEntry> = emptyList(),
    val states: List<MemoryCurrentState> = emptyList(),
    val knowledgeRevisions: List<MemoryKnowledgeRevision> = emptyList(),
    val derivedLinks: List<MemoryTransferDerivedLink> = emptyList(),
    val origins: List<MemoryTransferOriginLink> = emptyList(),
    val vectors: List<MemoryTransferVectorIndex> = emptyList(),
) {
    val itemCount: Int
        get() = records.count { it.status != MemoryRecordStatus.DELETED } +
                events.count { it.status != MemoryEventStatus.DELETED } + residents.count { it.status != "DELETED" } + states.count { it.status != "DELETED" }
    val isEmpty: Boolean get() = itemCount == 0 && sources.isEmpty()
}

data class MemoryTransferRecordSource(
    val recordId: String,
    val recordRevision: Long,
    val source: MemorySourceRef,
)

data class MemoryTransferEventMember(
    val eventId: String,
    val recordId: String,
    val coveredRevision: Long?,
    val status: MemoryEventMemberStatus,
    val addedAt: Long,
)

data class MemoryTransferDerivedLink(
    val kind: MemoryMaterialKind,
    val ownerId: String,
    val record: MemoryRecordVersion,
)

enum class MemoryTransferObjectKind { SPACE, RECORD, SOURCE, EVENT, RESIDENT, STATE }

/** 元数据不保存任何记忆/来源正文，删除后不能从出处重建已擦除的内容。 */
data class MemoryTransferOrigin(
    val importBatchId: String,
    val bundleVersion: Int,
    val exportedAt: Long,
    val importedAt: Long,
    val originalId: String?,
    val originalSpaceId: String?,
    val originalCharacterId: String?,
    val originalPersonaKey: String?,
    val originalRevision: Long?,
    val originalCreatedAt: Long? = null,
    val originalUpdatedAt: Long? = null,
    val originalSourceKind: MemorySourceKind? = null,
    val originalSourceStatus: MemorySourceStatus? = null,
    val originalConversationId: String? = null,
    val originalMessageId: String? = null,
    val originalSpeakerId: String? = null,
    val originalSpeakerRole: String? = null,
    val originalVisibleCharacterIds: List<String> = emptyList(),
    val originalPurpose: MemoryPurpose? = null,
    val originalScenarioId: String? = null,
    val originalRunId: String? = null,
    val originalGeneration: Long? = null,
    val originalSpaceStatus: MemorySpaceStatus? = null,
    val originalLegacyScopeType: String? = null,
    val originalLegacyScopeId: String? = null,
    val originalLegacySourceMessageId: String? = null,
    val legacySummary: MemoryImportedSummaryMetadata? = null,
)

/** 旧摘要仅为档案材料，这些原始指针没有本机覆盖/裁剪权限。 */
data class MemoryImportedSummaryMetadata(
    val conversationId: String,
    val assistantId: String,
    val segmentId: String?,
    val startMessageId: String,
    val endMessageId: String,
    val startCreatedAt: Long,
    val endCreatedAt: Long,
    val messageCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceSnapshot: List<ConversationSummarySource>,
    val completionStatus: PromptCompletionStatus,
    val originalMemoryContext: MemoryRuntimeContext?,
)

data class MemoryTransferOriginLink(
    val kind: MemoryTransferObjectKind,
    val localId: String,
    val localRevision: Long,
    val origin: MemoryTransferOrigin,
)

data class MemoryTransferVectorRecord(
    val recordId: String,
    val revision: Long,
    val fingerprint: String,
    val chunkCount: Int,
)

data class MemoryTransferVectorChunk(
    val recordId: String,
    val recordRevision: Long,
    val chunkIndex: Int,
    val spanStart: Int,
    val spanEnd: Int,
    val values: List<Float>,
)

data class MemoryTransferVectorIndex(
    val spaceId: String,
    val personaKey: String,
    val providerId: String,
    val endpoint: String,
    val model: String,
    val preprocessingVersion: Int,
    val dimensions: Int,
    val configurationKey: String,
    val vectorSpace: String,
    val originalGeneration: Long,
    val records: List<MemoryTransferVectorRecord>,
    val chunks: List<MemoryTransferVectorChunk>,
)

data class MemoryImportSourceGroup(
    val key: String,
    val spaceId: String,
    val characterId: String,
    val personaKey: String,
    val purpose: MemoryPurpose,
    val itemCount: Int,
)

enum class MemoryImportTargetMode { EXISTING, NEW_DAILY, NEW_ARCHIVE }

/** 必须由用户选择的本机目标；外来 owner ID 不能作为写入凭证。 */
data class MemoryImportTarget(
    val mode: MemoryImportTargetMode,
    val characterId: String,
    val spaceId: String = "",
    val personaKey: String = MEMORY_UNKNOWN_PERSONA,
    val expectedGeneration: Long = 0L,
)

data class MemoryImportTargetOption(val label: String, val target: MemoryImportTarget)

data class MemoryImportStateConflict(
    val sourceStateId: String,
    val targetSpaceId: String,
    val personaKey: String,
    val key: MemoryStateKey,
    val targetStateId: String?,
    val expectedRevision: Long?,
    val multipleImportedHeads: Boolean,
)

data class MemoryImportPlan(
    val localPlanId: String,
    val bundleFingerprint: String,
    val targets: Map<String, MemoryImportTarget>,
    val stateConflicts: List<MemoryImportStateConflict>,
    val replaceExistingStates: Boolean = false,
)

sealed interface MemoryArchiveImportResult {
    data class Imported(
        val itemCount: Int,
        val sourceCount: Int,
        val restoredVectorIndexes: Int,
        val vectorsNeedingRebuild: Int,
        val objectMappings: List<MemoryTransferObjectMapping>,
        val alreadyImported: Boolean = false,
        val contexts: List<MemoryRuntimeContext> = emptyList(),
    ) : MemoryArchiveImportResult

    data class Conflict(val reason: String) : MemoryArchiveImportResult
}

data class MemoryTransferObjectMapping(
    val kind: MemoryTransferObjectKind,
    val originalId: String,
    val localId: String,
    val originalRevision: Long? = null,
    val localRevision: Long? = null,
)
