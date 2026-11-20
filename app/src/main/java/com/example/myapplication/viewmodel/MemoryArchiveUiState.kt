package com.example.myapplication.viewmodel

import com.example.myapplication.model.*

enum class MemoryArchivePanel { ARCHIVE, RECEIPT, PROCESSING }

data class MemoryArchiveSourceUi(
    val ref: MemorySourceRef,
    val evidence: MemorySourceEvidence?,
)

data class MemoryArchiveDetailUi(
    val item: MemoryArchiveItem,
    val selectedRevision: Long,
    val content: String = "",
    val revisions: List<Long> = emptyList(),
    val sources: List<MemoryArchiveSourceUi> = emptyList(),
    val members: List<MemoryEventMember> = emptyList(),
    val subjective: Boolean = false,
    val historyLabel: String = "",
    val revisionTime: Long? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val importOrigins: List<MemoryTransferOriginLink> = emptyList(),
    val knowledgeRevision: MemoryKnowledgeRevision? = null,
)

data class MemoryArchiveEditorUi(
    val kind: MemoryMaterialKind,
    val id: String = "",
    val expectedRevision: Long = 0L,
    val content: String = "",
    val category: MemoryCategory = MemoryCategory.DAILY,
    val recordKind: MemoryRecordKind = MemoryRecordKind.EPISODE,
    val residentCategory: MemoryResidentCategory = MemoryResidentCategory.USER,
    val stateKey: MemoryStateKey = MemoryStateKey.MOOD,
    val subjective: Boolean = false,
    val expiresAt: Long? = null,
    val error: String? = null,
)

data class MemoryArchiveUiState(
    val characterId: String,
    val spaces: List<MemorySpace> = emptyList(),
    val context: MemoryRuntimeContext? = null,
    val personas: List<MemoryArchivePersona> = emptyList(),
    val query: MemoryArchiveQuery = MemoryArchiveQuery(),
    val panel: MemoryArchivePanel = MemoryArchivePanel.ARCHIVE,
    val items: List<MemoryArchiveItem> = emptyList(),
    val totalCount: Int = 0,
    val characterCount: Int = 0,
    val indexProgress: List<MemoryVectorIndexProgress>? = null,
    val indexError: String? = null,
    val indexCanRebuild: Boolean = false,
    val nextOffset: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = true,
    val pageError: String? = null,
    val detail: MemoryArchiveDetailUi? = null,
    val editor: MemoryArchiveEditorUi? = null,
    val isSaving: Boolean = false,
    val linkCandidates: List<MemoryRecord> = emptyList(),
    val linkSearch: String = "",
    val linkNextOffset: Int = 0,
    val linkHasMore: Boolean = false,
    val linkLoading: Boolean = false,
    val conversations: List<MemoryArchiveConversation> = emptyList(),
    val conversationId: String? = null,
    val messages: List<MemoryArchiveMessage> = emptyList(),
    val latestRoundMessage: MemoryArchiveMessage? = null,
    val olderMessageOffset: Int = 0,
    val messagesLoading: Boolean = false,
    val messagesError: String? = null,
    val selectedMessageId: String? = null,
    val selectedMessageExplicit: Boolean = false,
    val receipt: MemoryRecallReceipt? = null,
    val receiptLoading: Boolean = false,
    val receiptError: String? = null,
    val selectedMessageIds: Set<String> = emptySet(),
    val inspection: MemorySelectionInspection? = null,
    val jobs: List<MemoryProcessingJob> = emptyList(),
    val hasMoreJobs: Boolean = false,
    val jobsLoading: Boolean = false,
    val processingControl: MemoryProcessingControl? = null,
    val jobControls: Map<String, MemoryProcessingControl> = emptyMap(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

fun MemoryArchiveItem.revision(): Long = when (this) {
    is MemoryArchiveItem.Record -> record.revision
    is MemoryArchiveItem.Event -> event.summaryRevision
    is MemoryArchiveItem.Resident -> entry.revision
    is MemoryArchiveItem.State -> state.revision
}

fun MemoryArchiveItem.content(): String = when (this) {
    is MemoryArchiveItem.Record -> record.content
    is MemoryArchiveItem.Event -> event.summary
    is MemoryArchiveItem.Resident -> entry.content
    is MemoryArchiveItem.State -> state.content
}

data class MemoryArchiveTaskActions(
    val processSelection: suspend (MemoryRuntimeContext, MemoryMessageSelection, Boolean, Boolean) -> MemoryExtractionProgress,
    val resumeExtraction: suspend (MemoryRuntimeContext, String) -> MemoryExtractionProgress,
    val resumeEvent: suspend (MemoryRuntimeContext, String) -> MemoryEventProcessingProgress,
    val rebuildEvent: suspend (MemoryRuntimeContext, String, Long, Boolean) -> MemoryEventProcessingProgress,
)

data class MemoryArchiveReadExtras(
    val indexProgress: suspend (MemoryRuntimeContext) -> List<MemoryVectorIndexProgress>,
    val origins: suspend (MemoryRuntimeContext, MemoryTransferObjectKind, String) -> List<MemoryTransferOriginLink>,
    val rebuildIndex: suspend (MemoryRuntimeContext, Boolean) -> MemoryVectorBuildResult,
)
