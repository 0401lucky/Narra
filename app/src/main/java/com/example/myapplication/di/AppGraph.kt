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
import com.example.myapplication.data.local.ComplianceConsentRepository
import com.example.myapplication.data.local.ComplianceConsentStore
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.AppUpdateRepository
import com.example.myapplication.data.repository.AudioFileStorage
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.FileAttachmentResolver
import com.example.myapplication.data.repository.ImageAttachmentResolver
import com.example.myapplication.data.repository.SavedImageFile
import com.example.myapplication.data.repository.LocalImageStore
import com.example.myapplication.data.repository.VoiceCloneSampleStorage
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.ContextLogStore
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.data.repository.context.RoomMemoryArchiveRepository
import com.example.myapplication.data.repository.context.PresetRepository
import com.example.myapplication.data.repository.context.RoomPresetRepository
import com.example.myapplication.data.repository.context.RoomMemoryRepository
import com.example.myapplication.data.repository.context.RoomWorldBookRepository
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.data.repository.economy.RoleplayEconomyEventBus
import com.example.myapplication.data.repository.economy.RoleplayEconomyRepository
import com.example.myapplication.data.repository.economy.RoomRoleplayEconomyRepository
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiModelCatalogRepository
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.ai.AiTranslationService
import com.example.myapplication.data.repository.ai.ConversationSummaryPromptService
import com.example.myapplication.data.repository.ai.CharacterShakePromptService
import com.example.myapplication.data.repository.ai.CharacterArtPromptService
import com.example.myapplication.data.repository.ai.DefaultAiGateway
import com.example.myapplication.data.repository.ai.DefaultAiModelCatalogRepository
import com.example.myapplication.data.repository.ai.DefaultAiPromptExtrasService
import com.example.myapplication.data.repository.ai.DefaultAiSettingsEditor
import com.example.myapplication.data.repository.ai.DefaultAiSettingsRepository
import com.example.myapplication.data.repository.ai.DefaultAiTranslationService
import com.example.myapplication.data.repository.ai.SnapshotAiTranslationService
import com.example.myapplication.data.repository.ai.ImagePromptPolishService
import com.example.myapplication.data.repository.ai.MemoryProposalPromptService
import com.example.myapplication.data.repository.ai.MailboxPromptService
import com.example.myapplication.data.repository.ai.PhoneContentPromptService
import com.example.myapplication.data.repository.ai.PromptExtrasCore
import com.example.myapplication.data.repository.ai.RoleplayDiaryPromptService
import com.example.myapplication.data.repository.ai.RoleplayShopPromptService
import com.example.myapplication.data.repository.ai.RoleplaySuggestionPromptService
import com.example.myapplication.data.repository.ai.TitleAndChatSuggestionPromptService
import com.example.myapplication.data.repository.asr.AudioRecorder
import com.example.myapplication.data.repository.asr.AudioRecording
import com.example.myapplication.data.repository.asr.RemoteSpeechRecognitionService
import com.example.myapplication.data.repository.asr.SpeechRecognitionService
import com.example.myapplication.data.repository.asr.SystemSpeechRecognitionService
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
import com.example.myapplication.data.repository.ImageFileStorage
import com.example.myapplication.data.repository.moments.MomentsRepository
import com.example.myapplication.data.repository.moments.MomentsGenerationCoordinator
import com.example.myapplication.data.repository.moments.RoomMomentsRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoomRoleplayRepository
import com.example.myapplication.data.repository.roleplay.CompanionVoiceRepository
import com.example.myapplication.data.repository.roleplay.RoomCompanionVoiceRepository
import com.example.myapplication.data.repository.voice.PrivateCompanionVoiceFilePublisher
import com.example.myapplication.data.repository.roleplay.script.RoleplayScriptRepository
import com.example.myapplication.data.repository.roleplay.script.RoomRoleplayScriptRepository
import com.example.myapplication.roleplay.script.AndroidRoleplayScriptEngine
import com.example.myapplication.roleplay.script.RoleplayScriptEngine
import com.example.myapplication.model.CompanionVoiceSession
import com.example.myapplication.roleplay.companion.CompanionVoiceController
import com.example.myapplication.system.audio.AndroidCompanionVoicePlayerFactory
import com.example.myapplication.system.logging.AppLogger
import com.example.myapplication.system.performance.PerformanceTraceContract
import com.example.myapplication.system.performance.traceAsync
import com.example.myapplication.system.update.AndroidAppUpdateController
import com.example.myapplication.system.update.AppUpdateDownloadController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

internal enum class AppStartupTask(
    val taskName: String,
    val traceSectionName: String,
) {
    SENSITIVE_MIGRATION(
        taskName = "SensitiveMigration",
        traceSectionName = PerformanceTraceContract.STARTUP_SENSITIVE_MIGRATION,
    ),
    ENSURE_PRESETS(
        taskName = "EnsurePresets",
        traceSectionName = PerformanceTraceContract.STARTUP_ENSURE_PRESETS,
    ),
    CONTEXT_LOG_SETTINGS(
        taskName = "ContextLogSettings",
        traceSectionName = PerformanceTraceContract.STARTUP_CONTEXT_LOG_SETTINGS,
    ),
}

internal fun interface AppStartupTraceRunner {
    suspend fun trace(
        sectionName: String,
        block: suspend () -> Unit,
    )
}

internal class AppStartupTaskScheduler(
    private val scope: CoroutineScope,
    private val traceRunner: AppStartupTraceRunner = AppStartupTraceRunner { sectionName, block ->
        PerformanceTraceContract.APP_GRAPH.traceAsync {
            sectionName.traceAsync(block)
        }
    },
    private val failureReporter: (AppStartupTask, Throwable) -> Unit = { task, throwable ->
        AppLogger.w(
            tag = APP_STARTUP_LOG_TAG,
            message = "Startup task failed: ${task.taskName}",
            throwable = throwable,
        )
    },
) {
    fun schedule(
        task: AppStartupTask,
        traceTaskLifetime: Boolean = true,
        block: suspend () -> Unit,
    ): Job {
        return scope.launch {
            try {
                if (traceTaskLifetime) {
                    trace(task, block)
                } else {
                    block()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                failureReporter(task, throwable)
            }
        }
    }

    suspend fun trace(
        task: AppStartupTask,
        block: suspend () -> Unit,
    ) {
        traceRunner.trace(task.traceSectionName, block)
    }
}

private const val APP_STARTUP_LOG_TAG = "AppGraph"

class AppGraph @JvmOverloads constructor(
    private val application: Application,
    private val storageOverrides: AppGraphStorageOverrides = AppGraphStorageOverrides(),
) {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startupTaskScheduler = AppStartupTaskScheduler(startupScope)

    val database: ChatDatabase by lazy {
        storageOverrides.database ?: Room.databaseBuilder(
            application,
            ChatDatabase::class.java,
            CHAT_DATABASE_NAME,
        ).addMigrations(*ChatDatabase.ALL_MIGRATIONS).build()
    }

    val settingsStore: AppSettingsStore by lazy {
        storageOverrides.settingsStore ?: AppSettingsStore(application)
    }

    val appUpdateStore: AppUpdateStore by lazy {
        AppUpdateStore(application)
    }

    val complianceConsentStore: ComplianceConsentRepository by lazy {
        storageOverrides.complianceConsentStore ?: ComplianceConsentStore(application)
    }

    val apiServiceFactory: ApiServiceFactory by lazy {
        ApiServiceFactory()
    }

    val localImageStore: LocalImageStore by lazy {
        LocalImageStore(application)
    }

    val voiceCloneSampleCleaner: suspend (String?) -> Boolean = { path ->
        VoiceCloneSampleStorage.deleteIfLocal(application, path)
    }

    val systemSpeechRecognitionService: SpeechRecognitionService by lazy {
        SystemSpeechRecognitionService(application)
    }

    // 声明为具体类型：远程通道额外提供 testConnection，设置页的「测试连接」要用
    val remoteSpeechRecognitionService: RemoteSpeechRecognitionService by lazy {
        RemoteSpeechRecognitionService()
    }

    /** 录音器非线程安全，按使用点各建一个，不共享单例。 */
    val audioRecorderFactory: () -> AudioRecording = { AudioRecorder(application) }

    val phoneSnapshotRepository: PhoneSnapshotRepository by lazy {
        RoomPhoneSnapshotRepository(database.phoneSnapshotDao())
    }

    val mailboxRepository: MailboxRepository by lazy {
        RoomMailboxRepository(database.mailboxDao())
    }

    val momentsRepository: MomentsRepository by lazy {
        RoomMomentsRepository(database.momentDao())
    }

    val roleplayEconomyRepository: RoleplayEconomyRepository by lazy {
        RoomRoleplayEconomyRepository(database)
    }

    val roleplayEconomyEventBus: RoleplayEconomyEventBus by lazy {
        RoleplayEconomyEventBus()
    }

    val momentsGenerationCoordinator: MomentsGenerationCoordinator by lazy {
        MomentsGenerationCoordinator(
            momentsRepository = momentsRepository,
            settingsRepository = aiSettingsRepository,
            aiPromptExtrasService = aiPromptExtrasService,
            aiGateway = aiGateway,
            imageSaver = { b64Data ->
                ImageFileStorage.saveBase64Image(
                    context = application,
                    b64Data = b64Data,
                )
            },
        )
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
                ReadMemoryTool(
                    memoryArchiveRepository = memoryArchiveRepository,
                    canReadMemory = { characterId ->
                        settingsStore.settingsFlow.first().resolvedAssistants()
                            .any { it.id == characterId && it.memoryEnabled }
                    },
                ),
                GetConversationSummaryTool(memoryArchiveRepository = memoryArchiveRepository),
                SearchWorldBookTool(),
                SaveMemoryTool(),
                SearchWebTool(),
            ),
        )
    }

    val pendingMemoryProposalRepository: PendingMemoryProposalRepository by lazy {
        com.example.myapplication.data.repository.context.ArchivePendingMemoryProposalRepository(
            memoryArchiveRepository
        )
    }

    val memoryWriteService: MemoryWriteService by lazy {
        DefaultMemoryWriteService(
            settingsStore = settingsStore,
            memoryRepository = memoryRepository,
            pendingMemoryProposalRepository = pendingMemoryProposalRepository,
            aiPromptExtrasService = aiPromptExtrasService,
            memoryArchiveRepository = memoryArchiveRepository,
        )
    }

    val toolAvailabilityResolver: ToolAvailabilityResolver by lazy {
        ToolAvailabilityResolver(
            searchRepository = searchRepository,
            memoryRepository = memoryRepository,
            worldBookRepository = worldBookRepository,
            conversationSummaryRepository = conversationSummaryRepository,
            memoryWriteService = memoryWriteService,
            memoryArchiveRepository = memoryArchiveRepository,
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
            memoryArchiveRepository = memoryArchiveRepository,
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

    internal val characterShakePromptService: CharacterShakePromptService by lazy {
        CharacterShakePromptService(promptExtrasCore)
    }

    internal val characterArtPromptService: CharacterArtPromptService by lazy {
        CharacterArtPromptService(promptExtrasCore)
    }

    internal val imagePromptPolishService: ImagePromptPolishService by lazy {
        ImagePromptPolishService(promptExtrasCore)
    }

    internal val roleplayShopPromptService: RoleplayShopPromptService by lazy {
        RoleplayShopPromptService(promptExtrasCore)
    }

    val aiPromptExtrasService: AiPromptExtrasService by lazy {
        DefaultAiPromptExtrasService(
            titleService = titleAndChatSuggestionPromptService,
            summaryService = conversationSummaryPromptService,
            memoryService = memoryProposalPromptService,
            suggestionService = roleplaySuggestionPromptService,
            diaryService = roleplayDiaryPromptService,
            phoneService = phoneContentPromptService,
            characterShakeService = characterShakePromptService,
            characterArtService = characterArtPromptService,
            imagePromptPolishService = imagePromptPolishService,
            roleplayShopPromptService = roleplayShopPromptService,
            eventService = com.example.myapplication.data.repository.ai.MemoryEventPromptService(
                promptExtrasCore
            ),
        )
    }

    private val defaultAiTranslationService: DefaultAiTranslationService by lazy {
        DefaultAiTranslationService(
            settingsStore = settingsStore,
            apiServiceFactory = apiServiceFactory,
        )
    }

    val aiTranslationService: AiTranslationService get() = defaultAiTranslationService

    val snapshotAiTranslationService: SnapshotAiTranslationService get() = defaultAiTranslationService

    val mimoTtsClient: MimoTtsClient by lazy {
        MimoTtsClient()
    }

    val companionVoiceRepository: CompanionVoiceRepository by lazy {
        RoomCompanionVoiceRepository(
            database = database,
            publisher = PrivateCompanionVoiceFilePublisher(File(application.filesDir, "companion_voice")),
        )
    }

    private val companionVoicePlayerFactory by lazy {
        AndroidCompanionVoicePlayerFactory(application)
    }

    fun createCompanionVoiceSession(scope: CoroutineScope): CompanionVoiceSession =
        CompanionVoiceController(
            scope = scope,
            repository = companionVoiceRepository,
            translationService = snapshotAiTranslationService,
            playerFactory = companionVoicePlayerFactory,
            getTurn = companionRepository::getTurn,
            synthesize = { apiKey, request, baseUrl ->
                mimoTtsClient.synthesize(apiKey, request, baseUrl)
            },
        )

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
        RoomMemoryRepository(database.memoryDao(), database.companionDao())
    }

    val memoryRepository: MemoryRepository by lazy {
        com.example.myapplication.data.repository.context.ArchiveMemoryRepository(
            database,
            roomMemoryRepository,
            memoryArchiveRepository
        )
    }

    val memoryArchiveRepository: MemoryArchiveRepository by lazy {
        RoomMemoryArchiveRepository(
            database = database,
            isCharacterAvailable = { characterId ->
                settingsStore.settingsFlow.first().resolvedAssistants().any { it.id == characterId }
            },
            observeCharacterAvailability = { characterId ->
                settingsStore.settingsFlow.map { settings ->
                    settings.resolvedAssistants().any { it.id == characterId }
                }.distinctUntilChanged()
            },
            onRecordsChanged = ::scheduleMemoryMaintenance,
            usesLegacyGlobalMemory = { characterId ->
                settingsStore.settingsFlow.first().resolvedAssistants()
                    .firstOrNull { it.id == characterId }?.useGlobalMemory == true
            },
            onRecallReceiptChanged = { contextLogStore.attachMemoryReceipt(it) },
            residentLimits = { settingsStore.settingsFlow.first().memoryResidentLimits },
        )
    }

    val memoryVectorRepository by lazy {
        com.example.myapplication.data.repository.context.RoomMemoryVectorRepository(
            database = database,
            archiveRepository = memoryArchiveRepository,
            embeddingClient = com.example.myapplication.data.repository.embedding.OpenAiMemoryEmbeddingClient(
                apiServiceFactory
            ),
            apiServiceFactory = apiServiceFactory,
            provider = { settingsStore.settingsFlow.first().resolveEmbeddingProvider() },
            isAllowed = { context ->
                complianceConsentStore.isAccepted(com.example.myapplication.compliance.CompliancePolicy.CURRENT_VERSION) &&
                        memoryArchiveRepository.getEngineVersion() >= com.example.myapplication.model.MEMORY_ENGINE_CURRENT &&
                        settingsStore.settingsFlow.first().resolvedAssistants()
                            .any { it.id == context.characterId && it.memoryEnabled }
            },
        )
    }

    val memoryArchiveTransferRepository by lazy {
        com.example.myapplication.data.repository.context.RoomMemoryArchiveTransferRepository(
            database = database,
            archiveRepository = memoryArchiveRepository,
            vectorRepository = memoryVectorRepository,
            isCharacterAvailable = { characterId ->
                settingsStore.settingsFlow.first().resolvedAssistants().any { it.id == characterId }
            },
        )
    }

    private fun scheduleMemoryVectorIndex(context: com.example.myapplication.model.MemoryRuntimeContext) {
        startupScope.launch {
            try {
                memoryVectorRepository.rebuild(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.w(APP_STARTUP_LOG_TAG, "记忆索引暂未完成", failure)
            }
        }
    }

    private val memoryMaintenanceConcurrency = kotlinx.coroutines.sync.Semaphore(2)

    private fun scheduleMemoryMaintenance(context: com.example.myapplication.model.MemoryRuntimeContext) {
        startupScope.launch {
            try {
                val contexts = if (context.conversationId.isNotBlank()) {
                    listOf(context)
                } else {
                    memoryArchiveRepository.listActiveRecallContexts(context.characterId).filter {
                        it.spaceId == context.spaceId && it.personaKey == context.personaKey
                    }
                }
                contexts.distinctBy { it.spaceId to it.personaKey }
                    .forEach {
                        scheduleMemoryVectorIndex(it)
                        scheduleMemoryEventWork(it, resumeOnly = false)
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.w(APP_STARTUP_LOG_TAG, "事件整理暂未完成", failure)
            }
        }
    }

    private fun scheduleMemoryEventWork(
        context: com.example.myapplication.model.MemoryRuntimeContext,
        resumeOnly: Boolean,
    ) {
        startupScope.launch {
            try {
                memoryKnowledgeCoordinator.resume(context)
                if (!resumeOnly) memoryEventCoordinator.processAutomatic(context)
                memoryEventCoordinator.resumePlannedOnStartup(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.w(APP_STARTUP_LOG_TAG, "事件整理暂未完成", failure)
            }
        }
    }

    val memoryEventCoordinator by lazy {
        com.example.myapplication.conversation.MemoryEventCoordinator(
            archive = memoryArchiveRepository,
            ai = aiPromptExtrasService,
            currentSettings = { settingsStore.settingsFlow.first() },
            hasCurrentConsent = {
                complianceConsentStore.isAccepted(com.example.myapplication.compliance.CompliancePolicy.CURRENT_VERSION)
            },
            concurrency = memoryMaintenanceConcurrency,
        )
    }

    val memoryKnowledgeCoordinator by lazy {
        com.example.myapplication.conversation.MemoryKnowledgeCoordinator(
            archive = memoryArchiveRepository,
            currentSettings = { settingsStore.settingsFlow.first() },
            hasCurrentConsent = {
                complianceConsentStore.isAccepted(com.example.myapplication.compliance.CompliancePolicy.CURRENT_VERSION)
            },
        )
    }

    val durableMemoryExtractionCoordinator by lazy {
        com.example.myapplication.conversation.DurableMemoryExtractionCoordinator(
            archive = memoryArchiveRepository,
            ai = aiPromptExtrasService,
            currentSettings = { settingsStore.settingsFlow.first() },
            hasCurrentConsent = { complianceConsentStore.isAccepted(com.example.myapplication.compliance.CompliancePolicy.CURRENT_VERSION) },
            concurrency = memoryMaintenanceConcurrency,
        )
    }

    val conversationSummaryRepository: ConversationSummaryRepository by lazy {
        roomMemoryRepository
    }

    val companionRepository: com.example.myapplication.data.repository.roleplay.CompanionRepository by lazy {
        com.example.myapplication.data.repository.roleplay.RoomCompanionRepository(
            database = database,
            archive = memoryArchiveRepository,
            historyAllowed = companionHistoryRepository::validate,
            requestsAllowed = {
                complianceConsentStore.isAccepted(com.example.myapplication.compliance.CompliancePolicy.CURRENT_VERSION)
            },
        )
    }

    val companionHistoryRepository: com.example.myapplication.data.repository.roleplay.CompanionHistoryRepository by lazy {
        com.example.myapplication.data.repository.roleplay.RoomCompanionHistoryRepository(
            database,
            memoryArchiveRepository
        )
    }

    val companionRequestCoordinator by lazy {
        com.example.myapplication.conversation.CompanionRequestCoordinator(
            companionRepository,
            companionTurnCoordinator,
            companionHistoryRepository,
            roleplayRepository,
            conversationRepository,
            memoryArchiveRepository,
            settingsStore,
            promptContextAssembler,
            aiGateway,
        )
    }

    private val companionImageReferences by lazy {
        com.example.myapplication.data.repository.roleplay.CompanionImageReferences(database, settingsStore)
    }

    val companionResourcesRepository: com.example.myapplication.data.repository.roleplay.CompanionResourcesRepository by lazy {
        com.example.myapplication.data.repository.roleplay.AndroidCompanionResourcesRepository(
            application, localImageStore, settingsStore, companionImageReferences::read
        )
    }

    val companionReadingRepository: com.example.myapplication.data.repository.roleplay.CompanionReadingRepository by lazy {
        com.example.myapplication.data.repository.roleplay.RoomCompanionReadingRepository(database, memoryArchiveRepository, roleplayRepository)
    }

    val companionHistoryManagementRepository: com.example.myapplication.data.repository.roleplay.CompanionHistoryManagementRepository by lazy {
        com.example.myapplication.data.repository.roleplay.RoomCompanionHistoryManagementRepository(
            database, companionReadingRepository, companionRepository,
        )
    }

    val companionTextExporter by lazy {
        com.example.myapplication.data.repository.roleplay.CompanionTextExporter(
            companionHistoryManagementRepository,
            com.example.myapplication.data.repository.roleplay.AndroidCompanionTextDocumentWriter(application.contentResolver),
        )
    }

    val companionAppearanceDocumentStore by lazy {
        com.example.myapplication.data.repository.roleplay.CompanionAppearanceDocumentStore(application.contentResolver)
    }

    val contextTransferRecoveryRepository: com.example.myapplication.data.repository.context.ContextTransferRecoveryRepository by lazy {
        com.example.myapplication.data.repository.context.RoomContextTransferRecoveryRepository(
            database = database,
            settingsStore = settingsStore,
            worldBookRepository = worldBookRepository,
            presetRepository = presetRepository,
            memoryTransferRepository = memoryArchiveTransferRepository,
            files = com.example.myapplication.data.repository.context.PrivateCompanionTransferFileStore(application),
        )
    }

    val companionTurnCoordinator by lazy {
        com.example.myapplication.conversation.CompanionTurnCoordinator(
            repository = companionRepository,
            onCommitted = { receipt ->
                startupScope.launch {
                    val context =
                        companionRepository.getEncounter(receipt.turn.encounterId)?.context
                            ?: return@launch
                    durableMemoryExtractionCoordinator.processAutomatic(
                        context.conversationId,
                        context.characterId
                    )
                }
            },
        )
    }

    val presetRepository: PresetRepository by lazy {
        RoomPresetRepository(database.presetDao())
    }

    val roleplayRepository: RoleplayRepository by lazy {
        RoomRoleplayRepository(
            roleplayDao = database.roleplayDao(),
            conversationRepository = conversationRepository,
            imageFileCleaner = { path -> companionImageReferences.deleteIfUnreferenced(path, localImageStore) },
            mailboxCleanup = mailboxRepository::deleteScenarioData,
            economyCleanup = roleplayEconomyRepository::deleteScenarioData,
            database = database,
            memoryArchiveRepository = memoryArchiveRepository,
            companionRepository = companionRepository,
        )
    }

    val roleplayScriptRepository: RoleplayScriptRepository by lazy {
        RoomRoleplayScriptRepository(database.roleplayScriptDao())
    }

    val roleplayScriptEngine: RoleplayScriptEngine by lazy {
        AndroidRoleplayScriptEngine(application)
    }

    val promptContextAssembler: PromptContextAssembler by lazy {
        DefaultPromptContextAssembler(
            worldBookRepository = worldBookRepository,
            worldBookMatcher = WorldBookMatcher(),
            memoryRepository = memoryRepository,
            memorySelector = MemorySelector(),
            memoryArchiveRepository = memoryArchiveRepository,
            conversationSummaryRepository = conversationSummaryRepository,
            phoneSnapshotRepository = phoneSnapshotRepository,
            presetRepository = presetRepository,
            memoryRecallEngine = com.example.myapplication.context.MemoryRecallEngine(
                memoryArchiveRepository,
                vectorSearch = memoryVectorRepository
            ),
            onRecallContext = ::scheduleMemoryVectorIndex,
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

    suspend fun runDatabaseTransaction(block: suspend () -> Unit) {
        database.withTransaction {
            block()
        }
    }

    suspend fun saveBase64Image(
        b64Data: String,
        fileNamePrefix: String = java.util.UUID.randomUUID().toString(),
    ): SavedImageFile {
        return ImageFileStorage.saveBase64Image(
            context = application,
            b64Data = b64Data,
            fileNamePrefix = fileNamePrefix,
        )
    }

    fun launchStartupTasks() {
        startupTaskScheduler.schedule(AppStartupTask.SENSITIVE_MIGRATION) {
            settingsStore.migrateSensitiveData()
        }
        startupTaskScheduler.schedule(AppStartupTask.ENSURE_PRESETS) {
            presetRepository.ensureBuiltInPresets()
        }
        startupTaskScheduler.schedule(
            task = AppStartupTask.CONTEXT_LOG_SETTINGS,
            traceTaskLifetime = false,
        ) {
            settingsStore.settingsFlow
                .map { it.contextLogEnabled to it.contextLogCapacity }
                .distinctUntilChanged()
                .collect { (enabled, capacity) ->
                    startupTaskScheduler.trace(AppStartupTask.CONTEXT_LOG_SETTINGS) {
                        contextLogStore.setEnabled(enabled)
                        contextLogStore.setCapacity(capacity)
                    }
                }
        }
    }

    fun scheduleConsentDependentTasks() {
        com.example.myapplication.system.moments.MomentsAutoGenerationWorker.schedule(application)
        startupScope.launch {
            try {
                if (memoryArchiveRepository.getEngineVersion() < com.example.myapplication.model.MEMORY_ENGINE_CURRENT ||
                    !complianceConsentStore.isAccepted(com.example.myapplication.compliance.CompliancePolicy.CURRENT_VERSION)
                ) return@launch
                settingsStore.settingsFlow.first().resolvedAssistants().filter { it.memoryEnabled }
                    .forEach { assistant ->
                        memoryArchiveRepository.listSpaces(assistant.id).forEach { space ->
                            memoryArchiveRepository.listRecoverableJobs(space.id).filter {
                                it.purpose in setOf(
                                    com.example.myapplication.model.MemoryJobPurpose.EVENT_SUMMARY,
                                    com.example.myapplication.model.MemoryJobPurpose.EVENT_REBUILD,
                                )
                            }.map { it.context }.distinct()
                                .forEach { scheduleMemoryEventWork(it, resumeOnly = true) }
                        }
                        memoryArchiveRepository.listActiveRecallContexts(assistant.id)
                            .distinctBy { it.spaceId to it.personaKey }
                            .forEach { scheduleMemoryEventWork(it, resumeOnly = false) }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.w(APP_STARTUP_LOG_TAG, "恢复事件整理失败", failure)
            }
        }
        startupScope.launch {
            if (memoryArchiveRepository.getEngineVersion() < com.example.myapplication.model.MEMORY_ENGINE_CURRENT ||
                !complianceConsentStore.isAccepted(com.example.myapplication.compliance.CompliancePolicy.CURRENT_VERSION)
            ) return@launch
            val settings = settingsStore.settingsFlow.first()
            if (settings.resolveEmbeddingProvider() == null) return@launch
            settings.resolvedAssistants().filter { it.memoryEnabled }.forEach { assistant ->
                memoryArchiveRepository.listActiveRecallContexts(assistant.id)
                    .distinctBy { it.spaceId to it.personaKey }.forEach(::scheduleMemoryVectorIndex)
            }
        }
        startupScope.launch {
            try {
                if (!durableMemoryExtractionCoordinator.isEnabled()) return@launch
                val settings = settingsStore.settingsFlow.first()
                if (settings.memoryAutoSummaryEvery <= 0) return@launch
                settings.resolvedAssistants().filter { it.memoryEnabled }.forEach { assistant ->
                    memoryArchiveRepository.listSpaces(assistant.id).forEach { space ->
                        memoryArchiveRepository.listRecoverableJobs(space.id)
                            .filter { it.purpose == com.example.myapplication.model.MemoryJobPurpose.AUTOMATIC_EXTRACTION }
                            .map { it.context }.distinct().forEach { context ->
                                launch {
                                    try {
                                        durableMemoryExtractionCoordinator.resumePlannedOnStartup(
                                            context
                                        )
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        AppLogger.w(
                                            APP_STARTUP_LOG_TAG,
                                            "恢复记忆整理失败",
                                            failure
                                        )
                                    }
                                }
                            }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.w(APP_STARTUP_LOG_TAG, "恢复记忆整理失败", failure)
            }
        }
    }

    private companion object {
        const val CHAT_DATABASE_NAME = "chat.db"
    }
}
