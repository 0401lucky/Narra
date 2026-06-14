package com.example.myapplication.phone

import com.example.myapplication.model.MailboxLetter
import com.example.myapplication.model.MomentAuthorType
import com.example.myapplication.model.MomentPost
import com.example.myapplication.model.RoleplayChatSummary
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayOnlineMeta
import com.example.myapplication.model.isGroupChat
import com.example.myapplication.model.sanitizeMomentDisplayName

enum class RoleplayPhoneActivityKind {
    MOMENT,
    MAILBOX,
    DIARY,
    VIDEO_CALL,
}

data class RoleplayPhoneActivityItem(
    val id: String,
    val kind: RoleplayPhoneActivityKind,
    val scenarioId: String = "",
    val conversationId: String = "",
    val actorName: String = "",
    val title: String = "",
    val subtitle: String = "",
    val timestamp: Long = 0L,
    val unreadCount: Int = 0,
)

data class RoleplayPhoneEcosystemSnapshot(
    val unreadMailboxCount: Int = 0,
    val latestMomentCount: Int = 0,
    val activeVideoCallCount: Int = 0,
    val items: List<RoleplayPhoneActivityItem> = emptyList(),
) {
    val hasActivity: Boolean
        get() = items.isNotEmpty()
}

fun buildRoleplayPhoneEcosystemSnapshot(
    chatSummaries: List<RoleplayChatSummary>,
    moments: List<MomentPost>,
    unreadMailboxCountsByScenarioId: Map<String, Int> = emptyMap(),
    latestMailboxLettersByScenarioId: Map<String, MailboxLetter> = emptyMap(),
    latestDiaryByConversationId: Map<String, RoleplayDiaryEntry> = emptyMap(),
    onlineMetaByConversationId: Map<String, RoleplayOnlineMeta> = emptyMap(),
    maxItems: Int = 8,
): RoleplayPhoneEcosystemSnapshot {
    val summariesByScenarioId = chatSummaries.associateBy { it.scenario.id }
    val summariesByConversationId = chatSummaries
        .mapNotNull { summary ->
            val conversationId = summary.session?.conversationId?.takeIf { it.isNotBlank() }
            conversationId?.let { it to summary }
        }
        .toMap()
    val latestCharacterMoments = moments
        .filter { it.authorType == MomentAuthorType.ASSISTANT && it.createdAt > 0L }
        .sortedByDescending { it.createdAt }
    val items = buildList {
        latestCharacterMoments.forEach { moment ->
            val actorName = sanitizeMomentDisplayName(
                name = moment.authorName,
                stableKey = moment.authorId,
            )
            add(
                RoleplayPhoneActivityItem(
                    id = "moment:${moment.id}",
                    kind = RoleplayPhoneActivityKind.MOMENT,
                    actorName = actorName,
                    title = "$actorName 发了朋友圈",
                    subtitle = moment.content.toSingleLineExcerpt("点开看看角色最近在想什么"),
                    timestamp = moment.createdAt,
                ),
            )
        }
        chatSummaries.forEach { summary ->
            val scenarioId = summary.scenario.id
            val actorName = summary.ecosystemActorName()
            val unreadCount = unreadMailboxCountsByScenarioId[scenarioId].orZero()
            val latestLetter = latestMailboxLettersByScenarioId[scenarioId]
            if (unreadCount > 0 || latestLetter != null) {
                add(
                    RoleplayPhoneActivityItem(
                        id = "mailbox:$scenarioId:${latestLetter?.id.orEmpty()}",
                        kind = RoleplayPhoneActivityKind.MAILBOX,
                        scenarioId = scenarioId,
                        conversationId = summary.session?.conversationId.orEmpty(),
                        actorName = actorName,
                        title = if (unreadCount > 0) {
                            "$actorName 有 $unreadCount 封未读来信"
                        } else {
                            "$actorName 的信箱有更新"
                        },
                        subtitle = latestLetter?.subject?.trim()
                            ?.ifBlank { latestLetter.excerpt.trim() }
                            ?.ifBlank { "打开信箱看看最新书信" }
                            ?: "打开信箱查看未读内容",
                        timestamp = latestLetter?.activityTimestamp() ?: summary.lastActiveAt,
                        unreadCount = unreadCount,
                    ),
                )
            }
        }
        latestDiaryByConversationId.forEach { (conversationId, diary) ->
            val summary = summariesByConversationId[conversationId]
                ?: summariesByScenarioId[diary.scenarioId]
                ?: return@forEach
            val actorName = summary.ecosystemActorName()
            add(
                RoleplayPhoneActivityItem(
                    id = "diary:${diary.id}",
                    kind = RoleplayPhoneActivityKind.DIARY,
                    scenarioId = diary.scenarioId.ifBlank { summary.scenario.id },
                    conversationId = conversationId,
                    actorName = actorName,
                    title = "$actorName 更新了日记",
                    subtitle = diary.title.trim()
                        .ifBlank { diary.content.toSingleLineExcerpt("新的剧情章节已写入日记") },
                    timestamp = maxOf(diary.updatedAt, diary.createdAt),
                ),
            )
        }
        onlineMetaByConversationId.forEach { (conversationId, meta) ->
            if (meta.activeVideoCallSessionId.isBlank()) return@forEach
            val summary = summariesByConversationId[conversationId] ?: return@forEach
            val actorName = summary.ecosystemActorName()
            add(
                RoleplayPhoneActivityItem(
                    id = "video_call:${meta.activeVideoCallSessionId}",
                    kind = RoleplayPhoneActivityKind.VIDEO_CALL,
                    scenarioId = summary.scenario.id,
                    conversationId = conversationId,
                    actorName = actorName,
                    title = "$actorName 的视频通话仍在进行",
                    subtitle = "点开继续这次通话",
                    timestamp = meta.activeVideoCallStartedAt.takeIf { it > 0L } ?: meta.updatedAt,
                ),
            )
        }
    }
        .sortedWith(
            compareByDescending<RoleplayPhoneActivityItem> { it.timestamp }
                .thenBy { it.kind.ordinal }
                .thenBy { it.id },
        )
        .take(maxItems.coerceAtLeast(1))

    return RoleplayPhoneEcosystemSnapshot(
        unreadMailboxCount = unreadMailboxCountsByScenarioId.values.sumOf { it.coerceAtLeast(0) },
        latestMomentCount = latestCharacterMoments.size,
        activeVideoCallCount = onlineMetaByConversationId.values.count { it.activeVideoCallSessionId.isNotBlank() },
        items = items,
    )
}

private fun RoleplayChatSummary.ecosystemActorName(): String {
    val currentScenario = this.scenario
    return if (currentScenario.isGroupChat) {
        currentScenario.title.trim().ifBlank { "群聊" }
    } else {
        currentScenario.characterDisplayNameOverride.trim()
            .ifBlank { currentScenario.title.trim() }
            .ifBlank { "角色" }
    }
}

private fun MailboxLetter.activityTimestamp(): Long {
    return maxOf(sentAt, updatedAt, createdAt)
}

private fun String.toSingleLineExcerpt(fallback: String): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .take(48)
        .ifBlank { fallback }
}

private fun Int?.orZero(): Int = this?.coerceAtLeast(0) ?: 0
