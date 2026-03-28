package com.example.myapplication.testutil

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiModelCatalogRepository
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.ai.AiTranslationService
import com.example.myapplication.data.repository.ai.DefaultAiGateway
import com.example.myapplication.data.repository.ai.DefaultAiModelCatalogRepository
import com.example.myapplication.data.repository.ai.DefaultAiPromptExtrasService
import com.example.myapplication.data.repository.ai.DefaultAiSettingsEditor
import com.example.myapplication.data.repository.ai.DefaultAiSettingsRepository
import com.example.myapplication.data.repository.ai.DefaultAiTranslationService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.MessageAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

data class TestAiServices(
    val settingsStore: FakeSettingsStore,
    val settingsRepository: AiSettingsRepository,
    val settingsEditor: AiSettingsEditor,
    val modelCatalogRepository: AiModelCatalogRepository,
    val aiGateway: AiGateway,
    val aiPromptExtrasService: AiPromptExtrasService,
    val aiTranslationService: AiTranslationService,
)

fun createTestAiServices(
    settings: AppSettings,
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    apiServiceProvider: ((String, String) -> OpenAiCompatibleApi)? = null,
    streamClientProvider: ((String, String) -> OkHttpClient)? = null,
    imagePayloadResolver: suspend (MessageAttachment) -> String = { error("不应解析图片") },
    filePromptResolver: suspend (MessageAttachment) -> String = { error("不应解析文件") },
): TestAiServices {
    val settingsStore = FakeSettingsStore(settings)
    val apiServiceFactory = ApiServiceFactory()
    val resolvedApiServiceProvider = apiServiceProvider ?: { baseUrl, apiKey ->
        apiServiceFactory.create(
            baseUrl = baseUrl,
            apiKey = apiKey,
        )
    }
    val resolvedStreamClientProvider = streamClientProvider ?: { _, _ ->
        OkHttpClient.Builder().build()
    }

    return TestAiServices(
        settingsStore = settingsStore,
        settingsRepository = DefaultAiSettingsRepository(settingsStore),
        settingsEditor = DefaultAiSettingsEditor(settingsStore, apiServiceFactory),
        modelCatalogRepository = DefaultAiModelCatalogRepository(
            apiServiceFactory = apiServiceFactory,
            apiServiceProvider = resolvedApiServiceProvider,
        ),
        aiGateway = DefaultAiGateway(
            settingsStore = settingsStore,
            apiServiceFactory = apiServiceFactory,
            apiServiceProvider = resolvedApiServiceProvider,
            streamClientProvider = resolvedStreamClientProvider,
            imagePayloadResolver = imagePayloadResolver,
            filePromptResolver = filePromptResolver,
            ioDispatcher = dispatcher,
        ),
        aiPromptExtrasService = DefaultAiPromptExtrasService(
            apiServiceFactory = apiServiceFactory,
            apiServiceProvider = resolvedApiServiceProvider,
        ),
        aiTranslationService = DefaultAiTranslationService(
            settingsStore = settingsStore,
            apiServiceFactory = apiServiceFactory,
            apiServiceProvider = resolvedApiServiceProvider,
            streamClientProvider = resolvedStreamClientProvider,
            ioDispatcher = dispatcher,
        ),
    )
}
