package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.conversation.AssistantRoundTripOutcome
import com.example.myapplication.conversation.AssistantRoundTripRequest
import com.example.myapplication.conversation.AssistantRoundTripResult
import com.example.myapplication.conversation.ConversationAssistantRoundTripRunner
import com.example.myapplication.conversation.RoleplayContextStatusCoordinator
import com.example.myapplication.conversation.ConversationMemoryExtractionCoordinator
import com.example.myapplication.conversation.ConversationTransferCoordinator
import com.example.myapplication.conversation.ConversationMessageTransforms
import com.example.myapplication.conversation.ConversationSummaryCoordinator
import com.example.myapplication.conversation.RoundTripInitialPersistence
import com.example.myapplication.conversation.RoleplaySuggestionCoordinator
import com.example.myapplication.conversation.StreamedAssistantPayload
import com.example.myapplication.conversation.persistInitialRoundTripState
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.ai.tooling.MemoryWriteService
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.PendingMemoryProposalRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoleplaySessionStartResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.imageMessagePart
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.model.toPlainText
import com.example.myapplication.roleplay.RoleplayMessageUiMapper
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayOutputParser
import com.example.myapplication.roleplay.RoleplayPromptDecorator
import com.example.myapplication.roleplay.RoleplayRoundTripSupport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class RoleplayUiState(
    val settings: AppSettings = AppSettings(),
    val scenarios: List<RoleplayScenario> = emptyList(),
    val currentScenario: RoleplayScenario? = null,
    val currentSession: RoleplaySession? = null,
    val currentAssistant: Assistant? = null,
    val contextStatus: RoleplayContextStatus = RoleplayContextStatus(),
    val scenarioSessionIds: Set<String> = emptySet(),
    val messages: List<RoleplayMessageUiModel> = emptyList(),
    val suggestions: List<RoleplaySuggestionUiModel> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val isGeneratingSuggestions: Boolean = false,
    val isScenarioLoading: Boolean = false,
    val showAssistantMismatchDialog: Boolean = false,
    val previousAssistantName: String = "",
    val currentAssistantName: String = "",
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val latestPromptDebugDump: String = "",
    val streamingContent: String = "",
    val suggestionErrorMessage: String? = null,
    val pendingMemoryProposal: PendingMemoryProposal? = null,
    val recentMemoryProposalHistory: List<MemoryProposalHistoryItem> = emptyList(),
    val currentModel: String = "",
    val currentProviderId: String = "",
    val inputFocusToken: Long = 0L,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RoleplayViewModel(
    private val settingsRepository: AiSettingsRepository,
    private val settingsEditor: AiSettingsEditor,
    private val aiGateway: AiGateway,
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val conversationRepository: ConversationRepository,
    private val roleplayRepository: RoleplayRepository,
    private val promptContextAssembler: PromptContextAssembler,
    private val memoryRepository: MemoryRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val pendingMemoryProposalRepository: PendingMemoryProposalRepository,
    private val memoryWriteService: MemoryWriteService,
    private val outputParser: RoleplayOutputParser = RoleplayOutputParser(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val messageIdProvider: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val currentScenarioId = MutableStateFlow<String?>(null)
    private val currentRawMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _uiState = MutableStateFlow(RoleplayUiState())
    val uiState: StateFlow<RoleplayUiState> = _uiState.asStateFlow()

    private var sendingJob: Job? = null
    private val assistantRoundTripRunner = ConversationAssistantRoundTripRunner(
        conversationRepository = conversationRepository,
        aiGateway = aiGateway,
    )
    private val summaryCoordinator = ConversationSummaryCoordinator(
        aiPromptExtrasService = aiPromptExtrasService,
        conversationSummaryRepository = conversationSummaryRepository,
        nowProvider = nowProvider,
    )
    private val memoryExtractionCoordinator = ConversationMemoryExtractionCoordinator(
        aiPromptExtrasService = aiPromptExtrasService,
        memoryRepository = memoryRepository,
        nowProvider = nowProvider,
    )
    private val suggestionCoordinator = RoleplaySuggestionCoordinator(
        conversationRepository = conversationRepository,
        promptContextAssembler = promptContextAssembler,
        aiPromptExtrasService = aiPromptExtrasService,
        nowProvider = nowProvider,
    )
    private val contextStatusCoordinator = RoleplayContextStatusCoordinator(
        conversationSummaryRepository = conversationSummaryRepository,
        memoryRepository = memoryRepository,
    )
    private val transferCoordinator = ConversationTransferCoordinator(
        conversationRepository = conversationRepository,
    )
    private val suggestionActionSupport = RoleplaySuggestionActionSupport(
        scope = viewModelScope,
        uiState = { _uiState.value },
        updateUiState = { reducer -> _uiState.update(reducer) },
        currentRawMessages = currentRawMessages,
        suggestionCoordinator = suggestionCoordinator,
        outputParser = outputParser,
        recentMessageWindow = SUGGESTION_RECENT_MESSAGE_WINDOW,
    )
    private val contextUpdateSupport = RoleplayContextUpdateSupport(
        scope = viewModelScope,
        uiState = { _uiState.value },
        updateUiState = { reducer -> _uiState.update(reducer) },
        summaryCoordinator = summaryCoordinator,
        memoryExtractionCoordinator = memoryExtractionCoordinator,
        contextStatusCoordinator = contextStatusCoordinator,
        pendingMemoryProposalRepository = pendingMemoryProposalRepository,
        aiPromptExtrasService = aiPromptExtrasService,
    )
    private val scenarioActionSupport = RoleplayScenarioActionSupport(
        scope = viewModelScope,
        uiState = { _uiState.value },
        updateUiState = { reducer -> _uiState.update(reducer) },
        currentScenarioIdFlow = currentScenarioId,
        currentRawMessagesFlow = currentRawMessages,
        roleplayRepository = roleplayRepository,
        conversationRepository = conversationRepository,
        conversationSummaryRepository = conversationSummaryRepository,
        nowProvider = nowProvider,
        refreshContextStatus = contextUpdateSupport::refreshContextStatus,
        clearConversationScopedContext = contextUpdateSupport::clearConversationScopedContext,
    )
    private val roundTripExecutor = RoleplayRoundTripExecutor(
        aiGateway = aiGateway,
        conversationRepository = conversationRepository,
        conversationSummaryRepository = conversationSummaryRepository,
        promptContextAssembler = promptContextAssembler,
        assistantRoundTripRunner = assistantRoundTripRunner,
        outputParser = outputParser,
        nowProvider = nowProvider,
        currentUiState = { _uiState.value },
        updateUiState = { reducer -> _uiState.update(reducer) },
        updateRawMessages = { messages -> currentRawMessages.value = messages },
        launchConversationSummaryGeneration = { conversationId, completedMessages, settings, assistant, scenario ->
            contextUpdateSupport.launchConversationSummaryGeneration(
                conversationId = conversationId,
                completedMessages = completedMessages,
                settings = settings,
                assistant = assistant,
                scenario = scenario,
                summaryTriggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
                summaryRecentMessageWindow = SUMMARY_RECENT_MESSAGE_WINDOW,
                summaryMinCoveredMessageCount = SUMMARY_MIN_COVERED_MESSAGE_COUNT,
                maxSummaryInputLength = MAX_SUMMARY_INPUT_LENGTH,
            )
        },
        launchAutomaticMemoryExtraction = { conversationId, completedMessages, settings, assistant, scenario ->
            contextUpdateSupport.launchAutomaticMemoryExtraction(
                conversationId = conversationId,
                completedMessages = completedMessages,
                settings = settings,
                assistant = assistant,
                scenario = scenario,
                autoMemoryMessageWindow = AUTO_MEMORY_MESSAGE_WINDOW,
                roleplaySceneMemoryMaxItems = ROLEPLAY_SCENE_MEMORY_MAX_ITEMS,
                maxMemoryInputLength = MAX_MEMORY_INPUT_LENGTH,
            )
        },
    )
    private val sendActionSupport = RoleplaySendActionSupport(
        scope = viewModelScope,
        uiState = { _uiState.value },
        updateUiState = { reducer -> _uiState.update(reducer) },
        currentRawMessages = currentRawMessages,
        roleplayRepository = roleplayRepository,
        transferCoordinator = transferCoordinator,
        roundTripExecutor = roundTripExecutor,
        scenarioActionSupport = scenarioActionSupport,
        nowProvider = nowProvider,
        messageIdProvider = messageIdProvider,
        cancelSuggestionGeneration = suggestionActionSupport::cancelSuggestionGeneration,
        onSendingFinished = { sendingJob = null },
    )

    init {
        RoleplayObservationSupport.observeSettings(
            scope = viewModelScope,
            settings = settings,
            uiState = _uiState,
        )
        RoleplayObservationSupport.observeScenarios(
            scope = viewModelScope,
            roleplayRepository = roleplayRepository,
            uiState = _uiState,
            currentScenarioId = currentScenarioId,
        )
        RoleplayObservationSupport.observeSessions(
            scope = viewModelScope,
            roleplayRepository = roleplayRepository,
            uiState = _uiState,
        )
        RoleplayObservationSupport.observeCurrentScenario(
            scope = viewModelScope,
            roleplayRepository = roleplayRepository,
            uiState = _uiState,
            currentScenarioId = currentScenarioId,
        )
        RoleplayObservationSupport.observeCurrentSession(
            scope = viewModelScope,
            roleplayRepository = roleplayRepository,
            uiState = _uiState,
            currentScenarioId = currentScenarioId,
            onSessionObserved = { session ->
                contextUpdateSupport.refreshContextStatus(
                    conversationId = session?.conversationId,
                    isContinuingSession = _uiState.value.contextStatus.isContinuingSession,
                )
            },
        )
        observePendingMemoryProposal()
        observeMemoryProposalHistory()
        RoleplayObservationSupport.observeCurrentMessages(
            scope = viewModelScope,
            roleplayRepository = roleplayRepository,
            currentRawMessages = currentRawMessages,
            currentScenarioId = currentScenarioId,
        )
        RoleplayObservationSupport.observeMappedMessages(
            scope = viewModelScope,
            currentRawMessages = currentRawMessages,
            settings = settings,
            uiState = _uiState,
            outputParser = outputParser,
            nowProvider = nowProvider,
        )
    }

    fun updateInput(value: String) {
        if (_uiState.value.isGeneratingSuggestions) {
            suggestionActionSupport.cancelSuggestionGeneration(resetState = false)
        }
        _uiState.update { current ->
            RoleplayStateSupport.updateInput(current, value)
        }
    }

    fun enterScenario(scenarioId: String) {
        suggestionActionSupport.cancelSuggestionGeneration(resetState = false)
        scenarioActionSupport.enterScenario(scenarioId)
    }

    fun leaveScenario() {
        suggestionActionSupport.cancelSuggestionGeneration(resetState = false)
        scenarioActionSupport.leaveScenario()
    }

    fun dismissAssistantMismatchDialog(
        suppressFuturePrompt: Boolean = false,
    ) {
        _uiState.update { current ->
            RoleplayStateSupport.dismissAssistantMismatchDialog(
                current = current,
                suppressFuturePrompt = suppressFuturePrompt,
            )
        }
        if (suppressFuturePrompt) {
            viewModelScope.launch {
                settingsEditor.saveRoleplayAssistantMismatchDialogPreference(true)
            }
        }
    }

    fun upsertScenario(
        scenario: RoleplayScenario,
        onSuccess: (() -> Unit)? = null,
    ) {
        scenarioActionSupport.upsertScenario(scenario, onSuccess)
    }

    fun deleteScenario(
        scenarioId: String,
        onSuccess: (() -> Unit)? = null,
    ) {
        scenarioActionSupport.deleteScenario(scenarioId, onSuccess)
    }

    fun clearErrorMessage() {
        _uiState.update { current ->
            RoleplayStateSupport.clearErrorMessage(current)
        }
    }

    fun clearNoticeMessage() {
        _uiState.update { current ->
            RoleplayStateSupport.clearNoticeMessage(current)
        }
    }

    fun generateSuggestions() {
        suggestionActionSupport.generateSuggestions()
    }

    fun generateDraftInput() {
        suggestionActionSupport.generateDraftInput()
    }

    fun applySuggestion(text: String) {
        _uiState.update { current ->
            RoleplayStateSupport.applySuggestionDraft(current, text)
        }
    }

    fun retryTurn(sourceMessageId: String) {
        sendActionSupport.retryTurn(sourceMessageId)?.let { job ->
            sendingJob = job
        }
    }

    fun editUserMessage(sourceMessageId: String) {
        scenarioActionSupport.editUserMessage(sourceMessageId)
    }

    fun sendTransferPlay(
        counterparty: String,
        amount: String,
        note: String,
    ) {
        sendActionSupport.sendTransferPlay(counterparty, amount, note)?.let { job ->
            sendingJob = job
        }
    }

    fun confirmTransferReceipt(specialId: String) {
        sendActionSupport.confirmTransferReceipt(specialId)
    }

    fun clearSuggestions() {
        suggestionActionSupport.clearSuggestions()
    }

    fun approvePendingMemoryProposal() {
        val proposalId = _uiState.value.pendingMemoryProposal?.id.orEmpty()
        if (proposalId.isBlank()) {
            return
        }
        viewModelScope.launch {
            val savedEntry = runCatching {
                memoryWriteService.approveProposal(proposalId)
            }.getOrNull()
            _uiState.update { current ->
                if (savedEntry == null) {
                    RoleplayStateSupport.applyErrorMessage(current, "记忆确认失败")
                } else {
                    RoleplayStateSupport.applyPendingMemoryProposalSaved(current, savedEntry.content)
                }
            }
        }
    }

    fun rejectPendingMemoryProposal() {
        val proposalId = _uiState.value.pendingMemoryProposal?.id.orEmpty()
        if (proposalId.isBlank()) {
            return
        }
        viewModelScope.launch {
            memoryWriteService.rejectProposal(proposalId)
            _uiState.update { current ->
                RoleplayStateSupport.applyPendingMemoryProposalRejected(current)
            }
        }
    }

    fun cancelSending() {
        sendingJob?.cancel()
    }

    fun resetCurrentSession() {
        suggestionActionSupport.cancelSuggestionGeneration(resetState = false)
        scenarioActionSupport.resetCurrentSession()
    }

    fun restartCurrentSession() {
        suggestionActionSupport.cancelSuggestionGeneration(resetState = false)
        scenarioActionSupport.restartCurrentSession()
    }

    fun sendMessage() {
        sendActionSupport.sendMessage()?.let { job ->
            sendingJob = job
        }
    }

    private fun observePendingMemoryProposal() {
        viewModelScope.launch {
            _uiState
                .map { it.currentSession?.conversationId.orEmpty() }
                .flatMapLatest { conversationId ->
                    if (conversationId.isBlank()) {
                        flowOf(null)
                    } else {
                        pendingMemoryProposalRepository.observeProposal(conversationId)
                    }
                }
                .collect { proposal ->
                    _uiState.update { current ->
                        RoleplayStateSupport.updatePendingMemoryProposal(current, proposal)
                    }
                }
        }
    }

    private fun observeMemoryProposalHistory() {
        viewModelScope.launch {
            _uiState
                .map { it.currentSession?.conversationId.orEmpty() }
                .flatMapLatest { conversationId ->
                    if (conversationId.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        pendingMemoryProposalRepository.observeHistory(conversationId)
                    }
                }
                .collect { history ->
                    _uiState.update { current ->
                        RoleplayStateSupport.updateMemoryProposalHistory(
                            current = current,
                            history = history,
                        )
                    }
                }
        }
    }


    companion object {
        private const val SUMMARY_TRIGGER_MESSAGE_COUNT = 12
        private const val SUMMARY_MIN_COVERED_MESSAGE_COUNT = 4
        private const val SUMMARY_RECENT_MESSAGE_WINDOW = 8
        private const val SUGGESTION_RECENT_MESSAGE_WINDOW = 10
        private const val MAX_SUMMARY_INPUT_LENGTH = 4_000
        private const val AUTO_MEMORY_MESSAGE_WINDOW = 12
        private const val MAX_MEMORY_INPUT_LENGTH = 3_200
        private const val ROLEPLAY_SCENE_MEMORY_MAX_ITEMS = 12

        fun factory(
            settingsRepository: AiSettingsRepository,
            settingsEditor: AiSettingsEditor,
            aiGateway: AiGateway,
            aiPromptExtrasService: AiPromptExtrasService,
            conversationRepository: ConversationRepository,
            roleplayRepository: RoleplayRepository,
            promptContextAssembler: PromptContextAssembler,
            memoryRepository: MemoryRepository,
            conversationSummaryRepository: ConversationSummaryRepository,
            pendingMemoryProposalRepository: PendingMemoryProposalRepository,
            memoryWriteService: MemoryWriteService,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RoleplayViewModel(
                        settingsRepository = settingsRepository,
                        settingsEditor = settingsEditor,
                        aiGateway = aiGateway,
                        aiPromptExtrasService = aiPromptExtrasService,
                        conversationRepository = conversationRepository,
                        roleplayRepository = roleplayRepository,
                        promptContextAssembler = promptContextAssembler,
                        memoryRepository = memoryRepository,
                        conversationSummaryRepository = conversationSummaryRepository,
                        pendingMemoryProposalRepository = pendingMemoryProposalRepository,
                        memoryWriteService = memoryWriteService,
                    ) as T
                }
            }
        }
    }
}
