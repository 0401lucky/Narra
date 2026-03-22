package com.example.myapplication.data.local.worldbook

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "worldbook_entries",
    indices = [
        Index(value = ["scopeType", "scopeId"]),
        Index(value = ["updatedAt"]),
    ],
)
data class WorldBookEntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val keywordsJson: String = "[]",
    val aliasesJson: String = "[]",
    val secondaryKeywordsJson: String = "[]",
    val enabled: Boolean = true,
    val alwaysActive: Boolean = false,
    val selective: Boolean = false,
    val caseSensitive: Boolean = false,
    val priority: Int = 0,
    val insertionOrder: Int = 0,
    val sourceBookName: String = "",
    val scopeType: String = "global",
    val scopeId: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
