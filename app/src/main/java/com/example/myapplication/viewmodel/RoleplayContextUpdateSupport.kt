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
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.roleplay.RoleplayConversationSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
                runCatching {
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
        val result = summaryCoordinator.updateConversationSummary(
            conversationId = conversationId,
            assistantId = assistant?.id.orEmpty(),
            completedMessages = completedMessages,
            settings = settings,
            config = SummaryGenerationConfig(
                triggerMessageCount = summaryTriggerMessageCount,
                recentMessageWindow = summaryRecentMessageWindow,
                minCoveredMessageCount = summaryMinCoveredMessageCount,
            ),
            forceRefresh = forceRefresh,
            buildSummaryInput = { messages ->
                RoleplayConversationSupport.buildTranscriptInput(
                    messages = messages,
                    scenario = scenario,
                    assistant = assistant,
                    settings = settings,
                    maxLength = maxSummaryInputLength,
                )
            },
            generateSummary = { conversationText, baseUrl, apiKey, modelId, apiProtocol ->
                aiPromptExtrasService.generateRoleplayConversationSummary(
                    conversationText = conversationText,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    modelId = modelId,
                    apiProtocol = apiProtocol,
                    provider = settings.activeProvider(),
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
                runCatching {
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
            val updated = memoryExtractionCoordinator.updateRoleplayMemories(
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
            )
            updateUiState { current ->
                RoleplayStateSupport.applyContextStatus(current, contextStatus)
            }
        }
    }
}
