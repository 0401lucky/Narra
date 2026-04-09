package com.example.myapplication.data.local.phone

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "phone_observations",
    indices = [Index("updatedAt")],
)
data class PhoneObservationEntity(
    @PrimaryKey val conversationId: String,
    val scenarioId: String = "",
    val ownerType: String = "user",
    val viewMode: String = "character_looks_user_phone",
    val ownerName: String = "",
    val viewerName: String = "",
    val eventText: String = "",
    val keyFindingsJson: String = "[]",
    val observedAt: Long = 0L,
    val hasVisibleFeedback: Boolean = false,
    val feedbackMessageId: String = "",
    val usedFindingKeysJson: String = "[]",
    val updatedAt: Long = 0L,
)
