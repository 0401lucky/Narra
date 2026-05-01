package com.example.myapplication.data.local.roleplay

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "roleplay_scenarios",
    indices = [Index("updatedAt")],
)
data class RoleplayScenarioEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val descriptionPromptEnabled: Boolean = false,
    val assistantId: String,
    val backgroundUri: String,
    val userDisplayNameOverride: String,
    val userPersonaMaskId: String = "",
    val userPersonaOverride: String,
    val userPortraitUri: String,
    val userPortraitUrl: String,
    val characterDisplayNameOverride: String,
    val characterPortraitUri: String,
    val characterPortraitUrl: String,
    val openingNarration: String,
    val interactionMode: String = "offline_dialogue",
    val enableNarration: Boolean,
    val enableRoleplayProtocol: Boolean,
    val longformModeEnabled: Boolean = false,
    val autoHighlightSpeaker: Boolean,
    val enableDeepImmersion: Boolean = false,
    val enableTimeAwareness: Boolean = true,
    val enableNetMeme: Boolean = false,
    val chatType: String = "single",
    val groupReplyMode: String = "natural",
    val enableGroupMentionAutoReply: Boolean = true,
    val maxGroupAutoReplies: Int = 3,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
