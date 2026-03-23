package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.repository.AiRepository
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.SavedImageFile
import com.example.myapplication.data.repository.TransferUpdateDirective
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.DEFAULT_CONVERSATION_TITLE
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.model.imageMessagePart
import com.example.myapplication.model.inferModelAbilities
import com.example.myapplication.model.isTransferPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.model.toMessageAttachments
import com.example.myapplication.model.toPlainText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    val latestPromptDebugDump: String = "",
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
    private val repository: AiRepository,
    private val conversationRepository: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val promptContextAssembler: PromptContextAssembler,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val messageIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val imageSaver: suspend (String) -> SavedImageFile = { throw IllegalStateException("图片保存未配置") },
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settingsFlow.stateIn(
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

    init {
        observeSettings()
        observeConversations()
        observeMessages()
        observeMemoryEntries()
        observeRememberedMessageIds()
        ensureConversation()
    }

    fun updateInput(value: String) {
        _uiState.update { it.copy(input = value, errorMessage = null) }
    }

    fun addPendingParts(parts: List<ChatMessagePart>) {
        val normalizedParts = normalizeChatMessageParts(parts)
        if (normalizedParts.isEmpty()) {
            return
        }
        _uiState.update {
            it.copy(
                pendingParts = normalizeChatMessageParts(it.pendingParts + normalizedParts),
                errorMessage = null,
            )
        }
    }

    fun removePendingPart(index: Int) {
        _uiState.update {
            it.copy(
                pendingParts = it.pendingParts.filterIndexed { i, _ -> i != index },
                errorMessage = null,
            )
        }
    }

    fun clearPendingParts() {
        _uiState.update { it.copy(pendingParts = emptyList(), errorMessage = null) }
    }

    fun translateDraftInput() {
        val sourceText = _uiState.value.input.trim()
        if (sourceText.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请先输入要翻译的内容") }
            return
        }
        startTranslation(
            sourceText = sourceText,
            sourceLabel = "输入框内容",
        )
    }

    fun translateMessage(messageId: String) {
        val message = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val sourceText = message.parts.toPlainText()
            .ifBlank { message.content.trim() }
        if (sourceText.isBlank()) {
            _uiState.update { it.copy(errorMessage = "当前消息没有可翻译的文本内容") }
            return
        }
        startTranslation(
            sourceText = sourceText,
            sourceLabel = if (message.role == MessageRole.USER) "用户消息" else "助手回复",
        )
    }

    fun dismissTranslationSheet() {
        _uiState.update { it.copy(translation = TranslationUiState()) }
    }

    fun applyTranslationToInput(replace: Boolean) {
        val translatedText = _uiState.value.translation.translatedText.trim()
        if (translatedText.isBlank()) {
            return
        }
        _uiState.update { state ->
            val updatedInput = if (replace || state.input.isBlank()) {
                translatedText
            } else {
                state.input.trimEnd() + "\n\n" + translatedText
            }
            state.copy(
                input = updatedInput,
                translation = state.translation.copy(isVisible = false),
            )
        }
    }

    fun sendTranslationAsMessage() {
        val translatedText = _uiState.value.translation.translatedText.trim()
        if (translatedText.isBlank()) {
            return
        }
        _uiState.update { state ->
            state.copy(
                input = translatedText,
                translation = state.translation.copy(isVisible = false),
            )
        }
        sendMessage()
    }

    fun createConversation() {
        if (_uiState.value.isSending) {
            return
        }
        viewModelScope.launch {
            val assistantId = currentAssistantId.value
            val conversation = conversationRepository.createConversation(
                resolveSelectedModelId(_uiState.value.settings),
                assistantId,
            )
            activateConversation(
                conversationId = conversation.id,
                title = conversation.title,
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
        viewModelScope.launch {
            val conversation = conversationRepository.deleteConversation(
                conversationId = conversationId,
                selectedModel = resolveSelectedModelId(state.settings),
                assistantId = currentAssistantId.value,
            )
            activateConversation(
                conversationId = conversation.id,
                title = conversation.title,
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
        viewModelScope.launch {
            val conversation = conversationRepository.clearConversation(
                conversationId = conversationId,
                selectedModel = resolveSelectedModelId(state.settings),
            )
            activateConversation(
                conversationId = conversation.id,
                title = conversation.title,
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
                selectedModel = conversation.model.ifBlank { resolveSelectedModelId(state.settings) },
            )
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.input.trim()
        val pendingParts = state.pendingParts
        if (text.isBlank() && pendingParts.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请输入消息内容或添加附件") }
            return
        }

        val userParts = buildUserMessageParts(
            text = text,
            pendingParts = pendingParts,
        )
        submitOutgoingMessage(
            state = state,
            userParts = userParts,
            nextInput = "",
            nextPendingParts = emptyList(),
            imageGenerationPrompt = text,
            forceChatRoundTrip = false,
        )
    }

    fun sendTransferPlay(
        counterparty: String,
        amount: String,
        note: String,
    ) {
        val state = _uiState.value
        if (state.isSending) {
            return
        }
        val normalizedAmount = amount.trim()
        if (normalizedAmount.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入转账金额") }
            return
        }
        val normalizedCounterparty = counterparty.trim().ifBlank {
            state.currentAssistant?.name?.trim().orEmpty().ifBlank { "对方" }
        }
        val transferPart = transferMessagePart(
            direction = TransferDirection.USER_TO_ASSISTANT,
            status = TransferStatus.PENDING,
            counterparty = normalizedCounterparty,
            amount = normalizedAmount,
            note = note.trim(),
        )
        submitOutgoingMessage(
            state = state,
            userParts = listOf(transferPart),
            nextInput = state.input,
            nextPendingParts = state.pendingParts,
            forceChatRoundTrip = true,
        )
    }

    fun confirmTransferReceipt(specialId: String) {
        val state = _uiState.value
        val conversationId = state.currentConversationId
        if (specialId.isBlank() || conversationId.isBlank()) {
            return
        }

        val updatedMessages = updateTransferStatuses(
            messages = currentConversationMessages(state, conversationId),
            updates = listOf(
                TransferUpdateDirective(
                    refId = specialId,
                    status = TransferStatus.RECEIVED,
                ),
            ),
        )
        if (updatedMessages == state.messages) {
            return
        }

        viewModelScope.launch {
            conversationRepository.saveConversationMessages(
                conversationId = conversationId,
                messages = updatedMessages,
                selectedModel = resolveSelectedModelId(state.settings),
            )
            _uiState.update {
                it.copy(
                    messages = updatedMessages,
                    noticeMessage = "已收款",
                )
            }
        }
    }

    fun retryMessage(messageId: String) {
        val state = _uiState.value
        if (state.isSending || messageId.isBlank()) {
            return
        }

        val conversationId = validateAndGetConversationId(state) ?: return
        val selectedModel = resolveSelectedModelId(state.settings)
        val imageGenerationEnabled = supportsImageGeneration(state.settings, selectedModel)

        val currentMessages = currentConversationMessages(state, conversationId)
        val failedIndex = currentMessages.indexOfFirst { it.id == messageId && it.status == MessageStatus.ERROR }
        if (failedIndex != -1) {
            val failedMessage = currentMessages[failedIndex]
            val loadingMessage = failedMessage.copy(
                content = "",
                status = MessageStatus.LOADING,
                reasoningContent = "",
                attachments = emptyList(),
                parts = emptyList(),
            )
            val retryMessages = currentMessages.toMutableList().apply {
                this[failedIndex] = loadingMessage
            }
            val requestMessages = currentMessages
                .take(failedIndex)
                .filter { it.status == MessageStatus.COMPLETED && it.hasSendableContent() }
            val retryPrompt = if (imageGenerationEnabled) {
                requestMessages.lastOrNull { it.role == MessageRole.USER }?.content?.trim().orEmpty()
            } else {
                ""
            }

            if (imageGenerationEnabled && retryPrompt.isBlank()) {
                _uiState.update {
                    it.copy(errorMessage = "未找到可重试的生图提示词")
                }
                return
            }

            _uiState.update {
                it.copy(
                    messages = retryMessages,
                    streamingMessageId = loadingMessage.id,
                    streamingContent = "",
                    streamingReasoningContent = "",
                    streamingParts = emptyList(),
                    isSending = true,
                    errorMessage = null,
                )
            }

            if (imageGenerationEnabled) {
                executeImageGeneration(
                    conversationId = conversationId,
                    loadingMessage = loadingMessage,
                    prompt = retryPrompt,
                    selectedModel = selectedModel,
                    initialMessages = retryMessages,
                    buildFinalMessages = { completedAssistant ->
                        currentMessages.toMutableList().apply {
                            this[failedIndex] = completedAssistant
                        }
                    },
                )
            } else {
                executeAssistantRoundTrip(
                    conversationId = conversationId,
                    loadingMessage = loadingMessage,
                    requestMessages = requestMessages,
                    selectedModel = selectedModel,
                    initialMessages = retryMessages,
                buildFinalMessages = { completedAssistant ->
                    currentMessages.toMutableList().apply {
                        this[failedIndex] = completedAssistant
                    }
                },
                promptAssemblyInput = buildPromptAssemblyInput(
                    state = state,
                    conversationId = conversationId,
                    requestMessages = requestMessages,
                ),
            )
        }
        return
        }

        val assistantIndex = currentMessages.indexOfFirst {
            it.id == messageId &&
                it.role == MessageRole.ASSISTANT &&
                it.status == MessageStatus.COMPLETED
        }
        if (assistantIndex == -1) {
            return
        }

        val targetAssistantMessage = currentMessages[assistantIndex]
        val loadingMessage = targetAssistantMessage.copy(
            content = "",
            status = MessageStatus.LOADING,
            reasoningContent = "",
            attachments = emptyList(),
            parts = emptyList(),
        )
        val retryMessages = currentMessages
            .take(assistantIndex) + loadingMessage
        val requestMessages = currentMessages
            .take(assistantIndex)
            .filter { it.status == MessageStatus.COMPLETED && it.hasSendableContent() }
        val retryPrompt = if (imageGenerationEnabled) {
            requestMessages.lastOrNull { it.role == MessageRole.USER }?.content?.trim().orEmpty()
        } else {
            ""
        }

        if (imageGenerationEnabled && retryPrompt.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "未找到可重试的生图提示词")
            }
            return
        }

        _uiState.update {
            it.copy(
                messages = retryMessages,
                streamingMessageId = loadingMessage.id,
                streamingContent = "",
                streamingReasoningContent = "",
                streamingParts = emptyList(),
                isSending = true,
                errorMessage = null,
            )
        }

        if (imageGenerationEnabled) {
            executeImageGeneration(
                conversationId = conversationId,
                loadingMessage = loadingMessage,
                prompt = retryPrompt,
                selectedModel = selectedModel,
                initialMessages = retryMessages,
                buildFinalMessages = { completedAssistant ->
                    retryMessages.dropLast(1) + completedAssistant
                },
            )
        } else {
            executeAssistantRoundTrip(
                conversationId = conversationId,
                loadingMessage = loadingMessage,
                requestMessages = requestMessages,
                selectedModel = selectedModel,
                initialMessages = retryMessages,
                buildFinalMessages = { completedAssistant ->
                    retryMessages.dropLast(1) + completedAssistant
                },
                promptAssemblyInput = buildPromptAssemblyInput(
                    state = state,
                    conversationId = conversationId,
                    requestMessages = requestMessages,
                ),
            )
        }
    }

    fun cancelSending() {
        sendingJob?.cancel()
        sendingJob = null
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearNoticeMessage() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    fun toggleMessageMemory(messageId: String) {
        val state = _uiState.value
        if (!state.isConversationReady) {
            _uiState.update { it.copy(errorMessage = "会话切换中，请稍后再操作记忆") }
            return
        }
        val conversationId = state.currentConversationId.takeIf { it.isNotBlank() } ?: run {
            _uiState.update { it.copy(errorMessage = "会话初始化中，请稍后重试") }
            ensureConversation()
            return
        }
        val targetMessage = currentConversationMessages(state, conversationId)
            .firstOrNull { it.id == messageId }
            ?: return
        val memoryContent = targetMessage.parts.toPlainText()
            .ifBlank { targetMessage.content.trim() }
            .trim()
        if (memoryContent.isBlank()) {
            _uiState.update { it.copy(errorMessage = "当前消息没有可记忆的文本内容") }
            return
        }

        val assistantId = state.currentAssistant?.id
            ?.trim()
            .orEmpty()
            .ifBlank { currentAssistantId.value }
        val scope = if (state.currentAssistant?.useGlobalMemory == true) {
            MemoryScopeType.GLOBAL to ""
        } else if (assistantId.isNotBlank()) {
            MemoryScopeType.ASSISTANT to assistantId
        } else {
            MemoryScopeType.CONVERSATION to conversationId
        }

        viewModelScope.launch {
            val existingEntry = memoryRepository.findEntryBySourceMessage(
                scopeType = scope.first,
                scopeId = scope.second,
                sourceMessageId = messageId,
            )
            if (existingEntry != null) {
                memoryRepository.deleteEntry(existingEntry.id)
                _uiState.update { it.copy(noticeMessage = "已取消记忆") }
            } else {
                val timestamp = nowProvider()
                memoryRepository.upsertEntry(
                    MemoryEntry(
                        scopeType = scope.first,
                        scopeId = scope.second,
                        content = memoryContent,
                        importance = 80,
                        pinned = true,
                        sourceMessageId = messageId,
                        lastUsedAt = timestamp,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    ),
                )
                _uiState.update { it.copy(noticeMessage = "已记住这条") }
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
            ?: activeProvider?.selectedModel
            ?: state.settings.selectedModel

        _uiState.update {
            it.copy(
                translation = TranslationUiState(
                    isVisible = true,
                    isLoading = true,
                    sourceText = sourceText,
                    sourceLabel = sourceLabel,
                    modelName = translationModel,
                ),
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                repository.translateText(sourceText)
            }.onSuccess { translatedText ->
                _uiState.update {
                    it.copy(
                        translation = it.translation.copy(
                            isVisible = true,
                            isLoading = false,
                            translatedText = translatedText,
                        ),
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        translation = TranslationUiState(),
                        errorMessage = throwable.message ?: "翻译失败",
                    )
                }
            }
        }
    }

    private fun validateAndGetConversationId(state: ChatUiState): String? {
        if (!state.settings.hasRequiredConfig()) {
            _uiState.update { it.copy(errorMessage = "请先前往设置页完成配置") }
            return null
        }
        if (!state.isConversationReady) {
            _uiState.update { it.copy(errorMessage = "会话切换中，请稍后再发送") }
            return null
        }
        val conversationId = state.currentConversationId
        if (conversationId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "会话初始化中，请稍后重试") }
            ensureConversation()
            return null
        }
        return conversationId
    }

    private fun executeImageGeneration(
        conversationId: String,
        loadingMessage: ChatMessage,
        prompt: String,
        selectedModel: String,
        initialMessages: List<ChatMessage>,
        buildFinalMessages: (ChatMessage) -> List<ChatMessage>,
    ) {
        sendingJob = viewModelScope.launch {
            conversationRepository.saveConversationMessages(
                conversationId = conversationId,
                messages = initialMessages,
                selectedModel = selectedModel,
            )

            try {
                val results = repository.generateImage(prompt)
                val firstResult = results.firstOrNull()
                    ?: throw IllegalStateException("图片生成接口未返回数据")

                val imageAttachments = results.mapNotNull { result ->
                    if (result.b64Data.isNotBlank()) {
                        val savedImage = imageSaver(result.b64Data)
                        MessageAttachment(
                            type = AttachmentType.IMAGE,
                            uri = savedImage.path,
                            mimeType = savedImage.mimeType,
                            fileName = savedImage.fileName,
                        )
                    } else if (result.url.isNotBlank()) {
                        MessageAttachment(
                            type = AttachmentType.IMAGE,
                            uri = result.url,
                            mimeType = "",
                            fileName = "generated-remote",
                        )
                    } else {
                        null
                    }
                }

                val revisedPrompt = firstResult.revisedPrompt
                val contentText = if (revisedPrompt.isNotBlank()) {
                    revisedPrompt
                } else {
                    "图片已生成"
                }
                val assistantParts = normalizeChatMessageParts(
                    buildList {
                        if (contentText.isNotBlank()) {
                            add(textMessagePart(contentText))
                        }
                        imageAttachments.forEach { attachment ->
                            add(
                                imageMessagePart(
                                    uri = attachment.uri,
                                    mimeType = attachment.mimeType,
                                    fileName = attachment.fileName,
                                ),
                            )
                        }
                    },
                )

                val completedMessages = buildFinalMessages(
                    loadingMessage.copy(
                        content = contentText,
                        status = MessageStatus.COMPLETED,
                        attachments = imageAttachments,
                        parts = assistantParts,
                    ),
                )
                conversationRepository.saveConversationMessages(
                    conversationId = conversationId,
                    messages = completedMessages,
                    selectedModel = selectedModel,
                )
                finishSending(completedMessages, errorMessage = null)
                launchAiTitleGeneration(conversationId, completedMessages)
            } catch (e: CancellationException) {
                val cancelledMessages = buildFinalMessages(
                    loadingMessage.copy(
                        content = "已取消",
                        status = MessageStatus.ERROR,
                    ),
                )
                conversationRepository.saveConversationMessages(
                    conversationId = conversationId,
                    messages = cancelledMessages,
                    selectedModel = selectedModel,
                )
                finishSending(cancelledMessages, errorMessage = null)
            } catch (throwable: Throwable) {
                val errorText = throwable.message ?: "图片生成失败"
                val failedMessages = buildFinalMessages(
                    loadingMessage.copy(
                        content = errorText,
                        status = MessageStatus.ERROR,
                    ),
                )
                conversationRepository.saveConversationMessages(
                    conversationId = conversationId,
                    messages = failedMessages,
                    selectedModel = selectedModel,
                )
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
        initialMessages: List<ChatMessage>,
        buildFinalMessages: (ChatMessage) -> List<ChatMessage>,
        promptAssemblyInput: PromptAssemblyInput,
    ) {
        sendingJob = viewModelScope.launch {
            conversationRepository.saveConversationMessages(
                conversationId = conversationId,
                messages = initialMessages,
                selectedModel = selectedModel,
            )

            val streamBuffer = StreamingReplyBuffer()

            try {
                val promptContext = promptContextAssembler.assemble(
                    settings = promptAssemblyInput.settings,
                    assistant = promptAssemblyInput.assistant,
                    conversation = promptAssemblyInput.conversation,
                    userInputText = promptAssemblyInput.userInputText,
                    recentMessages = promptAssemblyInput.recentMessages,
                    promptMode = PromptMode.CHAT,
                )
                _uiState.update { state ->
                    if (state.currentConversationId != conversationId) {
                        state
                    } else {
                        state.copy(latestPromptDebugDump = promptContext.debugDump)
                    }
                }
                val effectiveRequestMessages = resolveRequestMessagesForRoundTrip(
                    promptAssemblyInput = promptAssemblyInput,
                    requestMessages = requestMessages,
                )
                streamAssistantReply(
                    conversationId = conversationId,
                    loadingMessageId = loadingMessage.id,
                    requestMessages = effectiveRequestMessages,
                    streamBuffer = streamBuffer,
                    systemPrompt = promptContext.systemPrompt,
                )

                val parsedOutput = repository.parseAssistantSpecialOutput(
                    content = streamBuffer.content(),
                    existingParts = streamBuffer.parts(),
                )
                val finalParts = parsedOutput.parts
                val resolvedContent = parsedOutput.content.takeIf { it.isNotBlank() }
                    ?: if (parsedOutput.transferUpdates.isNotEmpty()) {
                        "已收款"
                    } else {
                        null
                    }
                    ?: finalParts.toContentMirror(specialFallback = "特殊玩法")
                    .ifBlank { "模型未返回有效内容" }
                val completedMessages = updateTransferStatuses(
                    messages = buildFinalMessages(
                    loadingMessage.copy(
                        content = resolvedContent,
                        status = MessageStatus.COMPLETED,
                        reasoningContent = streamBuffer.reasoning(),
                        parts = finalParts,
                    ),
                    ),
                    updates = parsedOutput.transferUpdates,
                )
                conversationRepository.saveConversationMessages(
                    conversationId = conversationId,
                    messages = completedMessages,
                    selectedModel = selectedModel,
                )
                finishSending(completedMessages, errorMessage = null)
                launchAiTitleGeneration(conversationId, completedMessages)
                launchChatSuggestions(conversationId, completedMessages)
                launchConversationSummaryGeneration(conversationId, completedMessages)
                launchAutomaticMemoryExtraction(conversationId, completedMessages)
            } catch (e: CancellationException) {
                val partialParts = streamBuffer.parts()
                val partialContent = streamBuffer.content().ifBlank {
                    partialParts.toContentMirror(specialFallback = "特殊玩法")
                }
                val cancelledMessages = buildFinalMessages(
                    loadingMessage.copy(
                        content = partialContent.ifBlank { "已取消" },
                        status = MessageStatus.ERROR,
                        reasoningContent = streamBuffer.reasoning(),
                        parts = partialParts,
                    ),
                )
                conversationRepository.saveConversationMessages(
                    conversationId = conversationId,
                    messages = cancelledMessages,
                    selectedModel = selectedModel,
                )
                finishSending(cancelledMessages, errorMessage = null)
            } catch (throwable: Throwable) {
                val errorText = throwable.message ?: "发送失败"
                val partialParts = streamBuffer.parts()
                val partialContent = streamBuffer.content().ifBlank {
                    partialParts.toContentMirror(specialFallback = "特殊玩法")
                }
                val errorContent = if (partialContent.isNotBlank()) {
                    "$partialContent\n\n---\n错误：$errorText"
                } else {
                    errorText
                }
                val failedMessages = buildFinalMessages(
                    loadingMessage.copy(
                        content = errorContent,
                        status = MessageStatus.ERROR,
                        reasoningContent = streamBuffer.reasoning(),
                        parts = partialParts,
                    ),
                )
                conversationRepository.saveConversationMessages(
                    conversationId = conversationId,
                    messages = failedMessages,
                    selectedModel = selectedModel,
                )
                finishSending(failedMessages, errorMessage = errorText)
            } finally {
                sendingJob = null
            }
        }
    }

    private fun finishSending(messages: List<ChatMessage>, errorMessage: String?) {
        _uiState.update {
            it.copy(
                messages = messages,
                streamingMessageId = "",
                streamingContent = "",
                streamingReasoningContent = "",
                streamingParts = emptyList(),
                isSending = false,
                errorMessage = errorMessage,
            )
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settings.collect { currentSettings ->
                val newAssistantId = currentSettings.selectedAssistantId.ifBlank { DEFAULT_ASSISTANT_ID }
                val previousAssistantId = currentAssistantId.value
                val activeAssistant = currentSettings.activeAssistant()
                currentAssistantId.value = newAssistantId
                _uiState.update {
                    it.copy(
                        settings = currentSettings,
                        currentAssistant = activeAssistant,
                    )
                }
                if (newAssistantId != previousAssistantId) {
                    switchToAssistant(newAssistantId)
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
                    val resolvedConversation = conversations.firstOrNull { it.id == currentConversationId.value }
                        ?: conversations.firstOrNull()

                    if (resolvedConversation == null) {
                        _uiState.update {
                            it.copy(
                                conversations = conversations,
                                currentConversationId = "",
                                displayedConversationId = "",
                                currentConversationTitle = DEFAULT_CONVERSATION_TITLE,
                                messages = emptyList(),
                                streamingMessageId = "",
                                streamingContent = "",
                                streamingReasoningContent = "",
                                streamingParts = emptyList(),
                                isConversationReady = false,
                            )
                        }
                        return@collect
                    }

                    if (resolvedConversation.id != currentConversationId.value) {
                        activateConversation(
                            conversationId = resolvedConversation.id,
                            title = resolvedConversation.title,
                        )
                    }

                    _uiState.update {
                        it.copy(
                            conversations = conversations,
                            currentConversationId = resolvedConversation.id,
                            currentConversationTitle = resolvedConversation.title,
                            displayedConversationId = it.displayedConversationId.ifBlank { resolvedConversation.id },
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
                _uiState.update { it.copy(rememberedMessageIds = rememberedIds) }
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
                    if (_uiState.value.isSending && _uiState.value.currentConversationId == conversationId) {
                        return@collect
                    }
                    _uiState.update { state ->
                        if (state.currentConversationId != conversationId) {
                            return@update state
                        }
                        state.copy(
                            displayedConversationId = conversationId,
                            messages = messages,
                            streamingMessageId = "",
                            streamingContent = "",
                            streamingReasoningContent = "",
                            streamingParts = emptyList(),
                            isConversationReady = true,
                        )
                    }
                }
        }
    }

    private fun ensureConversation() {
        viewModelScope.launch {
            val assistantId = currentAssistantId.value
            val conversation = conversationRepository.ensureActiveConversation(
                currentConversationId.value,
                assistantId,
            )
            activateConversation(
                conversationId = conversation.id,
                title = conversation.title,
            )
        }
    }

    private fun switchToAssistant(assistantId: String) {
        viewModelScope.launch {
            val conversation = conversationRepository.ensureActiveConversation(
                currentConversationId = null,
                assistantId = assistantId,
            )
            activateConversation(
                conversationId = conversation.id,
                title = conversation.title,
            )
        }
    }

    fun selectAssistant(assistantId: String) {
        if (_uiState.value.isSending) return
        currentAssistantId.value = assistantId
        val activeAssistant = _uiState.value.settings.resolvedAssistants()
            .firstOrNull { it.id == assistantId }
        _uiState.update { it.copy(currentAssistant = activeAssistant) }
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

    private fun activateConversation(
        conversationId: String,
        title: String,
    ) {
        currentConversationId.value = conversationId
        _uiState.update {
            it.copy(
                currentConversationId = conversationId,
                displayedConversationId = conversationId,
                currentConversationTitle = title,
                messages = emptyList(),
                streamingMessageId = "",
                streamingContent = "",
                streamingReasoningContent = "",
                streamingParts = emptyList(),
                input = "",
                pendingParts = emptyList(),
                isConversationReady = false,
                errorMessage = null,
                noticeMessage = null,
                chatSuggestions = emptyList(),
                chatSuggestionsModelName = "",
                latestPromptDebugDump = "",
                translation = TranslationUiState(),
            )
        }
    }

    private fun launchAiTitleGeneration(
        conversationId: String,
        messages: List<ChatMessage>,
    ) {
        val state = _uiState.value
        val activeProvider = state.settings.activeProvider() ?: return
        val titleModel = activeProvider.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY)
        if (titleModel.isBlank()) return
        val firstUserMessage = messages.firstOrNull { it.role == MessageRole.USER }
            ?.content?.trim().orEmpty()
        if (firstUserMessage.isBlank()) return

        viewModelScope.launch {
            runCatching {
                repository.generateTitle(
                    firstUserMessage = firstUserMessage,
                    baseUrl = activeProvider.baseUrl,
                    apiKey = activeProvider.apiKey,
                    modelId = titleModel,
                )
            }.onSuccess { aiTitle ->
                conversationRepository.updateConversationTitle(conversationId, aiTitle)
                if (_uiState.value.currentConversationId == conversationId) {
                    _uiState.update {
                        it.copy(
                            noticeMessage = "标题已由 $titleModel 生成",
                        )
                    }
                }
            }
        }
    }

    private fun launchChatSuggestions(
        conversationId: String,
        messages: List<ChatMessage>,
    ) {
        val state = _uiState.value
        val activeProvider = state.settings.activeProvider() ?: return
        val suggestionModel = activeProvider.resolveFunctionModel(ProviderFunction.CHAT_SUGGESTION)
        if (suggestionModel.isBlank()) return

        val lastMessages = messages.takeLast(4)
        val summary = lastMessages.joinToString("\n") { msg ->
            val role = if (msg.role == MessageRole.USER) "用户" else "助手"
            "$role: ${msg.content.take(200)}"
        }

        viewModelScope.launch {
            runCatching {
                repository.generateChatSuggestions(
                    conversationSummary = summary,
                    baseUrl = activeProvider.baseUrl,
                    apiKey = activeProvider.apiKey,
                    modelId = suggestionModel,
                )
            }.onSuccess { suggestions ->
                if (_uiState.value.currentConversationId == conversationId) {
                    _uiState.update {
                        it.copy(
                            chatSuggestions = suggestions,
                            chatSuggestionsModelName = suggestionModel,
                        )
                    }
                }
            }
        }
    }

    private fun launchConversationSummaryGeneration(
        conversationId: String,
        messages: List<ChatMessage>,
    ) {
        val completedMessages = messages.filter { it.status == MessageStatus.COMPLETED }
        if (completedMessages.size <= SUMMARY_TRIGGER_MESSAGE_COUNT) {
            return
        }

        val activeProvider = _uiState.value.settings.activeProvider() ?: return
        val summaryModel = activeProvider.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY)
        if (summaryModel.isBlank()) return

        val olderMessages = completedMessages.dropLast(SUMMARY_RECENT_MESSAGE_WINDOW)
        if (olderMessages.size < SUMMARY_MIN_COVERED_MESSAGE_COUNT) {
            return
        }

        viewModelScope.launch {
            val existingSummary = conversationSummaryRepository.getSummary(conversationId)
            if (existingSummary != null && existingSummary.coveredMessageCount >= olderMessages.size) {
                return@launch
            }

            val summaryInput = buildSummaryInputText(olderMessages)
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
                        assistantId = currentAssistantId.value,
                        summary = summaryText,
                        coveredMessageCount = olderMessages.size,
                        updatedAt = nowProvider(),
                    ),
                )
                if (_uiState.value.currentConversationId == conversationId) {
                    _uiState.update {
                        it.copy(noticeMessage = "上下文摘要已更新")
                    }
                }
            }
        }
    }

    private fun launchAutomaticMemoryExtraction(
        conversationId: String,
        messages: List<ChatMessage>,
    ) {
        val state = _uiState.value
        val assistant = state.currentAssistant ?: state.settings.activeAssistant() ?: return
        if (!assistant.memoryEnabled) {
            return
        }

        val activeProvider = state.settings.activeProvider() ?: return
        val memoryModel = activeProvider.resolveFunctionModel(ProviderFunction.MEMORY)
        if (memoryModel.isBlank()) return

        val completedMessages = messages.filter { it.status == MessageStatus.COMPLETED }
        val recentMessages = completedMessages.takeLast(AUTO_MEMORY_MESSAGE_WINDOW)
        val memoryInput = buildMemoryExtractionInput(recentMessages)
        if (memoryInput.isBlank()) {
            return
        }
        val latestMessageId = recentMessages.lastOrNull()?.id.orEmpty()
        val assistantId = assistant.id.trim()
        if (assistantId.isBlank() && !assistant.useGlobalMemory) {
            return
        }

        viewModelScope.launch {
            runCatching {
                repository.generateMemoryEntries(
                    conversationExcerpt = memoryInput,
                    baseUrl = activeProvider.baseUrl,
                    apiKey = activeProvider.apiKey,
                    modelId = memoryModel,
                )
            }.onSuccess { memoryItems ->
                if (memoryItems.isEmpty()) return@onSuccess
                persistLongTermMemories(
                    assistant = assistant,
                    latestMessageId = latestMessageId,
                    memoryItems = memoryItems,
                    baseUrl = activeProvider.baseUrl,
                    apiKey = activeProvider.apiKey,
                    modelId = memoryModel,
                )
            }
        }
    }

    private fun buildMemoryExtractionInput(
        messages: List<ChatMessage>,
    ): String {
        return messages.joinToString(separator = "\n") { message ->
            val role = if (message.role == MessageRole.USER) "用户" else "助手"
            val content = message.parts.toPlainText()
                .ifBlank { message.content }
                .trim()
                .take(240)
            "$role: $content"
        }.take(MAX_MEMORY_INPUT_LENGTH)
    }

    private fun normalizeMemoryContent(
        value: String,
    ): String {
        return value.trim()
            .replace(Regex("\\s+"), " ")
            .removePrefix("-")
            .removePrefix("•")
            .trim()
    }

    private fun buildSummaryInputText(
        messages: List<ChatMessage>,
    ): String {
        return messages.joinToString(separator = "\n") { message ->
            val role = if (message.role == MessageRole.USER) "用户" else "助手"
            val content = message.parts.toPlainText()
                .ifBlank { message.content }
                .trim()
                .take(200)
            "$role: $content"
        }.take(MAX_SUMMARY_INPUT_LENGTH)
    }

    private suspend fun persistLongTermMemories(
        assistant: Assistant,
        latestMessageId: String,
        memoryItems: List<String>,
        baseUrl: String,
        apiKey: String,
        modelId: String,
    ) {
        val scopeType = if (assistant.useGlobalMemory) {
            MemoryScopeType.GLOBAL
        } else {
            MemoryScopeType.ASSISTANT
        }
        val scopeId = if (assistant.useGlobalMemory) {
            ""
        } else {
            assistant.id.trim()
        }
        if (scopeType == MemoryScopeType.ASSISTANT && scopeId.isBlank()) {
            return
        }
        val existingEntries = currentMemoryEntries.value.filter { entry ->
            entry.scopeType == scopeType && entry.resolvedScopeId() == scopeId
        }
        val pinnedEntries = existingEntries.filter { it.pinned }
        val mutableEntries = existingEntries.filterNot { it.pinned }
        val normalizedItems = (mutableEntries.map { normalizeMemoryContent(it.content) } + memoryItems.map(::normalizeMemoryContent))
            .filter { it.isNotBlank() }
            .distinct()
        val condensedTargetCount = assistant.memoryMaxItems.coerceAtLeast(1).coerceAtMost(3)
        val condensedItems = if (normalizedItems.size > condensedTargetCount) {
            runCatching {
                repository.condenseRoleplayMemories(
                    memoryItems = normalizedItems,
                    mode = RoleplayMemoryCondenseMode.CHARACTER,
                    maxItems = condensedTargetCount,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    modelId = modelId,
                )
            }.getOrDefault(normalizedItems.take(condensedTargetCount))
        } else {
            normalizedItems
        }
        mutableEntries.forEach { entry ->
            memoryRepository.deleteEntry(entry.id)
        }
        val timestamp = nowProvider()
        condensedItems
            .take((assistant.memoryMaxItems - pinnedEntries.size).coerceAtLeast(0))
            .forEachIndexed { index, content ->
                memoryRepository.upsertEntry(
                    MemoryEntry(
                        scopeType = scopeType,
                        scopeId = scopeId,
                        content = content,
                        importance = 60,
                        pinned = false,
                        sourceMessageId = latestMessageId,
                        lastUsedAt = timestamp + index,
                        createdAt = timestamp + index,
                        updatedAt = timestamp + index,
                    ),
                )
            }
    }

    private suspend fun resolveRequestMessagesForRoundTrip(
        promptAssemblyInput: PromptAssemblyInput,
        requestMessages: List<ChatMessage>,
    ): List<ChatMessage> {
        val summary = conversationSummaryRepository.getSummary(promptAssemblyInput.conversation.id)
        if (summary?.summary?.isBlank() != false) {
            return requestMessages
        }

        val recentWindow = promptAssemblyInput.assistant?.contextMessageSize
            ?.takeIf { it > 0 }
            ?: SUMMARY_RECENT_MESSAGE_WINDOW
        if (requestMessages.size <= recentWindow) {
            return requestMessages
        }
        return requestMessages.takeLast(recentWindow)
    }

    private suspend fun streamAssistantReply(
        conversationId: String,
        loadingMessageId: String,
        requestMessages: List<ChatMessage>,
        streamBuffer: StreamingReplyBuffer,
        systemPrompt: String = "",
    ) = coroutineScope {
        var streamCompleted = false
        val uiPumpJob = launch {
            while (true) {
                val advanced = advanceStreamingFrame(
                    streamBuffer = streamBuffer,
                    streamCompleted = streamCompleted,
                )
                if (advanced) {
                    publishStreamingFrame(
                        conversationId = conversationId,
                        loadingMessageId = loadingMessageId,
                        content = streamBuffer.visibleContent(),
                        reasoning = streamBuffer.visibleReasoning(),
                        parts = streamBuffer.visibleParts(),
                    )
                }
                if (streamCompleted && !streamBuffer.hasPending()) {
                    break
                }
                delay(STREAM_FRAME_DELAY_MILLIS)
            }
            publishStreamingFrame(
                conversationId = conversationId,
                loadingMessageId = loadingMessageId,
                content = streamBuffer.content(),
                reasoning = streamBuffer.reasoning(),
                parts = streamBuffer.parts(),
            )
        }

        try {
            repository.sendMessageStream(
                messages = requestMessages,
                systemPrompt = systemPrompt,
                promptMode = PromptMode.CHAT,
            ).collect { event ->
                when (event) {
                    is ChatStreamEvent.ContentDelta -> streamBuffer.appendContent(event.value)
                    is ChatStreamEvent.ImageDelta -> streamBuffer.appendImage(event.part)
                    is ChatStreamEvent.ReasoningDelta -> streamBuffer.appendReasoning(event.value)
                    ChatStreamEvent.Completed -> streamCompleted = true
                }
            }
            streamCompleted = true
            uiPumpJob.join()
        } catch (throwable: Throwable) {
            streamCompleted = true
            uiPumpJob.cancelAndJoin()
            throw throwable
        }
    }

    private fun advanceStreamingFrame(
        streamBuffer: StreamingReplyBuffer,
        streamCompleted: Boolean,
    ): Boolean {
        val contentAdvanced = streamBuffer.advanceContent(
            step = resolveStreamingBatchSize(
                pendingLength = streamBuffer.pendingContentLength,
                streamCompleted = streamCompleted,
            ),
        )
        val reasoningAdvanced = streamBuffer.advanceReasoning(
            step = resolveStreamingBatchSize(
                pendingLength = streamBuffer.pendingReasoningLength,
                streamCompleted = streamCompleted,
            ),
        )
        return contentAdvanced || reasoningAdvanced
    }

    private fun publishStreamingFrame(
        conversationId: String,
        loadingMessageId: String,
        content: String,
        reasoning: String,
        parts: List<ChatMessagePart>,
    ) {
        _uiState.update { state ->
            if (state.currentConversationId != conversationId || state.streamingMessageId != loadingMessageId) {
                return@update state
            }
            state.copy(
                streamingContent = content,
                streamingReasoningContent = reasoning,
                streamingParts = parts,
            )
        }
    }

    private fun currentConversationMessages(
        state: ChatUiState,
        conversationId: String,
    ): List<ChatMessage> {
        return state.messages.filter { it.conversationId == conversationId }
    }

    private fun resolveSelectedModelId(settings: AppSettings): String {
        return settings.activeProvider()?.selectedModel
            ?.takeIf { it.isNotBlank() }
            ?: settings.selectedModel
    }

    private fun supportsImageGeneration(
        settings: AppSettings,
        modelId: String,
    ): Boolean {
        if (modelId.isBlank()) {
            return false
        }
        val abilities = settings.activeProvider()?.resolveModelAbilities(modelId)
            ?: inferModelAbilities(modelId)
        return ModelAbility.IMAGE_GENERATION in abilities
    }

    private fun buildUserMessageParts(
        text: String,
        pendingParts: List<ChatMessagePart>,
    ): List<ChatMessagePart> {
        return normalizeChatMessageParts(
            buildList {
                if (text.isNotBlank()) {
                    add(textMessagePart(text))
                }
                addAll(pendingParts.filter { it.type != ChatMessagePartType.TEXT })
            },
        )
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
        val selectedModel = resolveSelectedModelId(state.settings)
        val baseMessages = currentConversationMessages(state, conversationId)
        val userMessage = buildMessage(
            conversationId = conversationId,
            role = MessageRole.USER,
            content = userParts.toContentMirror(
                imageFallback = "图片已发送",
                fileFallback = "文件已附加",
                specialFallback = "转账",
            ),
            attachments = userParts.toMessageAttachments(),
            parts = userParts,
        )
        val loadingMessage = buildMessage(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.LOADING,
            modelName = selectedModel,
        )
        val persistedMessages = baseMessages + userMessage + loadingMessage
        val requestMessages = baseMessages.filter { it.status == MessageStatus.COMPLETED } + userMessage

        _uiState.update {
            it.copy(
                messages = persistedMessages,
                streamingMessageId = loadingMessage.id,
                streamingContent = "",
                streamingReasoningContent = "",
                streamingParts = emptyList(),
                input = nextInput,
                pendingParts = nextPendingParts,
                isSending = true,
                errorMessage = null,
            )
        }

        if (!forceChatRoundTrip && supportsImageGeneration(state.settings, selectedModel)) {
            executeImageGeneration(
                conversationId = conversationId,
                loadingMessage = loadingMessage,
                prompt = imageGenerationPrompt,
                selectedModel = selectedModel,
                initialMessages = persistedMessages,
                buildFinalMessages = { completedAssistant ->
                    baseMessages + userMessage + completedAssistant
                },
            )
        } else {
            executeAssistantRoundTrip(
                conversationId = conversationId,
                loadingMessage = loadingMessage,
                requestMessages = requestMessages,
                selectedModel = selectedModel,
                initialMessages = persistedMessages,
                buildFinalMessages = { completedAssistant ->
                    baseMessages + userMessage + completedAssistant
                },
                promptAssemblyInput = buildPromptAssemblyInput(
                    state = state,
                    conversationId = conversationId,
                    requestMessages = requestMessages,
                ),
            )
        }
    }

    private fun buildPromptAssemblyInput(
        state: ChatUiState,
        conversationId: String,
        requestMessages: List<ChatMessage>,
    ): PromptAssemblyInput {
        val timestamp = nowProvider()
        val fallbackAssistantId = state.currentAssistant?.id
            ?.takeIf { it.isNotBlank() }
            ?: currentAssistantId.value
        val conversation = state.conversations.firstOrNull { it.id == conversationId }
            ?: Conversation(
                id = conversationId,
                createdAt = timestamp,
                updatedAt = timestamp,
                assistantId = fallbackAssistantId,
            )
        val latestUserMessage = requestMessages.lastOrNull { it.role == MessageRole.USER }

        return PromptAssemblyInput(
            settings = state.settings,
            assistant = state.currentAssistant ?: state.settings.activeAssistant(),
            conversation = conversation,
            userInputText = latestUserMessage
                ?.parts
                ?.toPlainText()
                .orEmpty()
                .ifBlank { latestUserMessage?.content.orEmpty() },
            recentMessages = requestMessages,
        )
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
                part.copy(
                    specialStatus = update.status,
                )
            }
            if (updatedParts == message.parts) {
                message
            } else {
                changed = true
                message.copy(
                    parts = normalizeChatMessageParts(updatedParts),
                    content = normalizeChatMessageParts(updatedParts).toContentMirror(
                        specialFallback = "转账",
                    ).ifBlank { message.content },
                )
            }
        }
        return if (changed) updatedMessages else messages
    }

    private fun buildMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        status: MessageStatus = MessageStatus.COMPLETED,
        modelName: String = "",
        reasoningContent: String = "",
        attachments: List<MessageAttachment> = emptyList(),
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
            attachments = attachments,
            parts = normalizeChatMessageParts(parts),
        )
    }

    companion object {
        private const val STREAM_FRAME_DELAY_MILLIS = 32L
        private const val STREAM_SMALL_BATCH_SIZE = 10
        private const val STREAM_MEDIUM_BATCH_SIZE = 20
        private const val STREAM_LARGE_BATCH_SIZE = 40
        private const val STREAM_FINISH_BATCH_SIZE = 96
        private const val SUMMARY_TRIGGER_MESSAGE_COUNT = 20
        private const val SUMMARY_MIN_COVERED_MESSAGE_COUNT = 8
        private const val SUMMARY_RECENT_MESSAGE_WINDOW = 12
        private const val MAX_SUMMARY_INPUT_LENGTH = 4_000
        private const val AUTO_MEMORY_MESSAGE_WINDOW = 6
        private const val MAX_MEMORY_INPUT_LENGTH = 2_400

        private fun resolveStreamingBatchSize(
            pendingLength: Int,
            streamCompleted: Boolean,
        ): Int {
            return when {
                pendingLength <= 0 -> 0
                streamCompleted -> pendingLength.coerceAtMost(STREAM_FINISH_BATCH_SIZE)
                pendingLength >= 200 -> STREAM_LARGE_BATCH_SIZE
                pendingLength >= 80 -> STREAM_MEDIUM_BATCH_SIZE
                else -> STREAM_SMALL_BATCH_SIZE
            }
        }

        fun factory(
            repository: AiRepository,
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
                        repository,
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

private data class PromptAssemblyInput(
    val settings: AppSettings,
    val assistant: Assistant?,
    val conversation: Conversation,
    val userInputText: String,
    val recentMessages: List<ChatMessage>,
)

private class StreamingReplyBuffer {
    private sealed interface PendingVisualDelta {
        data class Text(
            val value: StringBuilder,
        ) : PendingVisualDelta

        data class Image(
            val part: ChatMessagePart,
        ) : PendingVisualDelta
    }

    private val lock = Any()
    private val fullContent = StringBuilder()
    private val fullReasoning = StringBuilder()
    private val visibleContent = StringBuilder()
    private val visibleReasoning = StringBuilder()
    private val pendingReasoning = StringBuilder()
    private val fullParts = mutableListOf<ChatMessagePart>()
    private val visibleParts = mutableListOf<ChatMessagePart>()
    private val pendingVisuals = ArrayDeque<PendingVisualDelta>()

    val pendingContentLength: Int
        get() = synchronized(lock) {
            pendingVisuals.sumOf { delta ->
                when (delta) {
                    is PendingVisualDelta.Text -> delta.value.length
                    is PendingVisualDelta.Image -> 0
                }
            }
        }

    val pendingReasoningLength: Int
        get() = synchronized(lock) { pendingReasoning.length }

    fun appendContent(value: String) {
        if (value.isEmpty()) return
        synchronized(lock) {
            fullContent.append(value)
            pendingVisuals.addLast(PendingVisualDelta.Text(StringBuilder(value)))
            fullParts.appendText(value)
        }
    }

    fun appendImage(part: ChatMessagePart) {
        val normalizedPart = normalizeChatMessageParts(listOf(part)).firstOrNull() ?: return
        synchronized(lock) {
            fullParts += normalizedPart
            pendingVisuals.addLast(PendingVisualDelta.Image(normalizedPart))
        }
    }

    fun appendReasoning(value: String) {
        if (value.isEmpty()) return
        synchronized(lock) {
            fullReasoning.append(value)
            pendingReasoning.append(value)
        }
    }

    fun advanceContent(step: Int): Boolean = synchronized(lock) {
        drainPendingVisuals(step)
    }

    fun advanceReasoning(step: Int): Boolean = synchronized(lock) {
        drainLeadingText(
            source = pendingReasoning,
            target = visibleReasoning,
            step = step,
        )
    }

    fun hasPending(): Boolean = synchronized(lock) {
        pendingVisuals.isNotEmpty() || pendingReasoning.isNotEmpty()
    }

    fun content(): String = synchronized(lock) { fullContent.toString() }

    fun reasoning(): String = synchronized(lock) { fullReasoning.toString() }

    fun visibleContent(): String = synchronized(lock) { visibleContent.toString() }

    fun visibleReasoning(): String = synchronized(lock) { visibleReasoning.toString() }

    fun parts(): List<ChatMessagePart> = synchronized(lock) { fullParts.toList() }

    fun visibleParts(): List<ChatMessagePart> = synchronized(lock) { visibleParts.toList() }

    private fun drainLeadingText(
        source: StringBuilder,
        target: StringBuilder,
        step: Int,
    ): Boolean {
        if (source.isEmpty() || step <= 0) {
            return false
        }
        val endIndex = step.coerceAtMost(source.length)
        target.append(source.substring(0, endIndex))
        source.delete(0, endIndex)
        return true
    }

    private fun drainPendingVisuals(step: Int): Boolean {
        var remaining = step
        var advanced = false

        while (pendingVisuals.isNotEmpty()) {
            when (val next = pendingVisuals.first()) {
                is PendingVisualDelta.Image -> {
                    visibleParts += next.part
                    pendingVisuals.removeFirst()
                    advanced = true
                }

                is PendingVisualDelta.Text -> {
                    if (remaining <= 0) {
                        return advanced
                    }

                    val endIndex = remaining.coerceAtMost(next.value.length)
                    if (endIndex <= 0) {
                        return advanced
                    }

                    val chunk = next.value.substring(0, endIndex)
                    visibleContent.append(chunk)
                    visibleParts.appendText(chunk)
                    next.value.delete(0, endIndex)
                    remaining -= endIndex
                    advanced = true

                    if (next.value.isEmpty()) {
                        pendingVisuals.removeFirst()
                    }

                    if (remaining <= 0) {
                        return advanced
                    }
                }
            }
        }

        return advanced
    }

    private fun MutableList<ChatMessagePart>.appendText(text: String) {
        if (text.isEmpty()) {
            return
        }

        val lastPart = lastOrNull()
        if (lastPart?.type == com.example.myapplication.model.ChatMessagePartType.TEXT) {
            this[lastIndex] = lastPart.copy(text = lastPart.text + text)
        } else {
            add(textMessagePart(text))
        }
    }
}
