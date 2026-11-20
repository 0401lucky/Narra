package com.example.myapplication.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.roleplay.CompanionTextDocumentWriter
import com.example.myapplication.data.repository.roleplay.CompanionTextExporter
import com.example.myapplication.model.*
import com.example.myapplication.testutil.CompanionD4SettingsStore
import com.example.myapplication.testutil.FakeCompanionHistoryRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CompanionHistoryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val models = mutableListOf<CompanionHistoryViewModel>()

    @After
    fun closeModels() {
        models.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun loadedWindowAndFrozenSelection_neverDeleteNewerUnseenRevision() =
        runTest(main.dispatcher.scheduler) {
            val repository = FakeCompanionHistoryRepository()
            val model = model(repository)
            advanceUntilIdle()
            assertEquals(50, model.uiState.value.records.size)
            assertEquals(125, model.uiState.value.totalCount)
            assertEquals("Asia/Shanghai", model.uiState.value.query.zoneId)
            model.toggleSelection("history-090")
            repository.data = repository.data.map {
                if (it.message.id == "history-090") it.copy(
                    message = it.message.copy(
                        content = "用户后续修改",
                        memoryRevision = 2
                    )
                ) else it
            }
            model.refresh()
            advanceUntilIdle()
            model.deleteSelected()
            advanceUntilIdle()
            assertEquals(1L, repository.lastDeletes.single().revision)
            assertTrue(repository.data.any { it.message.id == "history-090" && it.message.content == "用户后续修改" })
            assertNotNull(model.uiState.value.errorMessage)
            assertEquals(setOf("history-090"), model.uiState.value.selectedIds)
        }

    @Test
    fun multipleEdits_keepDraftsOnCasConflictAndApplyExplicitReselection() =
        runTest(main.dispatcher.scheduler) {
            val repository = FakeCompanionHistoryRepository()
            val model = model(repository)
            advanceUntilIdle()
            model.beginEdit(setOf("history-090", "history-091"))
            model.updateDraft("history-090", "  <dialogue>完整人工修订</dialogue>  ")
            model.updateDraft("history-091", "第二条完整修订")
            repository.data = repository.data.map {
                if (it.message.id == "history-091") it.copy(
                    message = it.message.copy(
                        content = "用户新版本",
                        memoryRevision = 2
                    )
                ) else it
            }
            model.refresh()
            advanceUntilIdle()
            model.saveEdits()
            advanceUntilIdle()
            assertEquals(listOf(1L, 1L), repository.lastEdits.map { it.expectedRevision })
            assertEquals(
                "  <dialogue>完整人工修订</dialogue>  ",
                model.uiState.value.editingDrafts["history-090"]
            )
            assertNotNull(model.uiState.value.errorMessage)
            assertEquals(
                "未删节原文 90",
                repository.data.single { it.message.id == "history-090" }.message.content
            )
            model.cancelEdit()
            model.beginEdit(setOf("history-090", "history-091"))
            model.updateDraft("history-090", "明确重选后的第一条")
            model.updateDraft("history-091", "明确重选后的第二条")
            model.saveEdits()
            advanceUntilIdle()
            assertEquals(listOf(1L, 2L), repository.lastEdits.map { it.expectedRevision })
            assertTrue(model.uiState.value.editingDrafts.isEmpty())
            assertNotNull(model.uiState.value.noticeMessage)
            assertEquals(
                "明确重选后的第二条",
                repository.data.single { it.message.id == "history-091" }.message.content
            )
        }

    @Test
    fun staleSafCallback_isIgnoredAndCancellationDoesNotReadOrAnnounceSuccess() =
        runTest(main.dispatcher.scheduler) {
            val repository = FakeCompanionHistoryRepository()
            var writes = 0
            val model = model(repository, writer = CompanionTextDocumentWriter { _, _ -> writes++ })
            advanceUntilIdle()
            model.requestExport()
            val old = requireNotNull(model.uiState.value.exportRequest)
            model.setQuery(model.uiState.value.query.copy(month = "1970-01"))
            advanceUntilIdle()
            model.requestExport()
            val latest = requireNotNull(model.uiState.value.exportRequest)
            assertNotEquals(old.id, latest.id)
            model.completeExport("content://synthetic/late.txt", old.id)
            advanceUntilIdle()
            assertEquals(latest.id, model.uiState.value.exportRequest?.id)
            model.completeExport(null, latest.id)
            advanceUntilIdle()
            assertEquals(0, writes)
            assertEquals(0, repository.readAllCalls)
            assertNull(model.uiState.value.noticeMessage)
            assertNull(model.uiState.value.exportRequest)
        }

    @Test
    fun ioFailurePreservesHistory_andSuccessfulRetryUsesAllRepositoryRows() =
        runTest(main.dispatcher.scheduler) {
            val repository = FakeCompanionHistoryRepository()
            var fail = true
            var lastBytes: ByteArray? = null
            val model = model(repository, writer = CompanionTextDocumentWriter { _, bytes ->
                if (fail) throw IOException("/data/private/fake.txt") else lastBytes = bytes
            })
            advanceUntilIdle()
            val original = model.uiState.value.records
            model.requestExport()
            model.completeExport(
                "content://synthetic/failure.txt",
                model.uiState.value.exportRequest!!.id
            )
            advanceUntilIdle()
            assertNull(model.uiState.value.noticeMessage)
            assertNotNull(model.uiState.value.errorMessage)
            assertEquals(original, model.uiState.value.records)
            fail = false
            model.requestExport()
            model.completeExport(
                "content://synthetic/success.txt",
                model.uiState.value.exportRequest!!.id
            )
            advanceUntilIdle()
            assertEquals("已导出 125 条原文。", model.uiState.value.noticeMessage)
            assertTrue(String(requireNotNull(lastBytes), Charsets.UTF_8).contains("history-000@1"))
            assertEquals(50, model.uiState.value.records.size)
        }

    @Test
    fun removedCharacterAndLostScope_clearOldRecordsAndRejectLateOpening() =
        runTest(main.dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeCompanionHistoryRepository().apply {
                beforePage = { withContext(NonCancellable) { gate.await() } }
            }
            val settings = CompanionD4SettingsStore()
            val model = model(repository, settings)
            runCurrent()
            settings.settingsFlow.value =
                settings.settingsFlow.value.copy(assistants = listOf(Assistant(id = "bob")))
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(model.uiState.value.records.isEmpty())
            assertNull(model.uiState.value.scope)
            assertFalse(model.uiState.value.isLoading)
            val nextRepository = FakeCompanionHistoryRepository()
            val next = model(nextRepository)
            advanceUntilIdle()
            nextRepository.valid = false
            next.refresh()
            advanceUntilIdle()
            assertNull(next.uiState.value.scope)
            assertTrue(next.uiState.value.records.isEmpty())
            assertNotNull(next.uiState.value.errorMessage)
        }

    @Test
    fun deletedCursorKeepsExpandedWindow_andReadOnlyArchiveNeverCallsMutation() =
        runTest(main.dispatcher.scheduler) {
            val repository = FakeCompanionHistoryRepository()
            val model = model(repository)
            advanceUntilIdle()
            val boundary = requireNotNull(model.uiState.value.oldestLoaded)
            repository.data = repository.data.filterNot { it.message.id == boundary.messageId }
            model.refresh()
            advanceUntilIdle()
            assertEquals(boundary, model.uiState.value.oldestLoaded)
            model.loadOlder()
            advanceUntilIdle()
            assertEquals(99, model.uiState.value.records.size)
            assertEquals(99, model.uiState.value.records.map { it.message.id }.distinct().size)
            repository.scope =
                repository.scope.copy(readOnly = true, authorizationContext = repository.context)
            model.open()
            advanceUntilIdle()
            assertTrue(model.uiState.value.readOnly)
            model.toggleSelection("history-090")
            model.beginEdit()
            model.deleteSelected()
            advanceUntilIdle()
            assertEquals(0, repository.mutationCalls)
            assertTrue(model.uiState.value.editingDrafts.isEmpty())
        }

    @Test
    fun scopeLostAfterReadPreflight_clearsOriginalsDraftsSelectionAndPendingSaf() =
        runTest(main.dispatcher.scheduler) {
            val repository = FakeCompanionHistoryRepository()
            val model = model(repository)
            advanceUntilIdle()
            model.toggleSelection("history-090")
            model.beginEdit()
            model.requestExport()
            assertFalse(model.uiState.value.editingDrafts.isEmpty())
            assertNotNull(model.uiState.value.exportRequest)
            repository.beforePage = { repository.valid = false }

            model.refresh()
            advanceUntilIdle()

            assertNull(model.uiState.value.scope)
            assertTrue(model.uiState.value.records.isEmpty())
            assertTrue(model.uiState.value.groups.isEmpty())
            assertTrue(model.uiState.value.selectedIds.isEmpty())
            assertTrue(model.uiState.value.editingDrafts.isEmpty())
            assertNull(model.uiState.value.exportRequest)
            assertFalse(model.uiState.value.isWorking)
            assertNotNull(model.uiState.value.errorMessage)
        }

    @Test
    fun scopeLostDuringMutation_conflictCannotRetainOldAuthorizedWindow() =
        runTest(main.dispatcher.scheduler) {
            val repository = FakeCompanionHistoryRepository()
            val model = model(repository)
            advanceUntilIdle()
            model.toggleSelection("history-090")
            repository.beforeMutation = { repository.valid = false }

            model.deleteSelected()
            advanceUntilIdle()

            assertEquals(1, repository.mutationCalls)
            assertTrue(repository.data.any { it.message.id == "history-090" })
            assertNull(model.uiState.value.scope)
            assertTrue(model.uiState.value.records.isEmpty())
            assertTrue(model.uiState.value.selectedIds.isEmpty())
            assertNull(model.uiState.value.noticeMessage)
            assertFalse(model.uiState.value.isWorking)
        }

    @Test
    fun scopeLostDuringExportRead_clearsOldWindowWithoutWritingOrReportingSuccess() =
        runTest(main.dispatcher.scheduler) {
            val repository = FakeCompanionHistoryRepository()
            var writes = 0
            val model = model(repository, writer = CompanionTextDocumentWriter { _, _ -> writes++ })
            advanceUntilIdle()
            model.requestExport()
            val request = requireNotNull(model.uiState.value.exportRequest)
            repository.beforeRead = { repository.valid = false }

            model.completeExport("content://synthetic/revoked.txt", request.id)
            advanceUntilIdle()

            assertEquals(0, writes)
            assertEquals(1, repository.readAllCalls)
            assertNull(model.uiState.value.scope)
            assertTrue(model.uiState.value.records.isEmpty())
            assertNull(model.uiState.value.noticeMessage)
            assertFalse(model.uiState.value.isWorking)
        }

    @Test
    fun ordinaryReadFailure_preservesWindowAfterReconfirmingItsScope() =
        runTest(main.dispatcher.scheduler) {
            val repository = FakeCompanionHistoryRepository()
            val model = model(repository)
            advanceUntilIdle()
            val original = model.uiState.value.records
            repository.failure =
                IOException("/data/user/0/com.example.synthetic/files/history-read")

            model.refresh()
            advanceUntilIdle()

            assertEquals(original, model.uiState.value.records)
            assertEquals(repository.scope, model.uiState.value.scope)
            assertNotNull(model.uiState.value.errorMessage)
            assertFalse(requireNotNull(model.uiState.value.errorMessage).contains("/data/"))
            assertEquals("历史操作失败，请重试。", model.uiState.value.errorMessage)
            assertFalse(model.uiState.value.isWorking)
        }

    @Test
    fun successfulLateOpening_revokedScopeCannotPublishItsOriginals() =
        runTest(main.dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeCompanionHistoryRepository().apply {
                afterPage = { withContext(NonCancellable) { gate.await() } }
            }
            val model = model(repository)
            runCurrent()
            repository.valid = false
            gate.complete(Unit)
            advanceUntilIdle()

            assertRevokedWindow(model)
        }

    @Test
    fun successfulLateOlderPage_revokedScopeClearsOriginalsAndFrozenOperations() =
        runTest(main.dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeCompanionHistoryRepository()
            val model = model(repository)
            advanceUntilIdle()
            model.toggleSelection("history-090")
            model.beginEdit()
            model.requestExport()
            assertFalse(model.uiState.value.editingDrafts.isEmpty())
            assertNotNull(model.uiState.value.exportRequest)
            repository.afterPage = { withContext(NonCancellable) { gate.await() } }

            model.loadOlder()
            runCurrent()
            repository.valid = false
            gate.complete(Unit)
            advanceUntilIdle()

            assertRevokedWindow(model)
        }

    @Test
    fun successfulExpandedRefresh_revokedScopeCannotRestoreItsLoadedWindow() =
        runTest(main.dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeCompanionHistoryRepository()
            val model = model(repository)
            advanceUntilIdle()
            model.loadOlder()
            advanceUntilIdle()
            assertEquals(100, model.uiState.value.records.size)
            model.toggleSelection("history-090")
            model.beginEdit()
            model.requestExport()
            var fullReadReturned = false
            repository.afterRead = { fullReadReturned = true }
            repository.afterPage = {
                if (fullReadReturned) withContext(NonCancellable) { gate.await() }
            }

            model.refresh()
            runCurrent()
            assertTrue(fullReadReturned)
            repository.valid = false
            gate.complete(Unit)
            advanceUntilIdle()

            assertRevokedWindow(model)
        }

    @Test
    fun lateRevokedOlderScope_doesNotCancelNewOpening() =
        runTest(main.dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeCompanionHistoryRepository()
            val model = model(repository)
            advanceUntilIdle()
            repository.valid = false
            repository.afterScopeRefresh = { withContext(NonCancellable) { gate.await() } }

            model.loadOlder()
            runCurrent()
            repository.valid = true
            model.open()
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(2, repository.authorizations)
            assertEquals(repository.scope, model.uiState.value.scope)
            assertEquals(repository.data.takeLast(50), model.uiState.value.records)
            assertNull(model.uiState.value.errorMessage)
            assertFalse(model.uiState.value.isLoading)
            assertFalse(model.uiState.value.isWorking)
        }

    @Test
    fun lateRevokedRefreshScope_doesNotCancelNewQuery() =
        runTest(main.dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeCompanionHistoryRepository()
            val model = model(repository)
            advanceUntilIdle()
            repository.valid = false
            repository.afterScopeRefresh = { withContext(NonCancellable) { gate.await() } }

            model.refresh()
            runCurrent()
            repository.valid = true
            val query = model.uiState.value.query.copy(month = "1970-01")
            model.setQuery(query)
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(query, model.uiState.value.query)
            assertEquals(repository.scope, model.uiState.value.scope)
            assertEquals(repository.data.takeLast(50), model.uiState.value.records)
            assertEquals(125, model.uiState.value.totalCount)
            assertNull(model.uiState.value.errorMessage)
            assertFalse(model.uiState.value.isLoading)
            assertFalse(model.uiState.value.isWorking)
        }

    private fun assertRevokedWindow(model: CompanionHistoryViewModel) {
        val state = model.uiState.value
        assertNull(state.scope)
        assertTrue(state.records.isEmpty())
        assertTrue(state.groups.isEmpty())
        assertTrue(state.selectedIds.isEmpty())
        assertTrue(state.editingDrafts.isEmpty())
        assertNull(state.exportRequest)
        assertNull(state.noticeMessage)
        assertNotNull(state.errorMessage)
        assertFalse(state.isLoading)
        assertFalse(state.isWorking)
    }

    private fun model(
        repository: FakeCompanionHistoryRepository,
        settings: CompanionD4SettingsStore = CompanionD4SettingsStore(),
        writer: CompanionTextDocumentWriter = CompanionTextDocumentWriter { _, _ -> }
    ) =
        CompanionHistoryViewModel(
            "scene", "alice", repository, CompanionTextExporter(repository, writer), settings,
            deviceZoneId = { "Asia/Shanghai" }).also { models += it }
}
