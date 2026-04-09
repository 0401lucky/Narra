package com.example.myapplication.model

import androidx.compose.runtime.Immutable

@Immutable
data class PhoneObservationState(
    val conversationId: String,
    val scenarioId: String = "",
    val ownerType: PhoneSnapshotOwnerType = PhoneSnapshotOwnerType.USER,
    val viewMode: PhoneViewMode = PhoneViewMode.CHARACTER_LOOKS_USER_PHONE,
    val ownerName: String = "",
    val viewerName: String = "",
    val eventText: String = "",
    val keyFindings: List<String> = emptyList(),
    val observedAt: Long = 0L,
    val hasVisibleFeedback: Boolean = false,
    val feedbackMessageId: String = "",
    val usedFindingKeys: List<String> = emptyList(),
    val updatedAt: Long = 0L,
)
