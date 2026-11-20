package com.example.myapplication.data.local.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "memory_spaces",
    indices = [Index("identityKey", unique = true), Index("characterId")]
)
data class MemorySpaceEntity(
    @PrimaryKey val id: String,
    val identityKey: String,
    val characterId: String,
    val purpose: String,
    val scenarioId: String,
    val runId: String,
    val generation: Long,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "memory_conversation_bindings",
    primaryKeys = ["conversationId", "characterId"],
    foreignKeys = [ForeignKey(
        entity = MemorySpaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["spaceId"]
    )],
    indices = [Index("spaceId"), Index("scenarioId")],
)
data class MemoryConversationBindingEntity(
    val conversationId: String,
    val characterId: String,
    val spaceId: String,
    val purpose: String,
    val scenarioId: String,
    val runId: String,
    val personaKey: String,
    val archived: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "memory_records",
    foreignKeys = [ForeignKey(
        entity = MemorySpaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["spaceId"]
    )],
    indices = [Index(
        "spaceId",
        "status"
    ), Index("characterId"), Index(value = ["jobId", "localItemId"], unique = true)],
)
data class MemoryRecordEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val characterId: String,
    val kind: String,
    val category: String,
    val content: String,
    val revision: Long,
    val status: String,
    val importance: Int,
    val pinned: Boolean,
    val pinnedUntil: Long?,
    val occurredAt: Long?,
    val datePrecision: String,
    val personaKey: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long,
    val jobId: String?,
    val localItemId: String?,
    val legacyScopeType: String,
    val legacyScopeId: String,
    val legacySourceMessageId: String,
)

@Entity(
    tableName = "memory_record_revisions",
    primaryKeys = ["recordId", "revision"],
    foreignKeys = [ForeignKey(
        entity = MemoryRecordEntity::class,
        parentColumns = ["id"],
        childColumns = ["recordId"],
        onDelete = ForeignKey.CASCADE
    )],
)
data class MemoryRecordRevisionEntity(
    val recordId: String,
    val revision: Long,
    val content: String,
    val origin: String,
    val createdAt: Long,
)

@Entity(
    tableName = "memory_source_evidence",
    foreignKeys = [ForeignKey(
        entity = MemorySpaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["spaceId"]
    )],
    indices = [Index("spaceId"), Index(
        "conversationId",
        "messageId"
    ), Index(value = ["spaceId", "messageId", "messageRevision"], unique = true)],
)
data class MemorySourceEvidenceEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val characterId: String,
    val kind: String,
    val conversationId: String,
    val messageId: String?,
    val messageRevision: Long,
    val speakerId: String,
    val speakerRole: String,
    val personaKey: String,
    val visibleCharacterIdsJson: String,
    val content: String,
    val contentLength: Int,
    val status: String,
    val occurredAt: Long,
    val ingestedAt: Long,
    val contentFingerprint: String,
    @ColumnInfo(defaultValue = "1") val contentFormatVersion: Int = 1,
)

@Entity(
    tableName = "memory_record_sources",
    primaryKeys = ["recordId", "recordRevision", "sourceId", "spanStart", "spanEnd"],
    foreignKeys = [
        ForeignKey(
            entity = MemoryRecordRevisionEntity::class,
            parentColumns = ["recordId", "revision"],
            childColumns = ["recordId", "recordRevision"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemorySourceEvidenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"]
        ),
    ],
    indices = [Index("sourceId")],
)
data class MemoryRecordSourceEntity(
    val recordId: String,
    val recordRevision: Long,
    val sourceId: String,
    val sourceRevision: Long,
    val spanStart: Int,
    val spanEnd: Int,
)

@Entity(
    tableName = "memory_ingress_events",
    foreignKeys = [ForeignKey(
        entity = MemorySourceEvidenceEntity::class,
        parentColumns = ["id"],
        childColumns = ["sourceId"]
    )],
    indices = [Index("spaceId", "sequence"), Index("sourceId", unique = true)],
)
data class MemoryIngressEventEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0L,
    val spaceId: String,
    val sourceId: String,
    val status: String,
    val ingestedAt: Long,
)

@Entity(
    tableName = "memory_processing_jobs",
    foreignKeys = [ForeignKey(
        entity = MemorySpaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["spaceId"]
    )],
    indices = [Index(value = ["spaceId", "idempotencyKey"], unique = true), Index(
        "spaceId",
        "status"
    ), Index("eventId", "status")],
)
data class MemoryProcessingJobEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val characterId: String,
    val conversationId: String,
    val scenarioId: String,
    val runId: String,
    val personaKey: String,
    val generation: Long,
    val idempotencyKey: String,
    val targetRevisionsJson: String,
    val status: String,
    val artifact: String,
    val leaseOwner: String,
    val leaseUntil: Long,
    val failureCode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val resultRecordIdsJson: String = "[]",
    val cursorAfterCommit: Long = 0L,
    @ColumnInfo(defaultValue = "'AUTOMATIC_EXTRACTION'") val purpose: String = "AUTOMATIC_EXTRACTION",
    @ColumnInfo(defaultValue = "''") val requestJson: String = "",
    @ColumnInfo(defaultValue = "'UNKNOWN'") val requestState: String = "UNKNOWN",
    @ColumnInfo(defaultValue = "''") val eventId: String = "",
    @ColumnInfo(defaultValue = "''") val controlJobId: String = "",
)

@Entity(
    tableName = "memory_job_sources",
    primaryKeys = ["jobId", "sourceId", "spanStart", "spanEnd"],
    foreignKeys = [
        ForeignKey(
            entity = MemoryProcessingJobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemorySourceEvidenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"]
        ),
    ],
    indices = [Index("sourceId")],
)
data class MemoryJobSourceEntity(
    val jobId: String,
    val sourceId: String,
    val sourceRevision: Long,
    val spanStart: Int,
    val spanEnd: Int,
)

@Entity(
    tableName = "memory_processing_cursors",
    foreignKeys = [ForeignKey(
        entity = MemorySpaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["spaceId"]
    )],
)
data class MemoryProcessingCursorEntity(
    @PrimaryKey val spaceId: String,
    val committedSequence: Long,
    val historyBackfilled: Boolean,
    val updatedAt: Long,
)

@Entity(
    tableName = "memory_relation_intents",
    foreignKeys = [
        ForeignKey(
            entity = MemoryRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromRecordId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetRecordId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("fromRecordId"), Index("targetRecordId"), Index("spaceId", "status")],
)
data class MemoryRelationIntentEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val characterId: String,
    val fromRecordId: String,
    val fromRevision: Long,
    val targetRecordId: String,
    val targetRevision: Long,
    val label: String,
    val eventName: String,
    val status: String,
    val createdAt: Long,
)

@Entity(tableName = "memory_engine_state")
data class MemoryEngineStateEntity(
    @PrimaryKey val id: Int = 1,
    val engineVersion: Int,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val activationVersion: Int = 0,
)
