package com.example.myapplication.model

import java.util.UUID

data class RoleplayScenario(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val description: String = "",
    val descriptionPromptEnabled: Boolean = false,
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
    val enableTimeAwareness: Boolean = true,
    val enableNetMeme: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

fun RoleplayScenario.shouldInjectDescriptionPrompt(): Boolean =
    descriptionPromptEnabled && description.isNotBlank()

/**
 * 抽出 [RoleplayScenario] 中三项联动开关，用于在 UI / 测试里单独编排规则。
 */
fun RoleplayScenario.toInteractionSpec(): RoleplayInteractionSpec =
    RoleplayInteractionSpec(
        interactionMode = interactionMode,
        longformModeEnabled = longformModeEnabled,
        enableRoleplayProtocol = enableRoleplayProtocol,
    )

/**
 * 用新的 [RoleplayInteractionSpec] 覆写场景里对应的三个字段，其余字段保持不变。
 */
fun RoleplayScenario.withInteractionSpec(spec: RoleplayInteractionSpec): RoleplayScenario =
    copy(
        interactionMode = spec.interactionMode,
        longformModeEnabled = spec.longformModeEnabled,
        enableRoleplayProtocol = spec.enableRoleplayProtocol,
    )

fun RoleplayScenario.withInteractionMode(mode: RoleplayInteractionMode): RoleplayScenario =
    withInteractionSpec(toInteractionSpec().withInteractionMode(mode))

fun RoleplayScenario.withLongform(enabled: Boolean): RoleplayScenario =
    withInteractionSpec(toInteractionSpec().withLongform(enabled))

fun RoleplayScenario.withRoleplayProtocol(enabled: Boolean): RoleplayScenario =
    withInteractionSpec(toInteractionSpec().withRoleplayProtocol(enabled))
