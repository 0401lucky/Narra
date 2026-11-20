package com.example.myapplication.data.local.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "memory_event_boxes",
    foreignKeys = [ForeignKey(
        entity = MemorySpaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["spaceId"]
    )],
    indices = [Index("spaceId", "status")],
)
data class MemoryEventBoxEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val characterId: String,
    val title: String,
    val summary: String,
    val summaryRevision: Long,
    val summaryStatus: String,
    val status: String,
    val currentState: String,
    val generation: Long,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'unknown'") val personaKey: String = "unknown",
)

@Entity(
    tableName = "memory_event_members",
    primaryKeys = ["eventId", "recordId"],
    foreignKeys = [
        ForeignKey(
            entity = MemoryEventBoxEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("recordId")],
)
data class MemoryEventMemberEntity(
    val eventId: String,
    val recordId: String,
    val coveredRevision: Long?,
    val status: String,
    val addedAt: Long,
)

@Entity(
    tableName = "memory_resident_entries",
    foreignKeys = [ForeignKey(
        entity = MemorySpaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["spaceId"]
    )],
    indices = [Index("spaceId", "category", "personaKey")],
)
data class MemoryResidentEntryEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val characterId: String,
    val category: String,
    val personaKey: String,
    val content: String,
    val revision: Long,
    val status: String,
    val subjective: Boolean,
    val supersedesId: String?,
    val editedByUser: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "memory_current_states",
    foreignKeys = [ForeignKey(
        entity = MemorySpaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["spaceId"]
    )],
    indices = [Index(value = ["spaceId", "stateKey", "personaKey"], unique = true)],
)
data class MemoryCurrentStateEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val characterId: String,
    val stateKey: String,
    val personaKey: String,
    val content: String,
    val revision: Long,
    val status: String,
    val expiresAt: Long?,
    val endedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val editedByUser: Boolean = false,
    @ColumnInfo(defaultValue = "0") val observedAt: Long = 0L,
)

/** 多态目标由统一失效事务维护；来源记录有外键，不能留下失联来源。 */
@Entity(
    tableName = "memory_derived_links",
    primaryKeys = ["ownerKind", "ownerId", "recordId"],
    foreignKeys = [ForeignKey(
        entity = MemoryRecordEntity::class,
        parentColumns = ["id"],
        childColumns = ["recordId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recordId")],
)
data class MemoryDerivedLinkEntity(
    val ownerKind: String,
    val ownerId: String,
    val recordId: String,
    val recordRevision: Long,
)

@Entity(
    tableName = "memory_embedding_indexes",
    primaryKeys = ["spaceId", "vectorSpace", "generation", "personaKey"],
    foreignKeys = [ForeignKey(
        entity = MemorySpaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["spaceId"]
    )],
)
data class MemoryEmbeddingIndexEntity(
    val spaceId: String,
    val vectorSpace: String,
    val generation: Long,
    val providerId: String,
    val model: String,
    val dimensions: Int,
    val preprocessingVersion: Int,
    val status: String,
    val active: Boolean,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'unknown'") val personaKey: String = "unknown",
    @ColumnInfo(defaultValue = "''") val configurationKey: String = "",
    @ColumnInfo(defaultValue = "''") val contextJson: String = "",
    @ColumnInfo(defaultValue = "'[]'") val recordsJson: String = "[]",
    @ColumnInfo(defaultValue = "0") val totalChunks: Int = 0,
    @ColumnInfo(defaultValue = "0") val completedChunks: Int = 0,
    @ColumnInfo(defaultValue = "'[]'") val pendingBatchJson: String = "[]",
    @ColumnInfo(defaultValue = "''") val requestToken: String = "",
    @ColumnInfo(defaultValue = "''") val failureCode: String = "",
    @ColumnInfo(defaultValue = "0") val fullRebuild: Boolean = false,
)

@Entity(
    tableName = "memory_embeddings",
    primaryKeys = ["recordId", "recordRevision", "chunkIndex", "vectorSpace", "generation"],
    foreignKeys = [ForeignKey(
        entity = MemoryRecordEntity::class,
        parentColumns = ["id"],
        childColumns = ["recordId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("spaceId", "vectorSpace", "generation")],
)
data class MemoryEmbeddingEntity(
    val recordId: String,
    val recordRevision: Long,
    val chunkIndex: Int,
    val vectorSpace: String,
    val generation: Long,
    val spaceId: String,
    val dimensions: Int,
    val spanStart: Int,
    val spanEnd: Int,
    val vector: ByteArray,
    val status: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "memory_recall_receipts",
    foreignKeys = [ForeignKey(
        entity = MemorySpaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["spaceId"]
    )],
    indices = [Index("spaceId", "createdAt")],
)
data class MemoryRecallReceiptEntity(
    @PrimaryKey val requestId: String,
    val spaceId: String,
    val characterId: String,
    val conversationId: String,
    val generation: Long,
    val selectedJson: String,
    val sentJson: String,
    val omittedJson: String,
    val payloadFingerprint: String,
    val status: String,
    val createdAt: Long,
    val sentAt: Long?,
    @ColumnInfo(defaultValue = "'unknown'") val personaKey: String = "unknown",
    @ColumnInfo(defaultValue = "''") val scenarioId: String = "",
    @ColumnInfo(defaultValue = "''") val runId: String = "",
    @ColumnInfo(defaultValue = "''") val anchorMessageId: String = "",
    @ColumnInfo(defaultValue = "''") val userMessageId: String = "",
    @ColumnInfo(defaultValue = "''") val failureCode: String = "",
    @ColumnInfo(defaultValue = "'[]'") val transmissionsJson: String = "[]",
)

@Entity(
    tableName = "memory_proposals",
    foreignKeys = [ForeignKey(
        entity = MemorySpaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["spaceId"]
    )],
    indices = [Index("spaceId", "status")],
)
data class MemoryProposalEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val characterId: String,
    val conversationId: String,
    val generation: Long,
    val personaKey: String,
    val content: String,
    val kind: String,
    val category: String,
    val sourceRefsJson: String,
    val status: String,
    val finalRecordId: String?,
    val createdAt: Long,
    val decidedAt: Long?,
    @ColumnInfo(defaultValue = "''") val reason: String = "",
    @ColumnInfo(defaultValue = "0") val importance: Int = 0,
    @ColumnInfo(defaultValue = "''") val scenarioId: String = "",
    @ColumnInfo(defaultValue = "''") val runId: String = "",
    @ColumnInfo(defaultValue = "'assistant'") val scopeType: String = "assistant",
)
