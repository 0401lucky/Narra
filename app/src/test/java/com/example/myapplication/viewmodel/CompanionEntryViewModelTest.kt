package com.example.myapplication.viewmodel

import android.app.Application
import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import com.example.myapplication.conversation.CompanionRequestCoordinator
import com.example.myapplication.data.repository.roleplay.CompanionRepository
import com.example.myapplication.model.CompanionCommitResult
import com.example.myapplication.model.CompanionEncounterState
import com.example.myapplication.model.CompanionOpeningMode
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.CompanionTurnKind
import com.example.myapplication.model.CompanionGeneratedArtifact
import com.example.myapplication.model.PromptCompletionStatus
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
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionEntryViewModelTest : CompanionEntryTestFixture() {
    private val viewModels = mutableListOf<RoleplayCompanionViewModel>()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun clearMainDispatcher() {
        viewModels.forEach { it.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun directReopen_restoresOrigin() = runBlocking<Unit> {
        useFileDatabase()
        configureRepositories()
        val context = scene(mode = RoleplayInteractionMode.ONLINE_PHONE)
        val first = viewModel()
        first.open(context.scenarioId, "entry")
        settle(first)
        first.chooseOpening(CompanionOpeningMode.DIRECT)
        settle(first)
        val selected = requireNotNull(first.uiState.value.encounter)
        assertEquals(CompanionEncounterState.ACTIVE, selected.state)
        assertTrue(repository.listTurns(selected.id).isEmpty())
        assertTrue(conversations.listMessages(context.conversationId).isEmpty())
        first.cancel()

        reopen()
        configureRepositories()
        val restored = viewModel()
        restored.open(context.scenarioId, null, context.runId)
        settle(restored)
        assertEquals(selected.id, restored.uiState.value.encounter?.id)
        assertEquals(
            RoleplayInteractionMode.ONLINE_PHONE,
            restored.uiState.value.origin?.interactionMode
        )
        var returned = false
        restored.leave { returned = true }
        settle(restored)
        assertTrue(returned)
        assertEquals(
            RoleplayInteractionMode.ONLINE_PHONE,
            roleplay.getScenario(context.scenarioId)?.interactionMode
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun directRouteRecreation_usesPersistedChoice() = runBlocking<Unit> {
        val context = scene()
        val first = viewModel()
        first.open(context.scenarioId, "entry")
        settle(first)
        first.chooseOpening(CompanionOpeningMode.DIRECT)
        settle(first)
        val selectedId = first.uiState.value.encounter?.id
        first.cancel()

        val restored = viewModel()
        restored.open(context.scenarioId, "entry")
        settle(restored)
        assertNull(restored.uiState.value.errorMessage)
        assertEquals(CompanionEntryPhase.ACTIVE, restored.uiState.value.phase)
        assertEquals(selectedId, restored.uiState.value.encounter?.id)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun oldSessionOpen_restoresPendingPreviewWithoutRequest() = runBlocking<Unit> {
        val context = scene()
        val first = viewModel()
        first.open(context.scenarioId, "entry")
        settle(first)
        reply()
        first.chooseOpening(CompanionOpeningMode.INVITE)
        settle(first)
        assertNull(first.uiState.value.errorMessage)
        assertEquals(CompanionEntryPhase.PREVIEW, first.uiState.value.phase)
        val preview = first.uiState.value.preview
        val selectedId = first.uiState.value.encounter?.id
        first.cancel()

        val restored = viewModel()
        restored.open(context.scenarioId, null, context.runId)
        settle(restored)
        assertEquals(CompanionEntryPhase.PREVIEW, restored.uiState.value.phase)
        assertEquals(preview, restored.uiState.value.preview)
        assertEquals(selectedId, restored.uiState.value.encounter?.id)
        restored.open(context.scenarioId, null, context.runId)
        settle(restored)
        assertEquals(1, server.requestCount)
        assertTrue(conversations.listMessages(context.conversationId).isEmpty())
        assertTrue(archive.listPendingIngress(context).isEmpty())
    }

    @Test
    fun prepareRestoreAndDirect_doNotSeedStaticGreeting() = runBlocking<Unit> {
        roleplay.upsertScenario(
            RoleplayScenario(
                id = "new",
                assistantId = "alice",
                title = "合成准备",
                openingNarration = "不可自动保存的问候",
                interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE
            )
        )
        val first = viewModel()
        first.open("new", "entry")
        settle(first)
        val context = requireNotNull(first.uiState.value.encounter).context
        roleplay.startScenario("new")
        first.open("new", "entry")
        settle(first)
        assertEquals(CompanionEntryPhase.PREPARE, first.uiState.value.phase)
        assertTrue(conversations.listMessages(context.conversationId).isEmpty())
        first.chooseOpening(CompanionOpeningMode.DIRECT)
        settle(first)
        assertEquals(CompanionEntryPhase.ACTIVE, first.uiState.value.phase)
        assertTrue(conversations.listMessages(context.conversationId).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun acceptSaveFailure_keepsPreviewAndFreeAcceptRecovery() = runBlocking<Unit> {
        var failCommit = true
        val fault = object : CompanionRepository by repository {
            override suspend fun commitAttempt(
                id: String,
                acceptOpening: Boolean
            ): CompanionCommitResult {
                if (acceptOpening && failCommit) throw SQLiteException("synthetic failure")
                return repository.commitAttempt(id, acceptOpening)
            }
        }
        val context = scene()
        val vm = viewModel(requests(companionRepository = fault))
        vm.open(context.scenarioId, "entry")
        settle(vm)
        reply()
        vm.chooseOpening(CompanionOpeningMode.APPROACH)
        settle(vm)
        val preview = vm.uiState.value.preview
        assertTrue(preview.isNotBlank())
        vm.acceptOpening()
        settle(vm)
        assertEquals(CompanionEntryPhase.PREVIEW, vm.uiState.value.phase)
        assertEquals(preview, vm.uiState.value.preview)
        assertEquals(CompanionRecoveryAction.ACCEPT, vm.uiState.value.recoveryAction)
        assertFalse(vm.uiState.value.canAccept)
        assertTrue(conversations.listMessages(context.conversationId).isEmpty())
        failCommit = false
        vm.recover()
        vm.recover()
        settle(vm)
        assertEquals(CompanionEntryPhase.ACTIVE, vm.uiState.value.phase)
        assertEquals(1, conversations.listMessages(context.conversationId).size)
        assertEquals(1, server.requestCount)
        assertEquals(1, commits)
    }

    @Test
    fun repeatedAccept_createsOneCanonicalOpening() = runBlocking<Unit> {
        val context = scene()
        val vm = viewModel()
        vm.open(context.scenarioId, "entry")
        settle(vm)
        reply()
        vm.chooseOpening(CompanionOpeningMode.APPROACH)
        settle(vm)
        vm.acceptOpening()
        vm.acceptOpening()
        settle(vm)
        assertEquals(CompanionEntryPhase.ACTIVE, vm.uiState.value.phase)
        assertEquals(1, conversations.listMessages(context.conversationId).size)
        assertEquals(1, commits)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun preparedInputRecovery_requiresExplicitRequest() = runBlocking<Unit> {
        val context = scene()
        roleplay.prepareCompanionScenario(context.scenarioId, "entry")
        val selection =
            roleplay.selectCompanionOpening("entry", "entry", CompanionOpeningMode.DIRECT)
        requests().beginTurn(
            selection.encounter.id,
            CompanionTurnKind.INPUT,
            "已经保存、还没请求的输入",
            origin = selection.origin
        )
        val vm = viewModel()
        vm.open(context.scenarioId, null, context.runId)
        settle(vm)
        assertEquals(CompanionEntryPhase.ACTIVE, vm.uiState.value.phase)
        assertEquals(CompanionRecoveryAction.GENERATE, vm.uiState.value.recoveryAction)
        assertEquals(0, server.requestCount)
        reply()
        vm.recover()
        settle(vm)
        assertEquals(CompanionRecoveryAction.NONE, vm.uiState.value.recoveryAction)
        assertEquals(2, conversations.listMessages(context.conversationId).size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun inputSaveFailure_doesNotClearDraftOrCallModel() = runBlocking<Unit> {
        val context = scene()
        val vm = viewModel()
        vm.open(context.scenarioId, "entry")
        settle(vm)
        vm.chooseOpening(CompanionOpeningMode.DIRECT)
        settle(vm)
        sql("CREATE TRIGGER fail_input BEFORE INSERT ON companion_turns WHEN NEW.kind = 'INPUT' BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        var clearDraft = 0
        vm.send("需要保留的输入") { clearDraft++ }
        settle(vm)
        assertEquals(0, clearDraft)
        assertEquals(0, server.requestCount)
        assertTrue(conversations.listMessages(context.conversationId).isEmpty())
        assertNotNull(vm.uiState.value.errorMessage)
        sql("DROP TRIGGER fail_input")
    }

    @Test
    fun refreshAfterArtifactFailure_keepsFreeSave() = runBlocking<Unit> {
        var failArtifact = true
        val fault = object : CompanionRepository by repository {
            override suspend fun saveGeneratedArtifact(
                id: String, owner: String, artifact: CompanionGeneratedArtifact
            ): Boolean {
                if (failArtifact) throw SQLiteException("synthetic artifact failure")
                return repository.saveGeneratedArtifact(id, owner, artifact)
            }
        }
        val context = scene()
        val coordinator = requests(companionRepository = fault)
        val first = viewModel(coordinator)
        first.open(context.scenarioId, "entry")
        settle(first)
        first.chooseOpening(CompanionOpeningMode.DIRECT)
        settle(first)
        reply("<dialogue>已经收到，只需保存。</dialogue>")
        first.send("已保存的输入")
        settle(first)
        assertEquals(CompanionRecoveryAction.SAVE, first.uiState.value.recoveryAction)
        val attemptId = first.uiState.value.attemptId
        first.cancel()
        clock += 130_000

        val restored = viewModel(coordinator)
        restored.open(context.scenarioId, null, context.runId)
        settle(restored)
        restored.refresh()
        settle(restored)
        assertEquals(attemptId, restored.uiState.value.attemptId)
        assertEquals(CompanionRecoveryAction.SAVE, restored.uiState.value.recoveryAction)
        assertEquals(1, server.requestCount)
        failArtifact = false
        restored.recover()
        settle(restored)
        assertEquals(CompanionRecoveryAction.NONE, restored.uiState.value.recoveryAction)
        assertEquals(2, conversations.listMessages(context.conversationId).size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun incompleteRefresh_preservesExplicitRegeneration() = runBlocking<Unit> {
        val context = scene()
        val vm = viewModel()
        vm.open(context.scenarioId, "entry")
        settle(vm)
        vm.chooseOpening(CompanionOpeningMode.DIRECT)
        settle(vm)
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"<dialogue>未完成"},"finish_reason":"length"}]}""")
        )
        vm.send("让角色回应")
        settle(vm)
        assertEquals(PromptCompletionStatus.TRUNCATED, vm.uiState.value.completionStatus)
        assertEquals(CompanionRecoveryAction.GENERATE, vm.uiState.value.recoveryAction)
        vm.refresh()
        settle(vm)
        assertEquals(PromptCompletionStatus.TRUNCATED, vm.uiState.value.completionStatus)
        assertEquals(CompanionRecoveryAction.GENERATE, vm.uiState.value.recoveryAction)
        assertTrue(vm.uiState.value.preview.contains("未完成"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun claimFailure_exposesUnsentRecovery() = runBlocking<Unit> {
        val context = scene()
        val vm = viewModel()
        vm.open(context.scenarioId, "entry")
        settle(vm)
        vm.chooseOpening(CompanionOpeningMode.DIRECT)
        settle(vm)
        sql("CREATE TRIGGER fail_claim BEFORE UPDATE OF state ON companion_attempts WHEN NEW.state = 'REQUESTED' BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        vm.send("输入已经保存")
        settle(vm)
        assertEquals(1, conversations.listMessages(context.conversationId).size)
        assertEquals(0, server.requestCount)
        assertEquals(CompanionRecoveryAction.GENERATE, vm.uiState.value.recoveryAction)
        assertFalse(vm.uiState.value.isBusy)
        sql("DROP TRIGGER fail_claim")
        reply()
        vm.recover()
        settle(vm)
        assertEquals(2, conversations.listMessages(context.conversationId).size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun leavingAForB_cancelsLateResponseAndKeepsBPreparation() = runBlocking<Unit> {
        val context = scene()
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                release.await(5, TimeUnit.SECONDS)
                return MockResponse().setHeader("Content-Type", "application/json")
                    .setBody("""{"choices":[{"message":{"content":"<dialogue>迟到的 A</dialogue>"},"finish_reason":"stop"}]}""")
            }
        }
        try {
            val vm = viewModel()
            vm.open(context.scenarioId, "entry-A")
            settle(vm)
            vm.chooseOpening(CompanionOpeningMode.APPROACH)
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            vm.open(context.scenarioId, "entry-B")
            settle(vm)
            release.countDown()
            assertEquals("entry-B", vm.uiState.value.entryId)
            assertEquals("entry-B", vm.uiState.value.encounter?.id)
            assertEquals(CompanionEntryPhase.PREPARE, vm.uiState.value.phase)
            assertEquals("", vm.uiState.value.preview)
            assertTrue(conversations.listMessages(context.conversationId).isEmpty())
            assertEquals(1, server.requestCount)
        } finally {
            release.countDown()
        }
    }

    private fun viewModel(coordinator: CompanionRequestCoordinator = requests()) =
        RoleplayCompanionViewModel(
            SavedStateHandle(),
            roleplay,
            repository,
            coordinator,
            settings
        ).also(viewModels::add)

    private suspend fun settle(viewModel: RoleplayCompanionViewModel) {
        withTimeout(15_000) { viewModel.uiState.first { !it.isBusy } }
    }
}
