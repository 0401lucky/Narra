package com.example.myapplication.data.local.phone

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "phone_snapshots",
    primaryKeys = ["conversationId", "ownerType"],
    indices = [Index("updatedAt")],
)
data class PhoneSnapshotEntity(
    val conversationId: String,
    val ownerType: String = "character",
    val scenarioId: String = "",
    val assistantId: String = "",
    val updatedAt: Long = 0L,
    val snapshotJson: String = "",
)
