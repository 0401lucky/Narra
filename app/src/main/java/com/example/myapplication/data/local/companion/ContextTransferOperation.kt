package com.example.myapplication.data.local.companion

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** 大计划放私有文件；此表只保存 hash、阶段及无正文回执。 */
@Entity(
    tableName = "context_transfer_operations",
    indices = [Index(value = ["kind", "stage", "createdAt", "id"])]
)
data class ContextTransferOperationEntity(
    @PrimaryKey val id: String,
    val planVersion: Int,
    val kind: String,
    val stage: String,
    val revision: Long,
    val planHash: String,
    val settingsTargetCount: Int,
    val completedSettingsJson: String,
    val publishedImagesJson: String,
    val memoryResultJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface ContextTransferOperationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: ContextTransferOperationEntity)

    @Query("SELECT * FROM context_transfer_operations WHERE id = :id")
    suspend fun get(id: String): ContextTransferOperationEntity?

    @Query("SELECT * FROM context_transfer_operations WHERE kind = :kind AND stage NOT IN ('COMPLETE', 'CANCELLED') ORDER BY createdAt, id")
    suspend fun pending(kind: String): List<ContextTransferOperationEntity>

    @Query(
        """UPDATE context_transfer_operations SET stage = :stage, revision = revision + 1,
        completedSettingsJson = :completedSettingsJson, publishedImagesJson = :publishedImagesJson,
        memoryResultJson = :memoryResultJson, updatedAt = :updatedAt
        WHERE id = :id AND revision = :expectedRevision AND planHash = :planHash"""
    )
    suspend fun advance(
        id: String, expectedRevision: Long, planHash: String, stage: String,
        completedSettingsJson: String, publishedImagesJson: String, memoryResultJson: String,
        updatedAt: Long,
    ): Int
}
