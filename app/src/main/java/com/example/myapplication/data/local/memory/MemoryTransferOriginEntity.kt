package com.example.myapplication.data.local.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 每种 owner 都有真实外键；originJson 只允许不含正文的 MemoryTransferOrigin。 */
@Entity(
    tableName = "memory_transfer_origins",
    foreignKeys = [
        ForeignKey(
            entity = MemorySpaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["spaceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemorySourceEvidenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryEventBoxEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryResidentEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["residentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryCurrentStateEntity::class,
            parentColumns = ["id"],
            childColumns = ["stateId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("spaceId"), Index("recordId"), Index("sourceId"), Index("eventId"), Index("residentId"), Index(
        "stateId"
    )],
)
data class MemoryTransferOriginEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val kind: String,
    val recordId: String?,
    val sourceId: String?,
    val eventId: String?,
    val residentId: String?,
    val stateId: String?,
    val localRevision: Long,
    val originJson: String,
)

/** 本机确认的幂等凭据；只有 ID 映射和计数，删除正文后也不允许旧计划复活。 */
@Entity(tableName = "memory_transfer_receipts")
data class MemoryTransferReceiptEntity(
    @PrimaryKey val localPlanId: String,
    val bundleFingerprint: String,
    val targetsFingerprint: String,
    val resultJson: String,
    val createdAt: Long,
)
