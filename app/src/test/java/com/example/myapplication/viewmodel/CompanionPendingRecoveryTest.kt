package com.example.myapplication.viewmodel

import android.app.Application
import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import com.example.myapplication.conversation.CompanionRequestCoordinator
import com.example.myapplication.data.repository.roleplay.CompanionRepository
import com.example.myapplication.model.*
import com.example.myapplication.testutil.CompanionEntryTestFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionPendingRecoveryTest : CompanionEntryTestFixture() {
    private val viewModels = mutableListOf<RoleplayCompanionViewModel>()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        viewModels.forEach { it.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun onlineReopen_freeSave() = runBlocking<Unit> {
        useFileDatabase()
        configureRepositories()
        val context = scene(mode = RoleplayInteractionMode.ONLINE_PHONE)
        val vm = viewModel()
        openDirect(vm, context, "entry-A")
        blockCommit()
        reply("<dialogue>原场已收到的答复。</dialogue>")
        vm.send("原场输入")
        settle(vm)
        val attemptId = requireNotNull(vm.uiState.value.attemptId)
        val oldEncounter = requireNotNull(vm.uiState.value.encounter).id
        assertEquals(listOf(attemptId), vm.uiState.value.pendingResults.map { it.attemptId })
        vm.leave {}
        settle(vm)
        assertEquals(
            RoleplayInteractionMode.ONLINE_PHONE,
            roleplay.getScenario(context.scenarioId)?.interactionMode
        )

        reopen()
        configureRepositories()
        sql("DROP TRIGGER block_pending_commit")
        val reopened = viewModel()
        reopened.open(context.scenarioId, null, context.runId)
        settle(reopened)
        assertEquals(CompanionEntryPhase.NONE, reopened.uiState.value.phase)
        assertNull(reopened.uiState.value.encounter)
        assertEquals(listOf(attemptId), reopened.uiState.value.pendingResults.map { it.attemptId })
        reopened.savePendingResult(attemptId)
        settle(reopened)
        assertTrue(reopened.uiState.value.pendingResults.isEmpty())
        assertEquals(CompanionEntryPhase.NONE, reopened.uiState.value.phase)
        assertEquals(CompanionEncounterState.ENDED, repository.getEncounter(oldEncounter)?.state)
        assertEquals(
            RoleplayInteractionMode.ONLINE_PHONE,
            roleplay.getScenario(context.scenarioId)?.interactionMode
        )
        assertEquals(context.runId, roleplay.getSessionByScenario(context.scenarioId)?.runId)
        assertEquals(
            context.conversationId,
            roleplay.getSessionByScenario(context.scenarioId)?.conversationId
        )
        assertEquals(2, conversations.listMessages(context.conversationId).size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun newScene_canSavePreviousResultWithoutChangingCurrentScene() = runBlocking<Unit> {
        val context = scene()
        val vm = viewModel()
        openDirect(vm, context, "entry-A")
        blockCommit()
        reply()
        vm.send("先前一轮")
        settle(vm)
        val attemptId = requireNotNull(vm.uiState.value.attemptId)
        val oldEncounter = requireNotNull(vm.uiState.value.encounter).id
        openDirect(vm, context, "entry-B")
        val currentEncounter = requireNotNull(vm.uiState.value.encounter).id
        assertNotEquals(oldEncounter, currentEncounter)
        assertEquals(listOf(attemptId), vm.uiState.value.pendingResults.map { it.attemptId })
        sql("DROP TRIGGER block_pending_commit")

        vm.savePendingResult(attemptId)
        vm.savePendingResult(attemptId)
        settle(vm)
        assertEquals(currentEncounter, vm.uiState.value.encounter?.id)
        assertEquals(CompanionEntryPhase.ACTIVE, vm.uiState.value.phase)
        assertNull(vm.uiState.value.turn)
        assertTrue(vm.uiState.value.pendingResults.isEmpty())
        assertEquals(
            CompanionEncounterState.ACTIVE,
            repository.getEncounter(currentEncounter)?.state
        )
        assertEquals(CompanionEncounterState.ENDED, repository.getEncounter(oldEncounter)?.state)
        assertEquals(
            context,
            archive.getRuntimeContext(context.conversationId, context.characterId)
        )
        assertEquals(
            RoleplayInteractionMode.OFFLINE_DIALOGUE,
            roleplay.getScenario(context.scenarioId)?.interactionMode
        )
        assertEquals(2, conversations.listMessages(context.conversationId).size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun changedSource_rejectsVisiblePendingSaveWithoutRequest() = runBlocking<Unit> {
        val context = scene()
        val original = add(context, "旧来源证据")
        val vm = viewModel()
        openDirect(vm, context, "entry-A")
        blockCommit()
        reply()
        vm.send("引用已有事实")
        settle(vm)
        val attemptId = requireNotNull(vm.uiState.value.attemptId)
        openDirect(vm, context, "entry-B")
        val currentEncounter = vm.uiState.value.encounter?.id
        assertEquals(listOf(attemptId), vm.uiState.value.pendingResults.map { it.attemptId })
        sql("DROP TRIGGER block_pending_commit")
        store.upsertMessages(listOf(original.copy(content = "已经更正的来源")))

        vm.savePendingResult(attemptId)
        settle(vm)
        assertEquals(currentEncounter, vm.uiState.value.encounter?.id)
        assertEquals(CompanionEntryPhase.ACTIVE, vm.uiState.value.phase)
        assertNotNull(vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.pendingResults.isEmpty())
        assertEquals(CompanionAttemptState.RESULT_READY, repository.getAttempt(attemptId)?.state)
        assertEquals(
            0,
            conversations.listMessages(context.conversationId)
                .count { it.role == MessageRole.ASSISTANT })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun openingReroll_oldSceneSavesWithoutChangingCurrentScene() = runBlocking<Unit> {
        val context = scene()
        val vm = viewModel()
        vm.open(context.scenarioId, "entry-A")
        settle(vm)
        reply("<dialogue>第一次接受的开场。</dialogue>")
        vm.chooseOpening(CompanionOpeningMode.APPROACH)
        settle(vm)
        vm.acceptOpening()
        settle(vm)
        val oldReply = conversations.listMessages(context.conversationId).single()
        val oldEncounter = requireNotNull(vm.uiState.value.encounter).id
        blockCommit()
        reply("<dialogue>保留原位置的新开场。</dialogue>")
        vm.regenerate()
        settle(vm)
        val attemptId = requireNotNull(vm.uiState.value.attemptId)
        openDirect(vm, context, "entry-B")
        val currentEncounter = vm.uiState.value.encounter?.id
        assertEquals(listOf(attemptId), vm.uiState.value.pendingResults.map { it.attemptId })
        sql("DROP TRIGGER block_pending_commit")

        vm.savePendingResult(attemptId)
        settle(vm)
        val saved = conversations.listMessages(context.conversationId).single()
        assertEquals(oldReply.id, saved.id)
        assertEquals(oldReply.createdAt, saved.createdAt)
        assertTrue(saved.content.contains("保留原位置的新开场"))
        assertEquals(currentEncounter, vm.uiState.value.encounter?.id)
        assertEquals(CompanionEntryPhase.ACTIVE, vm.uiState.value.phase)
        assertEquals(CompanionEncounterState.ENDED, repository.getEncounter(oldEncounter)?.state)
        assertTrue(vm.uiState.value.pendingResults.isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun pendingList_excludesOtherIdentityDraftsAndIncompleteResults() = runBlocking<Unit> {
        val context = scene()
        val otherRole = scene(character = "bob")
        val otherPersona = scene(persona = "身份乙")
        val ready = readyResult(context)
        readyResult(otherRole)
        readyResult(otherPersona)
        readyResult(context, status = PromptCompletionStatus.TRUNCATED)
        val draft = repository.createEncounter(context, CompanionOpeningMode.APPROACH)
        val opening = repository.beginTurn(draft.id, CompanionTurnKind.OPENING)
        repository.claimAttempt(opening.attempt.id, "opening-owner")
        repository.saveGeneratedArtifact(
            opening.attempt.id, "opening-owner",
            CompanionGeneratedArtifact("未接受的预览", PromptCompletionStatus.COMPLETED)
        )

        val coordinator = requests()
        assertEquals(
            listOf(ready),
            coordinator.pendingResults(context.scenarioId).map { it.attemptId })
        assertTrue(
            coordinator.savePendingResult(otherRole.scenarioId, ready)
                    is com.example.myapplication.conversation.CompanionGenerationResult.Rejected
        )
        assertTrue(
            coordinator.savePendingResult(otherPersona.scenarioId, ready)
                    is com.example.myapplication.conversation.CompanionGenerationResult.Rejected
        )
        assertEquals(CompanionAttemptState.RESULT_READY, repository.getAttempt(ready)?.state)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun staleCurrentArtifact_explicitRetryUsesNewSources() = runBlocking<Unit> {
        val context = scene()
        val source = add(context, "改动前的原文来源")
        val vm = viewModel()
        openDirect(vm, context, "entry-A")
        blockCommit()
        reply("<dialogue>引用旧来源的待存结果。</dialogue>")
        vm.send("本轮只保存一次的输入")
        settle(vm)
        val oldAttempt = requireNotNull(vm.uiState.value.attemptId)
        assertEquals(CompanionRecoveryAction.SAVE, vm.uiState.value.recoveryAction)
        payload()
        store.upsertMessages(listOf(source.copy(content = "更正后的原文来源")))
        vm.recover()
        settle(vm)
        assertEquals(CompanionRecoveryAction.GENERATE, vm.uiState.value.recoveryAction)
        assertEquals(1, server.requestCount)
        sql("DROP TRIGGER block_pending_commit")

        reply("<dialogue>按更正后的来源重新回应。</dialogue>")
        vm.recover()
        settle(vm)
        assertNotEquals(oldAttempt, vm.uiState.value.attemptId)
        assertEquals(CompanionRecoveryAction.NONE, vm.uiState.value.recoveryAction)
        assertEquals(2, server.requestCount)
        val body = payload()
        assertTrue(body.contains("更正后的原文来源"))
        assertFalse(body.contains("改动前的原文来源"))
        assertEquals(
            1, conversations.listMessages(context.conversationId)
                .count { it.content == "本轮只保存一次的输入" })
        assertEquals(
            1, conversations.listMessages(context.conversationId)
                .count { it.role == MessageRole.ASSISTANT })
    }

    @Test
    fun unpersistedOpening_refreshKeepsPreviewAndFreeSave() = runBlocking<Unit> {
        var fail = true
        val coordinator = requests(companionRepository = artifactFault { fail })
        val context = scene()
        val vm = viewModel(coordinator)
        vm.open(context.scenarioId, "entry-A")
        settle(vm)
        reply("<dialogue>尚未落盘的候选。</dialogue>")
        vm.chooseOpening(CompanionOpeningMode.APPROACH)
        settle(vm)
        vm.cancel()
        vm.refresh()
        settle(vm)
        assertEquals(CompanionRecoveryAction.SAVE, vm.uiState.value.recoveryAction)
        assertTrue(vm.uiState.value.preview.contains("尚未落盘的候选"))
        assertFalse(vm.uiState.value.canAccept)
        assertTrue(vm.uiState.value.pendingResults.isEmpty())
        fail = false
        vm.recover()
        settle(vm)
        assertTrue(vm.uiState.value.canAccept)
        assertTrue(conversations.listMessages(context.conversationId).isEmpty())
        vm.acceptOpening()
        settle(vm)
        assertEquals(CompanionEntryPhase.ACTIVE, vm.uiState.value.phase)
        assertEquals(1, conversations.listMessages(context.conversationId).size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun endedScene_releasesUnpersistedCacheWithoutClaimingRecovery() = runBlocking<Unit> {
        val coordinator = requests(companionRepository = artifactFault { true })
        val context = scene(mode = RoleplayInteractionMode.ONLINE_PHONE)
        val vm = viewModel(coordinator)
        openDirect(vm, context, "entry-A")
        reply()
        vm.send("无法保存产物的输入")
        settle(vm)
        val attemptId = requireNotNull(vm.uiState.value.attemptId)
        assertNotNull(coordinator.pendingArtifact(attemptId))
        vm.leave {}
        settle(vm)
        assertNull(coordinator.pendingArtifact(attemptId))
        assertTrue(coordinator.pendingResults(context.scenarioId).isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun newCoordinator_withoutArtifactDoesNotInventFreeSave() = runBlocking<Unit> {
        val context = scene()
        val first = viewModel(requests(companionRepository = artifactFault { true }))
        openDirect(first, context, "entry-A")
        reply()
        first.send("只有请求证据")
        settle(first)
        first.cancel()
        clock += 130_000
        val restored = viewModel(requests())
        restored.open(context.scenarioId, null, context.runId)
        settle(restored)
        assertEquals(CompanionRecoveryAction.GENERATE, restored.uiState.value.recoveryAction)
        assertTrue(restored.uiState.value.preview.isEmpty())
        assertTrue(restored.uiState.value.pendingResults.isEmpty())
        assertEquals(1, server.requestCount)
    }

    private suspend fun readyResult(
        context: MemoryRuntimeContext,
        status: PromptCompletionStatus = PromptCompletionStatus.COMPLETED
    ): String {
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val turn = requests().beginTurn(encounter.id, CompanionTurnKind.INPUT, "合成待存输入")
        repository.claimAttempt(turn.attempt.id, "owner")
        assertTrue(
            repository.saveGeneratedArtifact(
                turn.attempt.id, "owner",
                CompanionGeneratedArtifact("合成待存结果", status)
            )
        )
        return turn.attempt.id
    }

    private fun artifactFault(shouldFail: () -> Boolean) =
        object : CompanionRepository by repository {
            override suspend fun saveGeneratedArtifact(
                id: String,
                owner: String,
                artifact: CompanionGeneratedArtifact
            ): Boolean {
                if (shouldFail()) throw SQLiteException("synthetic artifact failure")
                return repository.saveGeneratedArtifact(id, owner, artifact)
            }
        }

    private fun blockCommit() {
        sql("CREATE TRIGGER block_pending_commit BEFORE UPDATE ON companion_turns WHEN NEW.committedRevision > OLD.committedRevision BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
    }

    private fun viewModel(coordinator: CompanionRequestCoordinator = requests()) =
        RoleplayCompanionViewModel(SavedStateHandle(), roleplay, repository, coordinator, settings)
            .also(viewModels::add)

    private suspend fun openDirect(
        vm: RoleplayCompanionViewModel,
        context: MemoryRuntimeContext,
        entryId: String
    ) {
        vm.open(context.scenarioId, entryId)
        settle(vm)
        vm.chooseOpening(CompanionOpeningMode.DIRECT)
        settle(vm)
        assertEquals(CompanionEntryPhase.ACTIVE, vm.uiState.value.phase)
    }

    private suspend fun settle(vm: RoleplayCompanionViewModel) {
        withTimeout(15_000) { vm.uiState.first { !it.isBusy } }
    }
}
