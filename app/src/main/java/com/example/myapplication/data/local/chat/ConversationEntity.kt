package com.example.myapplication.data.local.chat

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [Index("updatedAt")],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    val assistantId: String = "",
)
