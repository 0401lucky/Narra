package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.conversation.AssistantRoundTripOutcome
import com.example.myapplication.conversation.AssistantRoundTripRequest
import com.example.myapplication.conversation.AssistantRoundTripResult
import com.example.myapplication.conversation.ChatConversationSupport
import com.example.myapplication.conversation.ChatPromptAssemblyInput
import com.example.myapplication.conversation.ChatSuggestionCoordinator
import com.example.myapplication.conversation.ConversationAssistantRoundTripRunner
import com.example.myapplication.conversation.ContextGovernanceSupport
import com.example.myapplication.conversation.ConversationMemoryExtractionCoordinator
import com.example.myapplication.conversation.ConversationMessageTransforms
import com.example.myapplication.conversation.ConversationSummaryCoordinator
import com.example.myapplication.conversation.ConversationSummaryDebugSupport
import com.example.myapplication.conversation.ConversationTitleCoordinator
import com.example.myapplication.conversation.ConversationTransferCoordinator
import com.example.myapplication.conversation.GiftImageGenerationCoordinator
import com.example.myapplication.conversation.GiftImageGenerationRequest
import com.example.myapplication.conversation.RoundTripInitialPersistence
import com.example.myapplication.conversation.StreamingReplyBuffer
import com.example.myapplication.conversation.SummaryGenerationConfig
import com.example.myapplication.conversation.SummaryUpdateResult
import com.example.myapplication.conversation.StreamedAssistantPayload
import com.example.myapplication.conversation.persistInitialRoundTripState
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.SavedImageFile
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.ai.AiTranslationService
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatReasoningStep
import com.example.myapplication.model.ChatSpecialPlayDraft
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.DEFAULT_CONVERSATION_TITLE
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.isGiftPart
import com.example.myapplication.model.isTransferPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.specialMetadataValue
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.model.transferResultText
import com.example.myapplication.model.withGiftImageGenerating
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: String = "",
    val displayedConversationId: String = "",
    val currentConversationTitle: String = DEFAULT_CONVERSATION_TITLE,
    val messages: List<ChatMessage> = emptyList(),
    val streamingMessageId: String = "",
    val streamingContent: String = "",
    val streamingReasoningContent: String = "",
    val streamingReasoningSteps: List<ChatReasoningStep> = emptyList(),
    val streamingParts: List<ChatMessagePart> = emptyList(),
    val input: String = "",
    val pendingParts: List<ChatMessagePart> = emptyList(),
    val isConversationReady: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val settings: AppSettings = AppSettings(),
    val chatSuggestions: List<String> = emptyList(),
    val chatSuggestionsModelName: String = "",
    val currentAssistant: Assistant? = null,
    val rememberedMessageIds: Set<String> = emptySet(),
    val hasConversationSummary: Boolean = false,
    val summaryCoveredMessageCount: Int = 0,
    val latestPromptDebugDump: String = "",
    val contextGovernance: ContextGovernanceSnapshot? = null,
    val translation: TranslationUiState = TranslationUiState(),
)

data class TranslationUiState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val sourceText: String = "",
    val sourceLabel: String = "",
    val targetLanguage: String = "简体中文",
    val translatedText: String = "",
    val modelName: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val settingsRepository: AiSettingsRepository,
    private val aiGateway: AiGateway,
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val aiTranslationService: AiTranslationService,
    private val conversationRepository: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val promptContextAssembler: PromptContextAssembler,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val messageIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val imageSaver: suspend (String) -> SavedImageFile = { throw IllegalStateException("图片保存未配置") },
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val currentConversationId = MutableStateFlow<String?>(null)
    private val currentAssistantId = MutableStateFlow(DEFAULT_ASSISTANT_ID)
    private val currentMemoryEntries = MutableStateFlow<List<MemoryEntry>>(emptyList())
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var sendingJob: Job? = null
    /** 确保后台 API 请求（摘要/记忆提取）串行执行，避免并发触发 429。 */
    private val backgroundApiMutex = Mutex()
    private val assistantRoundTripRunner = ConversationAssistantRoundTripRunner(
        conversationRepository = conversationRepository,
        aiGateway = aiGateway,
    )
    private val summaryCoordinator = ConversationSummaryCoordinator(
        aiPromptExtrasService = aiPromptExtrasService,
        conversationSummaryRepository = conversationSummaryRepository,
        nowProvider = nowProvider,
    )
    private val titleCoordinator = ConversationTitleCoordinator(
        aiPromptExtrasService = aiPromptExtrasService,
        conversationRepository = conversationRepository,
    )
    private val chatSuggestionCoordinator = ChatSuggestionCoordinator(
        aiPromptExtrasService = aiPromptExtrasService,
    )
    private val memoryExtractionCoordinator = ConversationMemoryExtractionCoordinator(
        aiPromptExtrasService = aiPromptExtrasService,
        memoryRepository = memoryRepository,
        nowProvider = nowProvider,
    )
    private val transferCoordinator = ConversationTransferCoordinator(
        conversationRepository = conversationRepository,
    )
    private val messageMemoryCoordinator = ChatMessageMemoryCoordinator(
        memoryRepository = memoryRepository,
        nowProvider = nowProvider,
    )
    private val imageGenerationSupport = ChatImageGenerationSupport(imageSaver)
    private val giftImageCoordinator = GiftImageGenerationCoordinator(
        aiPromptExtrasService = aiPromptExtrasService,
        aiGateway = aiGateway,
        conversationRepository = conversationRepository,
        imageSaver = imageSaver,
    )

    init {
        observeSettings()
        observeConversations()
        observeMessages()
        observeMemoryEntries()
        observeRememberedMessageIds()
        observeConversationSummary()
        ensureConversation()
    }

    fun updateInput(value: String) {
        _uiState.update { current ->
            ChatStateSupport.updateInput(current, value)
        }
    }

    fun addPendingParts(parts: List<ChatMessagePart>) {
        val normalizedParts = normalizeChatMessageParts(parts)
        if (normalizedParts.isEmpty()) {
            return
        }
        _uiState.update { current ->
            ChatStateSupport.addPendingParts(current, normalizedParts)
        }
    }

    fun removePendingPart(index: Int) {
        _uiState.update { current ->
            ChatStateSupport.removePendingPart(current, index)
        }
    }

    fun clearPendingParts() {
        _uiState.update { current ->
            ChatStateSupport.clearPendingParts(current)
        }
    }

    fun translateDraftInput() {
        when (val resolution = ChatInteractionSupport.resolveDraftTranslation(_uiState.value)) {
            is ChatTranslationResolution.Error -> {
                _uiState.update { current ->
                    ChatStateSupport.applyErrorMessage(current, resolution.message)
                }
            }

            ChatTranslationResolution.NoOp -> Unit

            is ChatTranslationResolution.Ready -> {
                startTranslation(
                    sourceText = resolution.request.sourceText,
                    sourceLabel = resolution.request.sourceLabel,
                )
            }
        }
    }

    fun translateMessage(messageId: String) {
        when (
            val resolution = ChatInteractionSupport.resolveMessageTranslation(
                messages = _uiState.value.messages,
                messageId = messageId,
            )
        ) {
            is ChatTranslationResolution.Error -> {
                _uiState.update { current ->
                    ChatStateSupport.applyErrorMessage(current, resolution.message)
                }
            }

            ChatTranslationResolution.NoOp -> Unit

            is ChatTranslationResolution.Ready -> {
                startTranslation(
                    sourceText = resolution.request.sourceText,
                    sourceLabel = resolution.request.sourceLabel,
                )
            }
        }
    }

    fun dismissTranslationSheet() {
        _uiState.update { current ->
            ChatStateSupport.dismissTranslation(current)
        }
    }

    fun applyTranslationToInput(replace: Boolean) {
        val translatedText = _uiState.value.translation.translatedText.trim()
        if (translatedText.isBlank()) {
            return
        }
        _uiState.update { current ->
            ChatStateSupport.applyTranslationToInput(
                current = current,
                translatedText = translatedText,
                replace = replace,
            )
        }
    }

    fun sendTranslationAsMessage() {
        val translatedText = _uiState.value.translation.translatedText.trim()
        if (translatedText.isBlank()) {
            return
        }
        _uiState.update { current ->
            ChatStateSupport.prepareTranslatedMessageInput(current, translatedText)
        }
        sendMessage()
    }

    fun createConversation() {
        if (_uiState.value.isSending) {
            return
        }
        launchConversationActivation {
            conversationRepository.createConversation(
                ChatConversationSupport.resolveSelectedModelId(_uiState.value.settings),
                currentAssistantId.value,
            )
        }
    }

    fun selectConversation(conversationId: String) {
        val state = _uiState.value
        if (state.isSending || conversationId.isBlank()) {
            return
        }
        val currentConversation = state.conversations.firstOrNull { it.id == conversationId } ?: return
        activateConversation(
            conversationId = currentConversation.id,
            title = currentConversation.title,
        )
    }

    fun deleteCurrentConversation() {
        val state = _uiState.value
        val conversationId = state.currentConversationId
        if (state.isSending || conversationId.isBlank()) {
            return
        }
        launchConversationActivation {
            conversationRepository.deleteConversation(
                conversationId = conversationId,
                selectedModel = ChatConversationSupport.resolveSelectedModelId(state.settings),
                assistantId = currentAssistantId.value,
            )
        }
    }

    fun deleteConversation(conversationId: String) {
        val state = _uiState.value
        if (state.isSending || conversationId.isBlank()) {
            return
        }
        if (state.currentConversationId == conversationId) {
            deleteCurrentConversation()
            return
        }

        viewModelScope.launch {
            conversationRepository.deleteConversationById(conversationId)
        }
    }

    fun clearCurrentConversation() {
        val state = _uiState.value
        val conversationId = state.currentConversationId
        if (state.isSending || conversationId.isBlank()) {
            return
        }
        launchConversationActivation {
            conversationRepository.clearConversation(
                conversationId = conversationId,
                selectedModel = ChatConversationSupport.resolveSelectedModelId(state.settings),
            )
        }
    }

    fun clearConversation(conversationId: String) {
        val state = _uiState.value
        if (state.isSending || conversationId.isBlank()) {
            return
        }
        if (state.currentConversationId == conversationId) {
            clearCurrentConversation()
            return
        }

        viewModelScope.launch {
            val conversation = conversationRepository.getConversation(conversationId) ?: return@launch
            conversationRepository.clearConversation(
                conversationId = conversationId,
                selectedModel = conversation.model.ifBlank { ChatConversationSupport.resolveSelectedModelId(state.settings) },
            )
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        when (val resolution = ChatOutgoingMessageSupport.resolveTextMessage(state)) {
            is ChatOutgoingMessageResolution.Error -> {
                _uiState.update { current ->
                    ChatStateSupport.applyErrorMessage(current, resolution.message)
                }
            }

            ChatOutgoingMessageResolution.NoOp -> Unit

            is ChatOutgoingMessageResolution.Ready -> {
                submitOutgoingMessage(
                    state = state,
                    userParts = resolution.plan.userParts,
                    nextInput = resolution.plan.nextInput,
                    nextPendingParts = resolution.plan.nextPendingParts,
                    imageGenerationPrompt = resolution.plan.imageGenerationPrompt,
                    forceChatRoundTrip = resolution.plan.forceChatRoundTrip,
                )
            }
        }
    }

    fun sendSpecialPlay(draft: ChatSpecialPlayDraft) {
        val state = _uiState.value
        when (
            val resolution = ChatOutgoingMessageSupport.resolveSpecialPlay(
                state = state,
                draft = draft,
            )
        ) {
            is ChatOutgoingMessageResolution.Error -> {
                _uiState.update { current ->
                    ChatStateSupport.applyErrorMessage(current, resolution.message)
                }
            }

            ChatOutgoingMessageResolution.NoOp -> Unit

            is ChatOutgoingMessageResolution.Ready -> {
                submitOutgoingMessage(
                    state = state,
                    userParts = resolution.plan.userParts,
                    nextInput = resolution.plan.nextInput,
                    nextPendingParts = resolution.plan.nextPendingParts,
                    imageGenerationPrompt = resolution.plan.imageGenerationPrompt,
                    forceChatRoundTrip = resolution.plan.forceChatRoundTrip,
                )
            }
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
        val state = _uiState.value
        val conversationId = state.currentConversationId
        if (specialId.isBlank() || conversationId.isBlank()) {
            return
        }

        viewModelScope.launch {
            val updatedMessages = transferCoordinator.confirmReceipt(
                conversationId = conversationId,
                specialId = specialId,
                selectedModel = ChatConversationSupport.resolveSelectedModelId(state.settings),
                currentMessages = ChatConversationSupport.currentConversationMessages(state.messages, conversationId),
            ) ?: return@launch
            _uiState.update { current ->
                ChatViewModelUiUpdates.applyTransferReceiptNotice(current, updatedMessages)
            }
        }
    }

    fun retryMessage(messageId: String) {
        val state = _uiState.value
        if (state.isSending || messageId.isBlank()) {
            return
        }

        val conversationId = validateAndGetConversationId(state) ?: return
        val selectedModel = ChatConversationSupport.resolveSelectedModelId(state.settings)
        val imageGenerationEnabled = ChatConversationSupport.supportsImageGeneration(state.settings, selectedModel)

        val currentMessages = ChatConversationSupport.currentConversationMessages(state.messages, conversationId)
        when (
            val retryResolution = ChatRetrySupport.resolveRetry(
                messageId = messageId,
                currentMessages = currentMessages,
                imageGenerationEnabled = imageGenerationEnabled,
            )
        ) {
            is ChatRetryResolution.Error -> {
                _uiState.update { current ->
                    ChatStateSupport.applyErrorMessage(current, retryResolution.message)
                }
            }

            ChatRetryResolution.NoOp -> Unit

            is ChatRetryResolution.Ready -> {
                _uiState.update { current ->
                    ChatViewModelUiUpdates.beginRetry(
                        current = current,
                        messages = retryResolution.retryMessages,
                        loadingMessageId = retryResolution.loadingMessage.id,
                    )
                }

                if (imageGenerationEnabled) {
                    executeImageGeneration(
                        conversationId = conversationId,
                        loadingMessage = retryResolution.loadingMessage,
                        prompt = retryResolution.retryPrompt,
                        selectedModel = selectedModel,
                        initialPersistence = retryResolution.initialPersistence,
                        buildFinalMessages = retryResolution::buildFinalMessages,
                    )
                } else {
                    executeAssistantRoundTrip(
                        conversationId = conversationId,
                        loadingMessage = retryResolution.loadingMessage,
                        requestMessages = retryResolution.requestMessages,
                        selectedModel = selectedModel,
                        initialPersistence = retryResolution.initialPersistence,
                        buildFinalMessages = retryResolution::buildFinalMessages,
                        promptAssemblyInput = ChatConversationSupport.buildPromptAssemblyInput(
                            settings = state.settings,
                            currentAssistant = state.currentAssistant,
                            currentConversations = state.conversations,
                            fallbackAssistantId = state.currentAssistant?.id
                                ?.takeIf { it.isNotBlank() }
                                ?: currentAssistantId.value,
                            conversationId = conversationId,
                            requestMessages = retryResolution.requestMessages,
                            nowProvider = nowProvider,
                        ),
                    )
                }
            }
        }
    }

    fun editUserMessage(messageId: String) {
        val state = _uiState.value
        val conversationId = state.currentConversationId.takeIf { it.isNotBlank() } ?: return
        if (state.isSending || messageId.isBlank()) {
            return
        }

        val currentMessages = ChatConversationSupport.currentConversationMessages(
            messages = state.messages,
            conversationId = conversationId,
        )
        val preparedEdit = ChatConversationSupport.prepareUserEdit(
            currentMessages = currentMessages,
            sourceMessageId = messageId,
        ) ?: return

        viewModelScope.launch {
            conversationRepository.replaceConversationSnapshot(
                conversationId = conversationId,
                messages = preparedEdit.rewoundMessages,
                selectedModel = ChatConversationSupport.resolveSelectedModelId(state.settings),
            )
            conversationSummaryRepository.deleteSummary(conversationId)
            _uiState.update { current ->
                ChatStateSupport.applyPreparedEdit(
                    current = current,
                    restoredInput = preparedEdit.restoredInput,
                    restoredPendingParts = preparedEdit.restoredPendingParts,
                    rewoundMessages = preparedEdit.rewoundMessages,
                )
            }
        }
    }

    fun cancelSending() {
        sendingJob?.cancel()
        sendingJob = null
    }

    fun clearErrorMessage() {
        _uiState.update { current ->
            ChatStateSupport.clearErrorMessage(current)
        }
    }

    fun clearNoticeMessage() {
        _uiState.update { current ->
            ChatStateSupport.clearNoticeMessage(current)
        }
    }

    fun refreshConversationSummary() {
        val state = _uiState.value
        val conversationId = state.currentConversationId.takeIf { it.isNotBlank() } ?: return
        val assistant = state.currentAssistant ?: state.settings.activeAssistant()
        viewModelScope.launch {
            val result = runCatching {
                updateConversationSummary(
                    conversationId = conversationId,
                    messages = state.messages,
                    settings = state.settings,
                    assistant = assistant,
                    forceRefresh = true,
                )
            }.getOrDefault(SummaryUpdateResult())
            if (result.updated && _uiState.value.currentConversationId == conversationId) {
                rebuildContextGovernanceSnapshot(
                    conversationId = conversationId,
                    messages = state.messages,
                    settings = state.settings,
                    assistant = assistant,
                )
                _uiState.update { current ->
                    ChatStateSupport.applyNoticeMessage(current, "上下文摘要已刷新")
                }
            }
        }
    }

    fun toggleConversationSearch() {
        val state = _uiState.value
        val conversationId = state.currentConversationId
        if (state.isSending || conversationId.isBlank()) {
            return
        }
        val currentConversation = state.conversations.firstOrNull { it.id == conversationId } ?: return
        val nextEnabled = !currentConversation.searchEnabled
        viewModelScope.launch {
            conversationRepository.updateConversationSearchEnabled(
                conversationId = conversationId,
                searchEnabled = nextEnabled,
            )
            val resolvedEnabled = conversationRepository.getConversation(conversationId)
                ?.searchEnabled
                ?: nextEnabled
            _uiState.update { current ->
                current.copy(
                    conversations = current.conversations.map { conversation ->
                        if (conversation.id == conversationId) {
                            conversation.copy(searchEnabled = resolvedEnabled)
                        } else {
                            conversation
                        }
                    },
                    noticeMessage = if (resolvedEnabled) {
                        "当前会话已开启搜索"
                    } else {
                        "当前会话已关闭搜索"
                    },
                )
            }
        }
    }

    fun toggleMessageMemory(messageId: String) {
        val state = _uiState.value
        viewModelScope.launch {
            when (
                val result = messageMemoryCoordinator.toggle(
                    state = state,
                    fallbackAssistantId = currentAssistantId.value,
                    messageId = messageId,
                )
            ) {
                is ChatMemoryToggleResult.Error -> {
                    _uiState.update { current ->
                        ChatStateSupport.applyErrorMessage(current, result.message)
                    }
                    if (result.shouldEnsureConversation) {
                        ensureConversation()
                    }
                }

                is ChatMemoryToggleResult.Notice -> {
                    _uiState.update { current ->
                        ChatStateSupport.applyNoticeMessage(current, result.message)
                    }
                }

                ChatMemoryToggleResult.NoOp -> Unit
            }
        }
    }

    private fun startTranslation(
        sourceText: String,
        sourceLabel: String,
    ) {
        val state = _uiState.value
        val activeProvider = state.settings.activeProvider()
        val translationModel = activeProvider?.resolveFunctionModel(ProviderFunction.TRANSLATION)
            .orEmpty()
        if (translationModel.isBlank()) {
            _uiState.update { current ->
                ChatStateSupport.applyErrorMessage(current, "请先在模型页开启翻译模型")
            }
            return
        }

        ChatTranslationSupport.startTranslation(
            scope = viewModelScope,
            translationService = aiTranslationService,
            sourceText = sourceText,
            onStart = {
                _uiState.update { current ->
                    ChatStateSupport.beginTranslation(
                        current = current,
                        sourceText = sourceText,
                        sourceLabel = sourceLabel,
                        modelName = translationModel,
                    )
                }
            },
            onSuccess = { translatedText ->
                _uiState.update { current ->
                    ChatStateSupport.completeTranslation(current, translatedText)
                }
            },
            onFailure = { message ->
                _uiState.update { current ->
                    ChatStateSupport.failTranslation(current, message)
                }
            },
        )
    }

    private fun validateAndGetConversationId(state: ChatUiState): String? {
        return when (val result = ChatInteractionSupport.validateConversationReadyForSend(state)) {
            is ChatConversationValidationResult.Error -> {
                _uiState.update { current ->
                    ChatStateSupport.applyErrorMessage(current, result.message)
                }
                if (result.shouldEnsureConversation) {
                    ensureConversation()
                }
                null
            }

            is ChatConversationValidationResult.Ready -> result.conversationId
        }
    }

    private fun executeImageGeneration(
        conversationId: String,
        loadingMessage: ChatMessage,
        prompt: String,
        selectedModel: String,
        initialPersistence: RoundTripInitialPersistence,
        buildFinalMessages: (ChatMessage) -> List<ChatMessage>,
    ) {
        sendingJob = viewModelScope.launch {
            conversationRepository.persistInitialRoundTripState(
                conversationId = conversationId,
                selectedModel = selectedModel,
                persistence = initialPersistence,
            )

            try {
                val completedAssistant = imageGenerationSupport.buildCompletedAssistant(
                    loadingMessage = loadingMessage,
                    results = aiGateway.generateImage(prompt),
                )
                conversationRepository.upsertMessages(
                    conversationId = conversationId,
                    messages = listOf(completedAssistant),
                    selectedModel = selectedModel,
                )
                val completedMessages = buildFinalMessages(completedAssistant)
                finishSending(completedMessages, errorMessage = null)
                launchAiTitleGeneration(conversationId, completedMessages)
            } catch (e: CancellationException) {
                val cancelledAssistant = imageGenerationSupport.buildCancelledAssistant(loadingMessage)
                conversationRepository.upsertMessages(
                    conversationId = conversationId,
                    messages = listOf(cancelledAssistant),
                    selectedModel = selectedModel,
                )
                val cancelledMessages = buildFinalMessages(cancelledAssistant)
                finishSending(cancelledMessages, errorMessage = null)
            } catch (throwable: Throwable) {
                val errorText = throwable.message ?: "图片生成失败"
                val failedAssistant = imageGenerationSupport.buildFailedAssistant(
                    loadingMessage = loadingMessage,
                    errorText = errorText,
                )
                conversationRepository.upsertMessages(
                    conversationId = conversationId,
                    messages = listOf(failedAssistant),
                    selectedModel = selectedModel,
                )
                val failedMessages = buildFinalMessages(failedAssistant)
                finishSending(failedMessages, errorMessage = errorText)
            } finally {
                sendingJob = null
            }
        }
    }

    private fun executeAssistantRoundTrip(
        conversationId: String,
        loadingMessage: ChatMessage,
        requestMessages: List<ChatMessage>,
        selectedModel: String,
        initialPersistence: RoundTripInitialPersistence,
        buildFinalMessages: (ChatMessage) -> List<ChatMessage>,
        promptAssemblyInput: ChatPromptAssemblyInput,
        giftImageRequest: GiftImageGenerationRequest? = null,
    ) {
        sendingJob = viewModelScope.launch {
            val streamBuffer = StreamingReplyBuffer()

            try {
                conversationRepository.persistInitialRoundTripState(
                    conversationId = conversationId,
                    selectedModel = selectedModel,
                    persistence = initialPersistence,
                )
                giftImageRequest?.let { request ->
                    launchGiftImageGeneration(request)
                }
                val promptContext = promptContextAssembler.assemble(
                    settings = promptAssemblyInput.settings,
                    assistant = promptAssemblyInput.assistant,
                    conversation = promptAssemblyInput.conversation,
                    userInputText = promptAssemblyInput.userInputText,
                    recentMessages = promptAssemblyInput.recentMessages,
                    promptMode = PromptMode.CHAT,
                )
                val effectiveRequestMessages = resolveRequestMessagesForRoundTrip(
                    promptAssemblyInput = promptAssemblyInput,
                    requestMessages = requestMessages,
                )
                val toolingOptions = GatewayToolingOptions.chat(
                    searchEnabled = promptAssemblyInput.conversation.searchEnabled,
                    runtimeContext = ChatConversationSupport.buildToolRuntimeContext(
                        promptAssemblyInput = promptAssemblyInput,
                        promptMode = PromptMode.CHAT,
                    ),
                )
                val completedMessageCount = requestMessages.count { it.status == MessageStatus.COMPLETED }
                val debugDump = ConversationSummaryDebugSupport.appendStatusLine(
                    debugDump = promptContext.debugDump,
                    hasSummary = promptContext.summaryCoveredMessageCount > 0,
                    coveredMessageCount = promptContext.summaryCoveredMessageCount,
                    completedMessageCount = completedMessageCount,
                    triggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
                )
                _uiState.update { current ->
                    ChatStateSupport.applyContextGovernance(
                        current = current,
                        conversationId = conversationId,
                        snapshot = ContextGovernanceSupport.buildSnapshot(
                            settings = promptAssemblyInput.settings,
                            assistant = promptAssemblyInput.assistant,
                            promptMode = PromptMode.CHAT,
                            selectedModel = selectedModel,
                            requestMessages = requestMessages,
                            effectiveRequestMessages = effectiveRequestMessages,
                            promptContext = promptContext,
                            completedMessageCount = completedMessageCount,
                            triggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
                            recentWindow = resolveSummaryRecentWindow(promptAssemblyInput.assistant),
                            minCoveredMessageCount = SUMMARY_MIN_COVERED_MESSAGE_COUNT,
                            toolingOptions = toolingOptions,
                            rawDebugDump = debugDump,
                        ),
                        debugDump = debugDump,
                    )
                }
                when (
                    val result = assistantRoundTripRunner.execute(
                        AssistantRoundTripRequest(
                            conversationId = conversationId,
                            selectedModel = selectedModel,
                            requestMessages = effectiveRequestMessages,
                            loadingMessage = loadingMessage,
                            buildFinalMessages = buildFinalMessages,
                            systemPrompt = promptContext.systemPrompt,
                            streamReply = { messages, systemPrompt ->
                                streamAssistantReply(
                                    conversationId = conversationId,
                                    loadingMessageId = loadingMessage.id,
                                    requestMessages = messages,
                                    streamBuffer = streamBuffer,
                                    systemPrompt = systemPrompt,
                                    toolingOptions = toolingOptions,
                                )
                            },
                            currentPayload = {
                                StreamedAssistantPayload(
                                    content = streamBuffer.content(),
                                    reasoning = streamBuffer.reasoning(),
                                    reasoningSteps = streamBuffer.reasoningSteps(),
                                    parts = streamBuffer.parts(),
                                    citations = streamBuffer.citations(),
                                )
                            },
                            onCompleted = { payload, parsedOutput, loading ->
                                val finalParts = parsedOutput.parts
                                val resolvedContent = parsedOutput.content.takeIf { it.isNotBlank() }
                                    ?: parsedOutput.transferUpdates.lastOrNull()?.status?.transferResultText()
                                    ?: finalParts.toContentMirror(specialFallback = "特殊玩法")
                                        .ifBlank { "模型未返回有效内容" }
                                loading.copy(
                                    content = resolvedContent,
                                    status = MessageStatus.COMPLETED,
                                    reasoningContent = payload.reasoning,
                                    reasoningSteps = payload.reasoningSteps,
                                    parts = finalParts,
                                    citations = payload.citations,
                                )
                            },
                            onCancelled = { payload, loading ->
                                val partialContent = payload.content.ifBlank {
                                    payload.parts.toContentMirror(specialFallback = "特殊玩法")
                                }
                                AssistantRoundTripOutcome(
                                    messages = buildFinalMessages(
                                        loading.copy(
                                            content = partialContent.ifBlank { "已取消" },
                                            status = MessageStatus.ERROR,
                                            reasoningContent = payload.reasoning,
                                            reasoningSteps = payload.reasoningSteps,
                                            parts = payload.parts,
                                            citations = payload.citations,
                                        ),
                                    ),
                                    errorMessage = null,
                                )
                            },
                            onFailed = { payload, throwable, loading ->
                                val errorText = throwable.message ?: "发送失败"
                                val partialContent = payload.content.ifBlank {
                                    payload.parts.toContentMirror(specialFallback = "特殊玩法")
                                }
                                val errorContent = if (partialContent.isNotBlank()) {
                                    "$partialContent\n\n---\n错误：$errorText"
                                } else {
                                    errorText
                                }
                                AssistantRoundTripOutcome(
                                    messages = buildFinalMessages(
                                        loading.copy(
                                            content = errorContent,
                                            status = MessageStatus.ERROR,
                                            reasoningContent = payload.reasoning,
                                            reasoningSteps = payload.reasoningSteps,
                                            parts = payload.parts,
                                            citations = payload.citations,
                                        ),
                                    ),
                                    errorMessage = errorText,
                                )
                            },
                        ),
                    )
                ) {
                    is AssistantRoundTripResult.Completed -> {
                        finishSending(result.messages, errorMessage = null)
                        launchAiTitleGeneration(conversationId, result.messages)
                        launchChatSuggestions(conversationId, result.messages)
                        launchConversationSummaryGeneration(conversationId, result.messages)
                        launchAutomaticMemoryExtraction(conversationId, result.messages)
                    }

                    is AssistantRoundTripResult.Cancelled -> {
                        finishSending(result.messages, errorMessage = result.errorMessage)
                    }

                    is AssistantRoundTripResult.Failed -> {
                        finishSending(result.messages, errorMessage = result.errorMessage)
                    }
                }
            } catch (cancellation: CancellationException) {
                if (_uiState.value.isSending) {
                    finishSending(_uiState.value.messages, errorMessage = null)
                }
                throw cancellation
            } catch (throwable: Throwable) {
                val errorText = throwable.message ?: "发送失败"
                val failedAssistant = loadingMessage.copy(
                    content = errorText,
                    status = MessageStatus.ERROR,
                    parts = emptyList(),
                )
                conversationRepository.upsertMessages(
                    conversationId = conversationId,
                    messages = listOf(failedAssistant),
                    selectedModel = selectedModel,
                )
                val failedMessages = buildFinalMessages(failedAssistant)
                finishSending(failedMessages, errorMessage = errorText)
            } finally {
                sendingJob = null
            }
        }
    }

    private fun finishSending(messages: List<ChatMessage>, errorMessage: String?) {
        _uiState.update { current ->
            ChatStateSupport.finishSending(
                current = current,
                messages = messages,
                errorMessage = errorMessage,
            )
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settings.collect { currentSettings ->
                val settingsUpdate = ChatObservationSupport.resolveObservedSettings(
                    currentAssistantId = currentAssistantId.value,
                    settings = currentSettings,
                )
                currentAssistantId.value = settingsUpdate.newAssistantId
                _uiState.update { current ->
                    ChatViewModelUiUpdates.applyObservedSettings(
                        current = current,
                        settings = currentSettings,
                        currentAssistant = settingsUpdate.activeAssistant,
                    )
                }
                if (settingsUpdate.shouldSwitchAssistant) {
                    switchToAssistant(settingsUpdate.newAssistantId)
                }
            }
        }
    }

    private fun observeConversations() {
        viewModelScope.launch {
            currentAssistantId
                .flatMapLatest { assistantId ->
                    conversationRepository.observeConversationsByAssistant(assistantId)
                }
                .collect { conversations ->
                    val conversationUpdate = ChatObservationSupport.resolveConversationCollection(
                        conversations = conversations,
                        currentConversationId = currentConversationId.value,
                    )
                    val resolvedConversation = conversationUpdate.resolvedConversation

                    if (resolvedConversation == null) {
                        _uiState.update { current ->
                            ChatViewModelUiUpdates.clearConversationCollection(current, conversations)
                        }
                        return@collect
                    }

                    if (conversationUpdate.shouldActivateConversation) {
                        activateConversation(
                            conversationId = resolvedConversation.id,
                            title = resolvedConversation.title,
                        )
                    }

                    _uiState.update { current ->
                        ChatViewModelUiUpdates.applyConversationCollection(
                            current = current,
                            conversations = conversations,
                            resolvedConversation = resolvedConversation,
                        )
                    }
                }
        }
    }

    private fun observeMemoryEntries() {
        viewModelScope.launch {
            memoryRepository.observeEntries().collect { entries ->
                currentMemoryEntries.value = entries
            }
        }
    }

    private fun observeRememberedMessageIds() {
        viewModelScope.launch {
            combine(
                currentMemoryEntries,
                _uiState.map { it.currentAssistant?.useGlobalMemory == true },
                currentAssistantId,
                currentConversationId,
            ) { entries, useGlobalMemory, assistantId, conversationId ->
                resolveRememberedMessageIds(
                    entries = entries,
                    useGlobalMemory = useGlobalMemory,
                    assistantId = assistantId,
                    conversationId = conversationId.orEmpty(),
                )
            }.collect { rememberedIds ->
                _uiState.update { current ->
                    ChatStateSupport.updateRememberedMessageIds(current, rememberedIds)
                }
            }
        }
    }

    private fun observeConversationSummary() {
        viewModelScope.launch {
            currentConversationId
                .flatMapLatest { conversationId ->
                    val resolvedConversationId = conversationId.orEmpty()
                    if (resolvedConversationId.isBlank()) {
                        kotlinx.coroutines.flow.flowOf(null)
                    } else {
                        conversationSummaryRepository.observeSummary(resolvedConversationId)
                    }
                }
                .collect { summary ->
                    _uiState.update { current ->
                        ChatStateSupport.updateConversationSummaryStatus(
                            current = current,
                            hasSummary = summary?.summary?.isNotBlank() == true,
                            coveredMessageCount = summary?.coveredMessageCount ?: 0,
                        )
                    }
                }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            currentConversationId
                .filterNotNull()
                .flatMapLatest { conversationId ->
                    conversationRepository.observeMessages(conversationId).map { conversationId to it }
                }
                .collect { (conversationId, messages) ->
                    if (ChatObservationSupport.shouldIgnoreObservedMessages(_uiState.value, conversationId)) {
                        return@collect
                    }
                    _uiState.update { current ->
                        ChatViewModelUiUpdates.applyObservedMessages(
                            current = current,
                            conversationId = conversationId,
                            messages = messages,
                        )
                    }
                }
        }
    }

    private fun ensureConversation() {
        launchConversationActivation {
            conversationRepository.ensureActiveConversation(
                currentConversationId.value,
                currentAssistantId.value,
            )
        }
    }

    private fun switchToAssistant(assistantId: String) {
        launchConversationActivation {
            conversationRepository.ensureActiveConversation(
                currentConversationId = null,
                assistantId = assistantId,
            )
        }
    }

    fun selectAssistant(assistantId: String) {
        if (_uiState.value.isSending) return
        currentAssistantId.value = assistantId
        val activeAssistant = _uiState.value.settings.resolvedAssistants()
            .firstOrNull { it.id == assistantId }
        _uiState.update { current ->
            ChatStateSupport.selectAssistant(current, activeAssistant)
        }
        switchToAssistant(assistantId)
    }

    private fun resolveRememberedMessageIds(
        entries: List<MemoryEntry>,
        useGlobalMemory: Boolean,
        assistantId: String,
        conversationId: String,
    ): Set<String> {
        return entries
            .filter { entry ->
                when (entry.scopeType) {
                    MemoryScopeType.GLOBAL -> true
                    MemoryScopeType.ASSISTANT -> !useGlobalMemory && entry.resolvedScopeId() == assistantId
                    MemoryScopeType.CONVERSATION -> entry.resolvedScopeId() == conversationId
                }
            }
            .mapNotNull { entry ->
                entry.sourceMessageId.trim().takeIf { it.isNotEmpty() }
            }
            .toSet()
    }

    private fun launchConversationActivation(
        action: suspend () -> Conversation,
    ) {
        viewModelScope.launch {
            val conversation = action()
            activateConversation(
                conversationId = conversation.id,
                title = conversation.title,
            )
        }
    }

    private fun activateConversation(
        conversationId: String,
        title: String,
    ) {
        currentConversationId.value = conversationId
        _uiState.update { current ->
            ChatStateSupport.activateConversation(
                current = current,
                conversationId = conversationId,
                title = title,
            )
        }
    }

    private fun launchAiTitleGeneration(
        conversationId: String,
        messages: List<ChatMessage>,
    ) {
        val state = _uiState.value
        viewModelScope.launch {
            val titleModel = runCatching {
                titleCoordinator.updateConversationTitle(
                    conversationId = conversationId,
                    messages = messages,
                    settings = state.settings,
                )
            }.getOrNull()
            if (!titleModel.isNullOrBlank() && _uiState.value.currentConversationId == conversationId) {
                _uiState.update { current ->
                    ChatViewModelUiUpdates.applyTitleGeneratedNotice(current, titleModel)
                }
            }
        }
    }

    private fun launchChatSuggestions(
        conversationId: String,
        messages: List<ChatMessage>,
    ) {
        val state = _uiState.value
        viewModelScope.launch {
            val result = runCatching {
                chatSuggestionCoordinator.generateSuggestions(
                    messages = messages,
                    settings = state.settings,
                )
            }.getOrNull()
            if (result != null && _uiState.value.currentConversationId == conversationId) {
                _uiState.update { current ->
                    ChatStateSupport.applyChatSuggestions(
                        current = current,
                        suggestions = result.suggestions,
                        modelName = result.modelName,
                    )
                }
            }
        }
    }

    private fun launchConversationSummaryGeneration(
        conversationId: String,
        messages: List<ChatMessage>,
    ) {
        val state = _uiState.value
        val assistant = state.currentAssistant ?: state.settings.activeAssistant()
        viewModelScope.launch {
            backgroundApiMutex.withLock {
                val result = runCatching {
                    updateConversationSummary(
                        conversationId = conversationId,
                        messages = messages,
                        settings = state.settings,
                        assistant = assistant,
                    )
                }.getOrDefault(SummaryUpdateResult())
                if (result.updated && _uiState.value.currentConversationId == conversationId) {
                    rebuildContextGovernanceSnapshot(
                        conversationId = conversationId,
                        messages = messages,
                        settings = state.settings,
                        assistant = assistant,
                    )
                    _uiState.update { current ->
                        ChatStateSupport.applyNoticeMessage(current, "上下文摘要已更新")
                    }
                }
            }
        }
    }

    private fun launchAutomaticMemoryExtraction(
        conversationId: String,
        messages: List<ChatMessage>,
    ) {
        val completedMessages = messages.filter { it.status == MessageStatus.COMPLETED }
        val state = _uiState.value
        val assistant = state.currentAssistant ?: state.settings.activeAssistant() ?: return
        viewModelScope.launch {
            backgroundApiMutex.withLock {
                runCatching {
                    memoryExtractionCoordinator.updateChatMemories(
                        assistant = assistant,
                        completedMessages = completedMessages,
                        settings = state.settings,
                        recentMessageWindow = AUTO_MEMORY_MESSAGE_WINDOW,
                        buildMemoryInput = { messages ->
                            ChatConversationSupport.buildConversationExcerpt(
                                messages = messages,
                                maxLength = MAX_MEMORY_INPUT_LENGTH,
                                perMessageLimit = 240,
                            )
                        },
                    )
                }
            }
        }
    }

    private suspend fun resolveRequestMessagesForRoundTrip(
        promptAssemblyInput: ChatPromptAssemblyInput,
        requestMessages: List<ChatMessage>,
    ): List<ChatMessage> {
        val summary = conversationSummaryRepository.getSummary(promptAssemblyInput.conversation.id)
        return ConversationMessageTransforms.trimRequestMessagesWithSummary(
            requestMessages = requestMessages,
            completedMessageCount = requestMessages.count { it.status == MessageStatus.COMPLETED },
            summaryCoveredMessageCount = summary
                ?.takeIf { it.summary.isNotBlank() }
                ?.coveredMessageCount
                ?: 0,
            recentWindow = resolveSummaryRecentWindow(promptAssemblyInput.assistant),
        )
    }

    private suspend fun updateConversationSummary(
        conversationId: String,
        messages: List<ChatMessage>,
        settings: AppSettings,
        assistant: Assistant?,
        forceRefresh: Boolean = false,
    ): SummaryUpdateResult {
        val completedMessages = messages.filter { it.status == MessageStatus.COMPLETED }
        return summaryCoordinator.updateConversationSummary(
            conversationId = conversationId,
            assistantId = assistant?.id.orEmpty().ifBlank { currentAssistantId.value },
            completedMessages = completedMessages,
            settings = settings,
            config = SummaryGenerationConfig(
                triggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
                recentMessageWindow = resolveSummaryRecentWindow(assistant),
                minCoveredMessageCount = SUMMARY_MIN_COVERED_MESSAGE_COUNT,
            ),
            forceRefresh = forceRefresh,
            buildSummaryInput = { summaryMessages ->
                ChatConversationSupport.buildConversationExcerpt(
                    messages = summaryMessages,
                    maxLength = MAX_SUMMARY_INPUT_LENGTH,
                    perMessageLimit = 200,
                )
            },
            generateSummary = { conversationText, baseUrl, apiKey, modelId, apiProtocol ->
                aiPromptExtrasService.generateConversationSummary(
                    conversationText = conversationText,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    modelId = modelId,
                    apiProtocol = apiProtocol,
                    provider = settings.activeProvider(),
                )
            },
        )
    }

    private fun resolveSummaryRecentWindow(
        assistant: Assistant?,
    ): Int {
        return assistant?.contextMessageSize
            ?.takeIf { it > 0 }
            ?: SUMMARY_RECENT_MESSAGE_WINDOW
    }

    private suspend fun rebuildContextGovernanceSnapshot(
        conversationId: String,
        messages: List<ChatMessage>,
        settings: AppSettings,
        assistant: Assistant?,
    ) {
        val current = _uiState.value
        if (current.currentConversationId != conversationId) {
            return
        }
        val requestMessages = messages.filter { it.status == MessageStatus.COMPLETED }
        val promptAssemblyInput = ChatConversationSupport.buildPromptAssemblyInput(
            settings = settings,
            currentAssistant = assistant,
            currentConversations = current.conversations,
            fallbackAssistantId = assistant?.id
                ?.takeIf { it.isNotBlank() }
                ?: currentAssistantId.value,
            conversationId = conversationId,
            requestMessages = requestMessages,
            nowProvider = nowProvider,
        )
        val promptContext = promptContextAssembler.assemble(
            settings = promptAssemblyInput.settings,
            assistant = promptAssemblyInput.assistant,
            conversation = promptAssemblyInput.conversation,
            userInputText = promptAssemblyInput.userInputText,
            recentMessages = promptAssemblyInput.recentMessages,
            promptMode = PromptMode.CHAT,
        )
        val effectiveRequestMessages = resolveRequestMessagesForRoundTrip(
            promptAssemblyInput = promptAssemblyInput,
            requestMessages = requestMessages,
        )
        val selectedModel = ChatConversationSupport.resolveSelectedModelId(settings)
        val toolingOptions = GatewayToolingOptions.chat(
            searchEnabled = promptAssemblyInput.conversation.searchEnabled,
            runtimeContext = ChatConversationSupport.buildToolRuntimeContext(
                promptAssemblyInput = promptAssemblyInput,
                promptMode = PromptMode.CHAT,
            ),
        )
        val completedMessageCount = requestMessages.size
        val debugDump = ConversationSummaryDebugSupport.appendStatusLine(
            debugDump = promptContext.debugDump,
            hasSummary = promptContext.summaryCoveredMessageCount > 0,
            coveredMessageCount = promptContext.summaryCoveredMessageCount,
            completedMessageCount = completedMessageCount,
            triggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
        )
        _uiState.update { currentState ->
            ChatStateSupport.applyContextGovernance(
                current = currentState,
                conversationId = conversationId,
                snapshot = ContextGovernanceSupport.buildSnapshot(
                    settings = settings,
                    assistant = assistant,
                    promptMode = PromptMode.CHAT,
                    selectedModel = selectedModel,
                    requestMessages = requestMessages,
                    effectiveRequestMessages = effectiveRequestMessages,
                    promptContext = promptContext,
                    completedMessageCount = completedMessageCount,
                    triggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
                    recentWindow = resolveSummaryRecentWindow(assistant),
                    minCoveredMessageCount = SUMMARY_MIN_COVERED_MESSAGE_COUNT,
                    toolingOptions = toolingOptions,
                    rawDebugDump = debugDump,
                ),
                debugDump = debugDump,
            )
        }
    }

    private suspend fun streamAssistantReply(
        conversationId: String,
        loadingMessageId: String,
        requestMessages: List<ChatMessage>,
        streamBuffer: StreamingReplyBuffer,
        systemPrompt: String = "",
        toolingOptions: GatewayToolingOptions = GatewayToolingOptions(),
    ) {
        ChatStreamingSupport.collectStreamingReply(
            streamBuffer = streamBuffer,
            streamEvents = aiGateway.sendMessageStream(
                messages = requestMessages,
                systemPrompt = systemPrompt,
                promptMode = PromptMode.CHAT,
                toolingOptions = toolingOptions,
            ),
            publishFrame = { content, reasoning, reasoningSteps, parts ->
                publishStreamingFrame(
                    conversationId = conversationId,
                    loadingMessageId = loadingMessageId,
                    content = content,
                    reasoning = reasoning,
                    reasoningSteps = reasoningSteps,
                    parts = parts,
                )
            },
        )
    }

    private fun publishStreamingFrame(
        conversationId: String,
        loadingMessageId: String,
        content: String,
        reasoning: String,
        reasoningSteps: List<ChatReasoningStep>,
        parts: List<ChatMessagePart>,
    ) {
        _uiState.update { current ->
            ChatViewModelUiUpdates.applyStreamingFrame(
                current = current,
                conversationId = conversationId,
                loadingMessageId = loadingMessageId,
                content = content,
                reasoning = reasoning,
                reasoningSteps = reasoningSteps,
                parts = parts,
            )
        }
    }

    private fun submitOutgoingMessage(
        state: ChatUiState,
        userParts: List<ChatMessagePart>,
        nextInput: String,
        nextPendingParts: List<ChatMessagePart>,
        imageGenerationPrompt: String = "",
        forceChatRoundTrip: Boolean,
    ) {
        val conversationId = validateAndGetConversationId(state) ?: return
        val selectedModel = ChatConversationSupport.resolveSelectedModelId(state.settings)
        val baseMessages = ChatConversationSupport.currentConversationMessages(state.messages, conversationId)
        val activeProvider = state.settings.activeProvider()
        val originalGiftPart = normalizeChatMessageParts(userParts).firstOrNull { it.isGiftPart() }
        val giftImageModelId = activeProvider
            ?.resolveFunctionModel(ProviderFunction.GIFT_IMAGE)
            .orEmpty()
            .trim()
        val shouldGenerateGiftImage = originalGiftPart != null &&
            activeProvider != null &&
            giftImageModelId.isNotBlank()
        val resolvedUserParts = if (shouldGenerateGiftImage) {
            userParts.map { part ->
                if (part.specialId == originalGiftPart?.specialId) {
                    part.withGiftImageGenerating()
                } else {
                    part
                }
            }
        } else {
            userParts
        }
        val preparedRoundTrip = ChatConversationSupport.prepareOutgoingRoundTrip(
            baseMessages = baseMessages,
            conversationId = conversationId,
            userParts = resolvedUserParts,
            selectedModel = selectedModel,
            nowProvider = nowProvider,
            messageIdProvider = messageIdProvider,
        )
        val giftImageRequest = if (shouldGenerateGiftImage) {
            val giftPart = normalizeChatMessageParts(resolvedUserParts).firstOrNull { it.isGiftPart() }
            if (giftPart == null) {
                null
            } else {
                GiftImageGenerationRequest(
                    conversationId = conversationId,
                    selectedModel = selectedModel,
                    provider = activeProvider,
                    specialId = giftPart.specialId,
                    giftName = giftPart.specialMetadataValue("item"),
                    recipientName = giftPart.specialMetadataValue("target"),
                    userName = state.settings.resolvedUserDisplayName(),
                    assistantName = state.currentAssistant?.name
                        ?.trim()
                        .orEmpty()
                        .ifBlank { state.settings.activeAssistant()?.name.orEmpty() },
                    contextExcerpt = ChatConversationSupport.buildConversationExcerpt(
                        messages = preparedRoundTrip.requestMessages.takeLast(6),
                        maxLength = 600,
                        perMessageLimit = 120,
                    ),
                )
            }
        } else {
            null
        }

        _uiState.update { current ->
            ChatViewModelUiUpdates.beginRoundTrip(
                current = current,
                messages = preparedRoundTrip.persistedMessages,
                loadingMessageId = preparedRoundTrip.loadingMessage.id,
                nextInput = nextInput,
                nextPendingParts = nextPendingParts,
            ).let { updated ->
                if (originalGiftPart != null && !shouldGenerateGiftImage) {
                    updated.copy(noticeMessage = "未配置礼物生图模型，已按普通礼物卡发送")
                } else {
                    updated
                }
            }
        }

        if (!forceChatRoundTrip && ChatConversationSupport.supportsImageGeneration(state.settings, selectedModel)) {
            executeImageGeneration(
                conversationId = conversationId,
                loadingMessage = preparedRoundTrip.loadingMessage,
                prompt = imageGenerationPrompt,
                selectedModel = selectedModel,
                initialPersistence = RoundTripInitialPersistence.Append(
                    messages = listOf(preparedRoundTrip.userMessage, preparedRoundTrip.loadingMessage),
                ),
                buildFinalMessages = { completedAssistant ->
                    baseMessages + preparedRoundTrip.userMessage + completedAssistant
                },
            )
        } else {
            executeAssistantRoundTrip(
                conversationId = conversationId,
                loadingMessage = preparedRoundTrip.loadingMessage,
                requestMessages = preparedRoundTrip.requestMessages,
                selectedModel = selectedModel,
                initialPersistence = RoundTripInitialPersistence.Append(
                    messages = listOf(preparedRoundTrip.userMessage, preparedRoundTrip.loadingMessage),
                ),
                buildFinalMessages = { completedAssistant ->
                    baseMessages + preparedRoundTrip.userMessage + completedAssistant
                },
                promptAssemblyInput = ChatConversationSupport.buildPromptAssemblyInput(
                    settings = state.settings,
                    currentAssistant = state.currentAssistant,
                    currentConversations = state.conversations,
                    fallbackAssistantId = state.currentAssistant?.id
                        ?.takeIf { it.isNotBlank() }
                        ?: currentAssistantId.value,
                    conversationId = conversationId,
                    requestMessages = preparedRoundTrip.requestMessages,
                    nowProvider = nowProvider,
                ),
                giftImageRequest = giftImageRequest,
            )
        }
    }

    private fun launchGiftImageGeneration(
        request: GiftImageGenerationRequest,
    ) {
        viewModelScope.launch {
            val updatedMessages = giftImageCoordinator.generate(request) ?: return@launch
            _uiState.update { current ->
                if (current.currentConversationId != request.conversationId) {
                    current
                } else {
                    current.copy(messages = updatedMessages)
                }
            }
        }
    }

    companion object {
        private const val SUMMARY_TRIGGER_MESSAGE_COUNT = 20
        private const val SUMMARY_MIN_COVERED_MESSAGE_COUNT = 8
        private const val SUMMARY_RECENT_MESSAGE_WINDOW = 12
        private const val MAX_SUMMARY_INPUT_LENGTH = 4_000
        private const val AUTO_MEMORY_MESSAGE_WINDOW = 6
        private const val MAX_MEMORY_INPUT_LENGTH = 2_400

        fun factory(
            settingsRepository: AiSettingsRepository,
            aiGateway: AiGateway,
            aiPromptExtrasService: AiPromptExtrasService,
            aiTranslationService: AiTranslationService,
            conversationRepository: ConversationRepository,
            memoryRepository: MemoryRepository,
            conversationSummaryRepository: ConversationSummaryRepository,
            promptContextAssembler: PromptContextAssembler,
            imageSaver: suspend (String) -> SavedImageFile = { throw IllegalStateException("图片保存未配置") },
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(
                        settingsRepository,
                        aiGateway,
                        aiPromptExtrasService,
                        aiTranslationService,
                        conversationRepository,
                        memoryRepository,
                        conversationSummaryRepository,
                        promptContextAssembler,
                        imageSaver = imageSaver,
                    ) as T
                }
            }
        }
    }
}
