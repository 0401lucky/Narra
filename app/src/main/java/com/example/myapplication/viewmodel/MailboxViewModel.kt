package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.ai.MailboxProactiveRequest
import com.example.myapplication.data.repository.ai.MailboxPromptService
import com.example.myapplication.data.repository.ai.MailboxReplyRequest
import com.example.myapplication.data.repository.ai.tooling.MemoryWriteService
import com.example.myapplication.data.repository.context.PendingMemoryProposalRepository
import com.example.myapplication.data.repository.mailbox.MailboxRepository
import com.example.myapplication.data.repository.phone.PhoneSnapshotRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MailboxBox
import com.example.myapplication.model.MailboxLetter
import com.example.myapplication.model.MailboxProactiveFrequency
import com.example.myapplication.model.MailboxSettings
import com.example.myapplication.model.MailboxSource
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayScenario
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MailboxScreenMode {
    LIST,
    DETAIL,
    COMPOSE,
}

data class MailboxUiState(
    val scenarioId: String = "",
    val conversationId: String = "",
    val assistantId: String = "",
    val characterName: String = "角色",
    val userName: String = "我",
    val selectedBox: MailboxBox = MailboxBox.INBOX,
    val letters: List<MailboxLetter> = emptyList(),
    val visibleLetters: List<MailboxLetter> = emptyList(),
    val selectedLetter: MailboxLetter? = null,
    val unreadCount: Int = 0,
    val searchQuery: String = "",
    val selectedTag: String = "",
    val unreadOnly: Boolean = false,
    val availableTags: List<String> = emptyList(),
    val diaryEntryCount: Int = 0,
    val latestDiaryTitle: String = "",
    val phoneClueCount: Int = 0,
    val phoneClueSummary: String = "",
    val draftLetterId: String = "",
    val draftSubject: String = "",
    val draftContent: String = "",
    val replyToLetterId: String = "",
    val includeRecentChat: Boolean = true,
    val includePhoneClues: Boolean = true,
    val allowMemory: Boolean = true,
    val generateReplyAfterSend: Boolean = true,
    val settings: MailboxSettings = MailboxSettings(),
    val showSettings: Boolean = false,
    val activeMode: MailboxScreenMode = MailboxScreenMode.LIST,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isGeneratingReply: Boolean = false,
    val isGeneratingProactiveLetter: Boolean = false,
    val pendingMemoryProposal: PendingMemoryProposal? = null,
    val pendingMemoryLetterId: String = "",
    val noticeMessage: String? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MailboxViewModel(
    private val initialScenarioId: String,
    private val settingsRepository: AiSettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val roleplayRepository: RoleplayRepository,
    private val phoneSnapshotRepository: PhoneSnapshotRepository,
    private val mailboxRepository: MailboxRepository,
    private val mailboxPromptService: MailboxPromptService,
    private val pendingMemoryProposalRepository: PendingMemoryProposalRepository,
    private val memoryWriteService: MemoryWriteService,
) : ViewModel() {
    private val selectedBoxFlow = MutableStateFlow(MailboxBox.INBOX)
    private var pendingProposalJob: Job? = null
    private var diaryContextJob: Job? = null
    private var phoneContextJob: Job? = null
    private var settingsJob: Job? = null

    private val _uiState = MutableStateFlow(
        MailboxUiState(
            scenarioId = initialScenarioId,
            isLoading = true,
        ),
    )
    val uiState: StateFlow<MailboxUiState> = _uiState.asStateFlow()

    init {
        loadContext()
        observeLetters()
        observeUnreadCount()
        observeMailboxSettings()
    }

    fun selectBox(box: MailboxBox) {
        selectedBoxFlow.value = box
        _uiState.update {
            val next = it.copy(
                selectedBox = box,
                selectedLetter = null,
                selectedTag = "",
                unreadOnly = if (box == MailboxBox.INBOX) it.unreadOnly else false,
                activeMode = MailboxScreenMode.LIST,
            )
            next.copy(visibleLetters = filterLetters(next))
        }
    }

    fun updateSearchQuery(value: String) {
        _uiState.update {
            val next = it.copy(searchQuery = value.take(48))
            next.copy(visibleLetters = filterLetters(next))
        }
    }

    fun selectTag(tag: String) {
        _uiState.update {
            val normalized = tag.trim()
            val next = it.copy(
                selectedTag = if (it.selectedTag == normalized) "" else normalized,
            )
            next.copy(visibleLetters = filterLetters(next))
        }
    }

    fun updateUnreadOnly(enabled: Boolean) {
        _uiState.update {
            val next = it.copy(unreadOnly = enabled && it.selectedBox == MailboxBox.INBOX)
            next.copy(visibleLetters = filterLetters(next))
        }
    }

    fun clearFilters() {
        _uiState.update {
            val next = it.copy(
                searchQuery = "",
                selectedTag = "",
                unreadOnly = false,
            )
            next.copy(visibleLetters = filterLetters(next))
        }
    }

    fun selectLetter(letterId: String) {
        if (letterId.isBlank()) {
            return
        }
        viewModelScope.launch {
            val letter = mailboxRepository.getLetter(letterId) ?: return@launch
            if (letter.box == MailboxBox.INBOX && !letter.isRead) {
                mailboxRepository.markRead(letterId)
            }
            _uiState.update {
                it.copy(
                    selectedLetter = letter.copy(isRead = true),
                    activeMode = MailboxScreenMode.DETAIL,
                )
            }
        }
    }

    fun backToList() {
        _uiState.update {
            it.copy(
                activeMode = MailboxScreenMode.LIST,
                selectedLetter = null,
            )
        }
    }

    fun startCompose(replyToLetterId: String = "") {
        val replyTo = _uiState.value.letters.firstOrNull { it.id == replyToLetterId }
            ?: _uiState.value.selectedLetter?.takeIf { it.id == replyToLetterId }
        _uiState.update {
            it.copy(
                activeMode = MailboxScreenMode.COMPOSE,
                draftLetterId = "",
                draftSubject = replyTo?.subject?.let { subject ->
                    if (subject.startsWith("Re:", ignoreCase = true)) subject else "Re: $subject"
                }.orEmpty(),
                draftContent = "",
                replyToLetterId = replyToLetterId,
                includeRecentChat = it.settings.includeRecentChatByDefault,
                includePhoneClues = it.settings.includePhoneCluesByDefault,
                allowMemory = replyTo?.allowMemory ?: it.settings.allowMemoryByDefault,
                generateReplyAfterSend = it.settings.autoReplyToUserLetters,
            )
        }
    }

    fun editDraft(letterId: String) {
        viewModelScope.launch {
            val draft = mailboxRepository.getLetter(letterId) ?: return@launch
            if (draft.box != MailboxBox.DRAFT) {
                return@launch
            }
            _uiState.update {
                it.copy(
                    activeMode = MailboxScreenMode.COMPOSE,
                    draftLetterId = draft.id,
                    draftSubject = draft.subject,
                    draftContent = draft.content,
                    replyToLetterId = draft.replyToLetterId,
                    allowMemory = draft.allowMemory,
                )
            }
        }
    }

    fun updateDraftSubject(value: String) {
        _uiState.update { it.copy(draftSubject = value) }
    }

    fun updateDraftContent(value: String) {
        _uiState.update { it.copy(draftContent = value) }
    }

    fun updateIncludeRecentChat(enabled: Boolean) {
        _uiState.update { it.copy(includeRecentChat = enabled) }
    }

    fun updateIncludePhoneClues(enabled: Boolean) {
        _uiState.update { it.copy(includePhoneClues = enabled) }
    }

    fun updateAllowMemory(enabled: Boolean) {
        _uiState.update { it.copy(allowMemory = enabled) }
    }

    fun updateGenerateReplyAfterSend(enabled: Boolean) {
        _uiState.update { it.copy(generateReplyAfterSend = enabled) }
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    fun updateAutoReplySetting(enabled: Boolean) {
        updateMailboxSettings { it.copy(autoReplyToUserLetters = enabled) }
    }

    fun updateDefaultIncludeRecentChatSetting(enabled: Boolean) {
        updateMailboxSettings { it.copy(includeRecentChatByDefault = enabled) }
    }

    fun updateDefaultIncludePhoneCluesSetting(enabled: Boolean) {
        updateMailboxSettings { it.copy(includePhoneCluesByDefault = enabled) }
    }

    fun updateDefaultAllowMemorySetting(enabled: Boolean) {
        updateMailboxSettings { it.copy(allowMemoryByDefault = enabled) }
    }

    fun updateProactiveFrequency(frequency: MailboxProactiveFrequency) {
        updateMailboxSettings { it.copy(proactiveFrequency = frequency) }
    }

    fun requestProactiveLetterNow() {
        generateProactiveLetter(force = true)
    }

    fun saveDraft() {
        val state = _uiState.value
        if (state.draftSubject.isBlank() && state.draftContent.isBlank()) {
            _uiState.update { it.copy(errorMessage = "没有可保存的内容") }
            return
        }
        if (state.isSaving) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                mailboxRepository.saveDraft(
                    scenarioId = state.scenarioId,
                    conversationId = state.conversationId,
                    assistantId = state.assistantId,
                    subject = state.draftSubject,
                    content = state.draftContent,
                    replyToLetterId = state.replyToLetterId,
                    allowMemory = state.allowMemory,
                    existingLetterId = state.draftLetterId,
                )
            }.onSuccess { draft ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        draftLetterId = draft.id,
                        selectedBox = MailboxBox.DRAFT,
                        noticeMessage = "草稿已保存",
                    )
                }
                selectedBoxFlow.value = MailboxBox.DRAFT
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = throwable.message ?: "草稿保存失败，请稍后重试",
                    )
                }
            }
        }
    }

    fun sendLetter() {
        val state = _uiState.value
        if (state.draftContent.isBlank()) {
            _uiState.update { it.copy(errorMessage = "正文还没有内容") }
            return
        }
        if (state.isGeneratingReply && state.generateReplyAfterSend) {
            _uiState.update { it.copy(errorMessage = "上一封回信还在生成，稍等一下再寄出") }
            return
        }
        if (state.isSaving) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                val draft = mailboxRepository.saveDraft(
                    scenarioId = state.scenarioId,
                    conversationId = state.conversationId,
                    assistantId = state.assistantId,
                    subject = state.draftSubject,
                    content = state.draftContent,
                    replyToLetterId = state.replyToLetterId,
                    allowMemory = state.allowMemory,
                    existingLetterId = state.draftLetterId,
                )
                mailboxRepository.sendLetter(draft)
            }.onSuccess { sent ->
                selectedBoxFlow.value = MailboxBox.SENT
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        selectedBox = MailboxBox.SENT,
                        activeMode = MailboxScreenMode.LIST,
                        draftLetterId = "",
                        draftSubject = "",
                        draftContent = "",
                        replyToLetterId = "",
                        noticeMessage = "信已寄出",
                    )
                }
                if (state.generateReplyAfterSend) {
                    generateReplyFor(sent)
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = throwable.message ?: "寄出失败，已保留你的草稿",
                    )
                }
            }
        }
    }

    fun generateReplyFor(letterId: String) {
        if (letterId.isBlank()) {
            return
        }
        viewModelScope.launch {
            val letter = mailboxRepository.getLetter(letterId) ?: return@launch
            generateReplyFor(letter)
        }
    }

    fun archive(letterId: String) {
        if (letterId.isBlank()) {
            return
        }
        viewModelScope.launch {
            runCatching {
                mailboxRepository.archive(letterId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        activeMode = MailboxScreenMode.LIST,
                        selectedLetter = null,
                        noticeMessage = "已归档",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(errorMessage = throwable.message ?: "归档失败") }
            }
        }
    }

    fun delete(letterId: String) {
        if (letterId.isBlank()) {
            return
        }
        viewModelScope.launch {
            runCatching {
                mailboxRepository.moveToTrash(letterId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        activeMode = MailboxScreenMode.LIST,
                        selectedLetter = null,
                        noticeMessage = "已删除",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(errorMessage = throwable.message ?: "删除失败") }
            }
        }
    }

    fun quoteLetter(letterId: String) {
        val letter = _uiState.value.selectedLetter?.takeIf { it.id == letterId }
            ?: _uiState.value.letters.firstOrNull { it.id == letterId }
            ?: return
        _uiState.update {
            it.copy(
                activeMode = MailboxScreenMode.COMPOSE,
                draftLetterId = "",
                draftSubject = "Re: ${letter.subject.ifBlank { "那封信" }}",
                draftContent = "\n\n—— 引用来信：${letter.excerpt.ifBlank { letter.content.take(80) }}",
                replyToLetterId = letter.id,
            )
        }
    }

    fun createMemoryCandidate(letterId: String) {
        val state = _uiState.value
        val letter = state.selectedLetter?.takeIf { it.id == letterId }
            ?: state.letters.firstOrNull { it.id == letterId }
            ?: return
        if (!letter.allowMemory) {
            _uiState.update { it.copy(errorMessage = "这封信未允许沉淀为记忆") }
            return
        }
        viewModelScope.launch {
            val content = letter.memoryCandidate.ifBlank {
                buildMemoryCandidate(letter, state.characterName)
            }
            val proposal = PendingMemoryProposal(
                conversationId = state.conversationId,
                assistantId = state.assistantId,
                scopeType = resolveMemoryScope(state.assistantId),
                content = content,
                reason = "来自信箱：${letter.subject.ifBlank { "未命名的信" }}",
                importance = 3,
            )
            pendingMemoryProposalRepository.clearConversation(state.conversationId)
            pendingMemoryProposalRepository.upsertProposal(proposal)
            _uiState.update {
                it.copy(
                    pendingMemoryProposal = proposal,
                    pendingMemoryLetterId = letter.id,
                    noticeMessage = "已生成记忆候选，请确认后再写入记忆",
                )
            }
        }
    }

    fun approvePendingMemoryProposal() {
        val state = _uiState.value
        val proposalId = state.pendingMemoryProposal?.id.orEmpty()
        if (proposalId.isBlank()) {
            return
        }
        viewModelScope.launch {
            runCatching {
                memoryWriteService.approveProposal(proposalId)
            }.onSuccess { savedEntry ->
                if (savedEntry != null && state.pendingMemoryLetterId.isNotBlank()) {
                    mailboxRepository.linkMemory(state.pendingMemoryLetterId, savedEntry.id)
                }
                _uiState.update {
                    it.copy(
                        pendingMemoryProposal = null,
                        pendingMemoryLetterId = "",
                        noticeMessage = if (savedEntry == null) "记忆候选已处理" else "已写入记忆",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(errorMessage = throwable.message ?: "记忆写入失败") }
            }
        }
    }

    fun rejectPendingMemoryProposal() {
        val proposalId = _uiState.value.pendingMemoryProposal?.id.orEmpty()
        if (proposalId.isBlank()) {
            _uiState.update { it.copy(pendingMemoryProposal = null, pendingMemoryLetterId = "") }
            return
        }
        viewModelScope.launch {
            memoryWriteService.rejectProposal(proposalId)
            _uiState.update {
                it.copy(
                    pendingMemoryProposal = null,
                    pendingMemoryLetterId = "",
                    noticeMessage = "已取消记忆候选",
                )
            }
        }
    }

    fun clearNoticeMessage() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun loadContext() {
        viewModelScope.launch {
            runCatching {
                val scenario = roleplayRepository.getScenario(initialScenarioId)
                    ?: error("当前聊天不存在或已被删除")
                val session = roleplayRepository.getSessionByScenario(initialScenarioId)
                    ?: roleplayRepository.startScenario(initialScenarioId).session
                val conversation = conversationRepository.getConversation(session.conversationId)
                    ?: error("当前会话不存在或已被删除")
                val settings = settingsRepository.settingsFlow.first()
                val assistant = resolveAssistant(settings, scenario, conversation)
                ContextResult(
                    scenario = scenario,
                    conversation = conversation,
                    assistant = assistant,
                    settings = settings,
                )
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        scenarioId = result.scenario.id,
                        conversationId = result.conversation.id,
                        assistantId = result.scenario.assistantId.ifBlank { result.conversation.assistantId },
                        characterName = resolveCharacterName(result.scenario, result.assistant),
                        userName = result.settings.resolvedUserDisplayName(),
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                observePendingProposal(result.conversation.id)
                observeRelatedContext(result.conversation.id)
                generateProactiveLetter(force = false)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "信箱加载失败",
                    )
                }
            }
        }
    }

    private fun observeMailboxSettings() {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            mailboxRepository.observeSettings(initialScenarioId).collect { settings ->
                _uiState.update { current ->
                    applySettingsToState(
                        state = current,
                        settings = settings.copy(scenarioId = settings.scenarioId.ifBlank { initialScenarioId }),
                        forceApplyComposerDefaults = current.activeMode != MailboxScreenMode.COMPOSE,
                    )
                }
                if (!_uiState.value.isLoading) {
                    generateProactiveLetter(force = false)
                }
            }
        }
    }

    private fun observeLetters() {
        viewModelScope.launch {
            selectedBoxFlow
                .flatMapLatest { box ->
                    mailboxRepository.observeLetters(initialScenarioId, box)
                }
                .collect { letters ->
                    _uiState.update { current ->
                        val selected = current.selectedLetter?.let { active ->
                            letters.firstOrNull { it.id == active.id } ?: active
                        }
                        val availableTags = deriveAvailableTags(letters)
                        val selectedTag = current.selectedTag.takeIf { it in availableTags }.orEmpty()
                        current.copy(
                            letters = letters,
                            visibleLetters = filterLetters(
                                current.copy(
                                    letters = letters,
                                    availableTags = availableTags,
                                    selectedTag = selectedTag,
                                ),
                            ),
                            availableTags = availableTags,
                            selectedTag = selectedTag,
                            selectedLetter = selected,
                        )
                    }
                }
        }
    }

    private fun observeUnreadCount() {
        viewModelScope.launch {
            mailboxRepository.observeUnreadCount(initialScenarioId).collect { count ->
                _uiState.update { it.copy(unreadCount = count) }
            }
        }
    }

    private fun observePendingProposal(conversationId: String) {
        pendingProposalJob?.cancel()
        pendingProposalJob = viewModelScope.launch {
            pendingMemoryProposalRepository.observeProposal(conversationId).collect { proposal ->
                _uiState.update { it.copy(pendingMemoryProposal = proposal) }
            }
        }
    }

    private fun observeRelatedContext(conversationId: String) {
        diaryContextJob?.cancel()
        phoneContextJob?.cancel()
        if (conversationId.isBlank()) {
            _uiState.update {
                it.copy(
                    diaryEntryCount = 0,
                    latestDiaryTitle = "",
                    phoneClueCount = 0,
                    phoneClueSummary = "",
                )
            }
            return
        }
        diaryContextJob = viewModelScope.launch {
            roleplayRepository.observeDiaryEntries(conversationId).collect { entries ->
                val latest = entries.latestDiaryEntry()
                _uiState.update {
                    it.copy(
                        diaryEntryCount = entries.size,
                        latestDiaryTitle = latest?.title.orEmpty(),
                    )
                }
            }
        }
        phoneContextJob = viewModelScope.launch {
            phoneSnapshotRepository
                .observeSnapshot(
                    conversationId = conversationId,
                    ownerType = PhoneSnapshotOwnerType.CHARACTER,
                )
                .collect { snapshot ->
                    val summary = summarizePhoneClues(snapshot)
                    _uiState.update {
                        it.copy(
                            phoneClueCount = summary.count,
                            phoneClueSummary = summary.text,
                        )
                    }
            }
        }
    }

    private fun updateMailboxSettings(transform: (MailboxSettings) -> MailboxSettings) {
        val state = _uiState.value
        val scenarioId = state.scenarioId.ifBlank { initialScenarioId }.trim()
        if (scenarioId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "当前聊天不存在，无法保存信箱设置") }
            return
        }
        val nextSettings = transform(state.settings.copy(scenarioId = scenarioId))
        _uiState.update {
            applySettingsToState(
                state = it,
                settings = nextSettings,
                forceApplyComposerDefaults = it.activeMode != MailboxScreenMode.COMPOSE,
            )
        }
        viewModelScope.launch {
            runCatching {
                mailboxRepository.updateSettings(nextSettings)
            }.onSuccess { saved ->
                _uiState.update {
                    applySettingsToState(
                        state = it,
                        settings = saved,
                        forceApplyComposerDefaults = it.activeMode != MailboxScreenMode.COMPOSE,
                    )
                }
                generateProactiveLetter(force = false)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.message ?: "信箱设置保存失败")
                }
            }
        }
    }

    private fun applySettingsToState(
        state: MailboxUiState,
        settings: MailboxSettings,
        forceApplyComposerDefaults: Boolean,
    ): MailboxUiState {
        return state.copy(
            settings = settings,
            generateReplyAfterSend = if (forceApplyComposerDefaults) {
                settings.autoReplyToUserLetters
            } else {
                state.generateReplyAfterSend
            },
            includeRecentChat = if (forceApplyComposerDefaults) {
                settings.includeRecentChatByDefault
            } else {
                state.includeRecentChat
            },
            includePhoneClues = if (forceApplyComposerDefaults) {
                settings.includePhoneCluesByDefault
            } else {
                state.includePhoneClues
            },
            allowMemory = if (forceApplyComposerDefaults) {
                settings.allowMemoryByDefault
            } else {
                state.allowMemory
            },
        )
    }

    private fun generateProactiveLetter(force: Boolean) {
        val state = _uiState.value
        if (state.isLoading || state.conversationId.isBlank() || state.scenarioId.isBlank()) {
            return
        }
        if (state.isGeneratingProactiveLetter || state.isGeneratingReply) {
            if (force) {
                _uiState.update { it.copy(errorMessage = "上一封信还在生成，稍等一下") }
            }
            return
        }
        val frequency = state.settings.proactiveFrequency
        if (!force && frequency == MailboxProactiveFrequency.OFF) {
            return
        }
        if (!force && state.settings.lastProactiveLetterAt > 0L) {
            val elapsed = System.currentTimeMillis() - state.settings.lastProactiveLetterAt
            if (elapsed < frequency.cooldownMillis) {
                return
            }
        }
        _uiState.update { it.copy(isGeneratingProactiveLetter = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val currentState = _uiState.value
                val settings = settingsRepository.settingsFlow.first()
                val scenario = roleplayRepository.getScenario(currentState.scenarioId)
                val conversation = conversationRepository.getConversation(currentState.conversationId)
                val assistant = conversation?.let { resolveAssistant(settings, scenario, it) }
                val contextText = buildContextText(
                    conversationId = currentState.conversationId,
                    includeRecentChat = currentState.settings.includeRecentChatByDefault,
                    includePhoneClues = currentState.settings.includePhoneCluesByDefault,
                )
                val request = MailboxProactiveRequest(
                    scenario = scenario,
                    assistant = assistant,
                    userName = settings.resolvedUserDisplayName(),
                    characterName = resolveCharacterName(scenario, assistant),
                    recentLetters = currentState.letters,
                    assembledContextText = contextText,
                )
                val provider = settings.resolveFunctionProvider(ProviderFunction.CHAT)
                val modelId = provider?.resolveFunctionModel(ProviderFunction.CHAT).orEmpty()
                val letterDraft = if (provider?.hasBaseCredentials() == true && modelId.isNotBlank()) {
                    mailboxPromptService.generateProactiveLetter(
                        request = request,
                        baseUrl = provider.baseUrl,
                        apiKey = provider.apiKey,
                        modelId = modelId,
                        apiProtocol = provider.resolvedApiProtocol(),
                        provider = provider,
                    )
                } else {
                    mailboxPromptService.buildLocalProactiveLetter(request)
                }
                mailboxRepository.insertIncomingLetter(
                    scenarioId = currentState.scenarioId,
                    conversationId = currentState.conversationId,
                    assistantId = currentState.assistantId,
                    subject = letterDraft.subject,
                    content = letterDraft.content,
                    tags = letterDraft.tags,
                    mood = letterDraft.mood,
                    allowMemory = currentState.settings.allowMemoryByDefault,
                    memoryCandidate = letterDraft.memoryCandidate,
                    source = MailboxSource.ROLEPLAY_EVENT,
                )
                mailboxRepository.markProactiveLetterCreated(currentState.scenarioId)
            }.onSuccess {
                selectedBoxFlow.value = MailboxBox.INBOX
                _uiState.update {
                    val next = it.copy(
                        selectedBox = MailboxBox.INBOX,
                        activeMode = MailboxScreenMode.LIST,
                        isGeneratingProactiveLetter = false,
                        showSettings = false,
                        searchQuery = "",
                        selectedTag = "",
                        unreadOnly = false,
                        noticeMessage = "主动来信已放入收件箱",
                    )
                    next.copy(visibleLetters = filterLetters(next))
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isGeneratingProactiveLetter = false,
                        errorMessage = throwable.message ?: "主动来信生成失败",
                    )
                }
            }
        }
    }

    private suspend fun generateReplyFor(letter: MailboxLetter) {
        val state = _uiState.value
        if (state.isGeneratingReply) {
            return
        }
        _uiState.update { it.copy(isGeneratingReply = true, errorMessage = null) }
        runCatching {
            val settings = settingsRepository.settingsFlow.first()
            val scenario = roleplayRepository.getScenario(letter.scenarioId)
            val conversation = conversationRepository.getConversation(letter.conversationId)
            val assistant = conversation?.let { resolveAssistant(settings, scenario, it) }
            val contextText = buildContextText(
                conversationId = letter.conversationId,
                includeRecentChat = state.includeRecentChat,
                includePhoneClues = state.includePhoneClues,
            )
            val request = MailboxReplyRequest(
                scenario = scenario,
                assistant = assistant,
                userName = settings.resolvedUserDisplayName(),
                characterName = resolveCharacterName(scenario, assistant),
                userLetter = letter,
                recentLetters = state.letters,
                assembledContextText = contextText,
            )
            val provider = settings.resolveFunctionProvider(ProviderFunction.CHAT)
            val modelId = provider?.resolveFunctionModel(ProviderFunction.CHAT).orEmpty()
            val replyDraft = if (provider?.hasBaseCredentials() == true && modelId.isNotBlank()) {
                mailboxPromptService.generateMailboxReply(
                    request = request,
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    modelId = modelId,
                    apiProtocol = provider.resolvedApiProtocol(),
                    provider = provider,
                )
            } else {
                mailboxPromptService.buildLocalReply(request)
            }
            mailboxRepository.insertIncomingLetter(
                scenarioId = letter.scenarioId,
                conversationId = letter.conversationId,
                assistantId = letter.assistantId,
                subject = replyDraft.subject,
                content = replyDraft.content,
                tags = replyDraft.tags,
                mood = replyDraft.mood,
                replyToLetterId = letter.id,
                allowMemory = letter.allowMemory,
                memoryCandidate = replyDraft.memoryCandidate,
            )
        }.onSuccess {
            selectedBoxFlow.value = MailboxBox.INBOX
            _uiState.update {
                val next = it.copy(
                    selectedBox = MailboxBox.INBOX,
                    activeMode = MailboxScreenMode.LIST,
                    isGeneratingReply = false,
                    searchQuery = "",
                    selectedTag = "",
                    unreadOnly = false,
                    noticeMessage = "回信已放入收件箱",
                )
                next.copy(visibleLetters = filterLetters(next))
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    isGeneratingReply = false,
                    errorMessage = throwable.message ?: "回信生成失败，已保留你的已寄信件",
                )
            }
        }
    }

    private suspend fun buildContextText(
        conversationId: String,
        includeRecentChat: Boolean,
        includePhoneClues: Boolean,
    ): String {
        return buildString {
            if (includeRecentChat) {
                val messages = conversationRepository.listMessages(conversationId).takeLast(12)
                val excerpt = formatRecentMessages(messages)
                if (excerpt.isNotBlank()) {
                    appendLine("【最近聊天】")
                    appendLine(excerpt)
                }
            }
            if (includePhoneClues) {
                val snapshot = phoneSnapshotRepository.getSnapshot(
                    conversationId = conversationId,
                    ownerType = PhoneSnapshotOwnerType.CHARACTER,
                )
                val clues = buildPhoneClues(snapshot)
                if (clues.isNotBlank()) {
                    if (isNotBlank()) appendLine()
                    appendLine("【手机线索】")
                    appendLine(clues)
                }
            }
        }.trim()
    }

    private fun formatRecentMessages(messages: List<ChatMessage>): String {
        return messages
            .filter { it.content.isNotBlank() }
            .joinToString("\n") { message ->
                val speaker = when (message.role) {
                    MessageRole.USER -> _uiState.value.userName
                    MessageRole.ASSISTANT -> _uiState.value.characterName
                }
                "$speaker：${message.content.take(160)}"
            }
    }

    private fun buildPhoneClues(snapshot: PhoneSnapshot?): String {
        if (snapshot == null || !snapshot.hasContent()) {
            return ""
        }
        val highlights = snapshot.relationshipHighlights.take(3).joinToString("\n") {
            "- ${it.name}：${it.relationLabel}，${it.note}"
        }
        val notes = snapshot.notes.take(2).joinToString("\n") {
            "- 备忘：${it.title}，${it.summary}"
        }
        val searches = snapshot.searchHistory.take(2).joinToString("\n") {
            "- 搜索：${it.query}"
        }
        return listOf(highlights, notes, searches)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private suspend fun resolveMemoryScope(assistantId: String): MemoryScopeType {
        val settings = settingsRepository.settingsFlow.first()
        val assistant = settings.resolvedAssistants().firstOrNull { it.id == assistantId }
        return if (assistant?.useGlobalMemory == true) {
            MemoryScopeType.GLOBAL
        } else {
            MemoryScopeType.ASSISTANT
        }
    }

    private fun resolveAssistant(
        settings: AppSettings,
        scenario: RoleplayScenario?,
        conversation: Conversation,
    ): Assistant? {
        val assistantId = scenario?.assistantId.orEmpty().ifBlank { conversation.assistantId }
        return settings.resolvedAssistants().firstOrNull { it.id == assistantId }
            ?: settings.activeAssistant()
    }

    private fun resolveCharacterName(
        scenario: RoleplayScenario?,
        assistant: Assistant?,
    ): String {
        return scenario?.characterDisplayNameOverride?.trim().orEmpty()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
    }

    private fun buildMemoryCandidate(
        letter: MailboxLetter,
        characterName: String,
    ): String {
        val subject = letter.subject.ifBlank { "一封信" }
        val excerpt = letter.excerpt.ifBlank {
            letter.content
                .replace("\n", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(54)
        }
        return if (excerpt.isBlank()) {
            "$characterName 和用户之间有一封关于“$subject”的重要书信往来。"
        } else {
            "$characterName 和用户通过“$subject”谈到了：$excerpt"
        }
    }

    private fun filterLetters(state: MailboxUiState): List<MailboxLetter> {
        val query = state.searchQuery.trim().lowercase()
        val selectedTag = state.selectedTag.trim()
        return state.letters.filter { letter ->
            val matchesQuery = query.isBlank() ||
                letter.subject.lowercase().contains(query) ||
                letter.content.lowercase().contains(query) ||
                letter.excerpt.lowercase().contains(query) ||
                letter.mood.lowercase().contains(query) ||
                letter.tags.any { it.lowercase().contains(query) }
            val matchesTag = selectedTag.isBlank() || selectedTag in letter.tags
            val matchesUnread = !state.unreadOnly || (letter.box == MailboxBox.INBOX && !letter.isRead)
            matchesQuery && matchesTag && matchesUnread
        }
    }

    private fun deriveAvailableTags(letters: List<MailboxLetter>): List<String> {
        return letters
            .flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
    }

    private fun List<RoleplayDiaryEntry>.latestDiaryEntry(): RoleplayDiaryEntry? {
        return maxByOrNull { entry ->
            entry.updatedAt.takeIf { it > 0 } ?: entry.createdAt
        }
    }

    private fun summarizePhoneClues(snapshot: PhoneSnapshot?): PhoneClueSummary {
        if (snapshot == null || !snapshot.hasContent()) {
            return PhoneClueSummary()
        }
        val clues = buildList {
            snapshot.relationshipHighlights.firstOrNull()?.let {
                add("${it.name}：${it.note}")
            }
            snapshot.messageThreads.firstOrNull()?.let {
                add("${it.contactName}：${it.preview}")
            }
            snapshot.notes.firstOrNull()?.let {
                add("${it.title}：${it.summary}")
            }
            snapshot.searchHistory.firstOrNull()?.let {
                add("搜索过「${it.query}」")
            }
        }
        val count = snapshot.relationshipHighlights.size +
            snapshot.messageThreads.size +
            snapshot.notes.size +
            snapshot.searchHistory.size +
            snapshot.gallery.size +
            snapshot.shoppingRecords.size +
            snapshot.socialPosts.size
        return PhoneClueSummary(
            count = count,
            text = clues.firstOrNull().orEmpty(),
        )
    }

    private data class ContextResult(
        val scenario: RoleplayScenario,
        val conversation: Conversation,
        val assistant: Assistant?,
        val settings: AppSettings,
    )

    private data class PhoneClueSummary(
        val count: Int = 0,
        val text: String = "",
    )

    companion object {
        fun factory(
            scenarioId: String,
            settingsRepository: AiSettingsRepository,
            conversationRepository: ConversationRepository,
            roleplayRepository: RoleplayRepository,
            phoneSnapshotRepository: PhoneSnapshotRepository,
            mailboxRepository: MailboxRepository,
            mailboxPromptService: MailboxPromptService,
            pendingMemoryProposalRepository: PendingMemoryProposalRepository,
            memoryWriteService: MemoryWriteService,
        ): ViewModelProvider.Factory = typedViewModelFactory {
            MailboxViewModel(
                initialScenarioId = scenarioId,
                settingsRepository = settingsRepository,
                conversationRepository = conversationRepository,
                roleplayRepository = roleplayRepository,
                phoneSnapshotRepository = phoneSnapshotRepository,
                mailboxRepository = mailboxRepository,
                mailboxPromptService = mailboxPromptService,
                pendingMemoryProposalRepository = pendingMemoryProposalRepository,
                memoryWriteService = memoryWriteService,
            )
        }
    }
}
