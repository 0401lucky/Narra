package com.example.myapplication.viewmodel

import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ConnectionHealth
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.ModelDto
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ModelsResponse
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderTemplate
import com.example.myapplication.model.ProviderType
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ImageGenerationRequest
import com.example.myapplication.model.ImageGenerationResponse
import com.example.myapplication.model.ResponseApiRequest
import com.example.myapplication.model.ResponseApiResponse
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.testutil.createTestAiServices
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

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

        val uiState = viewModel.uiState.value
        assertEquals(providerB.id, uiState.selectedProviderId)
        assertEquals(providerB.id, uiState.currentProvider?.id)

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
    fun addProviderFromTemplate_selectsNewProviderAndKeepsExistingDrafts() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val providerA = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "model-a",
            availableModels = listOf("model-a"),
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(providerA),
                selectedProviderId = providerA.id,
            ),
        )

        advanceUntilIdle()
        val newProviderId = viewModel.addProviderFromTemplate(
            ProviderTemplate(
                name = "OpenAI",
                description = "官方兼容接口",
                defaultBaseUrl = "https://api.openai.com/v1/",
                type = ProviderType.OPENAI,
            ),
        )

        val uiState = viewModel.uiState.value
        assertEquals(2, uiState.providers.size)
        assertEquals(providerA.id, uiState.providers.first().id)
        assertEquals(newProviderId, uiState.selectedProviderId)
        assertEquals("OpenAI", uiState.providers.last().name)

        advanceUntilIdle()
        val stored = viewModel.storedSettings.value
        assertEquals(2, stored.resolvedProviders().size)
        assertEquals(newProviderId, stored.selectedProviderId)
    }

    @Test
    fun saveSettings_afterAddProviderFromTemplate_persistsExistingAndNewProviders() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val providerA = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "model-a",
            availableModels = listOf("model-a"),
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(providerA),
                selectedProviderId = providerA.id,
            ),
        )

        advanceUntilIdle()
        val newProviderId = viewModel.addProviderFromTemplate(
            ProviderTemplate(
                name = "OpenAI",
                description = "官方兼容接口",
                defaultBaseUrl = "https://api.openai.com/v1/",
                type = ProviderType.OPENAI,
            ),
        )
        viewModel.saveSettings {}
        advanceUntilIdle()

        val stored = viewModel.storedSettings.value
        assertEquals(2, stored.resolvedProviders().size)
        assertEquals(providerA.id, stored.resolvedProviders().first().id)
        assertEquals(newProviderId, stored.selectedProviderId)
        assertEquals("OpenAI", stored.resolvedProviders().last().name)
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

        val uiState = viewModel.uiState.value
        assertEquals(providerB.id, uiState.selectedProviderId)
        assertEquals("new-model-b", uiState.currentProvider?.selectedModel)

        val stored = viewModel.storedSettings.value
        val updatedProviderB = stored.resolvedProviders().first { it.id == providerB.id }

        assertEquals(providerB.id, stored.selectedProviderId)
        assertEquals("new-model-b", stored.selectedModel)
        assertEquals("new-model-b", updatedProviderB.selectedModel)
        assertTrue(updatedProviderB.availableModels.contains("new-model-b"))
        assertEquals(providerA.selectedModel, stored.resolvedProviders().first { it.id == providerA.id }.selectedModel)
    }

    @Test
    fun saveSelectedModel_supportsNewlyAddedDraftProviderBeforeFlowSync() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val providerA = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "model-a",
            availableModels = listOf("model-a"),
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(providerA),
                selectedProviderId = providerA.id,
            ),
        )

        advanceUntilIdle()
        val newProviderId = viewModel.addProviderFromTemplate(
            ProviderTemplate(
                name = "OpenAI",
                description = "官方兼容接口",
                defaultBaseUrl = "https://api.openai.com/v1/",
                type = ProviderType.OPENAI,
            ),
        )
        viewModel.saveSelectedModel("gpt-4o-mini")
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(newProviderId, uiState.selectedProviderId)
        assertEquals("gpt-4o-mini", uiState.currentProvider?.selectedModel)

        val stored = viewModel.storedSettings.value
        val newProvider = stored.resolvedProviders().first { it.id == newProviderId }
        assertEquals(newProviderId, stored.selectedProviderId)
        assertEquals("gpt-4o-mini", stored.selectedModel)
        assertEquals("gpt-4o-mini", newProvider.selectedModel)
        assertEquals("model-a", stored.resolvedProviders().first { it.id == providerA.id }.selectedModel)
    }

    @Test
    fun saveSelectedModelForProvider_supportsNewlyAddedDraftProviderBeforeFlowSync() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val providerA = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "model-a",
            availableModels = listOf("model-a"),
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(providerA),
                selectedProviderId = providerA.id,
            ),
        )

        advanceUntilIdle()
        val newProviderId = viewModel.addProviderFromTemplate(
            ProviderTemplate(
                name = "OpenAI",
                description = "官方兼容接口",
                defaultBaseUrl = "https://api.openai.com/v1/",
                type = ProviderType.OPENAI,
            ),
        )
        viewModel.saveSelectedModelForProvider(
            providerId = newProviderId,
            selectedModel = "gpt-4o-mini",
        )
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(newProviderId, uiState.selectedProviderId)
        assertEquals("gpt-4o-mini", uiState.currentProvider?.selectedModel)

        val stored = viewModel.storedSettings.value
        val newProvider = stored.resolvedProviders().first { it.id == newProviderId }
        assertEquals(newProviderId, stored.selectedProviderId)
        assertEquals("gpt-4o-mini", stored.selectedModel)
        assertEquals("gpt-4o-mini", newProvider.selectedModel)
    }

    @Test
    fun saveSelectedModelForProvider_thenSaveSettingsImmediately_keepsLatestModel() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val provider = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "old-model",
            availableModels = listOf("old-model"),
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        advanceUntilIdle()
        viewModel.saveSelectedModelForProvider(
            providerId = provider.id,
            selectedModel = "new-model",
        )
        viewModel.saveSettings {}
        advanceUntilIdle()

        val stored = viewModel.storedSettings.value
        assertEquals(provider.id, stored.selectedProviderId)
        assertEquals("new-model", stored.selectedModel)
        assertEquals("new-model", stored.resolvedProviders().first().selectedModel)
    }

    @Test
    fun updateProviderFunctionModel_doesNotSwitchActiveChatProvider() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val providerA = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "chat-a",
            availableModels = listOf("chat-a"),
        )
        val providerB = ProviderSettings(
            id = "provider-b",
            name = "Provider B",
            baseUrl = "https://b.example.com/v1/",
            apiKey = "key-b",
            selectedModel = "chat-b",
            availableModels = listOf("chat-b", "title-b"),
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(providerA, providerB),
                selectedProviderId = providerA.id,
            ),
        )

        advanceUntilIdle()
        viewModel.updateProviderTitleSummaryModel(providerB.id, "title-b")
        viewModel.saveSettings {}
        advanceUntilIdle()

        val stored = viewModel.storedSettings.value
        val updatedProviderB = stored.resolvedProviders().first { it.id == providerB.id }

        assertEquals(providerA.id, stored.selectedProviderId)
        assertEquals(providerA.id, viewModel.uiState.value.selectedProviderId)
        assertEquals(providerB.id, stored.functionModelProviderIds.providerIdFor(ProviderFunction.TITLE_SUMMARY))
        assertEquals(providerB.id, stored.resolveFunctionProvider(ProviderFunction.TITLE_SUMMARY)?.id)
        assertEquals("title-b", updatedProviderB.titleSummaryModel)
        assertEquals("title-b", stored.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY))
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
    fun saveSettings_persistsRoleplayAiHelperVisibility() = runTest(mainDispatcherRule.dispatcher.scheduler) {
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
        viewModel.updateShowRoleplayAiHelper(false)
        viewModel.saveSettings {}
        advanceUntilIdle()

        assertTrue(!viewModel.storedSettings.value.showRoleplayAiHelper)
    }

    @Test
    fun saveSettings_persistsRoleplayImmersivePreferences() = runTest(mainDispatcherRule.dispatcher.scheduler) {
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
        viewModel.updateRoleplayLongformTargetChars(1200)
        viewModel.updateShowRoleplayPresenceStrip(false)
        viewModel.updateShowRoleplayStatusStrip(true)
        viewModel.saveSettings {}
        advanceUntilIdle()

        val stored = viewModel.storedSettings.value
        assertEquals(1200, stored.roleplayLongformTargetChars)
        assertTrue(!stored.showRoleplayPresenceStrip)
        assertTrue(stored.showRoleplayStatusStrip)
    }

    @Test
    fun saveSettings_persistsOnlineRoleplayNarrationVisibility() = runTest(mainDispatcherRule.dispatcher.scheduler) {
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
        viewModel.updateShowOnlineRoleplayNarration(false)
        viewModel.saveSettings {}
        advanceUntilIdle()

        assertTrue(!viewModel.storedSettings.value.showOnlineRoleplayNarration)
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

    @Test
    fun updateProviderApiKey_clearsExistingConnectionHealthResult() = runTest(mainDispatcherRule.dispatcher.scheduler) {
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
            apiServiceProvider = { _, _ ->
                object : com.example.myapplication.testutil.TestOpenAiCompatibleApi() {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        return Response.success(ModelsResponse(data = listOf(ModelDto(id = "model-a"))))
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<com.example.myapplication.model.ChatCompletionResponse> {
                        error("不应调用 createChatCompletion")
                    }

                    override suspend fun createChatCompletionAt(
                        url: String,
                        request: ChatCompletionRequest,
                    ): Response<com.example.myapplication.model.ChatCompletionResponse> {
                        error("不应调用 createChatCompletionAt")
                    }

                    override suspend fun createResponseAt(
                        url: String,
                        request: ResponseApiRequest,
                    ): Response<ResponseApiResponse> {
                        error("不应调用 createResponseAt")
                    }

                    override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> {
                        error("不应调用 generateImage")
                    }
                }
            },
        )

        advanceUntilIdle()
        viewModel.checkProviderHealth(provider.id)
        advanceUntilIdle()
        assertEquals(ConnectionHealth.HEALTHY, viewModel.uiState.value.connectionHealthMap[provider.id])

        viewModel.updateProviderApiKey(provider.id, "key-b")

        assertEquals(null, viewModel.uiState.value.connectionHealthMap[provider.id])
    }

    @Test
    fun addAssistant_whenPersistenceFails_setsErrorMessage() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val services = createTestAiServices(
            settings = AppSettings(),
            dispatcher = mainDispatcherRule.dispatcher,
        )
        val failingEditor = object : AiSettingsEditor by services.settingsEditor {
            override suspend fun saveAssistants(
                assistants: List<Assistant>,
                selectedAssistantId: String,
            ) {
                error("助手写入失败")
            }
        }
        val viewModel = SettingsViewModel(
            settingsRepository = services.settingsRepository,
            settingsEditor = failingEditor,
            modelCatalogRepository = services.modelCatalogRepository,
        )

        advanceUntilIdle()
        viewModel.addAssistant(Assistant(id = "assistant-1", name = "新助手"))
        advanceUntilIdle()

        assertEquals("助手写入失败", viewModel.uiState.value.message)
        assertTrue(viewModel.storedSettings.value.assistants.isEmpty())
    }

    private fun createViewModel(
        settings: AppSettings,
        settingsEditor: AiSettingsEditor? = null,
        apiServiceProvider: ((String, String) -> OpenAiCompatibleApi)? = null,
    ): SettingsViewModel {
        val services = createTestAiServices(
            settings = settings,
            dispatcher = mainDispatcherRule.dispatcher,
            apiServiceProvider = apiServiceProvider,
        )
        return SettingsViewModel(
            settingsRepository = services.settingsRepository,
            settingsEditor = settingsEditor ?: services.settingsEditor,
            modelCatalogRepository = services.modelCatalogRepository,
        )
    }
}
