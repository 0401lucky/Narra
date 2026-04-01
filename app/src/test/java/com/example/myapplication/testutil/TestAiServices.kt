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
import com.example.myapplication.data.repository.ai.tooling.DefaultMemoryWriteService
import com.example.myapplication.data.repository.ai.tooling.GetConversationSummaryTool
import com.example.myapplication.data.repository.ai.tooling.MemoryWriteService
import com.example.myapplication.data.repository.ai.tooling.ReadMemoryTool
import com.example.myapplication.data.repository.ai.tooling.SaveMemoryTool
import com.example.myapplication.data.repository.ai.tooling.SearchWebTool
import com.example.myapplication.data.repository.ai.tooling.SearchWorldBookTool
import com.example.myapplication.data.repository.ai.tooling.ToolAvailabilityResolver
import com.example.myapplication.data.repository.ai.tooling.ToolRegistry
import com.example.myapplication.data.repository.context.EmptyConversationSummaryRepository
import com.example.myapplication.data.repository.context.EmptyMemoryRepository
import com.example.myapplication.data.repository.context.EmptyWorldBookRepository
import com.example.myapplication.data.repository.context.InMemoryPendingMemoryProposalRepository
import com.example.myapplication.data.repository.context.PendingMemoryProposalRepository
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.SearchSourceConfig
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
    memoryRepository: MemoryRepository = EmptyMemoryRepository,
    worldBookRepository: WorldBookRepository = EmptyWorldBookRepository,
    conversationSummaryRepository: ConversationSummaryRepository = EmptyConversationSummaryRepository,
    pendingMemoryProposalRepository: PendingMemoryProposalRepository = InMemoryPendingMemoryProposalRepository(),
    searchRepository: SearchRepository = object : SearchRepository {
        override suspend fun search(
            source: SearchSourceConfig,
            query: String,
            resultCount: Int,
        ) = error("不应执行搜索")
    },
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
    val toolRegistry = ToolRegistry(
        listOf(
            ReadMemoryTool(),
            GetConversationSummaryTool(),
            SearchWorldBookTool(),
            SaveMemoryTool(),
            SearchWebTool(),
        ),
    )
    val toolAvailabilityResolver = ToolAvailabilityResolver(
        searchRepository = searchRepository,
        memoryRepository = memoryRepository,
        worldBookRepository = worldBookRepository,
        conversationSummaryRepository = conversationSummaryRepository,
    )
    val memoryWriteService: MemoryWriteService = DefaultMemoryWriteService(
        settingsStore = settingsStore,
        memoryRepository = memoryRepository,
        pendingMemoryProposalRepository = pendingMemoryProposalRepository,
        aiPromptExtrasService = DefaultAiPromptExtrasService(
            apiServiceFactory = apiServiceFactory,
            apiServiceProvider = resolvedApiServiceProvider,
        ),
    )

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
            searchRepository = searchRepository,
            memoryRepository = memoryRepository,
            worldBookRepository = worldBookRepository,
            conversationSummaryRepository = conversationSummaryRepository,
            memoryWriteService = memoryWriteService,
            toolAvailabilityResolver = toolAvailabilityResolver,
            toolRegistry = toolRegistry,
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
