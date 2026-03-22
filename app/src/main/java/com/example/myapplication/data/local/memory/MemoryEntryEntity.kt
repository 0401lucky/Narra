package com.example.myapplication.data.local.memory

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_entries",
    indices = [
        Index(value = ["scopeType", "scopeId"]),
        Index(value = ["pinned"]),
        Index(value = ["updatedAt"]),
    ],
)
data class MemoryEntryEntity(
    @PrimaryKey val id: String,
    val scopeType: String = "global",
    val scopeId: String = "",
    val content: String = "",
    val importance: Int = 0,
    val pinned: Boolean = false,
    val sourceMessageId: String = "",
    val lastUsedAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
