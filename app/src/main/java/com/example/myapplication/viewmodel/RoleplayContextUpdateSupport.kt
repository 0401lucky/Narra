package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.ConversationMemoryExtractionCoordinator
import com.example.myapplication.conversation.ConversationSummaryCoordinator
import com.example.myapplication.conversation.RoleplayContextStatusCoordinator
import com.example.myapplication.conversation.SummaryGenerationConfig
import com.example.myapplication.conversation.SummaryUpdateResult
import com.example.myapplication.data.repository.context.PendingMemoryProposalRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.roleplay.RoleplayConversationSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import com.example.myapplication.system.logging.AppLogger
import com.example.myapplication.roleplay.RoleplayTranscriptFormatter
import com.example.myapplication.roleplay.RoleplayMessageFormatSupport

internal class RoleplayContextUpdateSupport(
    private val scope: CoroutineScope,
    private val uiState: () -> RoleplayUiState,
    private val updateUiState: ((RoleplayUiState) -> RoleplayUiState) -> Unit,
    private val summaryCoordinator: ConversationSummaryCoordinator,
    private val memoryExtractionCoordinator: ConversationMemoryExtractionCoordinator,
    private val contextStatusCoordinator: RoleplayContextStatusCoordinator,
    private val pendingMemoryProposalRepository: PendingMemoryProposalRepository,
    private val aiPromptExtrasService: com.example.myapplication.data.repository.ai.AiPromptExtrasService,
    private val refreshContextGovernance: suspend (String, List<ChatMessage>, AppSettings, Assistant?, RoleplayScenario) -> Unit,
    private val loadPersistedMessages: suspend (String) -> List<ChatMessage>? = { null },
) {
    /** 确保后台 API 请求（摘要/记忆提取）串行执行，避免并发触发 429。 */
    private val backgroundApiMutex = Mutex()

    fun launchConversationSummaryGeneration(
        conversationId: String,
        completedMessages: List<ChatMessage>,
        settings: AppSettings,
        assistant: Assistant?,
        scenario: RoleplayScenario,
        summaryTriggerMessageCount: Int,
        summaryRecentMessageWindow: Int,
        summaryMinCoveredMessageCount: Int,
        maxSummaryInputLength: Int,
        forceRefresh: Boolean = false,
    ) {
        scope.launch {
            backgroundApiMutex.withLock {
                try {
                    updateConversationSummary(
                        conversationId = conversationId,
                        completedMessages = completedMessages,
                        settings = settings,
                        assistant = assistant,
                        scenario = scenario,
                        summaryTriggerMessageCount = summaryTriggerMessageCount,
                        summaryRecentMessageWindow = summaryRecentMessageWindow,
                        summaryMinCoveredMessageCount = summaryMinCoveredMessageCount,
                        maxSummaryInputLength = maxSummaryInputLength,
                        forceRefresh = forceRefresh,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    AppLogger.w("RoleplaySummary", "自动摘要整理失败", failure)
                }
            }
        }
    }

    suspend fun updateConversationSummary(
        conversationId: String,
        completedMessages: List<ChatMessage>,
        settings: AppSettings,
        assistant: Assistant?,
        scenario: RoleplayScenario,
        summaryTriggerMessageCount: Int,
        summaryRecentMessageWindow: Int,
        summaryMinCoveredMessageCount: Int,
        maxSummaryInputLength: Int,
        forceRefresh: Boolean = false,
    ): SummaryUpdateResult {
        if (!forceRefresh && completedMessages.count { it.status == MessageStatus.COMPLETED } <= summaryTriggerMessageCount) {
            return SummaryUpdateResult()
        }
        val persistedMessages = loadPersistedMessages(conversationId) ?: completedMessages
        val memoryContext = memoryExtractionCoordinator.summaryRuntimeContext(
            conversationId,
            assistant?.id.orEmpty()
        )
        if (memoryExtractionCoordinator.hasArchiveContext && memoryContext == null) return SummaryUpdateResult()
        val allowedSourceIds = if (memoryContext == null) persistedMessages.map { it.id }
            .toSet() else persistedMessages.filter {
            memoryExtractionCoordinator.summarySourceAllowed(memoryContext, it)
        }.map { it.id }.toSet()
        val result = summaryCoordinator.updateConversationSummary(
            conversationId = conversationId,
            assistantId = assistant?.id.orEmpty(),
            completedMessages = persistedMessages,
            settings = settings,
            config = SummaryGenerationConfig(
                triggerMessageCount = summaryTriggerMessageCount,
                recentMessageWindow = summaryRecentMessageWindow,
                minCoveredMessageCount = summaryMinCoveredMessageCount,
            ),
            forceRefresh = forceRefresh,
            expectedMemoryContext = memoryContext,
            isSourceAllowed = { it.id in allowedSourceIds },
            buildSummaryInput = { messages ->
                val (userName, characterName) = RoleplayConversationSupport.resolveRoleplayNames(
                    scenario,
                    assistant,
                    settings
                )
                RoleplayTranscriptFormatter.formatMessageSegments(
                    messages, userName, characterName, scenario.enableNarration,
                    RoleplayMessageFormatSupport.resolveScenarioInteractionMode(scenario)
                ).joinToString("\n")
            },
            generateSummary = { conversationText, baseUrl, apiKey, modelId, apiProtocol ->
                aiPromptExtrasService.generateRoleplayConversationSummaryResult(
                    conversationText = conversationText,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    modelId = modelId,
                    apiProtocol = apiProtocol,
                    provider = settings.resolveFunctionProvider(com.example.myapplication.model.ProviderFunction.TITLE_SUMMARY),
                )
            },
        )
        if (result.updated) {
            refreshContextGovernance(
                conversationId,
                completedMessages,
                settings,
                assistant,
                scenario,
            )
        }
        return result
    }

    fun launchAutomaticMemoryExtraction(
        conversationId: String,
        completedMessages: List<ChatMessage>,
        settings: AppSettings,
        assistant: Assistant?,
        scenario: RoleplayScenario,
        autoMemoryMessageWindow: Int,
        roleplaySceneMemoryMaxItems: Int,
        maxMemoryInputLength: Int,
    ) {
        val targetAssistant = assistant ?: return
        scope.launch {
            backgroundApiMutex.withLock {
                try {
                    memoryExtractionCoordinator.updateRoleplayMemories(
                        conversationId = conversationId,
                        assistant = targetAssistant,
                        completedMessages = completedMessages,
                        settings = settings,
                        recentMessageWindow = autoMemoryMessageWindow,
                        sceneMemoryMaxItems = roleplaySceneMemoryMaxItems,
                        buildMemoryInput = { messages ->
                            RoleplayConversationSupport.buildTranscriptInput(
                                messages = messages,
                                scenario = scenario,
                                assistant = assistant,
                                settings = settings,
                                maxLength = maxMemoryInputLength,
                            )
                        },
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    AppLogger.w("RoleplayMemory", "自动记忆整理失败", failure)
                }
            }
        }
    }

    suspend fun updateRoleplayMemories(
        conversationId: String,
        completedMessages: List<ChatMessage>,
        settings: AppSettings,
        assistant: Assistant?,
        scenario: RoleplayScenario,
        recentMessageWindow: Int,
        sceneMemoryMaxItems: Int,
        maxMemoryInputLength: Int,
    ): Boolean {
        val targetAssistant = assistant ?: return false
        return backgroundApiMutex.withLock {
            val updated =
                if (memoryExtractionCoordinator.usesDurableMemory()) memoryExtractionCoordinator.updatePendingRoleplayMemories(
                    conversationId,
                    targetAssistant.id
                )
                else memoryExtractionCoordinator.updateRoleplayMemories(
                    conversationId = conversationId,
                    assistant = targetAssistant,
                    completedMessages = completedMessages,
                    settings = settings,
                    recentMessageWindow = recentMessageWindow,
                    sceneMemoryMaxItems = sceneMemoryMaxItems,
                    buildMemoryInput = { messages ->
                        RoleplayConversationSupport.buildTranscriptInput(
                            messages = messages,
                            scenario = scenario,
                            assistant = assistant,
                            settings = settings,
                            maxLength = maxMemoryInputLength,
                        )
                    },
                )
            if (updated) {
                refreshContextStatus(
                    conversationId = conversationId,
                    isContinuingSession = uiState().contextStatus.isContinuingSession,
                )
                refreshContextGovernance(
                    conversationId,
                    completedMessages,
                    settings,
                    assistant,
                    scenario,
                )
            }
            updated
        }
    }

    suspend fun clearConversationScopedContext(conversationId: String) {
        contextStatusCoordinator.clearConversationScopedContext(conversationId)
        pendingMemoryProposalRepository.clearConversation(conversationId)
    }

    fun refreshContextStatus(
        conversationId: String?,
        isContinuingSession: Boolean,
        worldBookHitCount: Int = uiState().contextStatus.worldBookHitCount,
        memoryInjectionCount: Int = uiState().contextStatus.memoryInjectionCount,
    ) {
        scope.launch {
            val contextStatus = contextStatusCoordinator.buildContextStatus(
                conversationId = conversationId,
                isContinuingSession = isContinuingSession,
                worldBookHitCount = worldBookHitCount,
                memoryInjectionCount = memoryInjectionCount,
                memoryContext = conversationId?.let { id ->
                    memoryExtractionCoordinator.summaryRuntimeContext(
                        id,
                        uiState().currentAssistant?.id.orEmpty()
                    )
                },
            )
            updateUiState { current ->
                RoleplayStateSupport.applyContextStatus(current, contextStatus)
            }
        }
    }
}
