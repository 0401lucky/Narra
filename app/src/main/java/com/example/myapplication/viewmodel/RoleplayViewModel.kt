package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.repository.AiRepository
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.TransferUpdateDirective
import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoleplaySessionStartResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.model.imageMessagePart
import com.example.myapplication.model.isTransferPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.toTransferCopyText
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.model.toPlainText
import com.example.myapplication.roleplay.RoleplayOutputParser
import com.example.myapplication.roleplay.RoleplayPromptDecorator
import com.example.myapplication.roleplay.RoleplayTranscriptFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val currentModel: String = "",
    val currentProviderId: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class RoleplayViewModel(
    private val repository: AiRepository,
    private val conversationRepository: ConversationRepository,
    private val roleplayRepository: RoleplayRepository,
    private val promptContextAssembler: PromptContextAssembler,
    private val memoryRepository: MemoryRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val outputParser: RoleplayOutputParser = RoleplayOutputParser(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val messageIdProvider: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val currentScenarioId = MutableStateFlow<String?>(null)
    private val currentRawMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _uiState = MutableStateFlow(RoleplayUiState())
    val uiState: StateFlow<RoleplayUiState> = _uiState.asStateFlow()

    private var sendingJob: Job? = null
    private var suggestionJob: Job? = null

    init {
        observeSettings()
        observeScenarios()
        observeSessions()
        observeCurrentScenario()
        observeCurrentSession()
        observeCurrentMessages()
        observeMappedMessages()
    }

    fun updateInput(value: String) {
        val shouldClearSuggestions = value.isNotBlank() && value != _uiState.value.input
        if (_uiState.value.isGeneratingSuggestions) {
            cancelSuggestionGeneration(resetState = false)
        }
        _uiState.update {
            it.copy(
                input = value,
                errorMessage = null,
                suggestions = if (shouldClearSuggestions) emptyList() else it.suggestions,
                isGeneratingSuggestions = false,
                suggestionErrorMessage = null,
            )
        }
    }

    fun enterScenario(scenarioId: String) {
        if (scenarioId.isBlank()) {
            return
        }
        cancelSuggestionGeneration(resetState = false)
        currentScenarioId.value = scenarioId
        _uiState.update {
            it.copy(
                currentScenario = it.currentScenario?.takeIf { scenario -> scenario.id == scenarioId },
                currentSession = null,
                messages = emptyList(),
                suggestions = emptyList(),
                input = "",
                isScenarioLoading = true,
                isGeneratingSuggestions = false,
                showAssistantMismatchDialog = false,
                previousAssistantName = "",
                currentAssistantName = "",
                errorMessage = null,
                noticeMessage = null,
                latestPromptDebugDump = "",
                streamingContent = "",
                suggestionErrorMessage = null,
                contextStatus = RoleplayContextStatus(),
            )
        }
        viewModelScope.launch {
            runCatching {
                val scenario = roleplayRepository.getScenario(scenarioId)
                    ?: error("场景不存在")
                val startResult = roleplayRepository.startScenario(scenarioId)
                applySessionStartResult(
                    startResult = startResult,
                    scenario = scenario,
                )
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isScenarioLoading = false,
                        errorMessage = throwable.message ?: "启动场景失败",
                        isSending = false,
                    )
                }
            }
        }
    }

    fun leaveScenario() {
        cancelSuggestionGeneration(resetState = false)
        currentScenarioId.value = null
        currentRawMessages.value = emptyList()
        _uiState.update {
            it.copy(
                currentScenario = null,
                currentSession = null,
                currentAssistant = null,
                contextStatus = RoleplayContextStatus(),
                messages = emptyList(),
                suggestions = emptyList(),
                input = "",
                isSending = false,
                isGeneratingSuggestions = false,
                isScenarioLoading = false,
                showAssistantMismatchDialog = false,
                previousAssistantName = "",
                currentAssistantName = "",
                errorMessage = null,
                noticeMessage = null,
                latestPromptDebugDump = "",
                streamingContent = "",
                suggestionErrorMessage = null,
            )
        }
    }

    fun dismissAssistantMismatchDialog() {
        _uiState.update {
            it.copy(
                showAssistantMismatchDialog = false,
                previousAssistantName = "",
                currentAssistantName = "",
                noticeMessage = "继续沿用旧剧情",
            )
        }
    }

    fun upsertScenario(
        scenario: RoleplayScenario,
        onSuccess: (() -> Unit)? = null,
    ) {
        val existed = _uiState.value.scenarios.any { it.id == scenario.id }
        viewModelScope.launch {
            runCatching {
                roleplayRepository.upsertScenario(scenario)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        errorMessage = null,
                        noticeMessage = if (existed) "场景已保存" else "场景已创建",
                    )
                }
                onSuccess?.invoke()
            }.onFailure { throwable ->
                _uiState.update { it.copy(errorMessage = throwable.message ?: "保存场景失败") }
            }
        }
    }

    fun deleteScenario(
        scenarioId: String,
        onSuccess: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                roleplayRepository.deleteScenario(scenarioId)
            }.onSuccess {
                if (currentScenarioId.value == scenarioId) {
                    leaveScenario()
                }
                _uiState.update {
                    it.copy(
                        errorMessage = null,
                        noticeMessage = "场景已删除",
                    )
                }
                onSuccess?.invoke()
            }.onFailure { throwable ->
                _uiState.update { it.copy(errorMessage = throwable.message ?: "删除场景失败") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearNoticeMessage() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    fun generateSuggestions() {
        requestRoleplaySuggestions(fillInputWithFirstSuggestion = false)
    }

    fun generateDraftInput() {
        requestRoleplaySuggestions(fillInputWithFirstSuggestion = true)
    }

    private fun requestRoleplaySuggestions(
        fillInputWithFirstSuggestion: Boolean,
    ) {
        val state = _uiState.value
        val scenario = state.currentScenario
        val session = state.currentSession
        if (state.isSending) {
            return
        }
        if (scenario == null) {
            _uiState.update { it.copy(suggestionErrorMessage = "当前场景不存在") }
            return
        }
        if (session == null) {
            _uiState.update { it.copy(suggestionErrorMessage = "当前剧情尚未初始化完成") }
            return
        }
        if (!state.settings.hasRequiredConfig()) {
            _uiState.update { it.copy(suggestionErrorMessage = "请先完成模型配置后再生成建议") }
            return
        }

        cancelSuggestionGeneration(resetState = false)
        _uiState.update {
            it.copy(
                isGeneratingSuggestions = true,
                suggestionErrorMessage = null,
            )
        }

        suggestionJob = viewModelScope.launch {
            val runningSuggestionJob = currentCoroutineContext()[Job]
            try {
                val latestState = _uiState.value
                val assistant = resolveAssistant(latestState.settings, scenario.assistantId)
                val conversation = conversationRepository.getConversation(session.conversationId)
                    ?: Conversation(
                        id = session.conversationId,
                        createdAt = nowProvider(),
                        updatedAt = nowProvider(),
                        assistantId = scenario.assistantId,
                    )
                val allMessages = conversationRepository.listMessages(session.conversationId)
                    .filter { message ->
                        message.status == MessageStatus.COMPLETED && message.hasSendableContent()
                    }
                val recentWindow = assistant?.contextMessageSize
                    ?.takeIf { it > 0 }
                    ?.coerceAtMost(SUGGESTION_RECENT_MESSAGE_WINDOW)
                    ?: SUGGESTION_RECENT_MESSAGE_WINDOW
                val recentMessages = allMessages.takeLast(recentWindow)
                val promptContext = promptContextAssembler.assemble(
                    settings = latestState.settings,
                    assistant = assistant,
                    conversation = conversation,
                    userInputText = latestState.input.trim(),
                    recentMessages = recentMessages,
                    promptMode = PromptMode.ROLEPLAY,
                )
                val directorNote = buildDynamicDirectorNote(
                    messages = recentMessages,
                    scenario = scenario,
                    assistant = assistant,
                    settings = latestState.settings,
                )
                val decoratedPrompt = RoleplayPromptDecorator.decorate(
                    baseSystemPrompt = promptContext.systemPrompt,
                    scenario = scenario,
                    assistant = assistant,
                    settings = latestState.settings,
                    includeOpeningNarrationReference = allMessages.isEmpty(),
                    directorNote = directorNote,
                )
                val (userName, characterName) = resolveRoleplayNames(
                    scenario = scenario,
                    assistant = assistant,
                    settings = latestState.settings,
                )
                val conversationExcerpt = RoleplayTranscriptFormatter.formatMessages(
                    messages = recentMessages,
                    userName = userName,
                    characterName = characterName,
                    allowNarration = scenario.enableNarration,
                )
                val activeProvider = latestState.settings.activeProvider()
                val baseUrl = activeProvider?.baseUrl ?: latestState.settings.baseUrl
                val apiKey = activeProvider?.apiKey ?: latestState.settings.apiKey
                val suggestionModel = resolveSuggestionModelId(latestState.settings)
                val suggestions = repository.generateRoleplaySuggestions(
                    conversationExcerpt = conversationExcerpt,
                    systemPrompt = decoratedPrompt,
                    playerStyleReference = buildPlayerStyleReference(recentMessages),
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    modelId = suggestionModel,
                    longformMode = scenario.longformModeEnabled,
                )
                if (!isSuggestionTargetStillCurrent(scenario.id, session.conversationId)) {
                    return@launch
                }
                if (fillInputWithFirstSuggestion) {
                    val draftText = suggestions.firstOrNull()
                        ?.text
                        .orEmpty()
                        .trim()
                    if (draftText.isBlank()) {
                        _uiState.update {
                            it.copy(
                                suggestions = emptyList(),
                                isGeneratingSuggestions = false,
                                suggestionErrorMessage = "AI 没有生成可用草稿",
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                input = draftText,
                                suggestions = emptyList(),
                                isGeneratingSuggestions = false,
                                suggestionErrorMessage = null,
                                errorMessage = null,
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            suggestions = suggestions,
                            isGeneratingSuggestions = false,
                            suggestionErrorMessage = null,
                        )
                    }
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                if (!isSuggestionTargetStillCurrent(scenario.id, session.conversationId)) {
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isGeneratingSuggestions = false,
                        suggestionErrorMessage = throwable.message ?: "建议生成失败",
                    )
                }
            } finally {
                if (suggestionJob === runningSuggestionJob) {
                    suggestionJob = null
                } else if (!(_uiState.value.isGeneratingSuggestions)) {
                    suggestionJob = null
                }
            }
        }
    }

    fun applySuggestion(text: String) {
        _uiState.update {
            it.copy(
                input = text,
                suggestions = emptyList(),
                isGeneratingSuggestions = false,
                errorMessage = null,
                suggestionErrorMessage = null,
            )
        }
    }

    fun retryTurn(sourceMessageId: String) {
        val state = _uiState.value
        val scenario = state.currentScenario ?: return
        val session = state.currentSession ?: return
        if (state.isSending || sourceMessageId.isBlank()) {
            return
        }

        val selectedModel = resolveSelectedModelId(state.settings)
        val assistant = resolveAssistant(state.settings, scenario.assistantId)
        sendingJob = viewModelScope.launch {
            val currentMessages = conversationRepository.listMessages(session.conversationId)
            val targetIndex = currentMessages.indexOfFirst { message ->
                message.id == sourceMessageId &&
                    message.role == MessageRole.ASSISTANT &&
                    (message.status == MessageStatus.COMPLETED || message.status == MessageStatus.ERROR)
            }
            if (targetIndex == -1) {
                sendingJob = null
                return@launch
            }

            val targetMessage = currentMessages[targetIndex]
            val loadingMessage = targetMessage.copy(
                content = "",
                status = MessageStatus.LOADING,
                reasoningContent = "",
                parts = emptyList(),
            )
            val requestMessages = currentMessages
                .take(targetIndex)
                .filter { it.status == MessageStatus.COMPLETED && it.hasSendableContent() }
            val initialMessages = currentMessages.take(targetIndex) + loadingMessage

            _uiState.update {
                it.copy(
                    suggestions = emptyList(),
                    isGeneratingSuggestions = false,
                    suggestionErrorMessage = null,
                    isSending = true,
                    errorMessage = null,
                    streamingContent = "",
                )
            }
            currentRawMessages.value = initialMessages
            executeRoleplayRoundTrip(
                state = state,
                scenario = scenario,
                session = session,
                selectedModel = selectedModel,
                assistant = assistant,
                requestMessages = requestMessages,
                initialMessages = initialMessages,
                loadingMessage = loadingMessage,
                buildFinalMessages = { completedAssistant ->
                    currentMessages.take(targetIndex) + completedAssistant
                },
            )
        }
    }

    fun sendTransferPlay(
        counterparty: String,
        amount: String,
        note: String,
    ) {
        val state = _uiState.value
        val scenario = state.currentScenario
        if (scenario == null) {
            _uiState.update { it.copy(errorMessage = "当前场景不存在") }
            return
        }
        if (!state.settings.hasRequiredConfig()) {
            _uiState.update { it.copy(errorMessage = "请先完成模型配置后再开始剧情互动") }
            return
        }
        if (state.isSending) {
            return
        }

        val normalizedAmount = amount.trim()
        if (normalizedAmount.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入转账金额") }
            return
        }

        val normalizedCounterparty = counterparty.trim().ifBlank {
            resolveRoleplayNames(
                scenario = scenario,
                assistant = resolveAssistant(state.settings, scenario.assistantId),
                settings = state.settings,
            ).second
        }
        val transferPart = transferMessagePart(
            direction = TransferDirection.USER_TO_ASSISTANT,
            status = TransferStatus.PENDING,
            counterparty = normalizedCounterparty,
            amount = normalizedAmount,
            note = note.trim(),
        )

        startRoleplaySend(
            state = state,
            scenario = scenario,
            userParts = listOf(transferPart),
            nextInput = state.input,
        )
    }

    fun confirmTransferReceipt(specialId: String) {
        val state = _uiState.value
        val session = state.currentSession
        if (specialId.isBlank() || session == null) {
            return
        }

        viewModelScope.launch {
            val currentMessages = conversationRepository.listMessages(session.conversationId)
            val updatedMessages = updateTransferStatuses(
                messages = currentMessages,
                updates = listOf(
                    TransferUpdateDirective(
                        refId = specialId,
                        status = TransferStatus.RECEIVED,
                    ),
                ),
            )
            if (updatedMessages == currentMessages) {
                return@launch
            }
            conversationRepository.saveConversationMessages(
                conversationId = session.conversationId,
                messages = updatedMessages,
                selectedModel = resolveSelectedModelId(state.settings),
            )
            currentRawMessages.value = updatedMessages
            _uiState.update {
                it.copy(
                    noticeMessage = "已收款",
                    errorMessage = null,
                )
            }
        }
    }

    fun clearSuggestions() {
        cancelSuggestionGeneration(resetState = false)
        _uiState.update {
            it.copy(
                suggestions = emptyList(),
                isGeneratingSuggestions = false,
                suggestionErrorMessage = null,
            )
        }
    }

    fun cancelSending() {
        sendingJob?.cancel()
    }

    fun resetCurrentSession() {
        val state = _uiState.value
        val session = state.currentSession
        if (session == null) {
            return
        }
        if (state.isSending) {
            return
        }
        cancelSuggestionGeneration(resetState = false)
        viewModelScope.launch {
            runCatching {
                conversationRepository.clearConversation(
                    conversationId = session.conversationId,
                    selectedModel = resolveSelectedModelId(state.settings),
                )
                clearConversationScopedContext(session.conversationId)
            }.onSuccess {
                currentRawMessages.value = emptyList()
                _uiState.update {
                    it.copy(
                        messages = emptyList(),
                        suggestions = emptyList(),
                        streamingContent = "",
                        latestPromptDebugDump = "",
                        isGeneratingSuggestions = false,
                        suggestionErrorMessage = null,
                        contextStatus = it.contextStatus.copy(
                            hasSummary = false,
                            summaryCoveredMessageCount = 0,
                            memoryInjectionCount = 0,
                            worldBookHitCount = 0,
                            isContinuingSession = false,
                        ),
                        noticeMessage = "剧情已清空",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.message ?: "清空剧情失败")
                }
            }
        }
    }

    fun restartCurrentSession() {
        val state = _uiState.value
        val session = state.currentSession
        val scenario = state.currentScenario
        if (session == null || scenario == null) {
            return
        }
        if (state.isSending) {
            return
        }
        cancelSuggestionGeneration(resetState = false)
        viewModelScope.launch {
            runCatching {
                val oldConversationId = session.conversationId
                val startResult = roleplayRepository.restartScenario(scenario.id)
                clearConversationScopedContext(oldConversationId)
                applySessionStartResult(
                    startResult = startResult,
                    scenario = scenario,
                )
                _uiState.update {
                    it.copy(
                        messages = emptyList(),
                        suggestions = emptyList(),
                        streamingContent = "",
                        latestPromptDebugDump = "",
                        isGeneratingSuggestions = false,
                        suggestionErrorMessage = null,
                        noticeMessage = "已重开剧情",
                        showAssistantMismatchDialog = false,
                        previousAssistantName = "",
                        currentAssistantName = "",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.message ?: "重开剧情失败")
                }
            }
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.input.trim()
        val scenario = state.currentScenario
        if (text.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入剧情内容") }
            return
        }
        if (scenario == null) {
            _uiState.update { it.copy(errorMessage = "当前场景不存在") }
            return
        }
        if (!state.settings.hasRequiredConfig()) {
            _uiState.update { it.copy(errorMessage = "请先完成模型配置后再开始剧情互动") }
            return
        }
        if (state.isSending) {
            return
        }

        startRoleplaySend(
            state = state,
            scenario = scenario,
            userParts = listOf(textMessagePart(text)),
            nextInput = "",
        )
    }

    private fun startRoleplaySend(
        state: RoleplayUiState,
        scenario: RoleplayScenario,
        userParts: List<ChatMessagePart>,
        nextInput: String,
    ) {
        cancelSuggestionGeneration(resetState = false)
        _uiState.update {
            it.copy(
                suggestions = emptyList(),
                input = nextInput,
                isSending = true,
                isGeneratingSuggestions = false,
                errorMessage = null,
                streamingContent = "",
                suggestionErrorMessage = null,
            )
        }

        sendingJob = viewModelScope.launch {
            val startResult = if (state.currentSession == null) {
                roleplayRepository.startScenario(scenario.id)
            } else {
                null
            }
            startResult?.let {
                applySessionStartResult(
                    startResult = it,
                    scenario = scenario,
                )
            }
            if (startResult?.assistantMismatch == true) {
                val restoredInput = userParts.toPlainText().ifBlank { nextInput }
                _uiState.update {
                    it.copy(
                        input = restoredInput,
                        isSending = false,
                        streamingContent = "",
                    )
                }
                sendingJob = null
                return@launch
            }

            val session = startResult?.session ?: state.currentSession
                ?: error("当前剧情会话不存在")
            val selectedModel = resolveSelectedModelId(state.settings)
            val assistant = resolveAssistant(state.settings, scenario.assistantId)
            val existingMessages = conversationRepository.listMessages(session.conversationId)
            val userMessage = buildMessage(
                conversationId = session.conversationId,
                role = MessageRole.USER,
                content = userParts.toContentMirror(specialFallback = "转账").ifBlank { "剧情互动" },
                parts = userParts,
            )
            val loadingMessage = buildMessage(
                conversationId = session.conversationId,
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.LOADING,
                modelName = selectedModel,
            )
            val requestMessages = existingMessages.filter {
                it.status == MessageStatus.COMPLETED && it.hasSendableContent()
            } + userMessage
            val initialMessages = existingMessages + userMessage + loadingMessage
            currentRawMessages.value = initialMessages

            executeRoleplayRoundTrip(
                state = state,
                scenario = scenario,
                session = session,
                selectedModel = selectedModel,
                assistant = assistant,
                requestMessages = requestMessages,
                initialMessages = initialMessages,
                loadingMessage = loadingMessage,
                buildFinalMessages = { completedAssistant ->
                    existingMessages + userMessage + completedAssistant
                },
            )
        }
    }

    private suspend fun executeRoleplayRoundTrip(
        state: RoleplayUiState,
        scenario: RoleplayScenario,
        session: RoleplaySession,
        selectedModel: String,
        assistant: Assistant?,
        requestMessages: List<ChatMessage>,
        initialMessages: List<ChatMessage>,
        loadingMessage: ChatMessage,
        buildFinalMessages: (ChatMessage) -> List<ChatMessage>,
    ) {
        try {
            conversationRepository.saveConversationMessages(
                conversationId = session.conversationId,
                messages = initialMessages,
                selectedModel = selectedModel,
            )
            val conversation = conversationRepository.getConversation(session.conversationId)
                ?: Conversation(
                    id = session.conversationId,
                    createdAt = nowProvider(),
                    updatedAt = nowProvider(),
                    assistantId = scenario.assistantId,
                )
            val promptContext = promptContextAssembler.assemble(
                settings = state.settings,
                assistant = assistant,
                conversation = conversation,
                userInputText = resolveLatestUserInputText(requestMessages),
                recentMessages = requestMessages,
                promptMode = PromptMode.ROLEPLAY,
            )
            val directorNote = buildDynamicDirectorNote(
                messages = requestMessages,
                scenario = scenario,
                assistant = assistant,
                settings = state.settings,
            )
            val decoratedPrompt = RoleplayPromptDecorator.decorate(
                baseSystemPrompt = promptContext.systemPrompt,
                scenario = scenario,
                assistant = assistant,
                settings = state.settings,
                includeOpeningNarrationReference = initialMessages.none { it.role == MessageRole.USER },
                directorNote = directorNote,
            )
            _uiState.update {
                it.copy(
                    contextStatus = it.contextStatus.copy(
                        hasSummary = promptContext.summaryCoveredMessageCount > 0,
                        summaryCoveredMessageCount = promptContext.summaryCoveredMessageCount,
                        worldBookHitCount = promptContext.worldBookHitCount,
                        memoryInjectionCount = promptContext.memoryInjectionCount,
                    ),
                    latestPromptDebugDump = buildString {
                        append(promptContext.debugDump)
                        if (decoratedPrompt.isNotBlank()) {
                            append("\n\n【RP 装饰后提示词】\n")
                            append(decoratedPrompt)
                        }
                    },
                )
            }

            val effectiveRequestMessages = resolveRequestMessagesForRoundTrip(
                conversation = conversation,
                assistant = assistant,
                requestMessages = requestMessages,
            )
            val fullContent = StringBuilder()
            val fullReasoning = StringBuilder()
            val fullParts = mutableListOf<ChatMessagePart>()

            repository.sendMessageStream(
                messages = effectiveRequestMessages,
                systemPrompt = decoratedPrompt,
                promptMode = PromptMode.ROLEPLAY,
            ).collect { event ->
                when (event) {
                    is ChatStreamEvent.ContentDelta -> {
                        fullContent.append(event.value)
                        _uiState.update {
                            it.copy(
                                streamingContent = outputParser.stripMarkup(fullContent.toString()),
                            )
                        }
                    }

                    is ChatStreamEvent.ReasoningDelta -> fullReasoning.append(event.value)
                    is ChatStreamEvent.ImageDelta -> {
                        fullParts += imageMessagePart(
                            uri = event.part.uri,
                            mimeType = event.part.mimeType,
                            fileName = event.part.fileName,
                        )
                    }

                    ChatStreamEvent.Completed -> Unit
                }
            }

            val parsedOutput = repository.parseAssistantSpecialOutput(
                content = fullContent.toString().trim(),
                existingParts = fullParts,
            )
            val completedAssistant = loadingMessage.copy(
                content = parsedOutput.content.takeIf { it.isNotBlank() }
                    ?: if (parsedOutput.transferUpdates.isNotEmpty()) {
                        "已收款"
                    } else {
                        parsedOutput.parts.toContentMirror(
                            imageFallback = "角色返回了图片",
                            specialFallback = "角色已回应",
                        ).ifBlank { "模型未返回有效内容" }
                    },
                status = MessageStatus.COMPLETED,
                reasoningContent = fullReasoning.toString(),
                parts = parsedOutput.parts,
            )
            val completedMessages = updateTransferStatuses(
                messages = buildFinalMessages(completedAssistant),
                updates = parsedOutput.transferUpdates,
            )
            conversationRepository.saveConversationMessages(
                conversationId = session.conversationId,
                messages = completedMessages,
                selectedModel = selectedModel,
            )
            currentRawMessages.value = completedMessages
            finishSending(errorMessage = null)
            launchConversationSummaryGeneration(
                conversationId = session.conversationId,
                completedMessages = completedMessages,
                settings = state.settings,
                assistant = assistant,
                scenario = scenario,
            )
            launchAutomaticMemoryExtraction(
                conversationId = session.conversationId,
                completedMessages = completedMessages,
                settings = state.settings,
                assistant = assistant,
                scenario = scenario,
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                finishSending(errorMessage = null)
                throw throwable
            }
            val errorText = throwable.message ?: "发送失败"
            val failedAssistant = loadingMessage.copy(
                content = errorText,
                status = MessageStatus.ERROR,
                parts = emptyList(),
            )
            val failedMessages = buildFinalMessages(failedAssistant)
            conversationRepository.saveConversationMessages(
                conversationId = session.conversationId,
                messages = failedMessages,
                selectedModel = selectedModel,
            )
            currentRawMessages.value = failedMessages
            finishSending(errorMessage = errorText)
        } finally {
            sendingJob = null
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settings.collect { currentSettings ->
                val scenario = _uiState.value.currentScenario
                val currentAssistant = scenario?.let { resolveAssistant(currentSettings, it.assistantId) }
                val activeProvider = currentSettings.activeProvider()
                _uiState.update {
                    it.copy(
                        settings = currentSettings,
                        currentAssistant = currentAssistant,
                        currentModel = activeProvider?.selectedModel.orEmpty(),
                        currentProviderId = activeProvider?.id.orEmpty(),
                    )
                }
            }
        }
    }

    private fun observeScenarios() {
        viewModelScope.launch {
            roleplayRepository.observeScenarios().collect { scenarios ->
                _uiState.update { state ->
                    val resolvedCurrentScenario = state.currentScenario?.let { current ->
                        scenarios.firstOrNull { it.id == current.id }
                    }
                    state.copy(
                        scenarios = scenarios,
                        currentScenario = resolvedCurrentScenario
                            ?: state.currentScenario?.takeIf { currentScenarioId.value == it.id },
                    )
                }
            }
        }
    }

    private fun observeSessions() {
        viewModelScope.launch {
            roleplayRepository.observeSessions().collect { sessions ->
                _uiState.update { state ->
                    state.copy(
                        scenarioSessionIds = sessions.mapTo(linkedSetOf()) { it.scenarioId },
                    )
                }
            }
        }
    }

    private fun observeCurrentScenario() {
        viewModelScope.launch {
            currentScenarioId
                .flatMapLatest { scenarioId ->
                    if (scenarioId.isNullOrBlank()) {
                        flowOf(null)
                    } else {
                        roleplayRepository.observeScenario(scenarioId)
                    }
                }
                .collect { scenario ->
                    _uiState.update { state ->
                        state.copy(
                            currentScenario = scenario,
                            currentAssistant = scenario?.let {
                                resolveAssistant(state.settings, it.assistantId)
                            },
                            isScenarioLoading = false,
                        )
                    }
                }
        }
    }

    private fun observeCurrentSession() {
        viewModelScope.launch {
            currentScenarioId
                .flatMapLatest { scenarioId ->
                    if (scenarioId.isNullOrBlank()) {
                        flowOf(null)
                    } else {
                        roleplayRepository.observeSessionByScenario(scenarioId)
                    }
                }
                .collect { session ->
                    _uiState.update { state ->
                        state.copy(
                            currentSession = session,
                            contextStatus = if (session == null) {
                                RoleplayContextStatus()
                            } else {
                                state.contextStatus
                            },
                        )
                    }
                    refreshContextStatus(
                        conversationId = session?.conversationId,
                        isContinuingSession = _uiState.value.contextStatus.isContinuingSession,
                    )
                }
        }
    }

    private fun observeCurrentMessages() {
        viewModelScope.launch {
            currentScenarioId
                .flatMapLatest { scenarioId ->
                    if (scenarioId.isNullOrBlank()) {
                        flowOf(emptyList())
                    } else {
                        roleplayRepository.observeConversationMessages(scenarioId)
                    }
                }
                .collect { messages ->
                    currentRawMessages.value = messages
                }
        }
    }

    private fun observeMappedMessages() {
        viewModelScope.launch {
            combine(currentRawMessages, settings, _uiState) { rawMessages, settingsState, uiStateState ->
                mapMessages(
                    scenario = uiStateState.currentScenario,
                    assistant = uiStateState.currentAssistant,
                    settings = settingsState,
                    rawMessages = rawMessages,
                    streamingContent = uiStateState.streamingContent.takeIf { uiStateState.isSending },
                )
            }.collect { mappedMessages ->
                _uiState.update { it.copy(messages = mappedMessages) }
            }
        }
    }

    private fun mapMessages(
        scenario: RoleplayScenario?,
        assistant: Assistant?,
        settings: AppSettings,
        rawMessages: List<ChatMessage>,
        streamingContent: String?,
    ): List<RoleplayMessageUiModel> {
        if (scenario == null) {
            return emptyList()
        }
        val userName = scenario.userDisplayNameOverride.trim()
            .ifBlank { settings.resolvedUserDisplayName() }
        val characterName = scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }

        val mappedMessages = buildList<RoleplayMessageUiModel> {
            rawMessages.forEach { message ->
                when (message.role) {
                    MessageRole.USER -> {
                        appendRoleplayUserMessages(
                            target = this,
                            message = message,
                            userName = userName,
                        )
                    }

                    MessageRole.ASSISTANT -> {
                        appendRoleplayAssistantMessages(
                            target = this,
                            message = message,
                            scenario = scenario,
                            userName = userName,
                            characterName = characterName,
                        )
                    }
                }
            }
            if (!streamingContent.isNullOrBlank()) {
                add(
                    RoleplayMessageUiModel(
                        sourceMessageId = "streaming",
                        contentType = if (scenario.longformModeEnabled) {
                            RoleplayContentType.LONGFORM
                        } else {
                            RoleplayContentType.DIALOGUE
                        },
                        speaker = RoleplaySpeaker.CHARACTER,
                        speakerName = characterName,
                        content = streamingContent,
                        createdAt = nowProvider(),
                        isStreaming = true,
                        messageStatus = MessageStatus.LOADING,
                        copyText = outputParser.stripMarkup(streamingContent),
                    ),
                )
            }
        }

        return mappedMessages
    }

    private fun appendRoleplayUserMessages(
        target: MutableList<RoleplayMessageUiModel>,
        message: ChatMessage,
        userName: String,
    ) {
        val normalizedParts = normalizeChatMessageParts(message.parts)
        val initialSize = target.size
        if (normalizedParts.isEmpty()) {
            val content = message.content.trim().ifBlank { "（无文本内容）" }
            target += RoleplayMessageUiModel(
                sourceMessageId = message.id,
                contentType = RoleplayContentType.DIALOGUE,
                speaker = RoleplaySpeaker.USER,
                speakerName = userName,
                content = content,
                createdAt = message.createdAt,
                messageStatus = message.status,
                copyText = content,
            )
            return
        }

        normalizedParts.forEach { part ->
            when {
                part.isTransferPart() -> {
                    target += RoleplayMessageUiModel(
                        sourceMessageId = message.id,
                        contentType = RoleplayContentType.SPECIAL_TRANSFER,
                        speaker = RoleplaySpeaker.USER,
                        speakerName = userName,
                        content = "",
                        createdAt = message.createdAt,
                        messageStatus = message.status,
                        copyText = part.toTransferCopyText(),
                        specialPart = part,
                    )
                }

                part.text.isNotBlank() -> {
                    val content = part.text.trim()
                    target += RoleplayMessageUiModel(
                        sourceMessageId = message.id,
                        contentType = RoleplayContentType.DIALOGUE,
                        speaker = RoleplaySpeaker.USER,
                        speakerName = userName,
                        content = content,
                        createdAt = message.createdAt,
                        messageStatus = message.status,
                        copyText = content,
                    )
                }
            }
        }
        if (target.size == initialSize && message.content.isNotBlank()) {
            val content = message.content.trim()
            target += RoleplayMessageUiModel(
                sourceMessageId = message.id,
                contentType = RoleplayContentType.DIALOGUE,
                speaker = RoleplaySpeaker.USER,
                speakerName = userName,
                content = content,
                createdAt = message.createdAt,
                messageStatus = message.status,
                copyText = content,
            )
        }
    }

    private fun appendRoleplayAssistantMessages(
        target: MutableList<RoleplayMessageUiModel>,
        message: ChatMessage,
        scenario: RoleplayScenario,
        userName: String,
        characterName: String,
    ) {
        val normalizedParts = normalizeChatMessageParts(message.parts)
        val initialSize = target.size
        val canRetry = !message.id.startsWith("opening-narration:") &&
            (message.status == MessageStatus.COMPLETED || message.status == MessageStatus.ERROR)
        if (scenario.longformModeEnabled) {
            val specialParts = normalizedParts.filter { it.isTransferPart() }
            specialParts.forEach { part ->
                target += RoleplayMessageUiModel(
                    sourceMessageId = message.id,
                    contentType = RoleplayContentType.SPECIAL_TRANSFER,
                    speaker = RoleplaySpeaker.CHARACTER,
                    speakerName = characterName,
                    content = "",
                    createdAt = message.createdAt,
                    messageStatus = message.status,
                    copyText = part.toTransferCopyText(),
                    canRetry = canRetry,
                    specialPart = part,
                )
            }
            val longformContent = normalizedParts.toPlainText()
                .ifBlank { message.content.trim() }
                .trim()
            if (message.status == MessageStatus.LOADING && longformContent.isBlank()) {
                return
            }
            if (longformContent.isNotBlank()) {
                target += RoleplayMessageUiModel(
                    sourceMessageId = message.id,
                    contentType = RoleplayContentType.LONGFORM,
                    speaker = RoleplaySpeaker.CHARACTER,
                    speakerName = characterName,
                    content = longformContent,
                    createdAt = message.createdAt,
                    messageStatus = message.status,
                    copyText = longformContent,
                    canRetry = canRetry,
                )
            }
            return
        }
        if (normalizedParts.isEmpty()) {
            val content = message.content.trim()
            if (message.status == MessageStatus.LOADING && content.isBlank()) {
                return
            }
            appendAssistantTextSegments(
                target = target,
                sourceMessageId = message.id,
                rawContent = content,
                userName = userName,
                characterName = characterName,
                allowNarration = scenario.enableNarration,
                createdAt = message.createdAt,
                messageStatus = message.status,
                canRetry = canRetry,
            )
            return
        }

        normalizedParts.forEach { part ->
            when {
                part.isTransferPart() -> {
                    target += RoleplayMessageUiModel(
                        sourceMessageId = message.id,
                        contentType = RoleplayContentType.SPECIAL_TRANSFER,
                        speaker = RoleplaySpeaker.CHARACTER,
                        speakerName = characterName,
                        content = "",
                        createdAt = message.createdAt,
                        messageStatus = message.status,
                        copyText = part.toTransferCopyText(),
                        canRetry = canRetry,
                        specialPart = part,
                    )
                }

                part.text.isNotBlank() -> {
                    appendAssistantTextSegments(
                        target = target,
                        sourceMessageId = message.id,
                        rawContent = part.text,
                        userName = userName,
                        characterName = characterName,
                        allowNarration = scenario.enableNarration,
                        createdAt = message.createdAt,
                        messageStatus = message.status,
                        canRetry = canRetry,
                    )
                }
            }
        }
        if (target.size == initialSize && message.content.isNotBlank()) {
            appendAssistantTextSegments(
                target = target,
                sourceMessageId = message.id,
                rawContent = message.content,
                userName = userName,
                characterName = characterName,
                allowNarration = scenario.enableNarration,
                createdAt = message.createdAt,
                messageStatus = message.status,
                canRetry = canRetry,
            )
        }
    }

    private fun appendAssistantTextSegments(
        target: MutableList<RoleplayMessageUiModel>,
        sourceMessageId: String,
        rawContent: String,
        userName: String,
        characterName: String,
        allowNarration: Boolean,
        createdAt: Long,
        messageStatus: MessageStatus,
        canRetry: Boolean,
    ) {
        val normalizedContent = rawContent.trim()
        if (normalizedContent.isBlank()) {
            return
        }
        outputParser.parseAssistantOutput(
            rawContent = normalizedContent,
            characterName = characterName,
            allowNarration = allowNarration,
        ).forEach { segment ->
            target += RoleplayMessageUiModel(
                sourceMessageId = sourceMessageId,
                contentType = segment.contentType,
                speaker = segment.speaker,
                speakerName = when (segment.speaker) {
                    RoleplaySpeaker.USER -> userName
                    RoleplaySpeaker.CHARACTER -> characterName
                    RoleplaySpeaker.NARRATOR -> "旁白"
                    RoleplaySpeaker.SYSTEM -> segment.speakerName
                },
                content = segment.content,
                emotion = segment.emotion,
                createdAt = createdAt,
                messageStatus = messageStatus,
                copyText = segment.content,
                canRetry = canRetry,
            )
        }
    }

    private fun resolveLatestUserInputText(requestMessages: List<ChatMessage>): String {
        val latestUserMessage = requestMessages.lastOrNull { it.role == MessageRole.USER } ?: return ""
        return latestUserMessage.parts.toPlainText()
            .ifBlank { latestUserMessage.content.trim() }
    }

    private fun updateTransferStatuses(
        messages: List<ChatMessage>,
        updates: List<TransferUpdateDirective>,
    ): List<ChatMessage> {
        if (updates.isEmpty()) {
            return messages
        }

        var changed = false
        val updatedMessages = messages.map { message ->
            val updatedParts = message.parts.map { part ->
                val update = updates.firstOrNull { it.refId == part.specialId } ?: return@map part
                if (!part.isTransferPart() || part.specialStatus == update.status) {
                    return@map part
                }
                changed = true
                part.copy(specialStatus = update.status)
            }
            if (updatedParts == message.parts) {
                message
            } else {
                val normalizedUpdatedParts = normalizeChatMessageParts(updatedParts)
                message.copy(
                    parts = normalizedUpdatedParts,
                    content = normalizedUpdatedParts.toContentMirror(
                        specialFallback = "转账",
                    ).ifBlank { message.content },
                )
            }
        }
        return if (changed) updatedMessages else messages
    }

    private suspend fun resolveRequestMessagesForRoundTrip(
        conversation: Conversation,
        assistant: Assistant?,
        requestMessages: List<ChatMessage>,
    ): List<ChatMessage> {
        val summary = conversationSummaryRepository.getSummary(conversation.id)
        if (summary?.summary?.isBlank() != false) {
            return requestMessages
        }
        val recentWindow = assistant?.contextMessageSize?.takeIf { it > 0 }
            ?: SUMMARY_RECENT_MESSAGE_WINDOW
        if (requestMessages.size <= recentWindow) {
            return requestMessages
        }
        return requestMessages.takeLast(recentWindow)
    }

    private fun launchConversationSummaryGeneration(
        conversationId: String,
        completedMessages: List<ChatMessage>,
        settings: AppSettings,
        assistant: Assistant?,
        scenario: RoleplayScenario,
    ) {
        if (completedMessages.size <= SUMMARY_TRIGGER_MESSAGE_COUNT) {
            return
        }
        val activeProvider = settings.activeProvider() ?: return
        val summaryModel = activeProvider.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY)
        if (summaryModel.isBlank()) {
            return
        }
        viewModelScope.launch {
            val olderMessages = completedMessages.dropLast(SUMMARY_RECENT_MESSAGE_WINDOW)
            if (olderMessages.size < SUMMARY_MIN_COVERED_MESSAGE_COUNT) {
                return@launch
            }
            val existingSummary = conversationSummaryRepository.getSummary(conversationId)
            if (existingSummary != null && existingSummary.coveredMessageCount >= olderMessages.size) {
                return@launch
            }
            val summaryInput = buildSummaryInputText(
                messages = olderMessages,
                scenario = scenario,
                assistant = assistant,
                settings = settings,
            )
            if (summaryInput.isBlank()) {
                return@launch
            }
            runCatching {
                repository.generateRoleplayConversationSummary(
                    conversationText = summaryInput,
                    baseUrl = activeProvider.baseUrl,
                    apiKey = activeProvider.apiKey,
                    modelId = summaryModel,
                )
            }.onSuccess { summaryText ->
                conversationSummaryRepository.upsertSummary(
                    ConversationSummary(
                        conversationId = conversationId,
                        assistantId = assistant?.id.orEmpty(),
                        summary = summaryText,
                        coveredMessageCount = olderMessages.size,
                        updatedAt = nowProvider(),
                    ),
                )
                refreshContextStatus(
                    conversationId = conversationId,
                    isContinuingSession = _uiState.value.contextStatus.isContinuingSession,
                )
            }
        }
    }

    private fun launchAutomaticMemoryExtraction(
        conversationId: String,
        completedMessages: List<ChatMessage>,
        settings: AppSettings,
        assistant: Assistant?,
        scenario: RoleplayScenario,
    ) {
        val targetAssistant = assistant ?: return
        if (!targetAssistant.memoryEnabled) {
            return
        }
        val activeProvider = settings.activeProvider() ?: return
        val memoryModel = activeProvider.resolveFunctionModel(ProviderFunction.MEMORY)
        if (memoryModel.isBlank()) {
            return
        }
        viewModelScope.launch {
            val recentMessages = completedMessages.takeLast(AUTO_MEMORY_MESSAGE_WINDOW)
            val memoryInput = buildMemoryExtractionInput(
                messages = recentMessages,
                scenario = scenario,
                assistant = assistant,
                settings = settings,
            )
            if (memoryInput.isBlank()) {
                return@launch
            }
            val latestMessageId = recentMessages.lastOrNull()?.id.orEmpty()
            runCatching {
                repository.generateRoleplayMemoryEntries(
                    conversationExcerpt = memoryInput,
                    baseUrl = activeProvider.baseUrl,
                    apiKey = activeProvider.apiKey,
                    modelId = memoryModel,
                )
            }.onSuccess { memoryResult ->
                if (memoryResult.persistentMemories.isEmpty() && memoryResult.sceneStateMemories.isEmpty()) {
                    return@onSuccess
                }
                persistRoleplayMemories(
                    conversationId = conversationId,
                    assistant = targetAssistant,
                    latestMessageId = latestMessageId,
                    persistentMemories = memoryResult.persistentMemories,
                    sceneStateMemories = memoryResult.sceneStateMemories,
                    baseUrl = activeProvider.baseUrl,
                    apiKey = activeProvider.apiKey,
                    modelId = memoryModel,
                )
            }
        }
    }

    private suspend fun persistRoleplayMemories(
        conversationId: String,
        assistant: Assistant,
        latestMessageId: String,
        persistentMemories: List<String>,
        sceneStateMemories: List<String>,
        baseUrl: String,
        apiKey: String,
        modelId: String,
    ) {
        val existingEntries = memoryRepository.listEntries().toMutableList()
        val longTermScopeType = if (assistant.useGlobalMemory) {
            MemoryScopeType.GLOBAL
        } else {
            MemoryScopeType.ASSISTANT
        }
        val longTermScopeId = if (assistant.useGlobalMemory) {
            ""
        } else {
            assistant.id
        }
        persistMemoryGroup(
            existingEntries = existingEntries,
            scopeType = longTermScopeType,
            scopeId = longTermScopeId,
            memoryItems = persistentMemories,
            latestMessageId = latestMessageId,
            importance = 60,
            maxItems = assistant.memoryMaxItems.coerceAtLeast(1),
            condensedTargetCount = assistant.memoryMaxItems.coerceAtLeast(1).coerceAtMost(3),
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId,
            condenseMode = RoleplayMemoryCondenseMode.CHARACTER,
        )
        persistMemoryGroup(
            existingEntries = existingEntries,
            scopeType = MemoryScopeType.CONVERSATION,
            scopeId = conversationId,
            memoryItems = sceneStateMemories,
            latestMessageId = latestMessageId,
            importance = 70,
            maxItems = ROLEPLAY_SCENE_MEMORY_MAX_ITEMS,
            condensedTargetCount = 4,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId,
            condenseMode = RoleplayMemoryCondenseMode.SCENE,
        )
    }

    private suspend fun persistMemoryGroup(
        existingEntries: MutableList<MemoryEntry>,
        scopeType: MemoryScopeType,
        scopeId: String,
        memoryItems: List<String>,
        latestMessageId: String,
        importance: Int,
        maxItems: Int,
        condensedTargetCount: Int,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        condenseMode: RoleplayMemoryCondenseMode,
    ) {
        val normalizedScopeId = scopeId.trim()
        if (normalizedScopeId.isBlank() || memoryItems.isEmpty()) {
            return
        }
        val scopeEntries = existingEntries.filter { entry ->
            entry.scopeType == scopeType && entry.resolvedScopeId() == normalizedScopeId
        }
        val pinnedEntries = scopeEntries.filter { it.pinned }
        val mutableEntries = scopeEntries.filterNot { it.pinned }
        val timestamp = nowProvider()
        val normalizedNewItems = memoryItems
            .map(::normalizeMemoryContent)
            .filter { it.isNotBlank() }
            .distinct()
        val combinedItems = (mutableEntries.map { entry -> normalizeMemoryContent(entry.content) } + normalizedNewItems)
            .filter { it.isNotBlank() }
            .distinct()
        val condensedItems = if (combinedItems.size > condensedTargetCount.coerceAtLeast(1)) {
            runCatching {
                repository.condenseRoleplayMemories(
                    memoryItems = combinedItems,
                    mode = condenseMode,
                    maxItems = condensedTargetCount.coerceAtLeast(1),
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    modelId = modelId,
                )
            }.getOrDefault(combinedItems.take(condensedTargetCount.coerceAtLeast(1)))
        } else {
            combinedItems
        }
        mutableEntries.forEach { entry ->
            memoryRepository.deleteEntry(entry.id)
            existingEntries.removeAll { current -> current.id == entry.id }
        }
        val availableSlots = (maxItems - pinnedEntries.size).coerceAtLeast(0)
        condensedItems
            .take(availableSlots)
            .forEachIndexed { index, content ->
                val entry = MemoryEntry(
                    scopeType = scopeType,
                    scopeId = normalizedScopeId,
                    content = content,
                    importance = importance,
                    sourceMessageId = latestMessageId.trim(),
                    lastUsedAt = timestamp + index,
                    createdAt = timestamp + index,
                    updatedAt = timestamp + index,
                )
                memoryRepository.upsertEntry(entry)
                existingEntries += entry
            }
        pruneMemoryScope(
            existingEntries = existingEntries,
            scopeType = scopeType,
            scopeId = normalizedScopeId,
            maxItems = maxItems,
        )
    }

    private suspend fun pruneMemoryScope(
        existingEntries: MutableList<MemoryEntry>,
        scopeType: MemoryScopeType,
        scopeId: String,
        maxItems: Int,
    ) {
        val scopedEntries = existingEntries
            .filter { entry ->
                entry.scopeType == scopeType && entry.resolvedScopeId() == scopeId
            }
            .sortedWith(
                compareByDescending<MemoryEntry> { it.pinned }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.lastUsedAt }
                    .thenByDescending { it.updatedAt },
            )
        scopedEntries
            .drop(maxItems.coerceAtLeast(1))
            .forEach { entry ->
                memoryRepository.deleteEntry(entry.id)
                existingEntries.removeAll { current -> current.id == entry.id }
            }
    }

    private suspend fun clearConversationScopedContext(conversationId: String) {
        conversationSummaryRepository.deleteSummary(conversationId)
        memoryRepository.listEntries()
            .filter { entry ->
                entry.scopeType == MemoryScopeType.CONVERSATION &&
                    entry.resolvedScopeId() == conversationId
            }
            .forEach { entry ->
                memoryRepository.deleteEntry(entry.id)
            }
    }

    private fun buildMemoryExtractionInput(
        messages: List<ChatMessage>,
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
    ): String {
        val (userName, characterName) = resolveRoleplayNames(
            scenario = scenario,
            assistant = assistant,
            settings = settings,
        )
        return RoleplayTranscriptFormatter.formatMessages(
            messages = messages,
            userName = userName,
            characterName = characterName,
            allowNarration = scenario.enableNarration,
        ).take(MAX_MEMORY_INPUT_LENGTH)
    }

    private fun normalizeMemoryContent(value: String): String {
        return value.trim()
            .replace(Regex("\\s+"), " ")
            .removePrefix("-")
            .removePrefix("•")
            .trim()
    }

    private fun buildSummaryInputText(
        messages: List<ChatMessage>,
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
    ): String {
        val (userName, characterName) = resolveRoleplayNames(
            scenario = scenario,
            assistant = assistant,
            settings = settings,
        )
        return RoleplayTranscriptFormatter.formatMessages(
            messages = messages,
            userName = userName,
            characterName = characterName,
            allowNarration = scenario.enableNarration,
        ).take(MAX_SUMMARY_INPUT_LENGTH)
    }

    private fun buildPlayerStyleReference(
        messages: List<ChatMessage>,
    ): String {
        return messages
            .filter { it.role == MessageRole.USER }
            .takeLast(3)
            .mapNotNull { message ->
                message.parts.toPlainText()
                    .ifBlank { message.content }
                    .trim()
                    .takeIf { it.isNotBlank() }
            }
            .joinToString(separator = "\n") { line -> "- $line" }
    }

    private fun buildDynamicDirectorNote(
        messages: List<ChatMessage>,
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
    ): String {
        val (userName, characterName) = resolveRoleplayNames(
            scenario = scenario,
            assistant = assistant,
            settings = settings,
        )
        val recentUserInput = messages
            .lastOrNull { it.role == MessageRole.USER }
            ?.parts
            ?.toPlainText()
            .orEmpty()
            .ifBlank { messages.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty() }
            .trim()
        val repeatedOpeners = messages
            .filter { it.role == MessageRole.ASSISTANT }
            .takeLast(3)
            .mapNotNull { message ->
                val plainText = outputParser.stripMarkup(
                    message.parts.toPlainText()
                        .ifBlank { message.content },
                ).trim()
                plainText.takeIf { it.isNotBlank() }?.take(10)
            }
            .distinct()
        val recentEmotions = messages
            .filter { it.role == MessageRole.ASSISTANT }
            .takeLast(3)
            .flatMap { message ->
                outputParser.parseAssistantOutput(
                    rawContent = message.parts.toPlainText().ifBlank { message.content },
                    characterName = characterName,
                    allowNarration = scenario.enableNarration,
                ).mapNotNull { segment ->
                    segment.emotion.trim().takeIf { it.isNotBlank() }
                }
            }
            .distinct()
        return buildString {
            if (recentUserInput.isNotBlank()) {
                append("优先回应 ")
                append(userName.ifBlank { "玩家" })
                append(" 刚刚提到的具体细节：")
                append(recentUserInput.take(80))
                append("。\n")
            }
            if (repeatedOpeners.isNotEmpty()) {
                append("避免直接复用最近出现过的起手句或动作模板：")
                append(repeatedOpeners.joinToString("、"))
                append("。\n")
            }
            if (recentEmotions.isNotEmpty()) {
                append("减少重复使用这些情绪标签：")
                append(recentEmotions.joinToString("、"))
                append("。\n")
            }
            append("允许停顿、试探、反问和转折，让 ")
            append(characterName)
            append(" 像在临场反应，不要每轮都完整解释动机。\n")
            append("这一轮至少推进一项：关系、信息或局势。")
        }.trim()
    }

    private fun resolveRoleplayNames(
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
    ): Pair<String, String> {
        val userName = scenario.userDisplayNameOverride.trim()
            .ifBlank { settings.resolvedUserDisplayName() }
        val characterName = scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
        return userName to characterName
    }

    private fun applySessionStartResult(
        startResult: RoleplaySessionStartResult,
        scenario: RoleplayScenario,
    ) {
        val state = _uiState.value
        val currentAssistantName = resolveRoleplayNames(
            scenario = scenario,
            assistant = resolveAssistant(state.settings, scenario.assistantId),
            settings = state.settings,
        ).second
        val previousAssistantName = state.settings.resolvedAssistants()
            .firstOrNull { assistant -> assistant.id == startResult.conversationAssistantId }
            ?.name
            ?.trim()
            .orEmpty()
            .ifBlank { "原角色" }

        _uiState.update {
            it.copy(
                currentSession = startResult.session,
                isScenarioLoading = false,
                showAssistantMismatchDialog = startResult.assistantMismatch,
                previousAssistantName = if (startResult.assistantMismatch) previousAssistantName else "",
                currentAssistantName = if (startResult.assistantMismatch) currentAssistantName else "",
                contextStatus = it.contextStatus.copy(
                    isContinuingSession = startResult.hasHistory,
                ),
            )
        }
        refreshContextStatus(
            conversationId = startResult.session.conversationId,
            isContinuingSession = startResult.hasHistory,
        )
    }

    private fun refreshContextStatus(
        conversationId: String?,
        isContinuingSession: Boolean,
        worldBookHitCount: Int = _uiState.value.contextStatus.worldBookHitCount,
        memoryInjectionCount: Int = _uiState.value.contextStatus.memoryInjectionCount,
    ) {
        if (conversationId.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    contextStatus = RoleplayContextStatus(
                        isContinuingSession = isContinuingSession,
                        worldBookHitCount = worldBookHitCount,
                        memoryInjectionCount = memoryInjectionCount,
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            val summary = conversationSummaryRepository.getSummary(conversationId)
            _uiState.update { state ->
                state.copy(
                    contextStatus = state.contextStatus.copy(
                        hasSummary = summary?.summary?.isNotBlank() == true,
                        summaryCoveredMessageCount = summary?.coveredMessageCount ?: 0,
                        worldBookHitCount = worldBookHitCount,
                        memoryInjectionCount = memoryInjectionCount,
                        isContinuingSession = isContinuingSession,
                    ),
                )
            }
        }
    }

    private fun resolveAssistant(settings: AppSettings, assistantId: String): Assistant? {
        return settings.resolvedAssistants().firstOrNull { it.id == assistantId }
            ?: settings.activeAssistant()
    }

    private fun finishSending(errorMessage: String?) {
        _uiState.update {
            it.copy(
                isSending = false,
                streamingContent = "",
                errorMessage = errorMessage,
            )
        }
    }

    private fun resolveSelectedModelId(settings: AppSettings): String {
        return settings.activeProvider()?.selectedModel
            ?.takeIf { it.isNotBlank() }
            ?: settings.selectedModel
    }

    private fun resolveSuggestionModelId(settings: AppSettings): String {
        return settings.activeProvider()
            ?.resolveFunctionModel(ProviderFunction.CHAT_SUGGESTION)
            ?.takeIf { it.isNotBlank() }
            ?: settings.selectedModel
    }

    private fun isSuggestionTargetStillCurrent(
        scenarioId: String,
        conversationId: String,
    ): Boolean {
        val state = _uiState.value
        return state.currentScenario?.id == scenarioId &&
            state.currentSession?.conversationId == conversationId
    }

    private fun cancelSuggestionGeneration(resetState: Boolean = true) {
        suggestionJob?.cancel()
        suggestionJob = null
        if (resetState) {
            _uiState.update {
                it.copy(
                    isGeneratingSuggestions = false,
                    suggestionErrorMessage = null,
                )
            }
        }
    }

    private fun buildMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        status: MessageStatus = MessageStatus.COMPLETED,
        modelName: String = "",
        reasoningContent: String = "",
        parts: List<ChatMessagePart> = emptyList(),
    ): ChatMessage {
        return ChatMessage(
            id = messageIdProvider(),
            conversationId = conversationId,
            role = role,
            content = content,
            status = status,
            createdAt = nowProvider(),
            modelName = modelName,
            reasoningContent = reasoningContent,
            parts = normalizeChatMessageParts(parts),
        )
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
            repository: AiRepository,
            conversationRepository: ConversationRepository,
            roleplayRepository: RoleplayRepository,
            promptContextAssembler: PromptContextAssembler,
            memoryRepository: MemoryRepository,
            conversationSummaryRepository: ConversationSummaryRepository,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RoleplayViewModel(
                        repository = repository,
                        conversationRepository = conversationRepository,
                        roleplayRepository = roleplayRepository,
                        promptContextAssembler = promptContextAssembler,
                        memoryRepository = memoryRepository,
                        conversationSummaryRepository = conversationSummaryRepository,
                    ) as T
                }
            }
        }
    }
}
