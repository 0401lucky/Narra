package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.conversation.PhoneContextBuilder
import com.example.myapplication.conversation.PhoneGenerationContext
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.phone.PhoneSnapshotRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.PhoneObservationState
import com.example.myapplication.model.PhoneSearchEntry
import com.example.myapplication.model.PhoneSnapshot
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.PhoneSnapshotSection
import com.example.myapplication.model.PhoneViewMode
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.textMessagePart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PhoneCheckUiState(
    val conversationId: String = "",
    val scenarioId: String = "",
    val ownerType: PhoneSnapshotOwnerType = PhoneSnapshotOwnerType.CHARACTER,
    val ownerName: String = "",
    val snapshot: PhoneSnapshot? = null,
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val generationRequestedSections: Set<PhoneSnapshotSection> = emptySet(),
    val generationCompletedSections: Set<PhoneSnapshotSection> = emptySet(),
    val generationStatusText: String = "",
    val loadingSearchEntryId: String = "",
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
)

class PhoneCheckViewModel(
    private val initialConversationId: String,
    private val initialScenarioId: String,
    private val initialOwnerType: PhoneSnapshotOwnerType,
    private val settingsRepository: AiSettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val roleplayRepository: RoleplayRepository,
    private val phoneSnapshotRepository: PhoneSnapshotRepository,
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val phoneContextBuilder: PhoneContextBuilder,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val _uiState = MutableStateFlow(
        PhoneCheckUiState(
            conversationId = initialConversationId,
            scenarioId = initialScenarioId,
            ownerType = initialOwnerType,
            isLoading = true,
        ),
    )
    val uiState: StateFlow<PhoneCheckUiState> = _uiState.asStateFlow()

    init {
        loadSnapshot()
    }

    fun loadSnapshot() {
        if (initialConversationId.isBlank()) {
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    errorMessage = "当前会话不存在，无法查看手机内容",
                )
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                val baseContext = resolveBaseContext() ?: error("当前会话不存在，无法查看手机内容")
                val snapshot = phoneSnapshotRepository.getSnapshot(
                    conversationId = initialConversationId,
                    ownerType = initialOwnerType,
                )
                LoadedSnapshotResult(
                    baseContext = baseContext,
                    resolution = resolveLoadedSnapshot(snapshot),
                )
            }.onSuccess { result ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        ownerName = result.baseContext.ownerName.ifBlank {
                            result.resolution.snapshot?.ownerName.orEmpty()
                        },
                        snapshot = result.resolution.snapshot,
                        noticeMessage = result.resolution.noticeMessage,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "读取手机快照失败",
                    )
                }
            }
        }
    }

    fun generateSnapshot() {
        refreshSections(PhoneSnapshotSection.entries.toSet())
    }

    fun refreshSections(
        sections: Set<PhoneSnapshotSection>,
    ) {
        if (initialConversationId.isBlank()) {
            _uiState.update { current ->
                current.copy(errorMessage = "当前会话不存在，无法生成手机内容")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isGenerating = true,
                    generationRequestedSections = sections.ifEmpty { PhoneSnapshotSection.entries.toSet() },
                    generationCompletedSections = emptySet(),
                    generationStatusText = "",
                    errorMessage = null,
                    noticeMessage = null,
                )
            }
            val existingSnapshot = _uiState.value.snapshot
            runCatching {
                val runtime = resolveGenerationRuntimeContext() ?: error("当前会话不存在，无法生成手机内容")
                refreshSnapshotSectionsInStages(
                    runtime = runtime,
                    requestedSections = sections,
                    existingSnapshot = existingSnapshot,
                )
            }.onSuccess { snapshot ->
                _uiState.update { current ->
                    current.copy(
                        snapshot = snapshot,
                        ownerName = snapshot.ownerName,
                        isGenerating = false,
                        generationRequestedSections = emptySet(),
                        generationCompletedSections = emptySet(),
                        generationStatusText = "",
                        noticeMessage = if (existingSnapshot == null) "手机内容已生成" else "已刷新所选内容",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        isGenerating = false,
                        generationRequestedSections = emptySet(),
                        generationCompletedSections = emptySet(),
                        generationStatusText = "",
                        errorMessage = throwable.message ?: "手机内容生成失败",
                    )
                }
            }
        }
    }

    private suspend fun refreshSnapshotSectionsInStages(
        runtime: RuntimeContext,
        requestedSections: Set<PhoneSnapshotSection>,
        existingSnapshot: PhoneSnapshot?,
    ): PhoneSnapshot {
        val normalizedSections = requestedSections.ifEmpty { PhoneSnapshotSection.entries.toSet() }
        val batches = buildGenerationBatches(normalizedSections)
        var workingSnapshot = existingSnapshot ?: PhoneSnapshot(
            conversationId = initialConversationId,
            ownerType = initialOwnerType,
            contentSemanticsVersion = PhoneSnapshot.currentContentSemanticsVersion(initialOwnerType),
        )
        val completedSections = linkedSetOf<PhoneSnapshotSection>()
        val failedSections = linkedSetOf<Set<PhoneSnapshotSection>>()
        var lastErrorMessage = ""

        val messageBatch = batches.firstOrNull { PhoneSnapshotSection.MESSAGES in it }
        val parallelBatches = batches.filterNot { it == messageBatch }

        updateGenerationProgress(
            requestedSections = normalizedSections,
            completedSections = completedSections,
            pendingBatches = if (messageBatch != null) listOf(messageBatch) else parallelBatches,
        )

        messageBatch?.let { batch ->
            runCatching {
                generateSectionBatch(
                    context = runtime.context,
                    requestedSections = batch,
                    existingSnapshot = workingSnapshot,
                    baseUrl = runtime.provider.baseUrl,
                    apiKey = runtime.provider.apiKey,
                    modelId = runtime.modelId,
                    apiProtocol = runtime.provider.resolvedApiProtocol(),
                    provider = runtime.provider,
                )
            }.onSuccess { generatedSections ->
                if (!generatedSections.hasContentFor(batch)) {
                    error("模型返回了空白的${batch.describeSections()}内容")
                }
                workingSnapshot = mergeBatchIntoSnapshot(
                    currentSnapshot = workingSnapshot,
                    sections = generatedSections,
                    requestedSections = batch,
                    runtime = runtime,
                )
                completedSections += batch
                phoneSnapshotRepository.upsertSnapshot(workingSnapshot)
                _uiState.update { current ->
                    current.copy(
                        snapshot = workingSnapshot,
                        ownerName = workingSnapshot.ownerName,
                    )
                }
            }.onFailure { throwable ->
                failedSections += batch
                lastErrorMessage = throwable.message.orEmpty()
            }

            updateGenerationProgress(
                requestedSections = normalizedSections,
                completedSections = completedSections,
                pendingBatches = parallelBatches,
            )
        }

        if (parallelBatches.isNotEmpty()) {
            val settledBatches = mutableSetOf<Set<PhoneSnapshotSection>>()
            val progressMutex = Mutex()
            val snapshotSeed = workingSnapshot

            coroutineScope {
                parallelBatches.map { batch ->
                    async {
                        val result = runCatching {
                            generateSectionBatch(
                                context = runtime.context,
                                requestedSections = batch,
                                existingSnapshot = snapshotSeed,
                                baseUrl = runtime.provider.baseUrl,
                                apiKey = runtime.provider.apiKey,
                                modelId = runtime.modelId,
                                apiProtocol = runtime.provider.resolvedApiProtocol(),
                                provider = runtime.provider,
                            )
                        }

                        progressMutex.withLock {
                            result.onSuccess { generatedSections ->
                                if (!generatedSections.hasContentFor(batch)) {
                                    error("模型返回了空白的${batch.describeSections()}内容")
                                }
                                workingSnapshot = mergeBatchIntoSnapshot(
                                    currentSnapshot = workingSnapshot,
                                    sections = generatedSections,
                                    requestedSections = batch,
                                    runtime = runtime,
                                )
                                completedSections += batch
                                phoneSnapshotRepository.upsertSnapshot(workingSnapshot)
                                _uiState.update { current ->
                                    current.copy(
                                        snapshot = workingSnapshot,
                                        ownerName = workingSnapshot.ownerName,
                                    )
                                }
                            }.onFailure { throwable ->
                                failedSections += batch
                                lastErrorMessage = throwable.message.orEmpty()
                            }

                            settledBatches += batch
                            updateGenerationProgress(
                                requestedSections = normalizedSections,
                                completedSections = completedSections,
                                pendingBatches = parallelBatches.filterNot { it in settledBatches },
                            )
                        }
                    }
                }.awaitAll()
            }
        }

        if (initialOwnerType == PhoneSnapshotOwnerType.USER && runtime.scenario != null && workingSnapshot.hasContent()) {
            val observation = buildPhoneObservationState(
                runtime = runtime,
                snapshot = workingSnapshot,
            )
            phoneSnapshotRepository.upsertObservation(observation)
            appendPhoneObservationEvent(
                runtime = runtime,
                observation = observation,
            )
        }

        if (failedSections.isEmpty()) {
            return workingSnapshot
        }

        if (failedSections.flatMap { it }.toSet().size == normalizedSections.size) {
            val detail = lastErrorMessage.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
            error("手机内容生成失败（${failedSections.flattenDisplayName()}）$detail")
        }

        _uiState.update { current ->
            current.copy(
                errorMessage = "部分内容刷新失败：${failedSections.flattenDisplayName()}，其余内容已保留",
            )
        }
        return workingSnapshot
    }

    fun loadSearchDetail(entryId: String) {
        val snapshot = _uiState.value.snapshot ?: return
        val targetEntry = snapshot.searchHistory.firstOrNull { it.id == entryId } ?: return
        if (targetEntry.detail != null) {
            return
        }
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    loadingSearchEntryId = entryId,
                    errorMessage = null,
                )
            }
            runCatching {
                val runtime = resolveGenerationRuntimeContext() ?: error("当前会话不存在，无法生成搜索详情")
                val detail = aiPromptExtrasService.generatePhoneSearchDetail(
                    context = runtime.context,
                    query = targetEntry.query,
                    relatedContext = phoneContextBuilder.buildSearchDetailContext(
                        snapshot = snapshot,
                        query = targetEntry.query,
                    ),
                    baseUrl = runtime.provider.baseUrl,
                    apiKey = runtime.provider.apiKey,
                    modelId = runtime.modelId,
                    apiProtocol = runtime.provider.resolvedApiProtocol(),
                    provider = runtime.provider,
                )
                val updatedSnapshot = snapshot.withSearchDetail(
                    entryId = entryId,
                    detail = detail,
                    updatedAt = nowProvider(),
                )
                phoneSnapshotRepository.upsertSnapshot(updatedSnapshot)
                updatedSnapshot
            }.onSuccess { updatedSnapshot ->
                _uiState.update { current ->
                    current.copy(
                        snapshot = updatedSnapshot,
                        loadingSearchEntryId = "",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        loadingSearchEntryId = "",
                        errorMessage = throwable.message ?: "搜索详情生成失败",
                    )
                }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { current -> current.copy(errorMessage = null) }
    }

    fun clearNoticeMessage() {
        _uiState.update { current -> current.copy(noticeMessage = null) }
    }

    private suspend fun resolveLoadedSnapshot(
        snapshot: PhoneSnapshot?,
    ): LoadedSnapshotResolution {
        if (snapshot == null) {
            return LoadedSnapshotResolution()
        }
        if (initialOwnerType != PhoneSnapshotOwnerType.USER || snapshot.isCompatibleWith(initialOwnerType)) {
            return LoadedSnapshotResolution(snapshot = snapshot)
        }
        phoneSnapshotRepository.deleteSnapshot(
            conversationId = initialConversationId,
            ownerType = initialOwnerType,
        )
        phoneSnapshotRepository.deleteObservation(initialConversationId)
        return LoadedSnapshotResolution(
            noticeMessage = "“我的手机”规则已更新，请重新生成手机内容",
        )
    }

    private suspend fun resolveBaseContext(): BaseContext? {
        val currentSettings = settingsRepository.settingsFlow.first()
        val conversation = conversationRepository.getConversation(initialConversationId)
            ?: return null
        val assistant = resolveAssistant(
            settings = currentSettings,
            conversation = conversation,
            scenarioId = initialScenarioId,
        )
        val scenario = initialScenarioId.takeIf { it.isNotBlank() }
            ?.let { scenarioId -> roleplayRepository.getScenario(scenarioId) }
        val ownerName = scenario?.characterDisplayNameOverride
            ?.trim()
            .orEmpty()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
        val viewerName = currentSettings.resolvedUserDisplayName()
        val resolvedOwnerName = when (initialOwnerType) {
            PhoneSnapshotOwnerType.CHARACTER -> ownerName
            PhoneSnapshotOwnerType.USER -> viewerName
        }
        return BaseContext(
            conversation = conversation,
            assistant = assistant,
            scenario = scenario,
            ownerName = resolvedOwnerName,
            viewerName = if (initialOwnerType == PhoneSnapshotOwnerType.USER) ownerName else viewerName,
        )
    }

    private suspend fun resolveGenerationRuntimeContext(): RuntimeContext? {
        val baseContext = resolveBaseContext() ?: return null
        val currentSettings = settingsRepository.settingsFlow.first()
        val messages = conversationRepository.listMessages(initialConversationId)
        val activeProvider = currentSettings.activeProvider()
            ?: error("当前未配置可用提供商")
        val modelId = activeProvider.resolveFunctionModel(ProviderFunction.PHONE_SNAPSHOT)
            .ifBlank { activeProvider.selectedModel.trim() }
        if (modelId.isBlank()) {
            error("当前未配置可用模型")
        }
        val context = phoneContextBuilder.build(
            settings = currentSettings,
            assistant = baseContext.assistant,
            conversation = baseContext.conversation,
            recentMessages = messages,
            scenario = baseContext.scenario,
            ownerType = initialOwnerType,
            nowProvider = nowProvider,
        )
        return RuntimeContext(
            conversation = baseContext.conversation,
            assistant = baseContext.assistant,
            scenario = baseContext.scenario,
            provider = activeProvider,
            modelId = modelId,
            ownerName = context.ownerName,
            context = context,
        )
    }

    private suspend fun appendPhoneObservationEvent(
        runtime: RuntimeContext,
        observation: PhoneObservationState,
    ) {
        val scenario = runtime.scenario ?: return
        val eventMessage = ChatMessage(
            id = "phone-observation-${runtime.conversation.id}-${observation.updatedAt}",
            conversationId = runtime.conversation.id,
            role = MessageRole.ASSISTANT,
            content = "<narration>${escapeXml(observation.eventText)}</narration>",
            createdAt = observation.updatedAt,
            parts = listOf(textMessagePart("<narration>${escapeXml(observation.eventText)}</narration>")),
            roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
        )
        val existingMessages = conversationRepository.listMessages(runtime.conversation.id)
        if (existingMessages.lastOrNull()?.content == eventMessage.content) {
            return
        }
        conversationRepository.appendMessages(
            conversationId = runtime.conversation.id,
            messages = listOf(eventMessage),
            selectedModel = runtime.provider.selectedModel.trim(),
        )
    }

    private fun buildPhoneObservationState(
        runtime: RuntimeContext,
        snapshot: PhoneSnapshot,
    ): PhoneObservationState {
        val findings = buildKeyFindings(snapshot)
        val viewerName = runtime.context.viewerName.ifBlank { "他" }
        return PhoneObservationState(
            conversationId = runtime.conversation.id,
            scenarioId = initialScenarioId,
            ownerType = initialOwnerType,
            viewMode = PhoneViewMode.CHARACTER_LOOKS_USER_PHONE,
            ownerName = snapshot.ownerName,
            viewerName = viewerName,
            eventText = buildString {
                append(viewerName)
                append("翻看了你的手机")
                findings.firstOrNull()?.takeIf { it.isNotBlank() }?.let { finding ->
                    append("，似乎记住了：")
                    append(finding)
                }
            },
            keyFindings = findings,
            observedAt = nowProvider(),
            updatedAt = nowProvider(),
        )
    }

    private fun buildKeyFindings(
        snapshot: PhoneSnapshot,
    ): List<String> {
        return buildList {
            snapshot.searchHistory.firstOrNull()?.query?.takeIf { it.isNotBlank() }?.let { add("搜索：$it") }
            snapshot.notes.firstOrNull()?.title?.takeIf { it.isNotBlank() }?.let { add("备忘录：$it") }
            snapshot.gallery.firstOrNull()?.title?.takeIf { it.isNotBlank() }?.let { add("相册：$it") }
            snapshot.shoppingRecords.firstOrNull()?.title?.takeIf { it.isNotBlank() }?.let { add("购物：$it") }
            snapshot.messageThreads.firstOrNull()?.preview?.takeIf { it.isNotBlank() }?.let { add("消息：$it") }
        }.distinct().take(3)
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private suspend fun resolveAssistant(
        settings: AppSettings,
        conversation: Conversation,
        scenarioId: String,
    ): Assistant? {
        val scenarioAssistantId = scenarioId.takeIf { it.isNotBlank() }
            ?.let { targetScenarioId -> roleplayRepository.getScenario(targetScenarioId) }
            ?.assistantId
            .orEmpty()
        val assistantId = scenarioAssistantId.ifBlank { conversation.assistantId }
        return settings.resolvedAssistants().firstOrNull { assistant ->
            assistant.id == assistantId
        } ?: settings.activeAssistant()
    }

    private data class RuntimeContext(
        val conversation: Conversation,
        val assistant: Assistant?,
        val scenario: RoleplayScenario?,
        val provider: com.example.myapplication.model.ProviderSettings,
        val modelId: String,
        val ownerName: String,
        val context: PhoneGenerationContext,
    )

    private data class BaseContext(
        val conversation: Conversation,
        val assistant: Assistant?,
        val scenario: RoleplayScenario?,
        val ownerName: String,
        val viewerName: String,
    )

    private data class LoadedSnapshotResolution(
        val snapshot: PhoneSnapshot? = null,
        val noticeMessage: String? = null,
    )

    private data class LoadedSnapshotResult(
        val baseContext: BaseContext,
        val resolution: LoadedSnapshotResolution,
    )

    private fun buildGenerationBatches(
        requestedSections: Set<PhoneSnapshotSection>,
    ): List<Set<PhoneSnapshotSection>> {
        val normalizedSections = requestedSections.ifEmpty { PhoneSnapshotSection.entries.toSet() }
        return buildList {
            if (PhoneSnapshotSection.MESSAGES in normalizedSections) {
                add(setOf(PhoneSnapshotSection.MESSAGES))
            }
            setOf(PhoneSnapshotSection.NOTES, PhoneSnapshotSection.SHOPPING)
                .intersect(normalizedSections)
                .takeIf { it.isNotEmpty() }
                ?.let(::add)
            setOf(PhoneSnapshotSection.GALLERY, PhoneSnapshotSection.SEARCH)
                .intersect(normalizedSections)
                .takeIf { it.isNotEmpty() }
                ?.let(::add)
        }
    }

    private suspend fun generateSectionBatch(
        context: PhoneGenerationContext,
        requestedSections: Set<PhoneSnapshotSection>,
        existingSnapshot: PhoneSnapshot,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
        provider: com.example.myapplication.model.ProviderSettings,
    ) = aiPromptExtrasService.generatePhoneSnapshotSections(
        context = context,
        requestedSections = requestedSections,
        existingSnapshot = existingSnapshot,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelId = modelId,
        apiProtocol = apiProtocol,
        provider = provider,
    )

    private fun mergeBatchIntoSnapshot(
        currentSnapshot: PhoneSnapshot,
        sections: com.example.myapplication.model.PhoneSnapshotSections,
        requestedSections: Set<PhoneSnapshotSection>,
        runtime: RuntimeContext,
    ): PhoneSnapshot {
        return currentSnapshot.mergeSections(
            sections = sections,
            requestedSections = requestedSections,
            updatedAt = nowProvider(),
            ownerType = initialOwnerType,
            scenarioId = initialScenarioId,
            assistantId = runtime.assistant?.id.orEmpty(),
            contentSemanticsVersion = PhoneSnapshot.currentContentSemanticsVersion(initialOwnerType),
            ownerName = runtime.ownerName,
        )
    }

    private fun updateGenerationProgress(
        requestedSections: Set<PhoneSnapshotSection>,
        completedSections: Set<PhoneSnapshotSection>,
        pendingBatches: List<Set<PhoneSnapshotSection>>,
    ) {
        val statusText = if (pendingBatches.isEmpty()) {
            ""
        } else {
            buildGenerationStatusText(
                requestedSections = requestedSections,
                completedSections = completedSections,
                pendingBatches = pendingBatches,
            )
        }
        _uiState.update { current ->
            current.copy(
                generationRequestedSections = requestedSections,
                generationCompletedSections = completedSections.toSet(),
                generationStatusText = statusText,
            )
        }
    }

    private fun buildGenerationStatusText(
        requestedSections: Set<PhoneSnapshotSection>,
        completedSections: Set<PhoneSnapshotSection>,
        pendingBatches: List<Set<PhoneSnapshotSection>>,
    ): String {
        val completedCount = completedSections.size
        val totalCount = requestedSections.size
        val batchLabel = pendingBatches.joinToString(" / ") { batch ->
            batch.sortedBy(PhoneSnapshotSection::ordinal).joinToString("、") { it.displayName }
        }
        return if (pendingBatches.size > 1) {
            "已完成 $completedCount/$totalCount，正在并行生成：$batchLabel"
        } else {
            "已完成 $completedCount/$totalCount，正在生成：$batchLabel"
        }
    }

    private fun Collection<Set<PhoneSnapshotSection>>.flattenDisplayName(): String {
        return flatMap { it }
            .distinct()
            .sortedBy(PhoneSnapshotSection::ordinal)
            .joinToString("、") { it.displayName }
    }

    private fun Set<PhoneSnapshotSection>.describeSections(): String {
        return sortedBy(PhoneSnapshotSection::ordinal).joinToString("、") { it.displayName }
    }

    private fun com.example.myapplication.model.PhoneSnapshotSections.hasContentFor(
        requestedSections: Set<PhoneSnapshotSection>,
    ): Boolean {
        return requestedSections.any { section ->
            when (section) {
                PhoneSnapshotSection.MESSAGES -> {
                    !relationshipHighlights.isNullOrEmpty() || !messageThreads.isNullOrEmpty()
                }
                PhoneSnapshotSection.NOTES -> !notes.isNullOrEmpty()
                PhoneSnapshotSection.GALLERY -> !gallery.isNullOrEmpty()
                PhoneSnapshotSection.SHOPPING -> !shoppingRecords.isNullOrEmpty()
                PhoneSnapshotSection.SEARCH -> !searchHistory.isNullOrEmpty()
            }
        }
    }

    companion object {
        fun factory(
            conversationId: String,
            scenarioId: String,
            ownerType: PhoneSnapshotOwnerType,
            settingsRepository: AiSettingsRepository,
            conversationRepository: ConversationRepository,
            roleplayRepository: RoleplayRepository,
            phoneSnapshotRepository: PhoneSnapshotRepository,
            aiPromptExtrasService: AiPromptExtrasService,
            phoneContextBuilder: PhoneContextBuilder,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PhoneCheckViewModel(
                        initialConversationId = conversationId,
                        initialScenarioId = scenarioId,
                        initialOwnerType = ownerType,
                        settingsRepository = settingsRepository,
                        conversationRepository = conversationRepository,
                        roleplayRepository = roleplayRepository,
                        phoneSnapshotRepository = phoneSnapshotRepository,
                        aiPromptExtrasService = aiPromptExtrasService,
                        phoneContextBuilder = phoneContextBuilder,
                    ) as T
                }
            }
        }
    }
}
