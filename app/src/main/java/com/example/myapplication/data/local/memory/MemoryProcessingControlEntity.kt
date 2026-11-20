package com.example.myapplication.data.local.memory

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "memory_processing_controls",
    primaryKeys = ["spaceId", "jobId"],
    foreignKeys = [ForeignKey(
        entity = MemorySpaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["spaceId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class MemoryProcessingControlEntity(
    val spaceId: String,
    val jobId: String,
    val userPaused: Boolean,
    val controlVersion: Long,
    val updatedAt: Long,
)
