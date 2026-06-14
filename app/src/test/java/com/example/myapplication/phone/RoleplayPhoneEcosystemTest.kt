package com.example.myapplication.phone

import com.example.myapplication.model.MailboxLetter
import com.example.myapplication.model.MomentAuthorType
import com.example.myapplication.model.MomentPost
import com.example.myapplication.model.RoleplayChatSummary
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayOnlineMeta
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayPhoneEcosystemTest {
    @Test
    fun buildSnapshot_emptyInputsReturnsEmptySnapshot() {
        val snapshot = buildRoleplayPhoneEcosystemSnapshot(
            chatSummaries = emptyList(),
            moments = emptyList(),
        )

        assertEquals(0, snapshot.unreadMailboxCount)
        assertEquals(0, snapshot.latestMomentCount)
        assertEquals(0, snapshot.activeVideoCallCount)
        assertTrue(snapshot.items.isEmpty())
    }

    @Test
    fun buildSnapshot_countsUnreadMailboxAndCreatesActivity() {
        val summary = summary(
            scenarioId = "scenario-1",
            conversationId = "conversation-1",
            characterName = "林夏",
            lastActiveAt = 500L,
        )
        val latestLetter = MailboxLetter(
            id = "letter-1",
            scenarioId = "scenario-1",
            conversationId = "conversation-1",
            subject = "晚风里的事",
            createdAt = 900L,
            updatedAt = 950L,
        )

        val snapshot = buildRoleplayPhoneEcosystemSnapshot(
            chatSummaries = listOf(summary),
            moments = emptyList(),
            unreadMailboxCountsByScenarioId = mapOf("scenario-1" to 2),
            latestMailboxLettersByScenarioId = mapOf("scenario-1" to latestLetter),
        )

        assertEquals(2, snapshot.unreadMailboxCount)
        assertEquals(RoleplayPhoneActivityKind.MAILBOX, snapshot.items.single().kind)
        assertEquals("林夏 有 2 封未读来信", snapshot.items.single().title)
        assertEquals("晚风里的事", snapshot.items.single().subtitle)
    }

    @Test
    fun buildSnapshot_ordersRecentActivitiesByTimestamp() {
        val summary = summary(
            scenarioId = "scenario-1",
            conversationId = "conversation-1",
            characterName = "林夏",
        )
        val moment = MomentPost(
            id = "moment-1",
            authorType = MomentAuthorType.ASSISTANT,
            authorName = "林夏",
            content = "今天路过旧书店，想起上次你说的那句话。",
            createdAt = 1_200L,
        )
        val diary = RoleplayDiaryEntry(
            id = "diary-1",
            conversationId = "conversation-1",
            scenarioId = "scenario-1",
            title = "雨停之后",
            content = "",
            sortOrder = 0,
            createdAt = 1_300L,
            updatedAt = 1_400L,
        )
        val onlineMeta = RoleplayOnlineMeta(
            conversationId = "conversation-1",
            activeVideoCallSessionId = "call-1",
            activeVideoCallStartedAt = 1_600L,
            updatedAt = 1_600L,
        )

        val snapshot = buildRoleplayPhoneEcosystemSnapshot(
            chatSummaries = listOf(summary),
            moments = listOf(moment),
            latestDiaryByConversationId = mapOf("conversation-1" to diary),
            onlineMetaByConversationId = mapOf("conversation-1" to onlineMeta),
        )

        assertEquals(1, snapshot.latestMomentCount)
        assertEquals(1, snapshot.activeVideoCallCount)
        assertEquals(
            listOf(
                RoleplayPhoneActivityKind.VIDEO_CALL,
                RoleplayPhoneActivityKind.DIARY,
                RoleplayPhoneActivityKind.MOMENT,
            ),
            snapshot.items.map { it.kind },
        )
    }

    @Test
    fun buildSnapshot_ignoresDiaryWithoutMatchingSummary() {
        val diary = RoleplayDiaryEntry(
            id = "diary-1",
            conversationId = "missing-conversation",
            scenarioId = "missing-scenario",
            title = "孤立日记",
            content = "",
            sortOrder = 0,
            createdAt = 100L,
            updatedAt = 100L,
        )

        val snapshot = buildRoleplayPhoneEcosystemSnapshot(
            chatSummaries = emptyList(),
            moments = emptyList(),
            latestDiaryByConversationId = mapOf("missing-conversation" to diary),
        )

        assertTrue(snapshot.items.isEmpty())
    }

    private fun summary(
        scenarioId: String,
        conversationId: String,
        characterName: String,
        lastActiveAt: Long = 0L,
    ): RoleplayChatSummary {
        return RoleplayChatSummary(
            scenario = RoleplayScenario(
                id = scenarioId,
                title = "与$characterName",
                characterDisplayNameOverride = characterName,
            ),
            session = RoleplaySession(
                id = "session-$scenarioId",
                scenarioId = scenarioId,
                conversationId = conversationId,
            ),
            lastActiveAt = lastActiveAt,
        )
    }
}
