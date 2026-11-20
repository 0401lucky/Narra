package com.example.myapplication.model

enum class MemoryEventStatus { ACTIVE, ARCHIVED, DELETED }
enum class MemoryEventSummaryStatus { EMPTY, READY, STALE }
enum class MemoryEventMemberStatus { LIVE, ARCHIVED }
enum class MemoryEventSummaryMode { INCREMENTAL, FULL }

data class MemoryEventBox(
    val id: String,
    val spaceId: String,
    val characterId: String,
    val personaKey: String,
    val title: String,
    val summary: String,
    val summaryRevision: Long,
    val summaryStatus: MemoryEventSummaryStatus,
    val status: MemoryEventStatus,
    val currentState: String,
    val generation: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class MemoryEventMember(
    val record: MemoryRecord,
    val coveredRevision: Long?,
    val status: MemoryEventMemberStatus,
    val addedAt: Long,
    val sourceRefs: List<MemorySourceRef>,
    val sources: List<MemorySourceEvidence>,
)

data class MemoryEventDetails(val event: MemoryEventBox, val members: List<MemoryEventMember>)

/** 只有当前有效的 READY 结论可作为后续认知维护的输入。 */
data class MemoryEventConclusion(
    val event: MemoryEventBox,
    val coveredRecords: List<MemoryRecordVersion>,
    val sourceRefs: List<MemorySourceRef>,
)

data class MemoryEventSummaryMember(
    val record: MemoryRecord,
    val sourceRefs: List<MemorySourceRef>,
    val sources: List<MemorySourceEvidence>,
)

data class MemoryEventSummarySnapshot(
    val eventId: String,
    val title: String,
    val mode: MemoryEventSummaryMode,
    val expectedGeneration: Long,
    val summaryRevision: Long,
    val previousSummary: String,
    val previousState: String,
    val previousCoverage: List<MemoryRecordVersion>,
    val members: List<MemoryEventSummaryMember>,
    val timeZoneId: String = "UTC",
    val characterName: String = "",
    val userName: String = "",
    val previousSummaryFromCurrentWork: Boolean = false,
)

data class MemoryEventSummaryOutput(
    val summary: String,
    val currentState: String,
    val coveredRecords: List<MemoryRecordVersion>,
)

/** 分批边界和全部原料一次冻结；FULL 后续批次只使用本次重整产生的前情。 */
data class MemoryEventSummaryWork(
    val version: Int = 1,
    val snapshot: MemoryEventSummarySnapshot,
    val batchEnds: List<Int>,
)

/** pendingBatch 是独立的未决请求证据，不能因释放 lease 或重启而清空。 */
data class MemoryEventSummaryArtifact(
    val version: Int = 1,
    val completions: List<PromptCompletionResult> = emptyList(),
    val pendingBatch: Int? = null,
)

data class MemoryEventSummaryPolicy(
    val newMemberThreshold: Int = 4,
    val newContentChars: Int = 6000,
    val batchContentChars: Int = 12_000,
)

data class MemoryEventSummaryOptions(
    val manualRebuild: Boolean = false,
    val expectedGeneration: Long? = null,
    val retryUncertain: Boolean = false,
    val characterName: String = "",
    val userName: String = "",
    val timeZoneId: String = "UTC",
    val policy: MemoryEventSummaryPolicy = MemoryEventSummaryPolicy(),
)

sealed interface MemoryEventPlanResult {
    data class Planned(val job: MemoryProcessingJob) : MemoryEventPlanResult
    data object NotNeeded : MemoryEventPlanResult
    data class NeedsReview(val jobIds: List<String>) : MemoryEventPlanResult
    data class Conflict(val reason: MemoryConflict) : MemoryEventPlanResult
}

enum class MemoryEventConflict { INVALID_INPUT, SCOPE_CHANGED, RECORD_CHANGED, SOURCE_CHANGED, EVENT_CHANGED, DIFFERENT_EVENTS }

sealed interface MemoryEventMutationResult {
    data class Updated(
        val eventId: String,
        val generation: Long,
        val alreadyApplied: Boolean = false
    ) : MemoryEventMutationResult

    data class Conflict(val reason: MemoryEventConflict) : MemoryEventMutationResult
}

data class MemoryEventRelationProgress(
    val applied: Int = 0,
    val ignored: Int = 0,
    val eventIds: List<String> = emptyList(),
)

data class MemoryEventProcessingProgress(
    val committedEvents: Int = 0,
    val completedBatches: Int = 0,
    val appliedRelations: Int = 0,
    val failureCode: String = "",
)
