package com.example.myapplication.viewmodel

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.TranslationHistoryEntry
import com.example.myapplication.model.TranslationSourceType
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
class TranslationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun updateInputText_updatesDetectedLanguageLabel() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val viewModel = createViewModel(AppSettings())

        advanceUntilIdle()
        viewModel.updateInputText("こんにちは")

        assertEquals("日语", viewModel.uiState.value.detectedSourceLanguageLabel)
    }

    @Test
    fun translate_savesHistoryEntry() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            translationModel = "translate-model",
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            apiServiceProvider = { _, _ ->
                object : com.example.myapplication.testutil.TestOpenAiCompatibleApi() {
                    override suspend fun listModels(): Response<com.example.myapplication.model.ModelsResponse> {
                        error("不应调用模型接口")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        return Response.success(
                            ChatCompletionResponse(
                                choices = listOf(
                                    com.example.myapplication.model.ChatChoiceDto(
                                        message = com.example.myapplication.model.AssistantMessageDto(
                                            content = "Hello",
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }

                    override suspend fun createChatCompletionAt(url: String, request: ChatCompletionRequest): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(url: String, request: com.example.myapplication.model.ResponseApiRequest): Response<com.example.myapplication.model.ResponseApiResponse> {
                        error("不应调用 responses 接口")
                    }

                    override suspend fun generateImage(request: com.example.myapplication.model.ImageGenerationRequest): Response<com.example.myapplication.model.ImageGenerationResponse> {
                        error("不应调用生图接口")
                    }
                }
            },
        )

        advanceUntilIdle()
        viewModel.updateInputText("你好")
        viewModel.translate()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Hello", state.translatedText)
        assertTrue(state.history.isNotEmpty())
        val firstEntry: TranslationHistoryEntry = state.history.first()
        assertEquals("你好", firstEntry.sourceText)
        assertEquals("Hello", firstEntry.translatedText)
        assertEquals("translate-model", firstEntry.modelName)
        assertEquals(TranslationSourceType.MANUAL, firstEntry.sourceType)
    }

    @Test
    fun useHistoryItem_restoresTranslationState() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val historyEntry = TranslationHistoryEntry(
            id = "history-1",
            sourceText = "Bonjour",
            translatedText = "你好",
            sourceLanguage = "法语",
            targetLanguage = "简体中文",
            modelName = "translate-model",
            createdAt = 100L,
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                translationHistory = listOf(historyEntry),
            ),
        )

        advanceUntilIdle()
        viewModel.useHistoryItem(historyEntry)

        val state = viewModel.uiState.value
        assertEquals("Bonjour", state.inputText)
        assertEquals("你好", state.translatedText)
        assertEquals("法语", state.sourceLanguage)
        assertEquals("简体中文", state.targetLanguage)
    }

    @Test
    fun translate_showsClearMessageWhenTranslationModelDisabled() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            translationModelMode = ProviderFunctionModelMode.DISABLED,
        )
        val viewModel = createViewModel(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        advanceUntilIdle()
        viewModel.updateInputText("你好")
        viewModel.translate()

        assertEquals("请先在模型页开启翻译模型", viewModel.uiState.value.errorMessage)
    }

    private fun createViewModel(
        settings: AppSettings,
        apiServiceProvider: ((String, String) -> OpenAiCompatibleApi)? = null,
    ): TranslationViewModel {
        val services = createTestAiServices(
            settings = settings,
            dispatcher = mainDispatcherRule.dispatcher,
            apiServiceProvider = apiServiceProvider ?: { _, _ ->
                error("未提供翻译接口桩")
            },
        )
        return TranslationViewModel(
            settingsRepository = services.settingsRepository,
            settingsEditor = services.settingsEditor,
            aiTranslationService = services.aiTranslationService,
            nowProvider = { 123L },
        )
    }
}
