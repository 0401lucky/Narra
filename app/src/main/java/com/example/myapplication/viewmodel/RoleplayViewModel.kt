package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.repository.AiRepository
import com.example.myapplication.data.repository.ConversationRepository
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
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.model.imageMessagePart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.textMessagePart
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
                val decoratedPrompt = RoleplayPromptDecorator.decorate(
                    baseSystemPrompt = promptContext.systemPrompt,
                    scenario = scenario,
                    assistant = assistant,
                    settings = latestState.settings,
                    includeOpeningNarrationReference = allMessages.isEmpty(),
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
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    modelId = suggestionModel,
                )
                if (!isSuggestionTargetStillCurrent(scenario.id, session.conversationId)) {
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        suggestions = suggestions,
                        isGeneratingSuggestions = false,
                        suggestionErrorMessage = null,
                    )
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
                errorMessage = null,
                suggestionErrorMessage = null,
            )
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

        cancelSuggestionGeneration(resetState = false)
        _uiState.update {
            it.copy(
                suggestions = emptyList(),
                input = "",
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
                _uiState.update {
                    it.copy(
                        input = text,
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
                content = text,
                parts = listOf(textMessagePart(text)),
            )
            val loadingMessage = buildMessage(
                conversationId = session.conversationId,
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.LOADING,
                modelName = selectedModel,
            )
            val initialMessages = existingMessages + userMessage + loadingMessage
            val requestMessages = existingMessages.filter {
                it.status == MessageStatus.COMPLETED && it.hasSendableContent()
            } + userMessage

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
                    userInputText = text,
                    recentMessages = requestMessages,
                    promptMode = PromptMode.ROLEPLAY,
                )
                val decoratedPrompt = RoleplayPromptDecorator.decorate(
                    baseSystemPrompt = promptContext.systemPrompt,
                    scenario = scenario,
                    assistant = assistant,
                    settings = state.settings,
                    includeOpeningNarrationReference = existingMessages.isEmpty(),
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
                        is ChatStreamEvent.ImageDelta -> fullParts += imageMessagePart(
                            uri = event.part.uri,
                            mimeType = event.part.mimeType,
                            fileName = event.part.fileName,
                        )

                        ChatStreamEvent.Completed -> Unit
                    }
                }

                val rawContent = fullContent.toString().trim()
                val completedParts = normalizeChatMessageParts(
                    buildList {
                        if (rawContent.isNotBlank()) {
                            add(textMessagePart(rawContent))
                        }
                        addAll(fullParts)
                    },
                )
                val completedAssistant = loadingMessage.copy(
                    content = rawContent.ifBlank {
                        completedParts.toContentMirror(
                            imageFallback = "角色返回了图片",
                            specialFallback = "角色已回应",
                        ).ifBlank { "模型未返回有效内容" }
                    },
                    status = MessageStatus.COMPLETED,
                    reasoningContent = fullReasoning.toString(),
                    parts = completedParts,
                )
                val completedMessages = existingMessages + userMessage + completedAssistant
                conversationRepository.saveConversationMessages(
                    conversationId = session.conversationId,
                    messages = completedMessages,
                    selectedModel = selectedModel,
                )
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
                conversationRepository.saveConversationMessages(
                    conversationId = session.conversationId,
                    messages = existingMessages + userMessage + failedAssistant,
                    selectedModel = selectedModel,
                )
                finishSending(errorMessage = errorText)
            } finally {
                sendingJob = null
            }
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
                        val content = message.parts.toPlainText()
                            .ifBlank { message.content.trim() }
                            .ifBlank { "（无文本内容）" }
                        add(
                            RoleplayMessageUiModel(
                                sourceMessageId = message.id,
                                contentType = RoleplayContentType.DIALOGUE,
                                speaker = RoleplaySpeaker.USER,
                                speakerName = userName,
                                content = content,
                                createdAt = message.createdAt,
                            ),
                        )
                    }

                    MessageRole.ASSISTANT -> {
                        val content = message.parts.toPlainText().ifBlank { message.content.trim() }
                        if (message.status == MessageStatus.LOADING && content.isBlank()) {
                            return@forEach
                        }
                        outputParser.parseAssistantOutput(
                            rawContent = content,
                            characterName = characterName,
                            allowNarration = scenario.enableNarration,
                        ).forEach { segment ->
                            add(
                                RoleplayMessageUiModel(
                                    sourceMessageId = message.id,
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
                                    createdAt = message.createdAt,
                                ),
                            )
                        }
                    }
                }
            }
            if (!streamingContent.isNullOrBlank()) {
                add(
                    RoleplayMessageUiModel(
                        sourceMessageId = "streaming",
                        contentType = RoleplayContentType.DIALOGUE,
                        speaker = RoleplaySpeaker.CHARACTER,
                        speakerName = characterName,
                        content = streamingContent,
                        createdAt = nowProvider(),
                        isStreaming = true,
                    ),
                )
            }
        }

        return mappedMessages
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
                repository.generateConversationSummary(
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
    ) {
        val existingEntries = memoryRepository.listEntries().toMutableList()
        persistMemoryGroup(
            existingEntries = existingEntries,
            scopeType = MemoryScopeType.ASSISTANT,
            scopeId = assistant.id,
            memoryItems = persistentMemories,
            latestMessageId = latestMessageId,
            importance = 60,
            maxItems = assistant.memoryMaxItems.coerceAtLeast(1),
        )
        persistMemoryGroup(
            existingEntries = existingEntries,
            scopeType = MemoryScopeType.CONVERSATION,
            scopeId = conversationId,
            memoryItems = sceneStateMemories,
            latestMessageId = latestMessageId,
            importance = 70,
            maxItems = ROLEPLAY_SCENE_MEMORY_MAX_ITEMS,
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
    ) {
        val normalizedScopeId = scopeId.trim()
        if (normalizedScopeId.isBlank() || memoryItems.isEmpty()) {
            return
        }
        val scopeEntries = existingEntries.filter { entry ->
            entry.scopeType == scopeType && entry.resolvedScopeId() == normalizedScopeId
        }
        val existingNormalized = scopeEntries
            .mapTo(mutableSetOf()) { entry -> normalizeMemoryContent(entry.content) }
        val existingSourcePairs = scopeEntries
            .mapTo(mutableSetOf()) { entry ->
                entry.sourceMessageId.trim() to normalizeMemoryContent(entry.content)
            }
        val timestamp = nowProvider()
        memoryItems
            .map(::normalizeMemoryContent)
            .filter { it.isNotBlank() }
            .distinct()
            .forEachIndexed { index, content ->
                val sourcePair = latestMessageId.trim() to content
                if (content in existingNormalized || sourcePair in existingSourcePairs) {
                    return@forEachIndexed
                }
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
                existingNormalized += content
                existingSourcePairs += sourcePair
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
        private const val AUTO_MEMORY_MESSAGE_WINDOW = 8
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
