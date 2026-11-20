package com.example.myapplication.model

enum class MemoryArchiveTab { RECORDS, EVENTS, RESIDENTS, STATES }
enum class MemoryArchiveStatusFilter { ALL, ACTIVE, ARCHIVED, STALE, ENDED }

data class MemoryArchiveQuery(
    val tab: MemoryArchiveTab = MemoryArchiveTab.RECORDS,
    val search: String = "",
    val category: MemoryCategory? = null,
    val residentCategory: MemoryResidentCategory? = null,
    val stateKey: MemoryStateKey? = null,
    val status: MemoryArchiveStatusFilter = MemoryArchiveStatusFilter.ALL,
    val limit: Int = 50,
    val offset: Int = 0,
)

data class MemoryArchivePage<T>(
    val items: List<T>,
    val totalCount: Int,
    val offset: Int,
    val limit: Int,
) {
    val nextOffset: Int get() = offset + items.size
    val hasMore: Boolean get() = nextOffset < totalCount
}

sealed interface MemoryArchiveItem {
    val id: String

    data class Record(val record: MemoryRecord) : MemoryArchiveItem {
        override val id: String get() = record.id
    }

    data class Event(val event: MemoryEventBox) : MemoryArchiveItem {
        override val id: String get() = event.id
    }

    data class Resident(val entry: MemoryResidentEntry) : MemoryArchiveItem {
        override val id: String get() = entry.id
    }

    data class State(val state: MemoryCurrentState) : MemoryArchiveItem {
        override val id: String get() = state.id
    }
}

data class MemoryArchivePersona(val key: String, val label: String)

data class MemoryArchiveConversation(
    val id: String,
    val title: String,
    val scenarioId: String,
    val runId: String,
    val archived: Boolean,
    val exists: Boolean,
    val messageCount: Int,
    val personaKeys: List<String>,
)

/** 原文保留当时消息署名；缺少来源的旧消息不会借当前面具补造身份。 */
data class MemoryArchiveMessage(
    val message: ChatMessage,
    val personaKey: String,
    val source: MemorySourceEvidence?,
)

data class MemoryRecordRevisionDetails(
    val record: MemoryRecord,
    val revision: MemoryRecordRevision,
    val sources: List<MemorySourceEvidence>,
    val sourceRefs: List<MemorySourceRef>,
)

data class ManualMemoryRecordDraft(
    val content: String,
    val kind: MemoryRecordKind = MemoryRecordKind.EPISODE,
    val category: MemoryCategory = MemoryCategory.DAILY,
    val importance: Int = 50,
    val pinned: Boolean = false,
    val pinnedUntil: Long? = null,
    val occurredAt: Long? = null,
)

/** jobId 为空表示整个空间；任务控制与空间控制任一个暂停都会阻止新处理。 */
data class MemoryProcessingControl(
    val spaceId: String,
    val jobId: String = "",
    val userPaused: Boolean = false,
    val version: Long = 0L,
    val updatedAt: Long = 0L,
)

sealed interface MemoryProcessingControlResult {
    data class Updated(val control: MemoryProcessingControl) : MemoryProcessingControlResult
    data class Conflict(val reason: MemoryConflict) : MemoryProcessingControlResult
}

data class MemorySelectionOverlap(
    val jobId: String,
    val purpose: MemoryJobPurpose,
    val status: MemoryJobStatus,
    val requestState: MemoryRequestState,
    val hasArtifact: Boolean,
    val sameContext: Boolean,
    val messageIds: List<String>,
)

data class MemorySelectionInspection(
    val selection: MemoryMessageSelection,
    val availableMessageIds: List<String>,
    val unavailableMessageIds: List<String>,
    val overlappingJobs: List<MemorySelectionOverlap>,
    val failureCode: String = "",
) {
    val requiresReview: Boolean get() = overlappingJobs.isNotEmpty()
    val hasUncertainRequest: Boolean
        get() = overlappingJobs.any {
            !it.hasArtifact && it.requestState != MemoryRequestState.NOT_STARTED &&
                    it.status != MemoryJobStatus.COMMITTED
        }
}
