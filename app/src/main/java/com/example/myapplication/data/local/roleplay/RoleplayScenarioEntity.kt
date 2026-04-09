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
    val assistantId: String,
    val backgroundUri: String,
    val userDisplayNameOverride: String,
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
    val createdAt: Long,
    val updatedAt: Long,
)
