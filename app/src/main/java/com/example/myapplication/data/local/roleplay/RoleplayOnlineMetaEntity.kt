package com.example.myapplication.data.local.roleplay

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "roleplay_online_meta",
    indices = [Index("updatedAt")],
)
data class RoleplayOnlineMetaEntity(
    @PrimaryKey val conversationId: String,
    val lastCompensationBucket: String = "",
    val lastConsumedObservationUpdatedAt: Long = 0L,
    val lastSystemEventToken: String = "",
    val updatedAt: Long = 0L,
)
