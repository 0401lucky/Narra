package com.example.myapplication.data.local.roleplay

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "roleplay_diary_entries",
    indices = [
        Index("conversationId"),
        Index("scenarioId"),
        Index(value = ["conversationId", "sortOrder"]),
    ],
)
data class RoleplayDiaryEntryEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val scenarioId: String,
    val title: String,
    val content: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
