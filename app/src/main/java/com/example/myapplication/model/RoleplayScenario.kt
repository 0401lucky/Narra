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
    val userPersonaMaskId: String = "",
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
    val chatType: RoleplayChatType = RoleplayChatType.SINGLE,
    val groupReplyMode: RoleplayGroupReplyMode = RoleplayGroupReplyMode.NATURAL,
    val enableGroupMentionAutoReply: Boolean = true,
    val maxGroupAutoReplies: Int = DEFAULT_GROUP_AUTO_REPLIES,
    val onlineReplyMinCount: Int = DEFAULT_ONLINE_REPLY_MIN_COUNT,
    val onlineReplyMaxCount: Int = DEFAULT_ONLINE_REPLY_MAX_COUNT,
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

val RoleplayScenario.isGroupChat: Boolean
    get() = chatType == RoleplayChatType.GROUP

enum class RoleplayChatType(
    val storageValue: String,
    val displayName: String,
) {
    SINGLE("single", "单聊"),
    GROUP("group", "群聊");

    companion object {
        fun fromStorageValue(value: String): RoleplayChatType {
            return entries.firstOrNull { it.storageValue == value } ?: SINGLE
        }
    }
}

enum class RoleplayGroupReplyMode(
    val storageValue: String,
    val displayName: String,
    val description: String,
) {
    NATURAL(
        storageValue = "natural",
        displayName = "自然聊天",
        description = "由导演根据角色内容和上下文决定谁说话、几个人说。",
    ),
    ALL_MEMBERS(
        storageValue = "all_members",
        displayName = "全员回复",
        description = "未禁言成员按顺序依次回复。",
    ),
    MANUAL_ONLY(
        storageValue = "manual_only",
        displayName = "指定发言",
        description = "不会自动回复，必须 @角色或手动指定。",
    );

    companion object {
        fun fromStorageValue(value: String): RoleplayGroupReplyMode {
            return entries.firstOrNull { it.storageValue == value } ?: NATURAL
        }
    }
}

data class RoleplayGroupParticipant(
    val id: String = UUID.randomUUID().toString(),
    val scenarioId: String,
    val assistantId: String,
    val displayNameOverride: String = "",
    val avatarUriOverride: String = "",
    val sortOrder: Int = 0,
    val isMuted: Boolean = false,
    val canAutoReply: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

const val DEFAULT_GROUP_AUTO_REPLIES = 3
const val MAX_GROUP_AUTO_REPLIES = 6
const val DEFAULT_ONLINE_REPLY_MIN_COUNT = 1
const val DEFAULT_ONLINE_REPLY_MAX_COUNT = 3
const val MAX_ONLINE_REPLY_COUNT = 10

fun RoleplayScenario.normalizedOnlineReplyRange(): IntRange {
    val min = onlineReplyMinCount.coerceIn(1, MAX_ONLINE_REPLY_COUNT)
    val max = onlineReplyMaxCount.coerceIn(1, MAX_ONLINE_REPLY_COUNT)
    return if (min <= max) {
        min..max
    } else {
        max..min
    }
}
