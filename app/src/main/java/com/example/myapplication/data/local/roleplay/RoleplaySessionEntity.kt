package com.example.myapplication.data.local.roleplay

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "roleplay_sessions",
    foreignKeys = [
        ForeignKey(
            entity = RoleplayScenarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["scenarioId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["scenarioId"], unique = true),
        Index(value = ["conversationId"]),
        Index(value = ["updatedAt"]),
    ],
)
data class RoleplaySessionEntity(
    @PrimaryKey val id: String,
    val scenarioId: String,
    val conversationId: String,
    val createdAt: Long,
    val updatedAt: Long,
)
