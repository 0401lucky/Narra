package com.example.myapplication.system.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureSpecTest {

    @Test
    fun populatedFixture_containsExactlyOneHundredMessagesPerPath_andIsIdempotent() {
        val first = DeterministicFixtureFactory.create(BenchmarkFixtureId.S2_POPULATED)
        val second = DeterministicFixtureFactory.create(BenchmarkFixtureId.S2_POPULATED)

        assertEquals(100, first.chatMessages.size)
        assertEquals(100, first.roleplayMessages.size)
        assertEquals(first, second)
        assertEquals(first.safeSummary(), second.safeSummary())
    }

    @Test
    fun generatedFixture_containsNoSecretAttachmentOrRemoteUrl() {
        val dataset = DeterministicFixtureFactory.create(BenchmarkFixtureId.S2_POPULATED)
        val serializedView = (dataset.chatMessages + dataset.roleplayMessages)
            .joinToString("\n") { message ->
                listOf(
                    message.id,
                    message.role,
                    message.content,
                    message.attachmentsJson,
                    message.speakerAvatarUri,
                ).joinToString("|")
            }
            .lowercase()

        assertFalse(serializedView.contains("api_key"))
        assertFalse(serializedView.contains("bearer "))
        assertFalse(serializedView.contains("http://"))
        assertFalse(serializedView.contains("https://"))
        assertFalse(serializedView.contains("data:image"))
        assertTrue(dataset.chatMessages.all { it.attachmentsJson == "[]" })
        assertTrue(dataset.roleplayMessages.all { it.attachmentsJson == "[]" })
    }

    @Test
    fun safeSummary_dependsOnIdentityOrderRoleAndLength_notMessageBody() {
        val dataset = DeterministicFixtureFactory.create(BenchmarkFixtureId.S1_RETURNING_CORE)
        val original = dataset.safeSummary()
        val first = dataset.chatMessages.first()
        val sameLengthReplacement = first.content.map { character ->
            if (character == '甲') '乙' else '甲'
        }.joinToString("")
        val changed = dataset.copy(
            chatMessages = listOf(first.copy(content = sameLengthReplacement)) + dataset.chatMessages.drop(1),
        )

        assertEquals(original, changed.safeSummary())
    }

    @Test
    fun scenarioMatrix_mapsFrameNavigationAndProfileToPopulatedFixture() {
        val populatedScenarios = listOf(
            BenchmarkScenarioId.F1_CHAT_SCROLL,
            BenchmarkScenarioId.F2_ROLEPLAY_SCROLL,
            BenchmarkScenarioId.F3_ROLEPLAY_STREAM_REPLAY,
            BenchmarkScenarioId.N1_HEAVY_NAVIGATION,
            BenchmarkScenarioId.P1_BASELINE_PROFILE,
        )

        assertTrue(populatedScenarios.all { it.fixtureId == BenchmarkFixtureId.S2_POPULATED })
        assertEquals(BenchmarkFixtureId.S0_FIRST_RUN, BenchmarkScenarioId.S0_FIRST_RUN_STARTUP.fixtureId)
        assertEquals(BenchmarkFixtureId.S3_SUPPORTED_UPGRADE, BenchmarkScenarioId.S3_UPGRADE_STARTUP.fixtureId)
    }

    @Test
    fun supportedUpgradeFixture_declaresMainProcessSourceSchema47() {
        assertEquals(47, BenchmarkFixtureId.S3_SUPPORTED_UPGRADE.sourceDatabaseVersion)
    }

    @Test
    fun offlineReplaySpec_isFixedAtFortyEightMilliseconds() {
        val first = BenchmarkOfflineReplaySpec.DEFAULT
        val second = BenchmarkOfflineReplaySpec.DEFAULT

        assertEquals(48L, first.chunkDelayMillis)
        assertEquals(first, second)
        assertTrue(first.chunks.size >= 8)
        assertTrue(first.chunks.all { it.isNotBlank() })
        assertTrue(first.seedContent.isNotBlank())
        assertEquals(first.chunks.joinToString(""), first.completedContent)
        assertTrue(first.isInitialContent(first.seedContent))
        assertTrue(first.isCompletedContent(first.completedContent))
    }

    @Test
    fun offlineReplaySpec_matchesOnlyFixedRoleplayFixtureAndOneHundredPersistedMessages() {
        val spec = BenchmarkOfflineReplaySpec.DEFAULT

        assertTrue(
            spec.matchesFixedFixture(
                scenarioId = BenchmarkFixtureIds.ROLEPLAY_SCENARIO_ID,
                conversationId = BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID,
            ),
        )
        assertFalse(
            spec.matchesFixedFixture(
                scenarioId = "other-scenario",
                conversationId = BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID,
            ),
        )
        assertEquals(100, spec.persistedMessageCount(totalMessageCount = 101, streamingMessageCount = 1))
        assertTrue(spec.hasExpectedPersistedMessageCount(100))
        assertFalse(spec.hasExpectedPersistedMessageCount(99))
    }

    @Test
    fun roleplayMessageIdMatcher_acceptsOnlyDeterministicFixtureIds() {
        assertTrue(BenchmarkFixtureIds.isRoleplayMessageId("benchmark-roleplay-message-000"))
        assertFalse(BenchmarkFixtureIds.isRoleplayMessageId("benchmark-chat-message-000"))
        assertFalse(BenchmarkFixtureIds.isRoleplayMessageId("streaming"))
    }
}
