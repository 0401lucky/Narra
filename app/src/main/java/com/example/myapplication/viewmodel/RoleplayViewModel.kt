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
import com.example.myapplication.conversation.ConversationSummaryDebugSupport
import com.example.myapplication.conversation.ContextGovernanceSupport
import com.example.myapplication.conversation.GiftImageGenerationCoordinator
import com.example.myapplication.conversation.RoundTripInitialPersistence
import com.example.myapplication.conversation.RoleplaySuggestionCoordinator
import com.example.myapplication.conversation.RoleplayVideoCallCoordinator
import com.example.myapplication.conversation.StreamedAssistantPayload
import com.example.myapplication.conversation.SummaryUpdateResult
import com.example.myapplication.conversation.persistInitialRoundTripState
import com.example.myapplication.context.ContextPlaceholderResolver
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.SavedImageFile
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.ai.tooling.MemoryWriteService
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.PendingMemoryProposalRepository
import com.example.myapplication.data.repository.phone.PhoneSnapshotRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoleplaySessionStartResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatSpecialPlayDraft
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.RoleplayDiaryDraft
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.VoiceMessageDraft
import com.example.myapplication.model.imageMessagePart
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.model.toPlainText
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.model.shouldInjectDescriptionPrompt
import com.example.myapplication.roleplay.RoleplayMessageUiMapper
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayOutputParser
import com.example.myapplication.roleplay.RoleplayPromptDecorator
import com.example.myapplication.roleplay.RoleplayRoundTripSupport
import com.example.myapplication.roleplay.RoleplayTimeAwarenessSupport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
    private val phoneSnapshotRepository: PhoneSnapshotRepository,
    private val memoryWriteService: MemoryWriteService,
    private val contextLogStore: com.example.myapplication.data.repository.context.ContextLogStore,
    private val outputParser: RoleplayOutputParser = RoleplayOutputParser(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val messageIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val imageSaver: suspend (String) -> SavedImageFile = { throw IllegalStateException("图片保存未配置") },
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
    private var compensationJob: Job? = null
    private var diaryJob: Job? = null
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
    private val giftImageCoordinator = GiftImageGenerationCoordinator(
        aiPromptExtrasService = aiPromptExtrasService,
        aiGateway = aiGateway,
        conversationRepository = conversationRepository,
        imageSaver = imageSaver,
    )
    private val videoCallCoordinator = RoleplayVideoCallCoordinator(
        conversationRepository = conversationRepository,
        roleplayRepository = roleplayRepository,
        nowProvider = nowProvider,
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
        refreshContextGovernance = ::rebuildContextGovernanceSnapshot,
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
        phoneSnapshotRepository = phoneSnapshotRepository,
        roleplayRepository = roleplayRepository,
        promptContextAssembler = promptContextAssembler,
        assistantRoundTripRunner = assistantRoundTripRunner,
        outputParser = outputParser,
        nowProvider = nowProvider,
        currentUiState = { _uiState.value },
        updateUiState = { reducer -> _uiState.update(reducer) },
        updateRawMessages = { messages -> currentRawMessages.value = messages },
        launchGiftImageGeneration = { request, onUpdated ->
            viewModelScope.launch {
                val updatedMessages = giftImageCoordinator.generate(request) ?: return@launch
                onUpdated(updatedMessages)
            }
        },
        launchConversationSummaryGeneration = { conversationId, completedMessages, settings, assistant, scenario ->
            contextUpdateSupport.launchConversationSummaryGeneration(
                conversationId = conversationId,
                completedMessages = completedMessages,
                settings = settings,
                assistant = assistant,
                scenario = scenario,
                summaryTriggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
                summaryRecentMessageWindow = resolveSummaryRecentWindow(assistant),
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
                autoMemoryMessageWindow = settings.memoryAutoSummaryEvery,
                roleplaySceneMemoryMaxItems = ROLEPLAY_SCENE_MEMORY_MAX_ITEMS,
                maxMemoryInputLength = MAX_MEMORY_INPUT_LENGTH,
            )
        },
        contextLogStore = contextLogStore,
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
        RoleplayObservationSupport.observeChatSummaries(
            scope = viewModelScope,
            roleplayRepository = roleplayRepository,
            uiState = _uiState,
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
                viewModelScope.launch {
                    syncVideoCallState(session)
                }
            },
        )
        observePendingMemoryProposal()
        observeMemoryProposalHistory()
        RoleplayObservationSupport.observeDiaryEntries(
            scope = viewModelScope,
            roleplayRepository = roleplayRepository,
            uiState = _uiState,
        )
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
        observeOnlineCompensation()
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

    fun createChatForAssistant(
        assistantId: String,
        interactionMode: RoleplayInteractionMode,
        enableNarration: Boolean,
        onCreated: (String) -> Unit,
    ) {
        val scenarioId = UUID.randomUUID().toString()
        val normalizedAssistantId = assistantId.trim().ifBlank { com.example.myapplication.model.DEFAULT_ASSISTANT_ID }
        upsertScenario(
            RoleplayScenario(
                id = scenarioId,
                assistantId = normalizedAssistantId,
                interactionMode = interactionMode,
                enableNarration = enableNarration,
                longformModeEnabled = interactionMode == RoleplayInteractionMode.OFFLINE_LONGFORM,
                enableRoleplayProtocol = interactionMode != RoleplayInteractionMode.OFFLINE_LONGFORM,
                descriptionPromptEnabled = false,
            ),
        ) {
            onCreated(scenarioId)
        }
    }

    fun updateScenarioPinned(scenarioId: String, pinned: Boolean) {
        viewModelScope.launch {
            val scenario = roleplayRepository.getScenario(scenarioId) ?: return@launch
            roleplayRepository.upsertScenario(scenario.copy(isPinned = pinned))
        }
    }

    fun updateScenarioMuted(scenarioId: String, muted: Boolean) {
        viewModelScope.launch {
            val scenario = roleplayRepository.getScenario(scenarioId) ?: return@launch
            roleplayRepository.upsertScenario(scenario.copy(isMuted = muted))
        }
    }

    fun clearScenarioConversation(
        scenarioId: String,
        onSuccess: (() -> Unit)? = null,
    ) {
        val selectedModel = RoleplayConversationSupport.resolveSelectedModelId(_uiState.value.settings)
        viewModelScope.launch {
            runCatching {
                val session = roleplayRepository.getSessionByScenario(scenarioId) ?: return@runCatching
                conversationRepository.clearConversation(session.conversationId, selectedModel)
                roleplayRepository.deleteOnlineMeta(session.conversationId)
                roleplayRepository.deleteDiaryEntriesForConversation(session.conversationId)
                conversationSummaryRepository.deleteSummary(session.conversationId)
                contextUpdateSupport.clearConversationScopedContext(session.conversationId)
                if (_uiState.value.currentSession?.conversationId == session.conversationId) {
                    currentRawMessages.value = emptyList()
                    contextUpdateSupport.refreshContextStatus(
                        conversationId = session.conversationId,
                        isContinuingSession = false,
                    )
                }
            }.onSuccess {
                _uiState.update { current ->
                    RoleplayStateSupport.applyNoticeMessage(current, "聊天记录已清空")
                }
                onSuccess?.invoke()
            }.onFailure { throwable ->
                _uiState.update { current ->
                    RoleplayStateSupport.applyErrorMessage(current, throwable.message ?: "清空聊天失败")
                }
            }
        }
    }

    fun ensureScenarioSession(
        scenarioId: String,
        onReady: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                roleplayRepository.startScenario(scenarioId).session.conversationId
            }.onSuccess { conversationId ->
                if (conversationId.isNotBlank()) {
                    onReady(conversationId)
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    RoleplayStateSupport.applyErrorMessage(current, throwable.message ?: "进入功能失败")
                }
            }
        }
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

    fun sendSpecialPlay(draft: ChatSpecialPlayDraft) {
        sendActionSupport.sendSpecialPlay(draft)?.let { job ->
            sendingJob = job
        }
    }

    fun sendVoiceMessage(draft: VoiceMessageDraft) {
        sendActionSupport.sendVoiceMessage(draft)?.let { job ->
            sendingJob = job
        }
    }

    fun sendTransferPlay(
        counterparty: String,
        amount: String,
        note: String,
    ) {
        sendSpecialPlay(
            com.example.myapplication.model.TransferPlayDraft(
                counterparty = counterparty,
                amount = amount,
                note = note,
            ),
        )
    }

    fun confirmTransferReceipt(specialId: String) {
        sendActionSupport.confirmTransferReceipt(specialId)
    }

    fun startVideoCall() {
        val state = _uiState.value
        val scenario = state.currentScenario ?: return
        if (scenario.interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            return
        }
        suggestionActionSupport.cancelSuggestionGeneration(resetState = false)
        viewModelScope.launch {
            try {
                val session = ensureSessionForVideoCall(state, scenario) ?: return@launch
                val selectedModel = RoleplayConversationSupport.resolveSelectedModelId(_uiState.value.settings)
                val outcome = videoCallCoordinator.startCall(
                    conversationId = session.conversationId,
                    selectedModel = selectedModel,
                )
                outcome.refreshedMessages?.let { currentRawMessages.value = it }
                _uiState.update { current ->
                    RoleplayStateSupport.applyVideoCallState(
                        current = current,
                        callSessionId = outcome.callSessionId,
                        startedAt = outcome.startedAt,
                        inputFocusToken = if (outcome.alreadyActive) nowProvider() else outcome.startedAt,
                    )
                }
            } catch (throwable: Throwable) {
                _uiState.update { current ->
                    RoleplayStateSupport.applyErrorMessage(current, throwable.message ?: "启动视频通话失败")
                }
            }
        }
    }

    fun hangupVideoCall() {
        val state = _uiState.value
        val session = state.currentSession ?: return
        if (!state.isVideoCallActive) {
            return
        }
        suggestionActionSupport.cancelSuggestionGeneration(resetState = false)
        val hangingSendingJob = sendingJob
        val hangingCompensationJob = compensationJob
        sendingJob = null
        compensationJob = null
        _uiState.update { current ->
            RoleplayStateSupport.clearVideoCallState(current)
        }
        viewModelScope.launch {
            try {
                hangingSendingJob?.cancelAndJoin()
                hangingCompensationJob?.cancelAndJoin()
                val selectedModel = RoleplayConversationSupport.resolveSelectedModelId(_uiState.value.settings)
                val outcome = videoCallCoordinator.hangupCall(
                    conversationId = session.conversationId,
                    selectedModel = selectedModel,
                    fallbackSessionId = state.activeVideoCallSessionId,
                    fallbackStartedAt = state.activeVideoCallStartedAt,
                )
                outcome.refreshedMessages?.let { currentRawMessages.value = it }
            } catch (throwable: Throwable) {
                _uiState.update { current ->
                    RoleplayStateSupport.applyErrorMessage(current, throwable.message ?: "挂断视频通话失败")
                }
            }
        }
    }

    fun clearSuggestions() {
        suggestionActionSupport.clearSuggestions()
    }

    fun quoteMessage(
        sourceMessageId: String,
        speakerName: String,
        preview: String,
    ) {
        _uiState.update { current ->
            current.copy(
                replyToMessageId = sourceMessageId,
                replyToSpeakerName = speakerName.trim(),
                replyToPreview = preview.trim(),
            )
        }
    }

    fun clearQuotedMessage() {
        _uiState.update { current ->
            current.copy(
                replyToMessageId = "",
                replyToPreview = "",
                replyToSpeakerName = "",
            )
        }
    }

    fun recallMessage(sourceMessageId: String) {
        val state = _uiState.value
        val session = state.currentSession ?: return
        if (state.currentScenario?.interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            return
        }
        viewModelScope.launch {
            val currentMessages = currentRawMessages.value
            val latestUserMessage = currentMessages
                .filter { it.role == MessageRole.USER && it.status == MessageStatus.COMPLETED && !it.isRecalled }
                .maxByOrNull { it.createdAt }
            if (latestUserMessage == null || latestUserMessage.id != sourceMessageId) {
                _uiState.update { current ->
                    RoleplayStateSupport.applyErrorMessage(current, "当前只支持撤回你最近发送的一条消息")
                }
                return@launch
            }
            val updatedMessages = conversationRepository.recallMessage(
                conversationId = session.conversationId,
                messageId = sourceMessageId,
                selectedModel = RoleplayConversationSupport.resolveSelectedModelId(state.settings),
            )
            currentRawMessages.value = updatedMessages
            clearQuotedMessage()
            _uiState.update { current ->
                RoleplayStateSupport.applyNoticeMessage(current, "已撤回一条消息")
            }
        }
    }

    fun captureOnlineChat() {
        val state = _uiState.value
        val session = state.currentSession ?: return
        if (state.currentScenario?.interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            return
        }
        viewModelScope.launch {
            val eventMessage = ChatMessage(
                id = "online-event-screenshot-${session.conversationId}-${nowProvider()}",
                conversationId = session.conversationId,
                role = MessageRole.ASSISTANT,
                content = "你截了一张聊天截图。",
                createdAt = nowProvider(),
                parts = listOf(textMessagePart("你截了一张聊天截图。")),
                systemEventKind = RoleplayOnlineEventKind.SCREENSHOT,
                roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
            )
            conversationRepository.appendSystemEventMessage(
                conversationId = session.conversationId,
                message = eventMessage,
                selectedModel = RoleplayConversationSupport.resolveSelectedModelId(state.settings),
            )
            currentRawMessages.value = conversationRepository.listMessages(session.conversationId)
            _uiState.update { current ->
                RoleplayStateSupport.applyNoticeMessage(current, "已记录截图事件")
            }
        }
    }

    fun updateCurrentScenarioInteractionMode(mode: RoleplayInteractionMode) {
        val scenario = _uiState.value.currentScenario ?: return
        upsertScenario(
            scenario.copy(
                interactionMode = mode,
                longformModeEnabled = mode == RoleplayInteractionMode.OFFLINE_LONGFORM,
                enableRoleplayProtocol = when (mode) {
                    RoleplayInteractionMode.OFFLINE_LONGFORM -> false
                    RoleplayInteractionMode.OFFLINE_DIALOGUE -> scenario.enableRoleplayProtocol
                    RoleplayInteractionMode.ONLINE_PHONE -> true
                },
            ),
        )
    }

    fun updateCurrentScenarioNarrationEnabled(enabled: Boolean) {
        val scenario = _uiState.value.currentScenario ?: return
        upsertScenario(scenario.copy(enableNarration = enabled))
    }

    fun updateCurrentScenarioDeepImmersionEnabled(enabled: Boolean) {
        val scenario = _uiState.value.currentScenario ?: return
        upsertScenario(scenario.copy(enableDeepImmersion = enabled))
    }

    fun updateCurrentScenarioTimeAwarenessEnabled(enabled: Boolean) {
        val scenario = _uiState.value.currentScenario ?: return
        upsertScenario(scenario.copy(enableTimeAwareness = enabled))
    }

    fun updateCurrentScenarioNetMemeEnabled(enabled: Boolean) {
        val scenario = _uiState.value.currentScenario ?: return
        upsertScenario(scenario.copy(enableNetMeme = enabled))
    }

    fun generateRoleplayDiaries() {
        val state = _uiState.value
        val scenario = state.currentScenario ?: return
        val session = state.currentSession ?: return
        val assistant = state.currentAssistant ?: RoleplayConversationSupport.resolveAssistant(
            settings = state.settings,
            assistantId = scenario.assistantId,
        )
        val activeProvider = state.settings.activeProvider()
        val selectedModel = RoleplayConversationSupport.resolveSelectedModelId(state.settings)
        if (state.isGeneratingDiary || diaryJob?.isActive == true) {
            return
        }
        if (state.isSending) {
            _uiState.update { current ->
                RoleplayStateSupport.applyErrorMessage(current, "请等待当前回复完成后再生成日记")
            }
            return
        }
        if (
            activeProvider == null ||
            activeProvider.baseUrl.isBlank() ||
            activeProvider.apiKey.isBlank() ||
            selectedModel.isBlank()
        ) {
            _uiState.update { current ->
                RoleplayStateSupport.applyErrorMessage(current, "请先完成当前模型配置后再生成日记")
            }
            return
        }

        val conversationId = session.conversationId
        diaryJob = viewModelScope.launch {
            try {
                _uiState.update { current ->
                    RoleplayStateSupport.beginDiaryGeneration(current)
                }
                val conversation = conversationRepository.getConversation(conversationId)
                    ?: Conversation(
                        id = conversationId,
                        createdAt = nowProvider(),
                        updatedAt = nowProvider(),
                        assistantId = scenario.assistantId,
                    )
                val requestMessages = currentRawMessages.value.filter { it.status == MessageStatus.COMPLETED }
                val promptContext = promptContextAssembler.assemble(
                    settings = RoleplayConversationSupport.resolvePromptSettings(scenario, state.settings),
                    assistant = RoleplayConversationSupport.resolvePromptAssistant(scenario, assistant),
                    conversation = conversation,
                    userInputText = RoleplayConversationSupport.resolveLatestUserInputText(requestMessages),
                    recentMessages = requestMessages,
                    promptMode = PromptMode.ROLEPLAY,
                )
                val diaryDrafts = aiPromptExtrasService.generateRoleplayDiaries(
                    characterContext = this@RoleplayViewModel.buildDiaryCharacterContext(
                        systemPrompt = promptContext.systemPrompt,
                        scenario = scenario,
                        assistant = assistant,
                        settings = state.settings,
                    ),
                    scenarioContext = this@RoleplayViewModel.buildDiaryScenarioContext(
                        scenario = scenario,
                        assistant = assistant,
                        settings = state.settings,
                    ),
                    conversationExcerpt = RoleplayConversationSupport.buildTranscriptInput(
                        messages = requestMessages,
                        scenario = scenario,
                        assistant = assistant,
                        settings = state.settings,
                        maxLength = MAX_SUMMARY_INPUT_LENGTH,
                    ),
                    characterName = this@RoleplayViewModel.resolveCharacterName(scenario, assistant),
                    userName = this@RoleplayViewModel.resolveUserName(scenario, state.settings),
                    todayLabel = this@RoleplayViewModel.formatDiaryTodayLabel(),
                    baseUrl = activeProvider.baseUrl,
                    apiKey = activeProvider.apiKey,
                    modelId = selectedModel,
                    apiProtocol = activeProvider.resolvedApiProtocol(),
                    provider = activeProvider,
                )
                val savedEntries = roleplayRepository.replaceDiaryEntries(
                    conversationId = conversationId,
                    scenarioId = scenario.id,
                    entries = diaryDrafts,
                )
                if (_uiState.value.currentSession?.conversationId == conversationId) {
                    _uiState.update { current ->
                        RoleplayStateSupport.finishDiaryGeneration(current, savedEntries)
                    }
                }
            } catch (throwable: Throwable) {
                if (_uiState.value.currentSession?.conversationId == conversationId) {
                    val failureMessage = throwable.localizedMessage
                        ?: throwable.message
                        ?: throwable::class.simpleName
                        ?: "角色日记生成失败"
                    _uiState.update { current ->
                        RoleplayStateSupport.failDiaryGeneration(
                            current,
                            failureMessage,
                        )
                    }
                }
            } finally {
                diaryJob = null
            }
        }
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

    fun resetCurrentSession(onSuccess: () -> Unit = {}) {
        suggestionActionSupport.cancelSuggestionGeneration(resetState = false)
        scenarioActionSupport.resetCurrentSession(onSuccess)
    }

    fun restartCurrentSession(onSuccess: () -> Unit = {}) {
        suggestionActionSupport.cancelSuggestionGeneration(resetState = false)
        scenarioActionSupport.restartCurrentSession(onSuccess)
    }

    fun refreshCurrentConversationSummary() {
        val state = _uiState.value
        val session = state.currentSession ?: return
        val scenario = state.currentScenario ?: return
        val assistant = state.currentAssistant ?: RoleplayConversationSupport.resolveAssistant(
            settings = state.settings,
            assistantId = scenario.assistantId,
        )
        viewModelScope.launch {
            val result = runCatching {
                contextUpdateSupport.updateConversationSummary(
                    conversationId = session.conversationId,
                    completedMessages = currentRawMessages.value.filter { it.status == MessageStatus.COMPLETED },
                    settings = state.settings,
                    assistant = assistant,
                    scenario = scenario,
                    summaryTriggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
                    summaryRecentMessageWindow = resolveSummaryRecentWindow(assistant),
                    summaryMinCoveredMessageCount = SUMMARY_MIN_COVERED_MESSAGE_COUNT,
                    maxSummaryInputLength = MAX_SUMMARY_INPUT_LENGTH,
                    forceRefresh = true,
                )
            }.getOrDefault(SummaryUpdateResult())
            if (result.updated && _uiState.value.currentSession?.conversationId == session.conversationId) {
                _uiState.update { current ->
                    RoleplayStateSupport.applyNoticeMessage(current, "上下文摘要已刷新")
                }
            }
        }
    }

    fun sendMessage() {
        sendActionSupport.sendMessage()?.let { job ->
            sendingJob = job
        }
    }

    private fun observeOnlineCompensation() {
        viewModelScope.launch {
            currentRawMessages.collectLatest {
                maybeTriggerOnlineCompensation()
            }
        }
    }

    private suspend fun maybeTriggerOnlineCompensation() {
        val state = _uiState.value
        val scenario = state.currentScenario ?: return
        val session = state.currentSession ?: return
        if (scenario.interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            return
        }
        if (state.isVideoCallActive || state.isSending || state.input.isNotBlank() || compensationJob?.isActive == true) {
            return
        }
        if (currentRawMessages.value.any { it.status == MessageStatus.LOADING }) {
            return
        }
        val bucket = resolveOnlineCompensationBucket(currentRawMessages.value) ?: return
        val meta = roleplayRepository.getOnlineMeta(session.conversationId)
        if (meta?.lastCompensationBucket == bucket) {
            return
        }
        val assistant = RoleplayConversationSupport.resolveAssistant(state.settings, scenario.assistantId)
        val selectedModel = RoleplayConversationSupport.resolveSelectedModelId(state.settings)
        if (selectedModel.isBlank()) {
            return
        }
        val baseMessages = RoleplayRoundTripSupport.currentConversationMessages(
            messages = currentRawMessages.value,
            conversationId = session.conversationId,
        )
        val loadingMessage = ChatMessage(
            id = "online-compensation-loading-${session.conversationId}-${nowProvider()}",
            conversationId = session.conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.LOADING,
            createdAt = nowProvider(),
            modelName = selectedModel,
            systemEventKind = RoleplayOnlineEventKind.COMPENSATION_OPENING,
            roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
            roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )
        compensationJob = viewModelScope.launch {
            try {
                updateUiStateForCompensationStart()
                roundTripExecutor.execute(
                    state = state,
                    scenario = scenario,
                    session = session,
                    selectedModel = selectedModel,
                    assistant = assistant,
                    requestMessages = baseMessages.filter { it.status == MessageStatus.COMPLETED && it.hasSendableContent() },
                    cancelledMessages = baseMessages,
                    initialPersistence = RoundTripInitialPersistence.Append(messages = listOf(loadingMessage)),
                    loadingMessage = loadingMessage,
                    buildFinalMessages = { completedAssistant ->
                        baseMessages + completedAssistant
                    },
                )
                roleplayRepository.upsertOnlineMeta(
                    com.example.myapplication.model.RoleplayOnlineMeta(
                        conversationId = session.conversationId,
                        lastCompensationBucket = bucket,
                        lastConsumedObservationUpdatedAt = roleplayRepository.getOnlineMeta(session.conversationId)?.lastConsumedObservationUpdatedAt ?: 0L,
                        lastSystemEventToken = "compensation-$bucket",
                        activeVideoCallSessionId = roleplayRepository.getOnlineMeta(session.conversationId)?.activeVideoCallSessionId.orEmpty(),
                        activeVideoCallStartedAt = roleplayRepository.getOnlineMeta(session.conversationId)?.activeVideoCallStartedAt ?: 0L,
                        updatedAt = nowProvider(),
                    ),
                )
            } finally {
                compensationJob = null
            }
        }
    }

    private fun updateUiStateForCompensationStart() {
        _uiState.update { current ->
            current.copy(
                isSending = true,
                errorMessage = null,
                noticeMessage = null,
            )
        }
    }

    private fun resolveOnlineCompensationBucket(
        messages: List<ChatMessage>,
    ): String? {
        val latestTimestamp = messages
            .filter { it.status == MessageStatus.COMPLETED && it.createdAt > 0L }
            .maxOfOrNull { it.createdAt }
            ?: return null
        val gapMillis = (nowProvider() - latestTimestamp).coerceAtLeast(0L)
        return when {
            gapMillis < 6 * 60 * 60 * 1000L -> null
            gapMillis < 24 * 60 * 60 * 1000L -> "6h_24h"
            gapMillis < 3 * 24 * 60 * 60 * 1000L -> "1d_3d"
            gapMillis < 14 * 24 * 60 * 60 * 1000L -> "3d_14d"
            else -> "14d_plus"
        }
    }

    private fun resolveSummaryRecentWindow(
        assistant: Assistant?,
    ): Int {
        // 下限保护：Assistant 若把 contextMessageSize 配成 1 或 2，
        // olderMessages 会被拉得过大导致摘要输入爆炸，强制不小于 MIN。
        return (assistant?.contextMessageSize?.takeIf { it > 0 }
            ?: SUMMARY_RECENT_MESSAGE_WINDOW)
            .coerceAtLeast(SUMMARY_RECENT_MESSAGE_WINDOW_MIN)
    }

    private suspend fun rebuildContextGovernanceSnapshot(
        conversationId: String,
        messages: List<ChatMessage>,
        settings: AppSettings,
        assistant: Assistant?,
        scenario: RoleplayScenario,
    ) {
        val current = _uiState.value
        val session = current.currentSession ?: return
        if (session.conversationId != conversationId) {
            return
        }
        val requestMessages = messages.filter { it.status == MessageStatus.COMPLETED }
        val conversation = conversationRepository.getConversation(conversationId)
            ?: Conversation(
                id = conversationId,
                createdAt = nowProvider(),
                updatedAt = nowProvider(),
                assistantId = scenario.assistantId,
            )
        val promptContext = promptContextAssembler.assemble(
            settings = RoleplayConversationSupport.resolvePromptSettings(scenario, settings),
            assistant = RoleplayConversationSupport.resolvePromptAssistant(scenario, assistant),
            conversation = conversation,
            userInputText = RoleplayConversationSupport.resolveLatestUserInputText(requestMessages),
            recentMessages = requestMessages,
            promptMode = PromptMode.ROLEPLAY,
        )
        val directorNote = RoleplayConversationSupport.buildDynamicDirectorNote(
            messages = requestMessages,
            scenario = scenario,
            assistant = assistant,
            settings = settings,
            outputParser = outputParser,
            isVideoCallActive = current.isVideoCallActive,
        )
        val decoratedPrompt = RoleplayPromptDecorator.decorate(
            baseSystemPrompt = promptContext.systemPrompt,
            scenario = scenario,
            assistant = assistant,
            settings = settings,
            includeOpeningNarrationReference = requestMessages.none { it.role == MessageRole.USER },
            isVideoCallActive = current.isVideoCallActive,
            directorNote = directorNote,
            modelId = RoleplayConversationSupport.resolveSelectedModelId(settings),
        )
        val effectiveRequestMessages = resolveRoleplayRequestMessagesForRoundTrip(
            conversationId = conversationId,
            assistant = assistant,
            requestMessages = requestMessages,
        )
        val toolingOptions = GatewayToolingOptions.localContextOnly(
            com.example.myapplication.model.GatewayToolRuntimeContext(
                promptMode = PromptMode.ROLEPLAY,
                assistant = assistant,
                conversation = conversation,
                userInputText = RoleplayConversationSupport.resolveLatestUserInputText(requestMessages),
                recentMessages = requestMessages,
            ),
        )
        val completedMessageCount = requestMessages.size
        val debugDump = buildString {
            append(
                ConversationSummaryDebugSupport.appendStatusLine(
                    debugDump = promptContext.debugDump,
                    hasSummary = promptContext.summaryCoveredMessageCount > 0,
                    coveredMessageCount = promptContext.summaryCoveredMessageCount,
                    completedMessageCount = completedMessageCount,
                    triggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
                ),
            )
            if (decoratedPrompt.isNotBlank()) {
                append("\n\n【RP 装饰后提示词】\n")
                append(decoratedPrompt)
            }
        }
        val contextSnapshot = ContextGovernanceSupport.buildSnapshot(
            settings = settings,
            assistant = assistant,
            promptMode = PromptMode.ROLEPLAY,
            selectedModel = RoleplayConversationSupport.resolveSelectedModelId(settings),
            requestMessages = requestMessages,
            effectiveRequestMessages = effectiveRequestMessages,
            promptContext = promptContext,
            completedMessageCount = completedMessageCount,
            triggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
            recentWindow = resolveSummaryRecentWindow(assistant),
            minCoveredMessageCount = SUMMARY_MIN_COVERED_MESSAGE_COUNT,
            toolingOptions = toolingOptions,
            rawDebugDump = debugDump,
        )
        contextLogStore.push(contextSnapshot)
        _uiState.update { currentState ->
            RoleplayStateSupport.applyPromptContext(
                current = currentState,
                summaryCoveredMessageCount = promptContext.summaryCoveredMessageCount,
                worldBookHitCount = promptContext.worldBookHitCount,
                memoryInjectionCount = promptContext.memoryInjectionCount,
                debugDump = debugDump,
                contextGovernance = contextSnapshot,
            )
        }
    }

    private suspend fun resolveRoleplayRequestMessagesForRoundTrip(
        conversationId: String,
        assistant: Assistant?,
        requestMessages: List<ChatMessage>,
    ): List<ChatMessage> {
        val summary = conversationSummaryRepository.getSummary(conversationId)
        return ConversationMessageTransforms.trimRequestMessagesWithSummary(
            requestMessages = requestMessages,
            completedMessageCount = requestMessages.count { it.status == MessageStatus.COMPLETED },
            summaryCoveredMessageCount = summary
                ?.takeIf { it.summary.isNotBlank() }
                ?.coveredMessageCount
                ?: 0,
            recentWindow = resolveSummaryRecentWindow(assistant),
        )
    }

    private suspend fun ensureSessionForVideoCall(
        state: RoleplayUiState,
        scenario: RoleplayScenario,
    ): RoleplaySession? {
        val currentSession = state.currentSession
        if (currentSession != null) {
            return currentSession
        }
        val startResult = roleplayRepository.startScenario(scenario.id)
        scenarioActionSupport.applySessionStartResult(
            startResult = startResult,
            scenario = scenario,
        )
        currentRawMessages.value = startResult.conversationMessages
        if (startResult.assistantMismatch) {
            return null
        }
        return startResult.session
    }

    private suspend fun syncVideoCallState(session: RoleplaySession?) {
        if (session == null) {
            _uiState.update { current ->
                RoleplayStateSupport.clearVideoCallState(current)
            }
            return
        }
        val activeCall = videoCallCoordinator.fetchActiveCall(session.conversationId)
        _uiState.update { current ->
            if (activeCall.isActive) {
                RoleplayStateSupport.applyVideoCallState(
                    current = current,
                    callSessionId = activeCall.callSessionId,
                    startedAt = activeCall.startedAt,
                )
            } else {
                RoleplayStateSupport.clearVideoCallState(current)
            }
        }
    }

    private fun buildDiaryCharacterContext(
        systemPrompt: String,
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
    ): String {
        val userName = resolveUserName(scenario, settings)
        val characterName = resolveCharacterName(scenario, assistant)
        return ContextPlaceholderResolver.resolve(
            text = systemPrompt.trim(),
            userName = userName,
            characterName = characterName,
        )
    }

    private fun buildDiaryScenarioContext(
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
    ): String {
        val userName = resolveUserName(scenario, settings)
        val characterName = resolveCharacterName(scenario, assistant)
        return buildString {
            appendLine("角色：$characterName")
            appendLine("对话对象：$userName")
            if (scenario.title.isNotBlank()) {
                appendLine("聊天标题：${scenario.title.trim()}")
            }
            if (scenario.shouldInjectDescriptionPrompt()) {
                appendLine("聊天背景补充：${scenario.description.trim()}")
            }
            if (scenario.openingNarration.isNotBlank()) {
                appendLine("开场旁白参考：${scenario.openingNarration.trim()}")
            }
            appendLine("交互模式：${scenario.interactionMode.displayName}")
            appendLine("心声/旁白生成：${if (scenario.enableNarration) "开启" else "关闭"}")
            appendLine("深度沉浸：${if (scenario.enableDeepImmersion) "开启" else "关闭"}")
            appendLine("时间感知：${if (scenario.enableTimeAwareness) "开启" else "关闭"}")
            appendLine("网络热梗：${if (scenario.enableNetMeme) "开启" else "关闭"}")
        }.trim()
    }

    private fun resolveUserName(
        scenario: RoleplayScenario,
        settings: AppSettings,
    ): String {
        return RoleplayConversationSupport.resolveUserPersona(scenario, settings).displayName
    }

    private fun resolveCharacterName(
        scenario: RoleplayScenario,
        assistant: Assistant?,
    ): String {
        return scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
    }

    private fun formatDiaryTodayLabel(): String {
        return runCatching {
            SimpleDateFormat("yyyy/M/d", Locale.SIMPLIFIED_CHINESE)
                .format(Date(nowProvider()))
        }.getOrDefault(RoleplayTimeAwarenessSupport.formatCurrentPromptTime(nowProvider()))
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
        private const val SUMMARY_RECENT_MESSAGE_WINDOW_MIN = 4
        private const val SUGGESTION_RECENT_MESSAGE_WINDOW = 10
        private const val MAX_SUMMARY_INPUT_LENGTH = 4_000
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
            phoneSnapshotRepository: PhoneSnapshotRepository,
            memoryWriteService: MemoryWriteService,
            contextLogStore: com.example.myapplication.data.repository.context.ContextLogStore,
            imageSaver: suspend (String) -> SavedImageFile = { throw IllegalStateException("图片保存未配置") },
        ): ViewModelProvider.Factory {
            return typedViewModelFactory {
                RoleplayViewModel(
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
                    phoneSnapshotRepository = phoneSnapshotRepository,
                    memoryWriteService = memoryWriteService,
                    contextLogStore = contextLogStore,
                    imageSaver = imageSaver,
                )
            }
        }
    }
}
