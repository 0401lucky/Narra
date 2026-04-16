package com.example.myapplication.model

import java.util.UUID

data class RoleplayScenario(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val description: String = "",
    val assistantId: String = DEFAULT_ASSISTANT_ID,
    val backgroundUri: String = "",
    val userDisplayNameOverride: String = "",
    val userPersonaOverride: String = "",
    val userPortraitUri: String = "",
    val userPortraitUrl: String = "",
    val characterDisplayNameOverride: String = "",
    val characterPortraitUri: String = "",
    val characterPortraitUrl: String = "",
    val openingNarration: String = "",
    val interactionMode: RoleplayInteractionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
    val enableNarration: Boolean = true,
    val enableRoleplayProtocol: Boolean = true,
    val longformModeEnabled: Boolean = false,
    val autoHighlightSpeaker: Boolean = true,
    val enableDeepImmersion: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
