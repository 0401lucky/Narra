package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionVisualSettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val models = mutableListOf<CompanionVisualSettingsViewModel>()
    @After
    fun close() {
        models.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun restoredDraft_keepsBaseRevisionAndConflictsUntilExplicitReload() =
        runTest(main.dispatcher.scheduler) {
            val store = CompanionD4SettingsStore()
            val files = Resources(store)
            val handle = SavedStateHandle()
            val first = model(store, files, handle)
            advanceUntilIdle()
            val draft = first.uiState.value.draft!!.copy(
                preferredMode = CompanionDisplayMode.STAGE,
                sceneOverrides = mapOf(
                    "scene" to CompanionSceneVisualOverride(
                        layout = CompanionStageLayout(
                            CompanionPortraitPosition.LEFT,
                            0.5f,
                            0.25f
                        )
                    )
                )
            )
            first.updateDraft(draft)
            first.viewModelScope.cancel()
            val latest = CompanionCharacterSettings(
                revision = 4,
                visuals = CompanionVisualSettings(
                    layout = CompanionStageLayout(
                        CompanionPortraitPosition.RIGHT
                    )
                )
            )
            store.settingsFlow.value =
                store.settingsFlow.value.copy(companionCharacterSettings = mapOf("alice" to latest))
            val restored = model(store, files, handle)
            advanceUntilIdle()
            assertEquals(draft, restored.uiState.value.draft)
            assertTrue(restored.uiState.value.hasConflict)
            restored.save()
            advanceUntilIdle()
            assertEquals(0L, files.saves.single().first)
            assertFalse(restored.uiState.value.closeRequested)
            assertEquals(draft, restored.uiState.value.draft)
            restored.reloadDiscardingDraft()
            advanceUntilIdle()
            assertEquals(latest.visuals, restored.uiState.value.draft)
            assertFalse(restored.uiState.value.hasConflict)
            assertFalse(restored.uiState.value.closeRequested)
        }

    @Test
    fun importFinishesAfterSettingsChange_usesOriginalCasAndKeepsPrivateDraft() =
        runTest(main.dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val store = CompanionD4SettingsStore()
            val files = Resources(store).apply { beforeImport = { gate.await() } }
            val model = model(store, files)
            advanceUntilIdle()
            model.importImage("content://synthetic/photo", CompanionImageKind.PORTRAIT, "新立绘")
            runCurrent()
            assertTrue(model.uiState.value.isImporting)
            val concurrent = CompanionCharacterSettings(
                revision = 3,
                writing = CompanionWritingSettings(style = CompanionWritingStyle.CINEMATIC)
            )
            store.settingsFlow.value =
                store.settingsFlow.value.copy(companionCharacterSettings = mapOf("alice" to concurrent))
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals("/private/new.png", model.uiState.value.draft!!.resources.single().path)
            model.save()
            advanceUntilIdle()
            assertEquals(0L, files.saves.single().first)
            assertTrue(model.uiState.value.hasConflict)
            assertFalse(model.uiState.value.closeRequested)
            assertTrue(files.discards.isEmpty())
            assertEquals(concurrent, store.settingsFlow.value.companionSettings("alice"))
        }

    @Test
    fun storageFailure_keepsDraftAndRestoresSaveAction() = runTest(main.dispatcher.scheduler) {
        val store = CompanionD4SettingsStore()
        val files = Resources(store).apply { failure = IllegalStateException("模拟保存失败") }
        val model = model(store, files)
        advanceUntilIdle()
        model.updateDraft(model.uiState.value.draft!!.copy(preferredMode = CompanionDisplayMode.STAGE))
        val draft = model.uiState.value.draft
        model.save()
        advanceUntilIdle()
        assertEquals(draft, model.uiState.value.draft)
        assertFalse(model.uiState.value.closeRequested)
        assertFalse(model.uiState.value.isSaving)
        assertTrue(model.uiState.value.editable)
        assertNotNull(model.uiState.value.errorMessage)
        assertNull(store.settingsFlow.value.companionSettings("alice").visuals.preferredMode)
    }

    @Test
    fun discardedImport_closesAfterCleanupAndNeverSavesPreference() =
        runTest(main.dispatcher.scheduler) {
            val store = CompanionD4SettingsStore()
            val files = Resources(store)
            val model = model(store, files)
            advanceUntilIdle()
            model.importImage("content://synthetic/photo", CompanionImageKind.PORTRAIT, "新图")
            advanceUntilIdle()
            model.requestClose()
            assertTrue(model.uiState.value.showDiscardConfirmation)
            assertFalse(model.uiState.value.closeRequested)
            model.dismissDiscard()
            assertFalse(model.uiState.value.showDiscardConfirmation)
            model.requestClose()
            model.discardAndClose()
            advanceUntilIdle()
            assertEquals(listOf(setOf("/private/new.png")), files.discards)
            assertTrue(model.uiState.value.closeRequested)
            assertTrue(store.writes.isEmpty())
        }

    @Test
    fun importedThenRemoved_noDirtyCloseStillCleansOnlyNewImage() =
        runTest(main.dispatcher.scheduler) {
            nondirtyCleanup(save = false)
        }

    @Test
    fun importedThenRemoved_noDirtySaveStillCleansOnlyNewImage() =
        runTest(main.dispatcher.scheduler) {
            nondirtyCleanup(save = true)
        }

    @Test
    fun successfulVisualSave_preservesWritingZoneAndClearsDraftOwnership() =
        runTest(main.dispatcher.scheduler) {
            val original = CompanionCharacterSettings(
                revision = 2,
                zoneId = "Asia/Tokyo",
                writing = CompanionWritingSettings(
                    style = CompanionWritingStyle.LITERARY,
                    extraStyle = "原文风"
                )
            )
            val store = CompanionD4SettingsStore(
                AppSettings(
                    assistants = listOf(Assistant(id = "alice")),
                    companionCharacterSettings = mapOf("alice" to original)
                )
            )
            val files = Resources(store)
            val model = model(store, files)
            advanceUntilIdle()
            model.importImage("content://synthetic/photo", CompanionImageKind.PORTRAIT, "新图")
            advanceUntilIdle()
            model.save()
            advanceUntilIdle()
            val saved = store.settingsFlow.value.companionSettings("alice")
            assertEquals(3L, saved.revision)
            assertEquals(original.writing, saved.writing)
            assertEquals(original.zoneId, saved.zoneId)
            assertEquals(setOf("/private/new.png"), files.saves.single().third)
            assertTrue(model.uiState.value.closeRequested)
            assertFalse(model.uiState.value.isDirty)
        }

    @Test
    fun deletedCharacter_andFutureVersionDoNotSaveOrLoseDraft() =
        runTest(main.dispatcher.scheduler) {
            val store = CompanionD4SettingsStore()
            val files = Resources(store)
            val model = model(store, files)
            advanceUntilIdle()
            model.updateDraft(model.uiState.value.draft!!.copy(preferredMode = CompanionDisplayMode.STAGE))
            val draft = model.uiState.value.draft
            store.settingsFlow.value =
                store.settingsFlow.value.copy(assistants = listOf(Assistant(id = "bob")))
            advanceUntilIdle()
            model.save()
            advanceUntilIdle()
            assertEquals(draft, model.uiState.value.draft)
            assertFalse(model.uiState.value.characterAvailable)
            assertTrue(files.saves.isEmpty())
            val futureStore = CompanionD4SettingsStore(
                AppSettings(
                    assistants = listOf(Assistant(id = "alice")),
                    companionCharacterSettings = mapOf(
                        "alice" to CompanionCharacterSettings(
                            schemaVersion = 9
                        )
                    )
                )
            )
            val futureFiles = Resources(futureStore)
            val future = model(futureStore, futureFiles)
            advanceUntilIdle()
            assertFalse(future.uiState.value.editable)
            future.save()
            advanceUntilIdle()
            assertTrue(futureFiles.saves.isEmpty())
        }

    private suspend fun TestScope.nondirtyCleanup(save: Boolean) {
        val existing = CompanionImageResource(
            "published",
            "共享图",
            "/published/shared.png",
            CompanionImageKind.PORTRAIT
        )
        val store = CompanionD4SettingsStore(
            AppSettings(
                assistants = listOf(Assistant(id = "alice")),
                companionCharacterSettings = mapOf(
                    "alice" to CompanionCharacterSettings(
                        visuals = CompanionVisualSettings(
                            resources = listOf(existing)
                        )
                    )
                )
            )
        )
        val files = Resources(store)
        val model = model(store, files)
        advanceUntilIdle()
        model.importImage("content://synthetic/photo", CompanionImageKind.PORTRAIT, "新图")
        advanceUntilIdle()
        model.removeResource("new")
        assertFalse(model.uiState.value.isDirty)
        if (save) model.save() else model.requestClose()
        advanceUntilIdle()
        assertEquals(listOf(setOf("/private/new.png")), files.discards)
        assertFalse(files.discards.flatten().contains(existing.path))
        assertEquals(
            listOf(existing),
            store.settingsFlow.value.companionSettings("alice").visuals.resources
        )
        assertTrue(model.uiState.value.closeRequested)
        assertTrue(store.writes.isEmpty())
    }

    private fun model(
        store: CompanionD4SettingsStore,
        files: Resources,
        handle: SavedStateHandle = SavedStateHandle()
    ) = CompanionVisualSettingsViewModel(
        "alice",
        "scene",
        store,
        files,
        handle
    ).also { models += it }

    private class Resources(private val store: CompanionD4SettingsStore) :
        CompanionResourcesRepository {
        var beforeImport: suspend () -> Unit = {}
        var failure: Exception? = null
        val saves = mutableListOf<Triple<Long, CompanionVisualSettings, Set<String>>>()
        val discards = mutableListOf<Set<String>>()
        override suspend fun importImage(
            uri: String,
            kind: CompanionImageKind,
            name: String
        ): CompanionImageResource {
            beforeImport()
            return CompanionImageResource("new", name, "/private/new.png", kind, 12, 16)
        }

        override suspend fun resolve(
            visuals: CompanionVisualSettings,
            scenario: RoleplayScenario,
            assistant: Assistant?
        ) = CompanionResolvedResources()

        override suspend fun save(
            assistantId: String,
            expectedRevision: Long,
            visuals: CompanionVisualSettings,
            importedPaths: Set<String>
        ): CompanionSettingsWriteResult {
            saves += Triple(expectedRevision, visuals, importedPaths)
            failure?.let { throw it }
            return store.updateCompanionSettings(
                assistantId,
                expectedRevision,
                CompanionSettingsPatch(visuals = visuals)
            )
        }

        override suspend fun discardImports(paths: Set<String>) {
            discards += paths
        }
    }
}
