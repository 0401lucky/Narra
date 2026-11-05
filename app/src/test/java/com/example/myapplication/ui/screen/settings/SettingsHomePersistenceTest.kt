package com.example.myapplication.ui.screen.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import com.example.myapplication.R
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.testutil.TestOpenAiCompatibleApi
import com.example.myapplication.testutil.createTestAiServices
import com.example.myapplication.ui.screenshot.SettingsHomeRenderTest
import com.example.myapplication.viewmodel.SettingsViewModel
import com.example.myapplication.viewmodel.updateThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHomePersistenceTest : SettingsHomeRenderTest() {
    @Test
    fun explicitSave_successDisablesDuplicatesAndOpensOnlyAfterPersistence() = saveThroughHome(fail = false)

    @Test
    fun explicitSave_failureKeepsDraftShowsMessageAndDoesNotCallSuccess() = saveThroughHome(fail = true)

    private fun saveThroughHome(fail: Boolean) {
        val fixture = fixtureState().savedSettings
        val saved = fixture.copy(providers = fixture.providers.map { it.copy(baseUrl = "https://fixture.invalid/") })
        val api = TestOpenAiCompatibleApi()
        val services = createTestAiServices(settings = saved, apiServiceProvider = { _, _ -> api })
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        var navigations = 0
        var consumed = 0
        val editor = object : AiSettingsEditor by services.settingsEditor {
            override suspend fun saveProviderSettings(providers: List<ProviderSettings>, selectedProviderId: String) {
                writes++
                gate.await()
                if (fail) error("合成设置写入失败")
                services.settingsEditor.saveProviderSettings(providers, selectedProviderId)
            }
        }
        val viewModel = SettingsViewModel(services.settingsRepository, editor, services.modelCatalogRepository)
        drainMainLooper()
        renderHome(content = {
            HomeContent(
                uiState = viewModel.uiState.collectAsState().value,
                onSave = { viewModel.saveSettings { navigations++ } },
                onConsumeMessage = { consumed++; viewModel.consumeMessage() },
                onThemeModeChange = viewModel::updateThemeMode,
            )
        })
        drainMainLooper()
        val title = composeRule.activity.getString(R.string.settings_color_mode)
        openSheet(title, title)
        windowControl(title, isSelectable() and hasText(ThemeMode.DARK.label)).performScrollTo().performTouchInput { click() }
        composeRule.waitForIdle()
        assertTrue(viewModel.uiState.value.hasDraftChanges())
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save)).performTouchInput { click() }
        drainMainLooper()
        composeRule.waitForIdle()
        val pendingAction = composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_chat_label))
        pendingAction.assertIsNotEnabled().performTouchInput { click() }
        composeRule.runOnIdle {
            assertTrue(viewModel.uiState.value.isSaving)
            assertEquals(1, writes)
            assertEquals(0, navigations)
            assertEquals(ThemeMode.SYSTEM, viewModel.storedSettings.value.themeMode)
            gate.complete(Unit)
        }
        drainMainLooper()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertFalse(viewModel.uiState.value.isSaving)
            assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
            assertEquals(fail, viewModel.uiState.value.hasDraftChanges())
            assertEquals(if (fail) 0 else 1, navigations)
            assertEquals(1, writes)
            assertEquals(saved.providers, viewModel.uiState.value.providers)
        }
        assertEquals(if (fail) ThemeMode.SYSTEM else ThemeMode.DARK,
            runBlocking { services.settingsStore.settingsFlow.first() }.themeMode)
        if (fail) {
            val message = viewModel.uiState.value.message.orEmpty()
            assertTrue(message.contains("合成设置写入失败"))
            composeRule.onNodeWithText(message).assertIsDisplayed()
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save)).assertIsEnabled()
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        drainMainLooper()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, consumed)
            assertEquals(null, viewModel.uiState.value.message)
        }
    }
}
