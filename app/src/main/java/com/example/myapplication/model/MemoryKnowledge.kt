package com.example.myapplication.model

enum class MemoryResidentOperation { ADD, REPLACE, REINFORCE, RETIRE }
enum class MemoryStateOperation { SET, END }
enum class MemoryStateKey(val label: String, val defaultLifetimeMillis: Long) {
    MOOD("心境", 6 * 60 * 60 * 1000L),
    LOCATION("地点", 24 * 60 * 60 * 1000L),
    TASK("任务", 30 * 24 * 60 * 60 * 1000L),
    APPOINTMENT("约定", 7 * 24 * 60 * 60 * 1000L),
}

/** 只限制常驻读取数量，不物理裁剪认知或来源历史。 */
data class MemoryResidentLimits(
    val user: Int = 12,
    val character: Int = 10,
    val relationship: Int = 10,
    val shared: Int = 8,
) {
    fun normalized() = MemoryResidentLimits(
        user.coerceIn(0, 100), character.coerceIn(0, 100),
        relationship.coerceIn(0, 100), shared.coerceIn(0, 100),
    )

    fun capacity(category: MemoryResidentCategory): Int = when (category) {
        MemoryResidentCategory.USER -> user
        MemoryResidentCategory.CHARACTER -> character
        MemoryResidentCategory.RELATIONSHIP -> relationship
        MemoryResidentCategory.SHARED -> shared
    }.coerceIn(0, 100)
}

data class MemoryResidentEntry(
    val id: String,
    val spaceId: String,
    val characterId: String,
    val personaKey: String,
    val category: MemoryResidentCategory,
    val content: String,
    val revision: Long,
    val status: String,
    val subjective: Boolean,
    val editedByUser: Boolean,
    val supersedesId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class MemoryCurrentState(
    val id: String,
    val spaceId: String,
    val characterId: String,
    val personaKey: String,
    val key: MemoryStateKey,
    val content: String,
    val revision: Long,
    val status: String,
    val expiresAt: Long?,
    val endedAt: Long?,
    val observedAt: Long,
    val editedByUser: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class MemoryKnowledgeRevision(
    val kind: MemoryMaterialKind,
    val ownerId: String,
    val revision: Long,
    val content: String,
    val status: String,
    val origin: MemoryChangeOrigin,
    val subjective: Boolean,
    val expiresAt: Long?,
    val endedAt: Long?,
    val sourceRefs: List<MemorySourceRef>,
    val supportingRecords: List<MemoryRecordVersion>,
    val createdAt: Long,
)

data class MemoryResidentDetails(
    val entry: MemoryResidentEntry,
    val revisions: List<MemoryKnowledgeRevision>,
    val sources: List<MemorySourceEvidence>,
)

data class MemoryCurrentStateDetails(
    val state: MemoryCurrentState,
    val revisions: List<MemoryKnowledgeRevision>,
    val sources: List<MemorySourceEvidence>,
)

data class MemoryResidentTarget(
    val id: String,
    val revision: Long,
    val category: MemoryResidentCategory,
    val content: String,
    val subjective: Boolean,
    val editedByUser: Boolean,
)

data class MemoryStateTarget(
    val id: String,
    val revision: Long,
    val key: MemoryStateKey,
    val content: String,
    val status: String,
    val editedByUser: Boolean,
)

/** null 的旧快照没有任何替代已有条目的权限。 */
data class MemoryKnowledgeTargets(
    val version: Int = 1,
    val residents: List<MemoryResidentTarget> = emptyList(),
    val states: List<MemoryStateTarget> = emptyList(),
)

data class MemoryResidentAction(
    val category: MemoryResidentCategory,
    val content: String,
    val subjective: Boolean,
    val sources: List<MemorySourceRef>,
    val operation: MemoryResidentOperation = MemoryResidentOperation.ADD,
    val targetId: String = "",
    val expectedRevision: Long = 0L,
    val stable: Boolean = true,
)

data class MemoryStateAction(
    val key: MemoryStateKey,
    val operation: MemoryStateOperation,
    val content: String,
    val sources: List<MemorySourceRef>,
    val expectedRevision: Long? = null,
    val expiresAt: Long? = null,
)

data class MemoryKnowledgeProgress(
    val consumedJobs: Int = 0,
    val residentWrites: Int = 0,
    val stateWrites: Int = 0,
    val skippedActions: Int = 0,
    val failureCode: String = "",
)

sealed interface MemoryKnowledgeWriteResult {
    data class Written(val id: String, val revision: Long) : MemoryKnowledgeWriteResult
    data class Conflict(val reason: MemoryConflict) : MemoryKnowledgeWriteResult
}
