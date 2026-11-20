package com.example.myapplication.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.roleplay.CompanionReadingRepository
import com.example.myapplication.model.*
import com.example.myapplication.testutil.CompanionD4SettingsStore
import com.example.myapplication.ui.navigation.AppRoutes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionReadingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val models = mutableListOf<CompanionReadingViewModel>()
    @After
    fun close() {
        models.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun expandedPages_refreshKeepsBoundaryEditsAndAnchorWithoutDuplicates() =
        runTest(main.dispatcher.scheduler) {
            val repository = Reading()
            val model = model(repository)
            advanceUntilIdle()
            assertEquals(40, model.uiState.value.records.size)
            model.loadOlder()
            model.loadOlder()
            advanceUntilIdle()
            assertEquals(2, repository.pageCalls.count { it.second == 40 })
            assertEquals(80, model.uiState.value.records.size)
            val item = model.uiState.value.items.first { it.record.message.id == "msg-045" }
            val anchor = CompanionReadingAnchor(
                item.record.message.id,
                item.id,
                item.record.cursor,
                17,
                false
            )
            model.updateAnchor(anchor)
            repository.data = repository.data.filterNot { it.message.id == "msg-050" }.map {
                if (it.message.id == "msg-090") it.copy(
                    message = it.message.copy(
                        content = "编辑后的原文",
                        memoryRevision = 2
                    )
                ) else it
            } + repository.record(120)
            model.refresh()
            advanceUntilIdle()
            val state = model.uiState.value
            assertEquals(80, state.records.size)
            assertEquals(80, state.records.map { it.message.id }.distinct().size)
            assertEquals("msg-040", state.oldestLoaded?.messageId)
            assertEquals(listOf(CompanionReadingCursor(40_000, "msg-040")), repository.windows)
            assertEquals(anchor, state.anchor)
            assertEquals(
                "编辑后的原文",
                state.records.first { it.message.id == "msg-090" }.message.content
            )
            assertEquals("msg-120", state.records.last().message.id)
            assertFalse(state.isRefreshing)
            assertEquals(1, repository.authorizeCalls)
        }

    @Test
    fun deletedBoundary_keepsValueCursorForNextOlderPage() = runTest(main.dispatcher.scheduler) {
        val repository = Reading()
        val model = model(repository)
        advanceUntilIdle()
        repository.data = repository.data.filterNot { it.message.id == "msg-080" }
        model.refresh()
        advanceUntilIdle()
        assertEquals("msg-080", model.uiState.value.oldestLoaded?.messageId)
        assertEquals(39, model.uiState.value.records.size)
        model.loadOlder()
        advanceUntilIdle()
        assertEquals(79, model.uiState.value.records.size)
        assertEquals("msg-040", model.uiState.value.oldestLoaded?.messageId)
        assertFalse(model.uiState.value.records.any { it.message.id == "msg-080" })
    }

    @Test
    fun olderPageFailure_preservesWindowAndAllowsExplicitRetry() =
        runTest(main.dispatcher.scheduler) {
            val repository = Reading()
            val model = model(repository)
            advanceUntilIdle()
            val original = model.uiState.value.records
            repository.failure = IllegalStateException("读取失败")
            model.loadOlder()
            advanceUntilIdle()
            assertEquals(original, model.uiState.value.records)
            assertFalse(model.uiState.value.isLoadingOlder)
            assertNotNull(model.uiState.value.errorMessage)
            repository.failure = null
            model.loadOlder()
            advanceUntilIdle()
            assertEquals(80, model.uiState.value.records.size)
            assertNull(model.uiState.value.errorMessage)
        }

    @Test
    fun characterDeletedDuringOpening_doesNotPublishLatePage() =
        runTest(main.dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val repository = Reading().apply {
                beforePage = { _, _ -> withContext(NonCancellable) { gate.await() } }
            }
            val store = CompanionD4SettingsStore()
            val model = model(repository, store)
            runCurrent()
            assertTrue(model.uiState.value.isLoading)
            removeCharacter(store)
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(model.uiState.value.records.isEmpty())
            assertNull(model.uiState.value.scope)
            assertFalse(model.uiState.value.isLoading)
            assertNotNull(model.uiState.value.errorMessage)
        }

    @Test
    fun characterDeletedDuringOlderIo_doesNotRefillClearedWindow() =
        runTest(main.dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val repository = Reading()
            val store = CompanionD4SettingsStore()
            val model = model(repository, store)
            advanceUntilIdle()
            repository.beforePage =
                { before, _ -> if (before != null) withContext(NonCancellable) { gate.await() } }
            model.loadOlder()
            runCurrent()
            assertTrue(model.uiState.value.isLoadingOlder)
            removeCharacter(store)
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(model.uiState.value.records.isEmpty())
            assertNull(model.uiState.value.scope)
            assertFalse(model.uiState.value.isLoadingOlder)
        }

    @Test
    fun characterDeletedDuringRefresh_doesNotPublishLateWindow() =
        runTest(main.dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val repository = Reading()
            val store = CompanionD4SettingsStore()
            val model = model(repository, store)
            advanceUntilIdle()
            repository.beforeWindow = { withContext(NonCancellable) { gate.await() } }
            model.refresh()
            runCurrent()
            assertTrue(model.uiState.value.isRefreshing)
            removeCharacter(store)
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(model.uiState.value.records.isEmpty())
            assertNull(model.uiState.value.scope)
            assertFalse(model.uiState.value.isRefreshing)
        }

    @Test
    fun requestedOldMessage_isLocatedOutsideInitialWindowAndOwnsReadingAnchor() =
        runTest(main.dispatcher.scheduler) {
            val repository = Reading()
            val model = CompanionReadingViewModel(
                "scene", "alice", repository, CompanionD4SettingsStore(),
                focusMessageId = "msg-010",
            ).also { models += it }
            advanceUntilIdle()
            val state = model.uiState.value
            assertTrue(state.records.any { it.message.id == "msg-010" })
            assertEquals("msg-010", state.anchor?.messageId)
            assertFalse(requireNotNull(state.anchor).followingBottom)
            assertEquals(repository.context, state.scope?.context)
            assertEquals(1, repository.authorizeCalls)
            assertTrue(repository.pageCalls.none { it.second == 40 })
        }

    @Test
    fun incompleteArchiveIdentity_isRejectedWithoutFallingBackToCurrentWindow() =
        runTest(main.dispatcher.scheduler) {
            val repository = Reading()
            val model = CompanionReadingViewModel(
                "scene", "alice", repository, CompanionD4SettingsStore(),
                focusMessageId = "msg-010", archiveSpaceId = "old-space",
            ).also { models += it }
            advanceUntilIdle()
            assertNull(model.uiState.value.scope)
            assertTrue(model.uiState.value.records.isEmpty())
            assertNotNull(model.uiState.value.errorMessage)
            assertTrue(repository.pageCalls.isEmpty())
        }

    @Test
    fun reopenIoFailure_keepsExpandedWindowAndAnchorAndResumesSourceObservation() =
        runTest(main.dispatcher.scheduler) {
            val repository = Reading()
            val model = model(repository)
            advanceUntilIdle()
            model.loadOlder()
            advanceUntilIdle()
            val item = model.uiState.value.items.first { it.record.message.id == "msg-045" }
            val anchor = CompanionReadingAnchor(item.record.message.id, item.id, item.record.cursor, 23, false)
            model.updateAnchor(anchor)
            val before = model.uiState.value
            assertEquals(80, before.records.size)
            repository.failure = IOException("合成读取失败")
            model.open()
            advanceUntilIdle()
            val failed = model.uiState.value
            assertEquals(before.records, failed.records)
            assertEquals(before.items, failed.items)
            assertEquals(before.oldestLoaded, failed.oldestLoaded)
            assertEquals(anchor, failed.anchor)
            assertEquals(before.scope, failed.scope)
            assertFalse(failed.isLoading)
            assertNotNull(failed.errorMessage)

            repository.failure = null
            repository.data = repository.data + repository.record(120)
            repository.changes.emit(Unit)
            advanceUntilIdle()
            assertTrue(model.uiState.value.records.any { it.message.id == "msg-120" })
            assertEquals("msg-040", model.uiState.value.oldestLoaded?.messageId)
            assertEquals(anchor, model.uiState.value.anchor)
            assertNull(model.uiState.value.errorMessage)
        }

    @Test
    fun lostAuthorization_clearsOriginalsAndSelectionDuringPagingAndReopen() =
        runTest(main.dispatcher.scheduler) {
            val repository = Reading()
            val model = model(repository)
            advanceUntilIdle()
            assertEquals(40, model.uiState.value.records.size)
            repository.refreshAuthorized = false
            model.loadOlder()
            advanceUntilIdle()
            assertNull(model.uiState.value.scope)
            assertTrue(model.uiState.value.records.isEmpty())
            assertTrue(model.uiState.value.items.isEmpty())
            assertNull(model.uiState.value.selection)
            assertNotNull(model.uiState.value.errorMessage)

            repository.refreshAuthorized = true
            model.open()
            advanceUntilIdle()
            assertEquals(40, model.uiState.value.records.size)
            repository.authorized = false
            model.open()
            advanceUntilIdle()
            assertNull(model.uiState.value.scope)
            assertTrue(model.uiState.value.records.isEmpty())
            assertTrue(model.uiState.value.items.isEmpty())
            assertNotNull(model.uiState.value.errorMessage)
        }

    @Test
    fun authorizationLostDuringIo_rejectsLateOlderAndRefreshWindows() =
        runTest(main.dispatcher.scheduler) {
            val repository = Reading()
            val model = model(repository)
            advanceUntilIdle()
            val pageGate = CompletableDeferred<Unit>()
            repository.beforePage = { before, _ ->
                if (before != null) withContext(NonCancellable) { pageGate.await() }
            }
            model.loadOlder()
            runCurrent()
            repository.refreshAuthorized = false
            pageGate.complete(Unit)
            advanceUntilIdle()
            assertNull(model.uiState.value.scope)
            assertTrue(model.uiState.value.records.isEmpty())
            assertFalse(model.uiState.value.isLoadingOlder)

            repository.refreshAuthorized = true
            repository.beforePage = { _, _ -> }
            model.open()
            advanceUntilIdle()
            assertEquals(40, model.uiState.value.records.size)
            val windowGate = CompletableDeferred<Unit>()
            repository.beforeWindow = { withContext(NonCancellable) { windowGate.await() } }
            model.refresh()
            runCurrent()
            repository.refreshAuthorized = false
            windowGate.complete(Unit)
            advanceUntilIdle()
            assertNull(model.uiState.value.scope)
            assertTrue(model.uiState.value.records.isEmpty())
            assertFalse(model.uiState.value.isRefreshing)
        }

    @Test
    fun readingExtension_preservesOldConstructorFactoryAndRouteJvmSignatures() {
        val string = String::class.java
        val repository = CompanionReadingRepository::class.java
        val store = SettingsStore::class.java
        val integer = Int::class.javaPrimitiveType!!
        val marker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        assertNotNull(CompanionReadingViewModel::class.java.getConstructor(string, string, repository, store, integer))
        assertNotNull(CompanionReadingViewModel::class.java.getConstructor(string, string, repository, store, integer, integer, marker))
        assertNotNull(CompanionReadingViewModel.Companion::class.java.getDeclaredMethod("factory", string, string, repository, store))
        assertNotNull(AppRoutes::class.java.getDeclaredMethod("roleplayCompanionReading", string, string))
    }

    private fun model(
        repository: Reading,
        store: CompanionD4SettingsStore = CompanionD4SettingsStore()
    ) = CompanionReadingViewModel("scene", "alice", repository, store).also { models += it }

    private fun removeCharacter(store: CompanionD4SettingsStore) {
        store.settingsFlow.value =
            store.settingsFlow.value.copy(assistants = listOf(Assistant(id = "bob")))
    }

    private class Reading : CompanionReadingRepository {
        val context =
            MemoryRuntimeContext("alice", "space", "conversation", "scene", "run", "persona")
        val binding = CompanionReadingBinding(
            context.conversationId,
            context.scenarioId,
            context.runId,
            "合成阅读",
            "alice"
        )
        val scope = CompanionReadingScope(context, listOf(binding), emptySet())
        var data = (0..119).map(::record)
        val pageCalls = mutableListOf<Pair<CompanionReadingCursor?, Int>>()
        val windows = mutableListOf<CompanionReadingCursor>()
        var authorizeCalls = 0
        var authorized = true
        var refreshAuthorized = true
        var beforePage: suspend (CompanionReadingCursor?, Int) -> Unit = { _, _ -> }
        var beforeWindow: suspend () -> Unit = {}
        var failure: Exception? = null
        val changes = MutableSharedFlow<Unit>()
        fun record(index: Int) = CompanionReadingRecord(
            ChatMessage(
                "msg-${index.toString().padStart(3, '0')}",
                context.conversationId,
                MessageRole.ASSISTANT,
                "合成正文 $index",
                createdAt = index * 1_000L,
                speakerId = "alice"
            ), binding
        )

        override suspend fun authorize(
            scenarioId: String,
            characterId: String
        ): CompanionReadingScope? {
            authorizeCalls++; return scope.takeIf { authorized }
        }

        override suspend fun refreshScope(scope: CompanionReadingScope) = scope.takeIf { refreshAuthorized }
        override suspend fun page(
            scope: CompanionReadingScope,
            before: CompanionReadingCursor?,
            limit: Int
        ): CompanionReadingPage {
            pageCalls += before to limit
            beforePage(before, limit)
            failure?.let { throw it }
            val all = data.filter { before == null || it.cursor < before }
                .sortedWith { first, second -> first.cursor.compareTo(second.cursor) }
            val selected = all.takeLast(limit)
            return CompanionReadingPage(selected, selected.firstOrNull()?.cursor, all.size > limit)
        }

        override suspend fun readWindow(
            scope: CompanionReadingScope,
            oldestInclusive: CompanionReadingCursor
        ): List<CompanionReadingRecord> {
            windows += oldestInclusive
            beforeWindow()
            return data.filter { it.cursor >= oldestInclusive }
        }

        override fun observeChanges(scope: CompanionReadingScope) = changes
    }
}
