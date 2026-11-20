package com.example.myapplication.viewmodel

import android.app.Application
import com.example.myapplication.conversation.RoleplayContextStatusCoordinator
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.context.ArchiveMemoryRepository
import com.example.myapplication.data.repository.context.RoomMemoryRepository
import com.example.myapplication.data.repository.roleplay.RoleplaySessionStartResult
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoomRoleplayRepository
import com.example.myapplication.model.*
import com.example.myapplication.testutil.MemoryExtractionFixture
import com.example.myapplication.testutil.remember
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoleplayMemoryLifecycleTest {
    @Test
    fun restartDaily_keepsOriginalArchiveThroughActualAction() =
        verifyRestart(MemoryPurpose.CONTINUOUS)

    @Test
    fun restartIndependent_keepsOriginalArchiveThroughActualAction() =
        verifyRestart(MemoryPurpose.INDEPENDENT)

    private fun verifyRestart(purpose: MemoryPurpose) = runBlocking {
        MemoryExtractionFixture().use { f ->
            val fixture = prepare(f, purpose)
            val runtime = requireNotNull(
                f.archive.getRuntimeContext(
                    fixture.start.session.conversationId,
                    "a"
                )
            )
            val record = f.remember(runtime, "原运行的叶栖706经历", "original-message")
            val summaries = RoomMemoryRepository(f.database.memoryDao())
            summaries.upsertSummary(
                ConversationSummary(
                    runtime.conversationId,
                    "a",
                    "保留的旧摘要",
                    1,
                    f.now
                )
            )
            fixture.repository.replaceDiaryEntries(
                runtime.conversationId,
                fixture.scenario.id,
                listOf(RoleplayDiaryDraft("原日记", "完整日记正文"))
            )
            val state = MutableStateFlow(
                RoleplayUiState(
                    settings = f.settings,
                    currentScenario = fixture.scenario,
                    currentSession = fixture.start.session
                )
            )
            var contextClearCalls = 0
            val support = support(f, fixture, this, state) { contextClearCalls++ }
            val completed = CompletableDeferred<Unit>()
            support.restartCurrentSession { completed.complete(Unit) }
            withTimeout(5_000) { completed.await() }
            assertEquals(0, contextClearCalls)
            assertTrue(
                f.store.listMessages(runtime.conversationId).any { it.id == "original-message" })
            assertEquals(
                "完整日记正文",
                fixture.repository.listDiaryEntries(runtime.conversationId).single().content
            )
            assertEquals("保留的旧摘要", summaries.listSummaries().single().summary)
            val next = requireNotNull(state.value.currentSession)
            assertNotEquals(fixture.start.session.runId, next.runId)
            assertNotEquals(runtime.conversationId, next.conversationId)
            val current = requireNotNull(f.archive.getRuntimeContext(next.conversationId, "a"))
            if (purpose == MemoryPurpose.CONTINUOUS) assertEquals(runtime.spaceId, current.spaceId)
            else assertNotEquals(runtime.spaceId, current.spaceId)
            val archive = requireNotNull(f.archive.getManagementContext("a", runtime.spaceId))
            assertEquals(
                record.content,
                f.archive.getRecordDetails(archive, record.id)?.record?.content
            )
            assertTrue(f.ai.snapshots.isEmpty())
        }
    }

    @Test
    fun clearCurrent_removesSourceDependentsAndKeepsManualRecord() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val fixture = prepare(f, MemoryPurpose.CONTINUOUS)
            val runtime = requireNotNull(
                f.archive.getRuntimeContext(
                    fixture.start.session.conversationId,
                    "a"
                )
            )
            val sourced = f.remember(runtime, "随当前原文清理的经历", "original-message")
            val manual = (f.archive.addRecord(
                runtime,
                ManualMemoryRecordDraft("独立人工版本")
            ) as MemoryWriteResult.Committed).recordIds.single()
            fixture.repository.replaceDiaryEntries(
                runtime.conversationId,
                fixture.scenario.id,
                listOf(RoleplayDiaryDraft("当前日记", "被明确清空"))
            )
            val summaries = RoomMemoryRepository(f.database.memoryDao())
            summaries.upsertSummary(
                ConversationSummary(
                    runtime.conversationId,
                    "a",
                    "当前摘要",
                    1,
                    f.now
                )
            )
            val facade = ArchiveMemoryRepository(f.database, summaries, f.archive)
            val cleanup = RoleplayContextStatusCoordinator(summaries, facade)
            val state = MutableStateFlow(
                RoleplayUiState(
                    settings = f.settings,
                    currentScenario = fixture.scenario,
                    currentSession = fixture.start.session
                )
            )
            val support =
                support(f, fixture, this, state, cleanup = cleanup::clearConversationScopedContext)
            val completed = CompletableDeferred<Unit>()
            support.resetCurrentSession { completed.complete(Unit) }
            withTimeout(5_000) { completed.await() }
            assertTrue(f.store.listMessages(runtime.conversationId).isEmpty())
            assertTrue(fixture.repository.listDiaryEntries(runtime.conversationId).isEmpty())
            assertTrue(summaries.listSummaries().isEmpty())
            val archive = requireNotNull(f.archive.getManagementContext("a", runtime.spaceId))
            assertEquals(
                MemoryRecordStatus.DELETED,
                f.archive.getRecordDetails(archive, sourced.id)?.record?.status
            )
            assertEquals(
                "独立人工版本",
                f.archive.getRecordDetails(archive, manual)?.record?.content
            )
            assertNull(state.value.errorMessage)
        }
    }

    @Test
    fun lateClear_doesNotReplaceNewConversation() = verifyDelayedCompletion(restart = false)

    @Test
    fun lateRestart_doesNotReplaceNewConversation() = verifyDelayedCompletion(restart = true)

    private fun verifyDelayedCompletion(restart: Boolean) = runBlocking {
        MemoryExtractionFixture().use { f ->
            val fixture = prepare(f, MemoryPurpose.INDEPENDENT)
            val state = MutableStateFlow(
                RoleplayUiState(
                    settings = f.settings,
                    currentScenario = fixture.scenario,
                    currentSession = fixture.start.session
                )
            )
            val scenarioIds = MutableStateFlow<String?>(fixture.scenario.id)
            val rawMessages = MutableStateFlow(emptyList<ChatMessage>())
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val repository = object : RoleplayRepository by fixture.repository {
                override suspend fun restartScenario(scenarioId: String): RoleplaySessionStartResult {
                    entered.complete(Unit)
                    release.await()
                    return fixture.repository.restartScenario(scenarioId)
                }
            }
            val support = support(f, fixture, this, state, scenarioIds, rawMessages, repository) {
                if (!restart) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            var callbacks = 0
            if (restart) support.restartCurrentSession { callbacks++ } else support.resetCurrentSession { callbacks++ }
            withTimeout(5_000) { entered.await() }
            val action = coroutineContext[Job]!!.children.single()
            val nextScenario = RoleplayScenario(id = "next-scene", assistantId = "b")
            val nextSession =
                RoleplaySession(scenarioId = nextScenario.id, conversationId = "next-conversation")
            scenarioIds.value = nextScenario.id
            state.value =
                state.value.copy(currentScenario = nextScenario, currentSession = nextSession)
            val newMessage = ChatMessage(
                "new-message",
                nextSession.conversationId,
                MessageRole.USER,
                "新页面不能被旧操作清掉"
            )
            rawMessages.value = listOf(newMessage)
            release.complete(Unit)
            withTimeout(5_000) { action.join() }
            assertEquals(nextSession, state.value.currentSession)
            assertEquals(listOf(newMessage), rawMessages.value)
            assertEquals(0, callbacks)
            assertNull(state.value.errorMessage)
        }
    }

    private data class ScenarioFixture(
        val scenario: RoleplayScenario,
        val repository: RoomRoleplayRepository,
        val conversations: ConversationRepository,
        val start: RoleplaySessionStartResult,
    )

    private suspend fun prepare(
        f: MemoryExtractionFixture,
        purpose: MemoryPurpose
    ): ScenarioFixture {
        val conversations = ConversationRepository(f.store, nowProvider = { f.now })
        val repository = RoomRoleplayRepository(
            f.database.roleplayDao(),
            conversations,
            { f.now },
            database = f.database,
            memoryArchiveRepository = f.archive
        )
        val scenario = RoleplayScenario(
            id = "scene",
            assistantId = "a",
            title = "合成场景",
            memoryPurpose = purpose
        )
        repository.upsertScenario(scenario)
        return ScenarioFixture(
            scenario,
            repository,
            conversations,
            repository.startScenario(scenario.id)
        )
    }

    private fun support(
        f: MemoryExtractionFixture, fixture: ScenarioFixture, scope: CoroutineScope,
        state: MutableStateFlow<RoleplayUiState>,
        scenarioIds: MutableStateFlow<String?> = MutableStateFlow(fixture.scenario.id),
        rawMessages: MutableStateFlow<List<ChatMessage>> = MutableStateFlow(emptyList()),
        repository: RoleplayRepository = fixture.repository,
        cleanup: suspend (String) -> Unit,
    ) = RoleplayScenarioActionSupport(
        scope,
        { state.value },
        { change -> state.update(change) },
        scenarioIds,
        rawMessages,
        repository,
        fixture.conversations,
        RoomMemoryRepository(f.database.memoryDao()),
        { f.now },
        { _, _, _, _ -> },
        cleanup,
    )
}
