package com.example.myapplication.model

enum class MemoryRecallLayer { RESIDENT, PINNED, EXPERIENCE, STATE }
enum class MemoryMaterialKind { RECORD, EVENT, RESIDENT, STATE }
enum class MemoryRecallStatus { PREPARED, SENT, FAILED }
enum class MemoryRecallOmissionReason(val label: String) {
    TOKEN_BUDGET("超过本轮额度"), ITEM_LIMIT("超过经历条数"), PARTIAL_RECORD("只提供所列片段，完整正文仍保留"),
    NOT_RENDERED("预设或组装未包含"), DUPLICATE_RENDERING("重复材料已去重"),
    SOURCE_CHANGED("来源或版本已改变"), SCOPE_CHANGED("运行或身份已改变"), PAYLOAD_CHANGED("发送内容已改变"),
}

enum class MemoryVectorAvailability { NOT_REQUESTED, AVAILABLE, EMPTY, TIMED_OUT, FAILED }

data class MemoryRecordVersion(val recordId: String, val revision: Long)

data class MemoryEventRecallContext(
    val eventId: String,
    val generation: Long,
    val anchor: MemoryRecordVersion,
)

/** 只保存出处元数据。span 是该版本正文的 UTF-16 半开区间，不是原始消息的位置。 */
data class MemoryMaterialRef(
    val kind: MemoryMaterialKind,
    val id: String,
    val revision: Long,
    val spanStart: Int,
    val spanEnd: Int,
    val contentFingerprint: String,
    val sourceRefs: List<MemorySourceRef> = emptyList(),
    val supportingRecords: List<MemoryRecordVersion> = emptyList(),
    val legacy: Boolean = false,
    val eventContext: MemoryEventRecallContext? = null,
    val imported: Boolean = false,
)

data class MemoryRecallMaterial(
    val ref: MemoryMaterialRef,
    val layer: MemoryRecallLayer,
    val content: String,
    val label: String,
    val fragment: Boolean = false,
    val renderKey: String = java.util.UUID.randomUUID().toString(),
)

data class MemoryRecallOmission(
    val ref: MemoryMaterialRef,
    val reason: MemoryRecallOmissionReason,
)

/** C4/C5 发布自己的维护产物后，由同一读取出口核对来源与当前修订。 */
data class MemoryDerivedRecallEntry(
    val id: String,
    val kind: MemoryMaterialKind,
    val content: String,
    val revision: Long,
    val label: String,
    val supportingRecords: List<MemoryRecordVersion>,
    val sourceRefs: List<MemorySourceRef>,
    val updatedAt: Long,
    /** 当前关联成员包括未摘要的 LIVE 进展；不扩充本条摘要的覆盖证明。 */
    val associatedRecords: List<MemoryRecordVersion> = supportingRecords,
    val imported: Boolean = false,
)

data class MemoryRecallSnapshot(
    val context: MemoryRuntimeContext,
    val records: List<MemoryRecord>,
    val recordSources: Map<String, List<MemorySourceRef>>,
    val events: List<MemoryDerivedRecallEntry> = emptyList(),
    val residents: List<MemoryDerivedRecallEntry> = emptyList(),
    val states: List<MemoryDerivedRecallEntry> = emptyList(),
    val legacyRecords: List<MemoryRecord> = emptyList(),
    val eventMemberships: Map<String, List<MemoryRecordVersion>> = emptyMap(),
    val eventGenerations: Map<String, Long> = emptyMap(),
    val importedRecordIds: Set<String> = emptySet(),
)

data class MemoryRecallPlan(
    val context: MemoryRuntimeContext,
    val selected: List<MemoryMaterialRef>,
    val materials: List<MemoryRecallMaterial>,
    val omitted: List<MemoryRecallOmission>,
    val section: String,
    val estimatedTokens: Int,
    val tokenBudget: Int,
    val vectorAvailability: MemoryVectorAvailability = MemoryVectorAvailability.NOT_REQUESTED,
)

/** 只在本轮内传递；预览组装不会创建请求，也不会写发送回执。 */
data class MemoryRecallRequest(
    val requestId: String,
    val anchorMessageId: String,
    val userMessageId: String,
    val plan: MemoryRecallPlan,
    val createdAt: Long,
)

data class MemoryRecallTransmission(
    val sequence: Int,
    val payloadFingerprint: String,
    val materials: List<MemoryMaterialRef>,
    val status: MemoryRecallStatus,
    val preparedAt: Long,
    val sentAt: Long? = null,
)

data class MemoryRecallReceipt(
    val requestId: String,
    val context: MemoryRuntimeContext,
    val anchorMessageId: String,
    val userMessageId: String,
    val selected: List<MemoryMaterialRef>,
    val sent: List<MemoryMaterialRef>,
    val omitted: List<MemoryRecallOmission>,
    val payloadFingerprint: String,
    val status: MemoryRecallStatus,
    val createdAt: Long,
    val sentAt: Long? = null,
    val failureCode: String = "",
    val transmissions: List<MemoryRecallTransmission> = emptyList(),
)
