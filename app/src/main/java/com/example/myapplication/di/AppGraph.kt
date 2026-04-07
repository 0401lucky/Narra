package com.example.myapplication.di

import android.app.Application
import androidx.room.Room
import com.example.myapplication.context.DefaultPromptContextAssembler
import com.example.myapplication.context.MemorySelector
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.context.WorldBookMatcher
import com.example.myapplication.data.local.AppSettingsStore
import com.example.myapplication.data.local.AppUpdateStore
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.AppUpdateRepository
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.FileAttachmentResolver
import com.example.myapplication.data.repository.ImageAttachmentResolver
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.RoomMemoryRepository
import com.example.myapplication.data.repository.context.RoomWorldBookRepository
import com.example.myapplication.data.repository.context.WorldBookRepository
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
import com.example.myapplication.data.repository.ai.tooling.GetConversationSummaryTool
import com.example.myapplication.data.repository.ai.tooling.DefaultMemoryWriteService
import com.example.myapplication.data.repository.ai.tooling.MemoryWriteService
import com.example.myapplication.data.repository.ai.tooling.ReadMemoryTool
import com.example.myapplication.data.repository.ai.tooling.SaveMemoryTool
import com.example.myapplication.data.repository.ai.tooling.SearchWebTool
import com.example.myapplication.data.repository.ai.tooling.SearchWorldBookTool
import com.example.myapplication.data.repository.ai.tooling.ToolAvailabilityResolver
import com.example.myapplication.data.repository.ai.tooling.ToolRegistry
import com.example.myapplication.data.repository.context.InMemoryPendingMemoryProposalRepository
import com.example.myapplication.data.repository.context.PendingMemoryProposalRepository
import com.example.myapplication.data.repository.search.DefaultSearchRepository
import com.example.myapplication.data.repository.search.SearchModelExecutor
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoomRoleplayRepository
import com.example.myapplication.system.update.AndroidAppUpdateController
import com.example.myapplication.system.update.AppUpdateDownloadController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppGraph(
    private val application: Application,
) {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: ChatDatabase by lazy {
        Room.databaseBuilder(
            application,
            ChatDatabase::class.java,
            CHAT_DATABASE_NAME,
        ).addMigrations(
            ChatDatabase.MIGRATION_1_2,
            ChatDatabase.MIGRATION_2_3,
            ChatDatabase.MIGRATION_3_4,
            ChatDatabase.MIGRATION_4_5,
            ChatDatabase.MIGRATION_5_6,
            ChatDatabase.MIGRATION_6_7,
            ChatDatabase.MIGRATION_7_8,
            ChatDatabase.MIGRATION_8_9,
            ChatDatabase.MIGRATION_9_10,
            ChatDatabase.MIGRATION_10_11,
            ChatDatabase.MIGRATION_11_12,
            ChatDatabase.MIGRATION_12_13,
            ChatDatabase.MIGRATION_13_14,
        ).build()
    }

    val settingsStore: AppSettingsStore by lazy {
        AppSettingsStore(application)
    }

    val appUpdateStore: AppUpdateStore by lazy {
        AppUpdateStore(application)
    }

    val apiServiceFactory: ApiServiceFactory by lazy {
        ApiServiceFactory()
    }

    val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(
            conversationStore = RoomConversationStore(database),
        )
    }

    val aiSettingsRepository: AiSettingsRepository by lazy {
        DefaultAiSettingsRepository(settingsStore)
    }

    val aiSettingsEditor: AiSettingsEditor by lazy {
        DefaultAiSettingsEditor(
            settingsStore = settingsStore,
            apiServiceFactory = apiServiceFactory,
        )
    }

    val aiModelCatalogRepository: AiModelCatalogRepository by lazy {
        DefaultAiModelCatalogRepository(apiServiceFactory)
    }

    val searchRepository: SearchRepository by lazy {
        DefaultSearchRepository(
            llmSearchExecutor = SearchModelExecutor(
                settingsStore = settingsStore,
                apiServiceFactory = apiServiceFactory,
            ),
        )
    }

    val toolRegistry: ToolRegistry by lazy {
        ToolRegistry(
            listOf(
                ReadMemoryTool(),
                GetConversationSummaryTool(),
                SearchWorldBookTool(),
                SaveMemoryTool(),
                SearchWebTool(),
            ),
        )
    }

    val pendingMemoryProposalRepository: PendingMemoryProposalRepository by lazy {
        InMemoryPendingMemoryProposalRepository()
    }

    val memoryWriteService: MemoryWriteService by lazy {
        DefaultMemoryWriteService(
            settingsStore = settingsStore,
            memoryRepository = memoryRepository,
            pendingMemoryProposalRepository = pendingMemoryProposalRepository,
            aiPromptExtrasService = aiPromptExtrasService,
        )
    }

    val toolAvailabilityResolver: ToolAvailabilityResolver by lazy {
        ToolAvailabilityResolver(
            searchRepository = searchRepository,
            memoryRepository = memoryRepository,
            worldBookRepository = worldBookRepository,
            conversationSummaryRepository = conversationSummaryRepository,
            memoryWriteService = memoryWriteService,
        )
    }

    val aiGateway: AiGateway by lazy {
        DefaultAiGateway(
            settingsStore = settingsStore,
            apiServiceFactory = apiServiceFactory,
            imagePayloadResolver = ImageAttachmentResolver(application)::resolveDataUrl,
            filePromptResolver = FileAttachmentResolver(application)::resolvePromptText,
            searchRepository = searchRepository,
            memoryRepository = memoryRepository,
            worldBookRepository = worldBookRepository,
            conversationSummaryRepository = conversationSummaryRepository,
            memoryWriteService = memoryWriteService,
            toolAvailabilityResolver = toolAvailabilityResolver,
            toolRegistry = toolRegistry,
        )
    }

    val aiPromptExtrasService: AiPromptExtrasService by lazy {
        DefaultAiPromptExtrasService(
            apiServiceFactory = apiServiceFactory,
        )
    }

    val aiTranslationService: AiTranslationService by lazy {
        DefaultAiTranslationService(
            settingsStore = settingsStore,
            apiServiceFactory = apiServiceFactory,
        )
    }

    val worldBookRepository: WorldBookRepository by lazy {
        RoomWorldBookRepository(database.worldBookDao())
    }

    private val roomMemoryRepository: RoomMemoryRepository by lazy {
        RoomMemoryRepository(database.memoryDao())
    }

    val memoryRepository: MemoryRepository by lazy {
        roomMemoryRepository
    }

    val conversationSummaryRepository: ConversationSummaryRepository by lazy {
        roomMemoryRepository
    }

    val roleplayRepository: RoleplayRepository by lazy {
        RoomRoleplayRepository(
            roleplayDao = database.roleplayDao(),
            conversationRepository = conversationRepository,
        )
    }

    val promptContextAssembler: PromptContextAssembler by lazy {
        DefaultPromptContextAssembler(
            worldBookRepository = worldBookRepository,
            worldBookMatcher = WorldBookMatcher(),
            memoryRepository = memoryRepository,
            memorySelector = MemorySelector(),
            conversationSummaryRepository = conversationSummaryRepository,
        )
    }

    val appUpdateRepository: AppUpdateRepository by lazy {
        AppUpdateRepository(
            stateStore = appUpdateStore,
        )
    }

    val appUpdateDownloadController: AppUpdateDownloadController by lazy {
        AndroidAppUpdateController(application)
    }

    fun launchStartupTasks() {
        startupScope.launch {
            settingsStore.migrateSensitiveData()
        }
    }

    private companion object {
        const val CHAT_DATABASE_NAME = "chat.db"
    }
}
