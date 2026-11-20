package com.example.myapplication.model

enum class MemoryJobPurpose { AUTOMATIC_EXTRACTION, USER_SELECTION, TOOL_WRITE, EVENT_SUMMARY, EVENT_REBUILD }
enum class MemorySemanticKind { SPEECH, NARRATION, THOUGHT, SYSTEM_EVENT, TRANSCRIPT, DESCRIPTION, UNKNOWN }
enum class MemoryExtractionFormat { V2, LEGACY_LIST }
enum class MemoryRequestState { NOT_STARTED, STARTED, UNKNOWN }

private val uncertainRequestCodes = setOf(
    "REQUEST_STARTED",
    "NETWORK_UNCERTAIN",
    "CANCELLED_DURING_REQUEST",
    "SAVE_FAILED",
    "SAVE_REJECTED",
)

internal fun memoryRequestMayHaveStarted(requestState: String, failureCode: String): Boolean =
    requestState != MemoryRequestState.NOT_STARTED.name || failureCode in uncertainRequestCodes

data class MemorySemanticText(
    val text: String,
    val kind: MemorySemanticKind,
    val privateToSpeaker: Boolean = false,
    val skipReason: String = "",
)

/** 内容来自冻结的来源快照，局部引用只在当前请求内有效。 */
data class MemoryExtractionSpan(
    val ref: MemorySourceRef,
    val messageId: String,
    val speakerId: String,
    val speakerRole: String,
    val personaKey: String,
    val visibleCharacterIds: List<String>,
    val occurredAt: Long,
    val sourceKind: MemorySourceKind,
    val semanticKind: MemorySemanticKind,
    val text: String,
    val skipReason: String = "",
)

data class MemoryExtractionTarget(
    val id: String,
    val revision: Long,
    val content: String,
)

data class MemoryExtractionSnapshot(
    val sources: List<MemoryExtractionSpan>,
    val targets: List<MemoryExtractionTarget>,
    val characterName: String,
    val userName: String,
    val format: MemoryExtractionFormat = MemoryExtractionFormat.V2,
    val promptOverride: String = "",
    val sourceSequence: Long = 0L,
    val timeZoneId: String = "UTC",
    val knowledgeTargets: MemoryKnowledgeTargets? = null,
)

/** 原响应与结束证据一起落盘；本地校验/事务失败后不再请求同一批模型。 */
data class MemoryExtractionArtifact(
    val version: Int = 1,
    val completion: PromptCompletionResult,
    val skipReasons: List<String> = emptyList(),
)

data class MemoryExtractionProgress(
    val committedJobs: Int = 0,
    val createdRecords: Int = 0,
    val skippedSources: Int = 0,
    val failureCode: String = "",
) {
    val updated: Boolean get() = committedJobs > 0
}

data class MemoryMessageSelection(
    val conversationId: String,
    val messageIds: List<String>,
)

data class MemoryManualSelectionResult(
    val sources: List<MemorySourceEvidence>,
    val unavailableMessageIds: List<String> = emptyList(),
)
