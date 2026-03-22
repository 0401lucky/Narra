package com.example.myapplication.viewmodel

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.AiRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.testutil.FakeSettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initializesDraftProvidersFromPersistedSettings() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val provider = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "model-a",
            availableModels = listOf("model-a"),
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(listOf(provider), uiState.providers)
        assertEquals(provider.id, uiState.selectedProviderId)
        assertEquals(provider.name, uiState.selectedProviderName)
        assertEquals(provider.baseUrl, uiState.baseUrl)
        assertEquals(provider.selectedModel, uiState.selectedModel)
    }

    @Test
    fun saveSelectedProvider_persistsTargetProviderAsChatActiveProvider() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val providerA = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "model-a",
            availableModels = listOf("model-a"),
        )
        val providerB = ProviderSettings(
            id = "provider-b",
            name = "Provider B",
            baseUrl = "https://b.example.com/v1/",
            apiKey = "key-b",
            selectedModel = "model-b",
            availableModels = listOf("model-b"),
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(providerA, providerB),
                selectedProviderId = providerA.id,
            ),
        )

        advanceUntilIdle()
        viewModel.saveSelectedProvider(providerB.id)
        advanceUntilIdle()

        val stored = viewModel.storedSettings.value
        assertEquals(providerB.id, stored.selectedProviderId)
        assertEquals(providerB.baseUrl, stored.baseUrl)
        assertEquals(providerB.selectedModel, stored.selectedModel)
    }

    @Test
    fun saveSelectedProvider_rejectsDisabledProvider() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val providerA = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "model-a",
            availableModels = listOf("model-a"),
        )
        val providerB = ProviderSettings(
            id = "provider-b",
            name = "Provider B",
            baseUrl = "https://b.example.com/v1/",
            apiKey = "key-b",
            selectedModel = "model-b",
            availableModels = listOf("model-b"),
            enabled = false,
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(providerA, providerB),
                selectedProviderId = providerA.id,
            ),
        )

        advanceUntilIdle()
        viewModel.saveSelectedProvider(providerB.id)
        advanceUntilIdle()

        val stored = viewModel.storedSettings.value
        assertEquals(providerA.id, stored.selectedProviderId)
        assertEquals("该提供商已停用，请先启用后再切换", viewModel.uiState.value.message)
    }

    @Test
    fun saveSelectedModelForProvider_updatesModelAndSwitchesActiveProvider() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val providerA = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "model-a",
            availableModels = listOf("model-a"),
        )
        val providerB = ProviderSettings(
            id = "provider-b",
            name = "Provider B",
            baseUrl = "https://b.example.com/v1/",
            apiKey = "key-b",
            selectedModel = "old-model-b",
            availableModels = listOf("old-model-b"),
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(providerA, providerB),
                selectedProviderId = providerA.id,
            ),
        )

        advanceUntilIdle()
        viewModel.saveSelectedModelForProvider(
            providerId = providerB.id,
            selectedModel = "new-model-b",
        )
        advanceUntilIdle()

        val stored = viewModel.storedSettings.value
        val updatedProviderB = stored.resolvedProviders().first { it.id == providerB.id }

        assertEquals(providerB.id, stored.selectedProviderId)
        assertEquals("new-model-b", stored.selectedModel)
        assertEquals("new-model-b", updatedProviderB.selectedModel)
        assertTrue(updatedProviderB.availableModels.contains("new-model-b"))
        assertEquals(providerA.selectedModel, stored.resolvedProviders().first { it.id == providerA.id }.selectedModel)
    }

    @Test
    fun updateProviderModelAbilities_updatesDraftModelInfo() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val provider = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "gpt-4o",
            availableModels = listOf("gpt-4o"),
            models = listOf(
                ModelInfo(
                    modelId = "gpt-4o",
                    abilities = setOf(ModelAbility.VISION, ModelAbility.TOOL),
                ),
            ),
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        advanceUntilIdle()
        viewModel.updateProviderModelAbilities(
            providerId = provider.id,
            modelId = "gpt-4o",
            abilities = setOf(ModelAbility.REASONING),
        )

        val updatedProvider = viewModel.uiState.value.providers.first()
        assertEquals(setOf(ModelAbility.REASONING), updatedProvider.models?.first()?.abilities)
        assertTrue(updatedProvider.models?.first()?.abilitiesCustomized == true)
    }

    @Test
    fun saveSettings_persistsDisplayPreferences() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val provider = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "model-a",
            availableModels = listOf("model-a"),
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        advanceUntilIdle()
        viewModel.updateThemeMode(ThemeMode.DARK)
        viewModel.updateMessageTextScale(1.2f)
        viewModel.updateReasoningExpandedByDefault(false)
        viewModel.updateShowThinkingContent(false)
        viewModel.updateAutoCollapseThinking(false)
        viewModel.updateAutoPreviewImages(false)
        viewModel.updateCodeBlockAutoWrap(true)
        viewModel.updateCodeBlockAutoCollapse(true)
        viewModel.saveSettings {}
        advanceUntilIdle()

        val stored = viewModel.storedSettings.value
        assertEquals(ThemeMode.DARK, stored.themeMode)
        assertEquals(1.2f, stored.messageTextScale)
        assertTrue(!stored.reasoningExpandedByDefault)
        assertTrue(!stored.showThinkingContent)
        assertTrue(!stored.autoCollapseThinking)
        assertTrue(!stored.autoPreviewImages)
        assertTrue(stored.codeBlockAutoWrap)
        assertTrue(stored.codeBlockAutoCollapse)
    }

    @Test
    fun saveSettings_persistsScreenTranslationPreferences() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val provider = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "model-a",
            availableModels = listOf("model-a"),
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        advanceUntilIdle()
        viewModel.updateScreenTranslationServiceEnabled(true)
        viewModel.updateScreenTranslationOverlayEnabled(false)
        viewModel.updateScreenTranslationSelectedTextEnabled(false)
        viewModel.updateScreenTranslationShowSourceText(false)
        viewModel.updateScreenTranslationTargetLanguage("日语")
        viewModel.updateScreenTranslationVendorGuideDismissed(true)
        viewModel.updateScreenTranslationOverlayOffset(0.2f, 0.7f)
        viewModel.saveSettings {}
        advanceUntilIdle()

        assertEquals(
            ScreenTranslationSettings(
                serviceEnabled = true,
                overlayEnabled = false,
                overlayOffsetX = 0.2f,
                overlayOffsetY = 0.7f,
                targetLanguage = "日语",
                selectedTextEnabled = false,
                showSourceText = false,
                vendorGuideDismissed = true,
            ),
            viewModel.storedSettings.value.screenTranslationSettings,
        )
    }

    @Test
    fun removeAssistant_deletesCustomAssistantAndFallsBackToDefaultSelection() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val customAssistant = Assistant(
            id = "assistant-custom",
            name = "自定义助手",
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                assistants = listOf(customAssistant),
                selectedAssistantId = customAssistant.id,
            ),
        )

        advanceUntilIdle()
        viewModel.removeAssistant(customAssistant.id)
        advanceUntilIdle()

        val stored = viewModel.storedSettings.value
        assertTrue(stored.assistants.none { it.id == customAssistant.id })
        assertEquals(DEFAULT_ASSISTANT_ID, stored.selectedAssistantId)
    }

    @Test
    fun removeAssistant_ignoresBuiltinAssistant() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = createViewModel(
            settings = AppSettings(
                selectedAssistantId = DEFAULT_ASSISTANT_ID,
            ),
        )

        advanceUntilIdle()
        viewModel.removeAssistant(DEFAULT_ASSISTANT_ID)
        advanceUntilIdle()

        val stored = viewModel.storedSettings.value
        assertEquals(DEFAULT_ASSISTANT_ID, stored.selectedAssistantId)
        assertTrue(stored.resolvedAssistants().any { it.id == DEFAULT_ASSISTANT_ID })
    }

    private fun createViewModel(settings: AppSettings): SettingsViewModel {
        val repository = AiRepository(
            settingsStore = FakeSettingsStore(settings),
            apiServiceFactory = ApiServiceFactory(),
            streamClientProvider = { _, _ -> OkHttpClient.Builder().build() },
            ioDispatcher = mainDispatcherRule.dispatcher,
        )
        return SettingsViewModel(repository)
    }
}
