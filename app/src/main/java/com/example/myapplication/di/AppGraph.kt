package com.example.myapplication.di

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
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
import com.example.myapplication.data.repository.AudioFileStorage
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.FileAttachmentResolver
import com.example.myapplication.data.repository.ImageAttachmentResolver
import com.example.myapplication.data.repository.LocalImageStore
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.ContextLogStore
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.PresetRepository
import com.example.myapplication.data.repository.context.RoomPresetRepository
import com.example.myapplication.data.repository.context.RoomMemoryRepository
import com.example.myapplication.data.repository.context.RoomWorldBookRepository
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiModelCatalogRepository
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.ai.AiTranslationService
import com.example.myapplication.data.repository.ai.ConversationSummaryPromptService
import com.example.myapplication.data.repository.ai.DefaultAiGateway
import com.example.myapplication.data.repository.ai.DefaultAiModelCatalogRepository
import com.example.myapplication.data.repository.ai.DefaultAiPromptExtrasService
import com.example.myapplication.data.repository.ai.DefaultAiSettingsEditor
import com.example.myapplication.data.repository.ai.DefaultAiSettingsRepository
import com.example.myapplication.data.repository.ai.DefaultAiTranslationService
import com.example.myapplication.data.repository.ai.MemoryProposalPromptService
import com.example.myapplication.data.repository.ai.MailboxPromptService
import com.example.myapplication.data.repository.ai.PhoneContentPromptService
import com.example.myapplication.data.repository.ai.PromptExtrasCore
import com.example.myapplication.data.repository.ai.RoleplayDiaryPromptService
import com.example.myapplication.data.repository.ai.RoleplaySuggestionPromptService
import com.example.myapplication.data.repository.ai.TitleAndChatSuggestionPromptService
import com.example.myapplication.data.repository.tts.MimoTtsClient
import com.example.myapplication.conversation.VoiceSynthesisCoordinator
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
import com.example.myapplication.data.repository.phone.PhoneSnapshotRepository
import com.example.myapplication.data.repository.phone.RoomPhoneSnapshotRepository
import com.example.myapplication.data.repository.mailbox.MailboxRepository
import com.example.myapplication.data.repository.mailbox.RoomMailboxRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoomRoleplayRepository
import com.example.myapplication.system.update.AndroidAppUpdateController
import com.example.myapplication.system.update.AppUpdateDownloadController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
        ).addMigrations(*ChatDatabase.ALL_MIGRATIONS).build()
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

    val localImageStore: LocalImageStore by lazy {
        LocalImageStore(application)
    }

    val phoneSnapshotRepository: PhoneSnapshotRepository by lazy {
        RoomPhoneSnapshotRepository(database.phoneSnapshotDao())
    }

    val mailboxRepository: MailboxRepository by lazy {
        RoomMailboxRepository(database.mailboxDao())
    }

    val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(
            conversationStore = RoomConversationStore(database),
            phoneSnapshotRepository = phoneSnapshotRepository,
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

    // --- T6.8：Prompt Extras 拆分后，AppGraph 暴露 Core + 6 个子服务 + 兼容性 facade。
    internal val promptExtrasCore: PromptExtrasCore by lazy {
        PromptExtrasCore(apiServiceFactory = apiServiceFactory)
    }

    internal val titleAndChatSuggestionPromptService: TitleAndChatSuggestionPromptService by lazy {
        TitleAndChatSuggestionPromptService(promptExtrasCore)
    }

    internal val conversationSummaryPromptService: ConversationSummaryPromptService by lazy {
        ConversationSummaryPromptService(promptExtrasCore)
    }

    internal val memoryProposalPromptService: MemoryProposalPromptService by lazy {
        MemoryProposalPromptService(promptExtrasCore, contextLogStore)
    }

    internal val roleplaySuggestionPromptService: RoleplaySuggestionPromptService by lazy {
        RoleplaySuggestionPromptService(promptExtrasCore)
    }

    internal val roleplayDiaryPromptService: RoleplayDiaryPromptService by lazy {
        RoleplayDiaryPromptService(promptExtrasCore)
    }

    internal val phoneContentPromptService: PhoneContentPromptService by lazy {
        PhoneContentPromptService(promptExtrasCore)
    }

    internal val mailboxPromptService: MailboxPromptService by lazy {
        MailboxPromptService(promptExtrasCore)
    }

    val aiPromptExtrasService: AiPromptExtrasService by lazy {
        DefaultAiPromptExtrasService(
            titleService = titleAndChatSuggestionPromptService,
            summaryService = conversationSummaryPromptService,
            memoryService = memoryProposalPromptService,
            suggestionService = roleplaySuggestionPromptService,
            diaryService = roleplayDiaryPromptService,
            phoneService = phoneContentPromptService,
        )
    }

    val aiTranslationService: AiTranslationService by lazy {
        DefaultAiTranslationService(
            settingsStore = settingsStore,
            apiServiceFactory = apiServiceFactory,
        )
    }

    val mimoTtsClient: MimoTtsClient by lazy {
        MimoTtsClient()
    }

    val voiceSynthesisCoordinator: VoiceSynthesisCoordinator by lazy {
        VoiceSynthesisCoordinator(
            mimoTtsClient = mimoTtsClient,
            conversationRepository = conversationRepository,
            audioSaver = { b64Data, fileNamePrefix ->
                AudioFileStorage.saveBase64Audio(
                    context = application,
                    b64Data = b64Data,
                    fileNamePrefix = fileNamePrefix,
                )
            },
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

    val presetRepository: PresetRepository by lazy {
        RoomPresetRepository(database.presetDao())
    }

    val roleplayRepository: RoleplayRepository by lazy {
        RoomRoleplayRepository(
            roleplayDao = database.roleplayDao(),
            conversationRepository = conversationRepository,
            imageFileCleaner = localImageStore::deleteIfLocalAsync,
            mailboxCleanup = mailboxRepository::deleteScenarioData,
        )
    }

    val promptContextAssembler: PromptContextAssembler by lazy {
        DefaultPromptContextAssembler(
            worldBookRepository = worldBookRepository,
            worldBookMatcher = WorldBookMatcher(),
            memoryRepository = memoryRepository,
            memorySelector = MemorySelector(),
            conversationSummaryRepository = conversationSummaryRepository,
            phoneSnapshotRepository = phoneSnapshotRepository,
            presetRepository = presetRepository,
        )
    }

    val contextLogStore: ContextLogStore by lazy {
        ContextLogStore()
    }

    val appUpdateRepository: AppUpdateRepository by lazy {
        AppUpdateRepository(
            stateStore = appUpdateStore,
        )
    }

    val appUpdateDownloadController: AppUpdateDownloadController by lazy {
        AndroidAppUpdateController(application)
    }

    fun scheduleStartup(block: suspend CoroutineScope.() -> Unit) {
        startupScope.launch(block = block)
    }

    suspend fun runDatabaseTransaction(block: suspend () -> Unit) {
        database.withTransaction {
            block()
        }
    }

    fun launchStartupTasks() {
        scheduleStartup {
            settingsStore.migrateSensitiveData()
        }
        scheduleStartup {
            presetRepository.ensureBuiltInPresets()
        }
        scheduleStartup {
            settingsStore.settingsFlow
                .map { it.contextLogEnabled to it.contextLogCapacity }
                .distinctUntilChanged()
                .collect { (enabled, capacity) ->
                    contextLogStore.setEnabled(enabled)
                    contextLogStore.setCapacity(capacity)
                }
        }
    }

    private companion object {
        const val CHAT_DATABASE_NAME = "chat.db"
    }
}
