package com.example.myapplication.data.local.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 多态所有者由统一事务清理；历史来源不与当前正文共用可变字段。 */
@Entity(
    tableName = "memory_knowledge_revisions",
    primaryKeys = ["ownerKind", "ownerId", "revision"],
    foreignKeys = [ForeignKey(
        entity = MemorySpaceEntity::class, parentColumns = ["id"], childColumns = ["spaceId"],
    )],
    indices = [Index("spaceId")],
)
data class MemoryKnowledgeRevisionEntity(
    val ownerKind: String,
    val ownerId: String,
    val revision: Long,
    val spaceId: String,
    val content: String,
    val status: String,
    val origin: String,
    val subjective: Boolean,
    val expiresAt: Long?,
    val endedAt: Long?,
    val recordRefsJson: String,
    val createdAt: Long,
)

@Entity(
    tableName = "memory_knowledge_sources",
    primaryKeys = ["ownerKind", "ownerId", "ownerRevision", "sourceId", "sourceRevision", "spanStart", "spanEnd"],
    foreignKeys = [
        ForeignKey(
            entity = MemoryKnowledgeRevisionEntity::class,
            parentColumns = ["ownerKind", "ownerId", "revision"],
            childColumns = ["ownerKind", "ownerId", "ownerRevision"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MemorySourceEvidenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
        ),
    ],
    indices = [Index("sourceId")],
)
data class MemoryKnowledgeSourceEntity(
    val ownerKind: String,
    val ownerId: String,
    val ownerRevision: Long,
    val sourceId: String,
    val sourceRevision: Long,
    val spanStart: Int,
    val spanEnd: Int,
)

/** 本地消费与派生写入同事务，无正文凭据阻止重复恢复和删除后复活。 */
@Entity(
    tableName = "memory_knowledge_consumptions",
    foreignKeys = [ForeignKey(
        entity = MemoryProcessingJobEntity::class, parentColumns = ["id"], childColumns = ["jobId"],
    )],
)
data class MemoryKnowledgeConsumptionEntity(
    @PrimaryKey val jobId: String,
    val residentWrites: Int,
    val stateWrites: Int,
    val skippedActions: Int,
    val reasonCodesJson: String,
    val createdAt: Long,
)
