package com.example.myapplication.data.repository.roleplay

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.roleplay.RoleplaySessionEntity
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.data.repository.context.RoomMemoryArchiveRepository
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MemoryCommitBatch
import com.example.myapplication.model.MemoryJobStatus
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.MemoryRecordDraft
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemorySpaceStatus
import com.example.myapplication.model.MemoryWriteResult
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayChatType
import com.example.myapplication.model.RoleplayDiaryDraft
import com.example.myapplication.model.RoleplayGroupParticipant
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoleplayRunRepositoryTest {
    private lateinit var database: ChatDatabase
    private lateinit var conversations: ConversationRepository
    private lateinit var archive: RoomMemoryArchiveRepository
    private lateinit var repository: RoomRoleplayRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChatDatabase::class.java,
        ).allowMainThreadQueries().build()
        conversations =
            ConversationRepository(RoomConversationStore(database), nowProvider = { clock++ })
        archive = RoomMemoryArchiveRepository(
            database,
            nowProvider = { clock++ },
            isCharacterAvailable = { it in setOf("alice", "bob") },
        )
        repository = createRepository()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun startScenario_bindsRunBeforeOpeningSource_andKeepsItWhenModeChanges() = runBlocking {
        val scenario = scenario(openingNarration = "暮色下的初遇")
        repository.upsertScenario(scenario)

        val first = repository.startScenario(scenario.id)
        val context = requireContext(first.session.conversationId)

        assertNotEquals(first.session.id, first.session.runId)
        assertEquals(first.session.runId, UUID.fromString(first.session.runId).toString())
        assertEquals(first.session.runId, context.runId)
        assertEquals(MemoryPurpose.INDEPENDENT, archive.getSpace(context.spaceId)?.purpose)
        assertTrue(
            archive.listPendingIngress(context).single().source.content.contains("暮色下的初遇")
        )
        val summary = repository.observeChatSummaries().first().single()
        assertEquals(MemoryPurpose.INDEPENDENT, summary.scenario.memoryPurpose)
        assertEquals(first.session.runId, summary.session?.runId)

        repository.upsertScenario(scenario.copy(interactionMode = RoleplayInteractionMode.ONLINE_PHONE))
        val resumed = repository.startScenario(scenario.id)

        assertEquals(first.session.conversationId, resumed.session.conversationId)
        assertEquals(first.session.runId, resumed.session.runId)
        assertEquals(context.spaceId, requireContext(resumed.session.conversationId).spaceId)
        assertEquals(1, conversations.listMessages(resumed.session.conversationId).size)
        assertTrue(resumed.reusedExistingSession)
    }

    @Test
    fun startScenario_legacySessionGetsOneRunWithoutBackfillingOriginalHistory() = runBlocking {
        val scenario = scenario(purpose = MemoryPurpose.LEGACY_COMPAT)
        repository.upsertScenario(scenario)
        val oldConversation = conversations.createConversation(assistantId = "alice")
        append(oldConversation.id, "过去的原文")
        database.roleplayDao().upsertSession(
            RoleplaySessionEntity("old-session", scenario.id, oldConversation.id, 1L, 2L),
        )

        val first = repository.startScenario(scenario.id)
        val resumed = repository.startScenario(scenario.id)
        val context = requireContext(oldConversation.id)

        assertEquals(oldConversation.id, first.session.conversationId)
        assertEquals("old-session", first.session.id)
        assertTrue(first.session.runId.isNotBlank())
        assertNotEquals("old-session", first.session.runId)
        assertEquals(first.session.runId, resumed.session.runId)
        assertEquals(MemoryPurpose.LEGACY_COMPAT, archive.getSpace(context.spaceId)?.purpose)
        assertEquals(
            listOf("过去的原文"),
            conversations.listMessages(oldConversation.id).map { it.content })
        assertTrue(archive.listPendingIngress(context).isEmpty())
        assertFalse(requireNotNull(archive.getCursor(context.spaceId)).historyBackfilled)
    }

    @Test
    fun restartScenario_independentKeepsOriginalMessagesMemoryAndDiary() = runBlocking {
        val scenario = scenario()
        repository.upsertScenario(scenario)
        val first = repository.startScenario(scenario.id)
        append(first.session.conversationId, "旧运行发生过的事")
        val oldContext = requireContext(first.session.conversationId)
        val recordId = remember(oldContext, "旧档案记忆")
        repository.replaceDiaryEntries(
            first.session.conversationId,
            scenario.id,
            listOf(RoleplayDiaryDraft("旧日记", "保留旧运行来源")),
        )

        val restarted = repository.restartScenario(scenario.id)
        val newContext = requireContext(restarted.session.conversationId)

        assertEquals(first.session.id, restarted.session.id)
        assertNotEquals(first.session.runId, restarted.session.runId)
        assertNotEquals(first.session.conversationId, restarted.session.conversationId)
        assertNotEquals(oldContext.spaceId, newContext.spaceId)
        assertEquals(MemorySpaceStatus.ARCHIVED, archive.getSpace(oldContext.spaceId)?.status)
        assertNull(archive.getRuntimeContext(first.session.conversationId, "alice"))
        assertEquals(
            listOf("旧运行发生过的事"),
            conversations.listMessages(first.session.conversationId).map { it.content })
        assertNull(archive.getRecordDetails(oldContext, recordId))
        assertTrue(archive.listRecords(oldContext).isEmpty())
        val archivedDetails =
            requireNotNull(archive.getRecordDetails(archiveContext(oldContext), recordId))
        assertEquals("旧档案记忆", archivedDetails.record.content)
        assertEquals("旧运行发生过的事", archivedDetails.sources.single().content)
        assertEquals("旧档案记忆", archivedDetails.revisions.single().content)
        assertEquals(
            "保留旧运行来源",
            repository.listDiaryEntries(first.session.conversationId).single().content
        )
        assertTrue(archive.listRecords(newContext).isEmpty())
        assertTrue(conversations.listMessages(restarted.session.conversationId).isEmpty())
    }

    @Test
    fun restartScenario_continuousKeepsDailySpaceWithoutCrossingCharacter() = runBlocking {
        val daily = scenario(purpose = MemoryPurpose.CONTINUOUS)
        val other = scenario(id = "other", characterId = "bob", purpose = MemoryPurpose.CONTINUOUS)
        repository.upsertScenario(daily)
        repository.upsertScenario(other)
        val first = repository.startScenario(daily.id)
        val bob = repository.startScenario(other.id)
        val bobContext = requireContext(bob.session.conversationId, "bob")
        append(first.session.conversationId, "日常共同经历")
        val oldContext = requireContext(first.session.conversationId)
        val recordId = remember(oldContext, "日常积累")

        val restarted = repository.restartScenario(daily.id)
        val newContext = requireContext(restarted.session.conversationId)

        assertNotEquals(first.session.runId, restarted.session.runId)
        assertEquals(oldContext.spaceId, newContext.spaceId)
        assertEquals(MemorySpaceStatus.ACTIVE, archive.getSpace(newContext.spaceId)?.status)
        assertEquals(recordId, archive.listRecords(newContext).single().id)
        assertTrue(archive.listRecords(bobContext).isEmpty())
        assertNotEquals(bobContext.spaceId, newContext.spaceId)
        assertNotNull(conversations.getConversation(first.session.conversationId))
    }

    @Test
    fun upsertScenario_newMemoryPurposeCreatesNewRunAndPreservesOldBinding() = runBlocking {
        val scenario = scenario(purpose = MemoryPurpose.CONTINUOUS)
        repository.upsertScenario(scenario)
        val first = repository.startScenario(scenario.id)
        append(first.session.conversationId, "日常原文")
        val oldContext = requireContext(first.session.conversationId)

        repository.upsertScenario(scenario.copy(memoryPurpose = MemoryPurpose.INDEPENDENT))
        val changed = requireNotNull(repository.getSessionByScenario(scenario.id))
        val newContext = requireContext(changed.conversationId)

        assertNotEquals(first.session.conversationId, changed.conversationId)
        assertNotEquals(first.session.runId, changed.runId)
        assertNotEquals(oldContext.spaceId, newContext.spaceId)
        assertEquals(MemoryPurpose.INDEPENDENT, repository.getScenario(scenario.id)?.memoryPurpose)
        assertEquals(MemoryPurpose.INDEPENDENT, archive.getSpace(newContext.spaceId)?.purpose)
        assertTrue(
            archive.listScenarioBindings(scenario.id)
                .single { it.conversationId == first.session.conversationId }.archived
        )
        assertEquals(
            "日常原文",
            conversations.listMessages(first.session.conversationId).single().content
        )
    }

    @Test
    fun upsertScenario_personaChangeInvalidatesOldJobAndKeepsSourceIdentity() = runBlocking {
        val scenario =
            scenario(purpose = MemoryPurpose.CONTINUOUS).copy(userPersonaMaskId = "mask-a")
        repository.upsertScenario(scenario)
        val session = repository.startScenario(scenario.id).session
        append(session.conversationId, "面具甲说过的话")
        val oldContext = requireContext(session.conversationId)
        val source = archive.listPendingIngress(oldContext).single().source
        val job = archive.planJob(oldContext, "old-persona-job", listOf(source.fullRef()))

        repository.upsertScenario(scenario.copy(userPersonaMaskId = "mask-b"))
        val newContext = requireContext(session.conversationId)
        append(session.conversationId, "面具乙说过的话")

        assertNotEquals(oldContext.personaKey, newContext.personaKey)
        assertTrue(newContext.generation > oldContext.generation)
        assertEquals(session.runId, newContext.runId)
        assertEquals(MemoryJobStatus.INVALIDATED, archive.getJob(job.id)?.status)
        assertNull(archive.getSource(oldContext, source.id))
        assertNull(archive.getSource(newContext, source.id))
        val archivedSource =
            requireNotNull(archive.getSource(archiveContext(oldContext), source.id))
        assertEquals(oldContext.personaKey, archivedSource.personaKey)
        assertEquals("面具甲说过的话", archivedSource.content)
        assertTrue(archive.listPendingIngress(oldContext).isEmpty())
        assertEquals(
            listOf("面具乙说过的话"),
            archive.listPendingIngress(newContext).map { it.source.content })
        assertEquals(
            newContext.personaKey,
            archive.listPendingIngress(newContext)
                .single { it.source.content == "面具乙说过的话" }.source.personaKey,
        )
    }

    @Test
    fun groupMemberExitAndRejoin_doesNotBackfillMessagesWhileAbsent() = runBlocking {
        val scenario = scenario().copy(chatType = RoleplayChatType.GROUP)
        val alice = RoleplayGroupParticipant(
            id = "member-a",
            scenarioId = scenario.id,
            assistantId = "alice"
        )
        val bob =
            RoleplayGroupParticipant(id = "member-b", scenarioId = scenario.id, assistantId = "bob")
        repository.upsertScenarioWithParticipants(scenario, listOf(alice, bob))
        val session = repository.startScenario(scenario.id).session
        val aliceContext = requireContext(session.conversationId)
        val oldBobContext = requireContext(session.conversationId, "bob")
        append(session.conversationId, "共同可见的第一条")

        repository.deleteGroupParticipant(bob.id)
        append(session.conversationId, "离开期间的新消息")

        assertNull(archive.getRuntimeContext(session.conversationId, "bob"))
        assertTrue(archive.listPendingIngress(oldBobContext).isEmpty())
        assertEquals(
            listOf("共同可见的第一条"),
            archive.listPendingIngress(archiveContext(oldBobContext)).map { it.source.content },
        )
        assertEquals(2, archive.listPendingIngress(aliceContext).size)

        repository.upsertGroupParticipant(bob)
        val newBobContext = requireContext(session.conversationId, "bob")
        append(session.conversationId, "再次共同可见的消息")

        assertEquals(oldBobContext.spaceId, newBobContext.spaceId)
        assertTrue(newBobContext.generation > oldBobContext.generation)
        assertTrue(archive.listPendingIngress(oldBobContext).isEmpty())
        assertNotEquals(aliceContext.spaceId, newBobContext.spaceId)
        assertEquals(
            setOf("共同可见的第一条", "再次共同可见的消息"),
            archive.listPendingIngress(newBobContext).map { it.source.content }.toSet(),
        )
    }

    @Test
    fun restartScenario_bindingFailureRollsBackConversationSessionAndArchive() = runBlocking {
        val scenario = scenario()
        repository.upsertScenario(scenario)
        val first = repository.startScenario(scenario.id).session
        append(first.conversationId, "完整保留的原文")
        val originalContext = requireContext(first.conversationId)
        val failingArchive = object : MemoryArchiveRepository by archive {
            override suspend fun bindConversation(
                scenario: RoleplayScenario,
                conversationId: String,
                characterIds: List<String>,
                runId: String,
            ): List<MemoryRuntimeContext> {
                archive.bindConversation(scenario, conversationId, characterIds, runId)
                error("synthetic_binding_failure")
            }
        }

        val failure =
            runCatching { createRepository(failingArchive).restartScenario(scenario.id) }.exceptionOrNull()

        assertEquals("synthetic_binding_failure", failure?.message)
        assertEquals(first, repository.getSessionByScenario(scenario.id))
        assertEquals(setOf(first.conversationId), conversations.listConversationIds())
        assertEquals(originalContext, requireContext(first.conversationId))
        assertEquals(MemorySpaceStatus.ACTIVE, archive.getSpace(originalContext.spaceId)?.status)
        assertEquals(1, archive.listSpaces("alice").size)
        assertEquals(
            "完整保留的原文",
            conversations.listMessages(first.conversationId).single().content
        )
    }

    @Test
    fun restartScenario_cancellationAfterArchiveRollsBackAndPropagates() = runBlocking {
        val scenario = scenario()
        repository.upsertScenario(scenario)
        val first = repository.startScenario(scenario.id).session
        val originalContext = requireContext(first.conversationId)
        val cancellingArchive = object : MemoryArchiveRepository by archive {
            override suspend fun archiveConversation(conversationId: String) {
                archive.archiveConversation(conversationId)
                throw CancellationException("synthetic_cancellation")
            }
        }

        val failure =
            runCatching { createRepository(cancellingArchive).restartScenario(scenario.id) }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals("synthetic_cancellation", failure?.message)
        assertEquals(first, repository.getSessionByScenario(scenario.id))
        assertEquals(originalContext, requireContext(first.conversationId))
        assertEquals(setOf(first.conversationId), conversations.listConversationIds())
    }

    @Test
    fun startScenario_seedFailureRollsBackNewConversationSessionAndSpace() = runBlocking {
        val scenario = scenario(openingNarration = "触发种子写入失败")
        repository.upsertScenario(scenario)
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_opening_insert BEFORE INSERT ON messages
            BEGIN SELECT RAISE(ABORT, 'synthetic_seed_failure'); END
            """.trimIndent(),
        )

        val failure = runCatching { repository.startScenario(scenario.id) }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("synthetic_seed_failure"))
        assertTrue(conversations.listConversationIds().isEmpty())
        assertNull(repository.getSessionByScenario(scenario.id))
        assertTrue(archive.listSpaces("alice").isEmpty())
        assertTrue(archive.listScenarioBindings(scenario.id).isEmpty())
        assertNotNull(repository.getScenario(scenario.id))
    }

    @Test
    fun startScenario_concurrentCallsShareOneConversationAndRun() = runBlocking {
        val scenario = scenario()
        repository.upsertScenario(scenario)

        val results = coroutineScope {
            val first = async { repository.startScenario(scenario.id) }
            val second = async { repository.startScenario(scenario.id) }
            listOf(first.await(), second.await())
        }

        assertEquals(1, results.map { it.session.runId }.distinct().size)
        assertEquals(1, results.map { it.session.conversationId }.distinct().size)
        assertEquals(1, conversations.listConversationIds().size)
        assertEquals(1, archive.listScenarioBindings(scenario.id).size)
    }

    @Test
    fun deleteScenario_removesAllItsRunsWithoutDeletingSharedDailySpace() = runBlocking {
        val story = scenario()
        val daily = scenario(id = "daily", purpose = MemoryPurpose.CONTINUOUS)
        repository.upsertScenario(story)
        repository.upsertScenario(daily)
        val oldStory = repository.startScenario(story.id).session
        append(oldStory.conversationId, "独立剧情旧来源")
        val oldStoryContext = requireContext(oldStory.conversationId)
        val oldRecord = remember(oldStoryContext, "独立剧情旧记忆")
        val oldSourceId =
            requireNotNull(archive.getRecordDetails(oldStoryContext, oldRecord)).sources.single().id
        val newStory = repository.restartScenario(story.id).session
        val newStoryContext = requireContext(newStory.conversationId)
        val dailySession = repository.startScenario(daily.id).session
        append(dailySession.conversationId, "日常来源")
        val dailyContext = requireContext(dailySession.conversationId)
        val dailyRecord = remember(dailyContext, "应保留的日常记忆")

        repository.deleteScenario(story.id)

        assertNull(repository.getScenario(story.id))
        assertNull(conversations.getConversation(oldStory.conversationId))
        assertNull(conversations.getConversation(newStory.conversationId))
        assertEquals(MemorySpaceStatus.DELETED, archive.getSpace(oldStoryContext.spaceId)?.status)
        assertEquals(MemorySpaceStatus.DELETED, archive.getSpace(newStoryContext.spaceId)?.status)
        assertNull(archive.getRecordDetails(archiveContext(oldStoryContext), oldRecord))
        val tombstone = requireNotNull(database.memoryArchiveDao().getRecord(oldRecord))
        assertEquals("DELETED", tombstone.status)
        assertEquals("", tombstone.content)
        assertTrue(database.memoryArchiveDao().listRevisions(oldRecord).isEmpty())
        assertTrue(database.memoryArchiveDao().getSource(oldSourceId)?.content.isNullOrEmpty())
        assertEquals(setOf(dailySession.conversationId), conversations.listConversationIds())
        assertEquals(MemorySpaceStatus.ACTIVE, archive.getSpace(dailyContext.spaceId)?.status)
        assertEquals(
            "应保留的日常记忆",
            archive.getRecordDetails(dailyContext, dailyRecord)?.record?.content
        )
    }

    private fun createRepository(memoryArchive: MemoryArchiveRepository = archive) =
        RoomRoleplayRepository(
            roleplayDao = database.roleplayDao(),
            conversationRepository = conversations,
            nowProvider = { clock++ },
            database = database,
            memoryArchiveRepository = memoryArchive,
        )

    private fun scenario(
        id: String = "story",
        characterId: String = "alice",
        purpose: MemoryPurpose = MemoryPurpose.INDEPENDENT,
        openingNarration: String = "",
    ) = RoleplayScenario(
        id = id,
        title = "合成场景",
        assistantId = characterId,
        memoryPurpose = purpose,
        openingNarration = openingNarration,
    )

    private suspend fun requireContext(
        conversationId: String,
        characterId: String = "alice"
    ): MemoryRuntimeContext =
        requireNotNull(archive.getRuntimeContext(conversationId, characterId))

    private suspend fun archiveContext(context: MemoryRuntimeContext): MemoryRuntimeContext =
        context.copy(
            conversationId = "",
            generation = requireNotNull(archive.getSpace(context.spaceId)).generation,
        )

    private suspend fun append(conversationId: String, content: String) {
        conversations.appendMessages(
            conversationId,
            listOf(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    role = MessageRole.USER,
                    content = content,
                    createdAt = clock++,
                ),
            ),
            selectedModel = "",
        )
    }

    private suspend fun remember(context: MemoryRuntimeContext, content: String): String {
        val source = archive.listPendingIngress(context).last().source.fullRef()
        val job = archive.planJob(context, UUID.randomUUID().toString(), listOf(source))
        assertNotNull(archive.claimJob(job.id, "synthetic-worker"))
        assertTrue(archive.saveExtraction(job.id, "synthetic-worker", "{}"))
        val result = archive.commitJob(
            job.id,
            "synthetic-worker",
            MemoryCommitBatch(records = listOf(MemoryRecordDraft("N0", content, listOf(source)))),
        )
        assertTrue(result is MemoryWriteResult.Committed)
        return (result as MemoryWriteResult.Committed).recordIds.single()
    }
}
