package com.example.myapplication.ui.screen.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.testutil.TestOpenAiCompatibleApi
import com.example.myapplication.testutil.createTestAiServices
import com.example.myapplication.ui.screenshot.ProviderSettingsRenderTest
import com.example.myapplication.viewmodel.SettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPersistenceTest : ProviderSettingsRenderTest() {
    @Test
    fun explicitSave_successPersistsDraftThenCallsSuccessOnce() = saveDraft(fail = false)
    @Test
    fun explicitSave_failureKeepsProviderAndOverrides_withoutSuccessCallback() =
        saveDraft(fail = true)

    private fun saveDraft(fail: Boolean) {
        val saved = fixtureState().savedSettings
        var apiFactories = 0
        val api = TestOpenAiCompatibleApi()
        val services = createTestAiServices(
            settings = saved,
            apiServiceProvider = { _, _ -> apiFactories++; api })
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        var successes = 0
        val editor = object : AiSettingsEditor by services.settingsEditor {
            override suspend fun saveProviderSettings(
                providers: List<ProviderSettings>,
                selectedProviderId: String
            ) {
                writes++
                gate.await()
                if (fail) error("合成提供商写入失败")
                services.settingsEditor.saveProviderSettings(providers, selectedProviderId)
            }
        }
        val viewModel =
            SettingsViewModel(services.settingsRepository, editor, services.modelCatalogRepository)
        drainMainLooper()
        renderProvider(content = {
            DetailContent(
                uiState = viewModel.uiState.collectAsState().value,
                onSave = { viewModel.saveSettings { successes++ } },
                onConsumeMessage = viewModel::consumeMessage,
                onNameChange = viewModel::updateProviderName,
            )
        })
        drainMainLooper()
        reveal(hasText("名称") and hasSetTextAction()).performTextReplacement("合成保存草稿")
        assertTrue(viewModel.uiState.value.hasDraftChanges())
        scrollToEnd()
        pageControl(hasText("保存设置") and hasClickAction()).performTouchInput { click() }
        drainMainLooper()
        pageControl(hasText("保存中...") and hasClickAction()).assertIsNotEnabled()
            .performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(1, writes)
            assertEquals(0, successes)
            assertTrue(viewModel.uiState.value.isSaving)
            gate.complete(Unit)
        }
        drainMainLooper()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertFalse(viewModel.uiState.value.isSaving)
            assertEquals(fail, viewModel.uiState.value.hasDraftChanges())
            assertEquals(if (fail) 0 else 1, successes)
            assertEquals(
                saved.providers.map { if (it.id == ProviderId) it.copy(name = "合成保存草稿") else it },
                viewModel.uiState.value.providers
            )
            assertEquals(saved.selectedProviderId, viewModel.uiState.value.selectedProviderId)
            assertEquals(0, apiFactories)
        }
        val persisted = runBlocking { services.settingsStore.settingsFlow.first() }
        assertEquals(
            if (fail) LongProviderName else "合成保存草稿",
            persisted.providers.first().name
        )
        assertEquals(saved.providers.first().models, persisted.providers.first().models)
        if (fail) {
            val message = viewModel.uiState.value.message.orEmpty()
            assertTrue(message.contains("合成提供商写入失败"))
            composeRule.onNodeWithText(message).assertIsDisplayed()
            pageControl(hasText("保存设置") and hasClickAction()).assertIsEnabled()
        }
    }
}
