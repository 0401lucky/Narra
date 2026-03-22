package com.example.myapplication.viewmodel

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.data.repository.AiRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.TranslationHistoryEntry
import com.example.myapplication.model.TranslationSourceType
import com.example.myapplication.testutil.FakeSettingsStore
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
                object : OpenAiCompatibleApi {
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

    private fun createViewModel(
        settings: AppSettings,
        apiServiceProvider: ((String, String) -> OpenAiCompatibleApi)? = null,
    ): TranslationViewModel {
        val repository = AiRepository(
            settingsStore = FakeSettingsStore(settings),
            apiServiceFactory = ApiServiceFactory(),
            apiServiceProvider = apiServiceProvider ?: { _, _ ->
                error("未提供翻译接口桩")
            },
            streamClientProvider = { _, _ ->
                OkHttpClient.Builder().build()
            },
            ioDispatcher = mainDispatcherRule.dispatcher,
        )
        return TranslationViewModel(
            repository = repository,
            nowProvider = { 123L },
        )
    }
}
