package com.example.myapplication.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.roleplay.CompanionResourcesRepository
import com.example.myapplication.model.*
import com.example.myapplication.testutil.CompanionD4SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionDisplayViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val models = mutableListOf<CompanionDisplayViewModel>()
    @After
    fun close() {
        models.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun explicitChoice_savesOnlyVisualPreferenceAndKeepsOriginalDefaults() =
        runTest(main.dispatcher.scheduler) {
            val store = CompanionD4SettingsStore()
            val before = store.settingsFlow.value
            val model = model(store, Resources())
            advanceUntilIdle()
            assertEquals(CompanionDisplayMode.LIST, model.uiState.value.mode)
            assertNull(store.settingsFlow.value.companionSettings("alice").visuals.preferredMode)
            model.chooseMode(CompanionDisplayMode.STAGE)
            advanceUntilIdle()
            assertEquals(1, store.writes.size)
            assertEquals("alice", store.writes.single().first)
            assertEquals(0L, store.writes.single().second)
            assertNull(store.writes.single().third.writing)
            assertNull(store.writes.single().third.zoneId)
            assertEquals(
                CompanionDisplayMode.STAGE,
                store.settingsFlow.value.companionSettings("alice").visuals.preferredMode
            )
            assertEquals(
                before.copy(companionCharacterSettings = store.settingsFlow.value.companionCharacterSettings),
                store.settingsFlow.value
            )
            assertFalse(model.uiState.value.isSavingPreference)
        }

    @Test
    fun failedPreference_keepsLocalChoiceAndNeverReportsSaved() =
        runTest(main.dispatcher.scheduler) {
            val store =
                CompanionD4SettingsStore().apply { result = CompanionSettingsWriteResult.Conflict }
            val model = model(store, Resources())
            advanceUntilIdle()
            model.chooseMode(CompanionDisplayMode.STAGE)
            advanceUntilIdle()
            assertEquals(CompanionDisplayMode.STAGE, model.uiState.value.mode)
            assertNotNull(model.uiState.value.errorMessage)
            assertNull(store.settingsFlow.value.companionSettings("alice").visuals.preferredMode)
            assertFalse(model.uiState.value.isSavingPreference)
        }

    @Test
    fun ownerChangesBeforeLaunch_doNotWriteOldDraftToNewCharacter() =
        runTest(main.dispatcher.scheduler) {
            val store = CompanionD4SettingsStore()
            val model = model(store, Resources())
            advanceUntilIdle()
            model.chooseMode(CompanionDisplayMode.STAGE)
            model.refresh(RoleplayScenario(id = "bob-scene", assistantId = "bob"))
            advanceUntilIdle()
            assertTrue(store.writes.isEmpty())
            assertEquals(CompanionDisplayMode.LIST, model.uiState.value.mode)
            assertNull(store.settingsFlow.value.companionSettings("bob").visuals.preferredMode)
        }

    @Test
    fun lateSave_keepsOriginalOwnerAndDoesNotChangeNewDisplay() =
        runTest(main.dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val store = CompanionD4SettingsStore().apply {
                beforeWrite = { withContext(NonCancellable) { gate.await() } }
            }
            val model = model(store, Resources())
            advanceUntilIdle()
            model.chooseMode(CompanionDisplayMode.STAGE)
            runCurrent()
            assertEquals("alice", store.writes.single().first)
            model.refresh(RoleplayScenario(id = "bob-scene", assistantId = "bob"))
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals("alice", store.writes.single().first)
            assertEquals(
                CompanionDisplayMode.STAGE,
                store.settingsFlow.value.companionSettings("alice").visuals.preferredMode
            )
            assertNull(store.settingsFlow.value.companionSettings("bob").visuals.preferredMode)
            assertEquals(CompanionDisplayMode.LIST, model.uiState.value.mode)
            assertFalse(model.uiState.value.isSavingPreference)
        }

    @Test
    fun canceledOldResourceIo_cannotReplaceNewCharacterResources() =
        runTest(main.dispatcher.scheduler) {
            val oldStarted = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            val files = Resources().apply {
                resolver = { scene ->
                    if (scene.assistantId == "alice") withContext(NonCancellable) {
                        oldStarted.complete(
                            Unit
                        ); gate.await(); picture("old-alice")
                    }
                    else picture("new-bob")
                }
            }
            val model = model(CompanionD4SettingsStore(), files)
            runCurrent()
            assertTrue(oldStarted.isCompleted)
            model.refresh(RoleplayScenario(id = "bob-scene", assistantId = "bob"))
            runCurrent()
            assertEquals(
                "new-bob",
                model.uiState.value.resources.defaultPortraits.single().resourceId
            )
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                "new-bob",
                model.uiState.value.resources.defaultPortraits.single().resourceId
            )
        }

    @Test
    fun deletedCharacter_clearsLocalStageAndRejectsLateResources() =
        runTest(main.dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val store =
                CompanionD4SettingsStore().apply { result = CompanionSettingsWriteResult.Conflict }
            val resources = Resources()
            val model = model(store, resources)
            advanceUntilIdle()
            model.chooseMode(CompanionDisplayMode.STAGE)
            advanceUntilIdle()
            resources.resolver =
                { withContext(NonCancellable) { gate.await(); picture("deleted-alice") } }
            model.refresh()
            runCurrent()
            store.settingsFlow.value =
                store.settingsFlow.value.copy(assistants = listOf(Assistant(id = "bob")))
            runCurrent()
            assertEquals(CompanionDisplayMode.LIST, model.uiState.value.mode)
            assertTrue(model.uiState.value.resources.defaultPortraits.isEmpty())
            val writes = store.writes.size
            model.chooseMode(CompanionDisplayMode.STAGE)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(writes, store.writes.size)
            assertTrue(model.uiState.value.resources.defaultPortraits.isEmpty())
            assertEquals(CompanionDisplayMode.LIST, model.uiState.value.mode)
        }

    private fun model(store: CompanionD4SettingsStore, resources: Resources) =
        CompanionDisplayViewModel(
            RoleplayScenario(id = "alice-scene", assistantId = "alice"),
            store,
            resources
        ).also { models += it }

    private fun picture(id: String) = CompanionResolvedResources(
        defaultPortraits = listOf(
            CompanionResolvedImage(
                id,
                "/synthetic/$id.png"
            )
        )
    )

    private inner class Resources : CompanionResourcesRepository {
        var resolver: suspend (RoleplayScenario) -> CompanionResolvedResources =
            { picture(it.assistantId) }

        override suspend fun resolve(
            visuals: CompanionVisualSettings,
            scenario: RoleplayScenario,
            assistant: Assistant?
        ) = resolver(scenario)

        override suspend fun importImage(
            uri: String,
            kind: CompanionImageKind,
            name: String
        ): CompanionImageResource = error("显示不能导入图片")

        override suspend fun save(
            assistantId: String,
            expectedRevision: Long,
            visuals: CompanionVisualSettings,
            importedPaths: Set<String>
        ): CompanionSettingsWriteResult = error("显示不能保存外观草稿")

        override suspend fun discardImports(paths: Set<String>) = error("显示不能删除图片")
    }
}
