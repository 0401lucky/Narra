package com.example.myapplication.model

/** 记忆用途独立于线上/线下表现形式，旧场景只按原作用域兼容读取。 */
enum class MemoryPurpose(val storageValue: String, val label: String) {
    CONTINUOUS("continuous", "日常相处"),
    INDEPENDENT("independent", "独立剧情"),
    LEGACY_COMPAT("legacy_compat", "旧记忆兼容");

    companion object {
        fun fromStorageValue(value: String): MemoryPurpose =
            entries.firstOrNull { it.storageValue == value } ?: LEGACY_COMPAT
    }
}

enum class MemorySpaceStatus { ACTIVE, ARCHIVED, DELETED }
enum class MemoryRecordStatus { ACTIVE, ARCHIVED, STALE, DELETED }
enum class MemorySourceStatus { AVAILABLE, UNKNOWN, CHANGED, MISSING, DELETED }
enum class MemorySourceKind { MESSAGE, MANUAL, IMPORTED, LEGACY_HINT }
enum class MemoryJobStatus { PLANNED, EXTRACTING, EXTRACTED, COMMITTED, FAILED, PAUSED, INVALIDATED }
enum class MemoryRecordKind { EPISODE, FACT, OPINION, WISH, LEGACY_UNCLASSIFIED }
enum class MemoryCategory { DAILY, SHARED, WORK_STUDY, USER_SOCIAL, CHARACTER_SELF, UNRESOLVED, FUTURE }
enum class MemoryChangeOrigin { EXTRACTION, CORRECTION, MANUAL, IMPORTED, MIGRATION }
enum class MemoryResidentCategory { USER, CHARACTER, RELATIONSHIP, SHARED }

data class MemorySpace(
    val id: String,
    val characterId: String,
    val purpose: MemoryPurpose,
    val scenarioId: String = "",
    val runId: String = "",
    val generation: Long = 1L,
    val status: MemorySpaceStatus = MemorySpaceStatus.ACTIVE,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class MemoryRuntimeContext(
    val characterId: String,
    val spaceId: String,
    val conversationId: String,
    val scenarioId: String = "",
    val runId: String = "",
    val personaKey: String = MEMORY_UNKNOWN_PERSONA,
    val generation: Long = 1L,
)

data class MemoryConversationBinding(
    val conversationId: String,
    val characterId: String,
    val spaceId: String,
    val purpose: MemoryPurpose,
    val scenarioId: String,
    val runId: String,
    val personaKey: String,
    val archived: Boolean,
    val createdAt: Long,
)

data class MemoryRecord(
    val id: String,
    val spaceId: String,
    val characterId: String,
    val kind: MemoryRecordKind,
    val category: MemoryCategory,
    val content: String,
    val revision: Long = 1L,
    val status: MemoryRecordStatus = MemoryRecordStatus.ACTIVE,
    val importance: Int = 0,
    val pinned: Boolean = false,
    val pinnedUntil: Long? = null,
    val occurredAt: Long? = null,
    val datePrecision: String = "unknown",
    val personaKey: String = MEMORY_UNKNOWN_PERSONA,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastUsedAt: Long = 0L,
    val legacyScopeType: String = "",
    val legacyScopeId: String = "",
    val legacySourceMessageId: String = "",
)

data class MemoryRecordRevision(
    val recordId: String,
    val revision: Long,
    val content: String,
    val origin: MemoryChangeOrigin,
    val createdAt: Long,
)

/** span 使用来源正文的 UTF-16 偏移；结束位置不含在区间内。 */
data class MemorySourceRef(
    val sourceId: String,
    val sourceRevision: Long,
    val start: Int,
    val end: Int,
)

data class MemorySourceEvidence(
    val id: String,
    val spaceId: String,
    val characterId: String,
    val kind: MemorySourceKind,
    val conversationId: String,
    val messageId: String,
    val messageRevision: Long,
    val speakerId: String,
    val speakerRole: String,
    val personaKey: String,
    val visibleCharacterIds: List<String>,
    val content: String,
    val contentLength: Int,
    val status: MemorySourceStatus,
    val occurredAt: Long,
    val ingestedAt: Long,
    val contentFingerprint: String,
    val contentFormatVersion: Int = 1,
) {
    fun fullRef(): MemorySourceRef = MemorySourceRef(id, messageRevision, 0, contentLength)
}

data class MemoryIngressEvent(
    val sequence: Long,
    val source: MemorySourceEvidence,
    val status: String,
)

data class MemoryProcessingCursor(
    val spaceId: String,
    val committedSequence: Long,
    val historyBackfilled: Boolean,
    val updatedAt: Long,
)

data class MemoryProcessingJob(
    val id: String,
    val context: MemoryRuntimeContext,
    val idempotencyKey: String,
    val sources: List<MemorySourceRef>,
    val targetRevisions: Map<String, Long>,
    val status: MemoryJobStatus,
    val artifact: String,
    val leaseOwner: String,
    val leaseUntil: Long,
    val failureCode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val purpose: MemoryJobPurpose = MemoryJobPurpose.AUTOMATIC_EXTRACTION,
    val requestJson: String = "",
    val requestState: MemoryRequestState = MemoryRequestState.UNKNOWN,
    val eventId: String = "",
    val controlJobId: String = "",
)

data class MemoryRecordDraft(
    val localId: String,
    val content: String,
    val sources: List<MemorySourceRef>,
    val kind: MemoryRecordKind = MemoryRecordKind.EPISODE,
    val category: MemoryCategory = MemoryCategory.DAILY,
    val importance: Int = 0,
    val occurredAt: Long? = null,
    val datePrecision: String = "unknown",
)

data class MemoryRecordCorrection(
    val recordId: String,
    val expectedRevision: Long,
    val content: String,
    val sources: List<MemorySourceRef>,
)

data class MemoryRelationDraft(
    val fromLocalId: String,
    val targetRecordId: String = "",
    val targetLocalId: String = "",
    val label: String = "same_event",
    val eventName: String = "",
)

data class MemoryCommitBatch(
    val records: List<MemoryRecordDraft> = emptyList(),
    val corrections: List<MemoryRecordCorrection> = emptyList(),
    val relations: List<MemoryRelationDraft> = emptyList(),
    val residents: List<MemoryResidentAction> = emptyList(),
    val states: List<MemoryStateAction> = emptyList(),
)

sealed interface MemoryWriteResult {
    data class Committed(
        val recordIds: List<String>,
        val committedSequence: Long = 0L,
        val alreadyCommitted: Boolean = false,
    ) : MemoryWriteResult

    data class Conflict(val reason: MemoryConflict) : MemoryWriteResult
}

enum class MemoryConflict { SPACE_CHANGED, SOURCE_CHANGED, RECORD_CHANGED, JOB_CHANGED, INVALID_INPUT }

data class MemoryRecordDetails(
    val record: MemoryRecord,
    val revisions: List<MemoryRecordRevision>,
    val sources: List<MemorySourceEvidence>,
    val sourceRefs: List<MemorySourceRef>,
)

const val MEMORY_UNKNOWN_PERSONA = "unknown"
const val MEMORY_UNASSIGNED_CHARACTER = "legacy-unassigned"
const val MEMORY_ENGINE_COMPATIBILITY = 1
const val MEMORY_ENGINE_CURRENT = 2
