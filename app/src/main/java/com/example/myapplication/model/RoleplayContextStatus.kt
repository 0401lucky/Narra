package com.example.myapplication.model

data class RoleplayContextStatus(
    val hasSummary: Boolean = false,
    val summaryCoveredMessageCount: Int = 0,
    val worldBookHitCount: Int = 0,
    val memoryInjectionCount: Int = 0,
    val isContinuingSession: Boolean = false,
)
