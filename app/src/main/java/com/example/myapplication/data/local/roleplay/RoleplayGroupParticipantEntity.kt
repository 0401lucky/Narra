package com.example.myapplication.data.local.roleplay

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "roleplay_group_participants",
    foreignKeys = [
        ForeignKey(
            entity = RoleplayScenarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["scenarioId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("scenarioId"),
        Index(value = ["scenarioId", "assistantId"], unique = true),
        Index(value = ["scenarioId", "sortOrder"]),
    ],
)
data class RoleplayGroupParticipantEntity(
    @PrimaryKey val id: String,
    val scenarioId: String,
    val assistantId: String,
    val displayNameOverride: String = "",
    val avatarUriOverride: String = "",
    val sortOrder: Int = 0,
    val isMuted: Boolean = false,
    val canAutoReply: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)
