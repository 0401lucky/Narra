package com.example.myapplication.model

data class RoleplayOnlineMeta(
    val conversationId: String,
    val lastCompensationBucket: String = "",
    val lastConsumedObservationUpdatedAt: Long = 0L,
    val lastSystemEventToken: String = "",
    val activeVideoCallSessionId: String = "",
    val activeVideoCallStartedAt: Long = 0L,
    val updatedAt: Long = 0L,
)
