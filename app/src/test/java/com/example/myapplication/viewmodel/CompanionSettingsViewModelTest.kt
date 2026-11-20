package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.model.*
import com.example.myapplication.testutil.FakeSettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionSettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val models = mutableListOf<CompanionSettingsViewModel>()

    @After
    fun closeModels() {
        models.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun recreatedDraftKeepsOriginalRevisionAndRequiresExplicitConflictReload() =
        runTest(main.dispatcher.scheduler) {
            val live = MutableStateFlow(
                AppSettings(
                    assistants = listOf(Assistant(id = "alice")),
                    companionCharacterSettings = mapOf(
                        "alice" to CompanionCharacterSettings(
                            revision = 4
                        )
                    )
                )
            )
            val expectedRevisions = mutableListOf<Long>()
            val store = object : SettingsStore by FakeSettingsStore(live.value) {
                override val settingsFlow = live
                override suspend fun updateCompanionSettings(
                    assistantId: String,
                    expectedRevision: Long,
                    patch: CompanionSettingsPatch
                ): CompanionSettingsWriteResult {
                    expectedRevisions += expectedRevision
                    val current = live.value.companionSettings(assistantId)
                    if (current.revision != expectedRevision) return CompanionSettingsWriteResult.Conflict
                    val next = current.copy(
                        revision = current.revision + 1,
                        zoneId = patch.zoneId ?: current.zoneId,
                        writing = patch.writing ?: current.writing
                    )
                    live.value =
                        live.value.copy(companionCharacterSettings = mapOf(assistantId to next))
                    return CompanionSettingsWriteResult.Saved(next)
                }
            }
            val handle = SavedStateHandle()
            val first = model(store, handle)
            advanceUntilIdle()
            first.updateWriting(
                CompanionWritingSettings(
                    style = CompanionWritingStyle.LITERARY,
                    extraStyle = "未保存草稿"
                )
            )
            first.viewModelScope.cancel()
            live.value = live.value.copy(
                companionCharacterSettings = mapOf(
                    "alice" to CompanionCharacterSettings(
                        revision = 5,
                        writing = CompanionWritingSettings(style = CompanionWritingStyle.CINEMATIC)
                    )
                )
            )
            val restored = model(store, handle)
            advanceUntilIdle()
            assertEquals("未保存草稿", restored.uiState.value.draft!!.writing.extraStyle)
            assertTrue(restored.uiState.value.hasConflict)
            restored.save()
            advanceUntilIdle()
            assertEquals(listOf(4L), expectedRevisions)
            assertFalse(restored.uiState.value.closeRequested)
            assertEquals(
                CompanionWritingStyle.LITERARY,
                restored.uiState.value.draft!!.writing.style
            )
            restored.reloadDiscardingDraft()
            assertEquals(
                CompanionWritingStyle.CINEMATIC,
                restored.uiState.value.draft!!.writing.style
            )
            restored.updateWriting(restored.uiState.value.draft!!.writing.copy(extraStyle = "最新草稿"))
            restored.save()
            advanceUntilIdle()
            assertEquals(listOf(4L, 5L), expectedRevisions)
            assertTrue(restored.uiState.value.closeRequested)
            assertEquals("最新草稿", live.value.companionSettings("alice").writing.extraStyle)
        }

    @Test
    fun savedStateDraftCanRoundTripWithAllWritingCollections() =
        runTest(main.dispatcher.scheduler) {
            val writing = CompanionWritingSettings(
                CompanionWritingStyle.INTENSE, CompanionPointOfView.THIRD_NAME, "补充\r\n正文",
                listOf(CompanionStyleTemplate("template", "名称", "模板原文")), false,
                CompanionObservationSettings(
                    true,
                    customFields = listOf(CompanionObservationField("obs_local", "自定义"))
                ),
            )
            val initial = AppSettings(assistants = listOf(Assistant(id = "alice")))
            val handle = SavedStateHandle()
            val viewModel = model(FakeSettingsStore(initial), handle)
            advanceUntilIdle()
            viewModel.updateWriting(writing)
            viewModel.updateZone("Asia/Tokyo")
            val draft = viewModel.uiState.value.draft!!
            val bytes = ByteArrayOutputStream().use { stream ->
                ObjectOutputStream(stream).use { it.writeObject(draft) }
                stream.toByteArray()
            }
            val restored =
                ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as CompanionCharacterSettings }
            assertEquals(draft, restored)
            assertEquals(writing, restored.writing)
            viewModel.requestClose()
            assertTrue(viewModel.uiState.value.showDiscardConfirmation)
            viewModel.dismissDiscard()
            assertEquals(draft, viewModel.uiState.value.draft)
            assertFalse(viewModel.uiState.value.closeRequested)
        }

    private fun model(store: SettingsStore, handle: SavedStateHandle) =
        CompanionSettingsViewModel("alice", store, handle).also(models::add)
}
