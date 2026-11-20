package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.data.repository.context.ContextTransferCodec
import com.example.myapplication.data.repository.context.ContextTransferRecoveryRepository
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.EmptyPresetRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.MemoryArchiveTransferRepository
import com.example.myapplication.data.repository.context.MemoryTransferLegacyConversion
import com.example.myapplication.data.repository.context.PresetRepository
import com.example.myapplication.data.repository.context.TavernCharacterAdapter
import com.example.myapplication.data.repository.context.TavernCharacterImageAdapter
import com.example.myapplication.data.repository.context.TavernPresetAdapter
import com.example.myapplication.data.repository.context.TavernWorldBookAdapter
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.BUILTIN_ASSISTANTS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANTS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES
import com.example.myapplication.model.ContextDataBundle
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ContextTransferOperationSummary
import com.example.myapplication.model.ContextTransferOperationKind
import com.example.myapplication.model.ContextTransferStage
import com.example.myapplication.model.ContextTransferAvatarInput
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.HIDDEN_BUILTIN_ASSISTANT_IDS
import com.example.myapplication.model.Preset
import com.example.myapplication.model.MemoryArchiveImportResult
import com.example.myapplication.model.MemoryImportPlan
import com.example.myapplication.model.MemoryImportSourceGroup
import com.example.myapplication.model.MemoryImportStateConflict
import com.example.myapplication.model.MemoryImportTarget
import com.example.myapplication.model.MemoryImportTargetOption
import com.example.myapplication.model.normalizedForContextImport
import com.example.myapplication.system.security.SensitiveTextRedactor
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ContextTransferSection(
    val label: String,
    val exportFileName: String,
) {
    ALL("资料库备份", "narra-library-export.json"),
    ASSISTANTS("角色包", "narra-character-pack.json"),
    WORLD_BOOK("世界书包", "narra-worldbook-pack.json"),
    MEMORY("记忆档案包", "narra-memory-pack.json"),
    PRESETS("预设包", "narra-preset-pack.json"),
}

data class ContextImportConflict(
    val typeLabel: String,
    val title: String,
    val id: String,
)

data class ContextImportPreview(
    val sourceLabel: String,
    val section: ContextTransferSection,
    val assistantCount: Int,
    val worldBookCount: Int,
    val memoryCount: Int,
    val summaryCount: Int,
    val presetCount: Int,
    val conflicts: List<ContextImportConflict>,
    val noticeMessages: List<String> = emptyList(),
    val memoryGroups: List<MemoryImportSourceGroup> = emptyList(),
    val memoryTargetOptions: List<MemoryImportTargetOption> = emptyList(),
    val memoryTargets: Map<String, MemoryImportTarget> = emptyMap(),
    val memoryStateConflicts: List<MemoryImportStateConflict> = emptyList(),
    val replaceExistingStates: Boolean = false,
    val canConfirmMemory: Boolean = true,
    val memorySourceCount: Int = 0,
    val companionCount: Int = 0,
)

data class ContextImportPayload(
    val fileName: String = "",
    val mimeType: String = "",
    val textContent: String? = null,
    val binaryContent: ByteArray? = null,
)

data class AssistantAvatarImport(
    val assistantId: String,
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class ContextTransferUiState(
    val customAssistantCount: Int = 0,
    val worldBookCount: Int = 0,
    val memoryCount: Int = 0,
    val summaryCount: Int = 0,
    val presetCount: Int = 0,
    val isBusy: Boolean = false,
    val importPreview: ContextImportPreview? = null,
    val message: String? = null,
    val companionCount: Int = 0,
    val pendingOperations: List<ContextTransferOperationSummary> = emptyList(),
)

class ContextTransferViewModel(
    private val settingsRepository: AiSettingsRepository,
    private val settingsEditor: AiSettingsEditor,
    private val worldBookRepository: WorldBookRepository,
    private val memoryRepository: MemoryRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val presetRepository: PresetRepository = EmptyPresetRepository,
    private val codec: ContextTransferCodec = ContextTransferCodec(),
    private val tavernCharacterAdapter: TavernCharacterAdapter = TavernCharacterAdapter(),
    private val tavernCharacterImageAdapter: TavernCharacterImageAdapter = TavernCharacterImageAdapter(),
    private val tavernWorldBookAdapter: TavernWorldBookAdapter = TavernWorldBookAdapter(),
    private val tavernPresetAdapter: TavernPresetAdapter = TavernPresetAdapter(),
    private val importedAssistantAvatarSaver: suspend (AssistantAvatarImport) -> String? = { null },
    private val dataImportTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() },
    private val existingConversationIdsProvider: suspend () -> Set<String>? = { null },
    private val memoryTransferRepository: MemoryArchiveTransferRepository? = null,
    private val contextTransferRecoveryRepository: ContextTransferRecoveryRepository? = null,
) : ViewModel() {
    val settings = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = com.example.myapplication.model.AppSettings(),
    )

    private val _uiState = MutableStateFlow(ContextTransferUiState())
    val uiState: StateFlow<ContextTransferUiState> = _uiState.asStateFlow()
    private var pendingImportBundle: ContextDataBundle? = null
    private var pendingAssistantAvatarImport: AssistantAvatarImport? = null
    private var pendingMemoryPlan: MemoryImportPlan? = null
    private var pendingImportSettings: AppSettings? = null
    private var pendingRecoveryOperationId: String? = null
    private var lastVectorsNeedingRebuild: Int = 0

    init {
        viewModelScope.launch {
            refreshCounts()
        }
    }

    fun exportBundleJson(
        section: ContextTransferSection,
        onSuccess: (String, String) -> Unit,
    ) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            try {
                val bundle = filterBundleBySection(
                    section = section,
                    bundle = buildCurrentBundle(),
                )
                onSuccess(codec.encode(bundle), section.exportFileName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update { it.copy(message = failure.toContextTransferUiError("导出失败")) }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun previewImportJson(
        rawJson: String,
        section: ContextTransferSection,
    ) {
        previewImportPayload(
            payload = ContextImportPayload(
                fileName = "import.json",
                mimeType = "application/json",
                textContent = rawJson,
            ),
            section = section,
        )
    }

    fun previewImportPayload(
        payload: ContextImportPayload,
        section: ContextTransferSection,
    ) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            runCatching {
                val expectedSettings = settings.value
                lastVectorsNeedingRebuild = 0
                val decodedImport = decodeImportBundle(payload, section)
                val filteredBundle = filterBundleBySection(
                    section = section,
                    bundle = decodedImport.bundle,
                    sourceType = decodedImport.sourceType,
                ).normalizedForContextImport()
                if (isBundleEmpty(filteredBundle)) {
                    error("导入文件中没有可用于 ${section.label} 的内容")
                }
                val preview = buildImportPreview(
                    sourceLabel = decodedImport.sourceType.sourceLabel,
                    section = section,
                    bundle = filteredBundle,
                )
                Triple(
                    filteredBundle,
                    preview,
                    resolveAssistantAvatarImport(
                        payload = payload,
                        bundle = filteredBundle,
                    ),
                )
                    .also { pendingImportSettings = expectedSettings }
            }.onSuccess { (bundle, preview, assistantAvatarImport) ->
                pendingImportBundle = bundle
                pendingAssistantAvatarImport = assistantAvatarImport
                pendingMemoryPlan = null
                pendingRecoveryOperationId = null
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        importPreview = preview,
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    _uiState.update { it.copy(isBusy = false) }
                    throw throwable
                }
                pendingImportBundle = null
                pendingAssistantAvatarImport = null
                pendingMemoryPlan = null
                pendingImportSettings = null
                pendingRecoveryOperationId = null
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        importPreview = null,
                        message = throwable.toContextTransferUiError("导入失败"),
                    )
                }
            }
        }
    }

    fun confirmImport() {
        val bundle = pendingImportBundle ?: return
        if (_uiState.value.isBusy || _uiState.value.importPreview?.canConfirmMemory == false) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            runCatching {
                val preparedBundle = if (contextTransferRecoveryRepository == null) applyImportedAssistantAvatar(bundle) else bundle
                mergeImportedData(preparedBundle).also { refreshCounts() }
            }.onSuccess { memoryResult ->
                pendingImportBundle = null
                pendingAssistantAvatarImport = null
                pendingMemoryPlan = null
                pendingImportSettings = null
                pendingRecoveryOperationId = null
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        importPreview = null,
                        message = if (maxOf(memoryResult?.vectorsNeedingRebuild ?: 0, lastVectorsNeedingRebuild) > 0)
                            "上下文数据已合并导入；${maxOf(memoryResult?.vectorsNeedingRebuild ?: 0, lastVectorsNeedingRebuild)} 组向量暂不可用，需手动重建" else "上下文数据已合并导入",
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    _uiState.update { it.copy(isBusy = false) }
                    throw throwable
                }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = throwable.toContextTransferUiError("导入失败"),
                    )
                }
                refreshPendingOperationsSafely()
            }
        }
    }

    fun dismissImportPreview() {
        if (_uiState.value.isBusy) return
        pendingImportBundle = null
        pendingAssistantAvatarImport = null
        pendingMemoryPlan = null
        pendingImportSettings = null
        pendingRecoveryOperationId = null
        _uiState.update { it.copy(importPreview = null) }
    }

    fun selectMemoryImportTarget(sourceKey: String, target: MemoryImportTarget?) {
        if (_uiState.value.isBusy) return
        if (pendingRecoveryOperationId != null) { _uiState.update { it.copy(message = "导入计划已冻结，请继续同一计划或重新预览") }; return }
        val preview = _uiState.value.importPreview ?: return
        val bundle = pendingImportBundle ?: return
        if (preview.memoryGroups.none { it.key == sourceKey } || target != null && preview.memoryTargetOptions.none { it.target == target }) return
        val targets = preview.memoryTargets.toMutableMap().apply {
            if (target == null) remove(sourceKey) else put(sourceKey, target)
        }.toMap()
        pendingMemoryPlan = null
        _uiState.update {
            it.copy(
                isBusy = true, message = null, importPreview = preview.copy(
                    memoryTargets = targets,
                    memoryStateConflicts = emptyList(),
                    replaceExistingStates = false,
                    canConfirmMemory = false
                )
            )
        }
        viewModelScope.launch {
            try {
                if (targets.size == preview.memoryGroups.size) {
                    val repository =
                        requireNotNull(memoryTransferRepository) { "记忆恢复服务不可用" }
                    val plan = repository.prepareImport(bundle, targets)
                    pendingMemoryPlan = plan
                    _uiState.update { state ->
                        state.copy(
                            importPreview = state.importPreview?.copy(
                                memoryStateConflicts = plan.stateConflicts,
                                canConfirmMemory = plan.stateConflicts.isEmpty()
                            )
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update { it.copy(message = failure.toContextTransferUiError("目标已改变，请重新预览")) }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun setReplaceMemoryStates(replace: Boolean) {
        if (_uiState.value.isBusy) return
        if (pendingRecoveryOperationId != null) return
        val plan = pendingMemoryPlan ?: return
        if (plan.stateConflicts.any { it.multipleImportedHeads }) return
        pendingMemoryPlan = plan.copy(replaceExistingStates = replace)
        _uiState.update { state ->
            state.copy(
                importPreview = state.importPreview?.copy(
                    replaceExistingStates = replace,
                    canConfirmMemory = plan.stateConflicts.isEmpty() || replace
                )
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun resumePendingImport(operationId: String) {
        val recovery = contextTransferRecoveryRepository ?: return
        if (_uiState.value.isBusy || _uiState.value.pendingOperations.none { it.operationId == operationId && it.kind == ContextTransferOperationKind.PRIVATE_LIBRARY }) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            try {
                val result = recovery.resumeOperation(operationId)
                check(result.stage == ContextTransferStage.COMPLETE) { "资料导入尚未完成" }
                if (pendingRecoveryOperationId == operationId) {
                    pendingImportBundle = null
                    pendingAssistantAvatarImport = null
                    pendingMemoryPlan = null
                    pendingImportSettings = null
                    pendingRecoveryOperationId = null
                    _uiState.update { it.copy(importPreview = null) }
                }
                refreshCounts()
                _uiState.update { it.copy(message = if (result.vectorsNeedingRebuild > 0) "已继续完成资料导入；${result.vectorsNeedingRebuild} 组向量需手动重建" else "已继续完成资料导入") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                _uiState.update { it.copy(message = failure.toContextTransferUiError("资料导入未完成，已完成阶段保留")) }
                refreshPendingOperationsSafely()
            } finally { _uiState.update { it.copy(isBusy = false) } }
        }
    }

    private suspend fun refreshPendingOperations() {
        val operations = contextTransferRecoveryRepository?.listPendingOperations(ContextTransferOperationKind.PRIVATE_LIBRARY).orEmpty()
        _uiState.update { it.copy(pendingOperations = operations) }
    }

    private suspend fun refreshPendingOperationsSafely() {
        try { refreshPendingOperations() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* 原失败仍在页面；无法读取回执时不伪造成功或清空待恢复状态。 */ }
    }

    private suspend fun buildCurrentBundle(): ContextDataBundle {
        val currentSettings = settings.value
        val archive = memoryTransferRepository?.exportArchive()
        val archiveIds = archive?.records?.map { it.id }?.toSet().orEmpty()
        return ContextDataBundle(
            version = if (contextTransferRecoveryRepository != null) 3 else 2,
            assistants = currentSettings.assistants,
            worldBookEntries = worldBookRepository.listEntries(),
            memoryEntries = memoryRepository.listEntries().filterNot { it.id in archiveIds },
            conversationSummaries = conversationSummaryRepository.listSummaries(),
            conversationSummarySegments = conversationSummaryRepository.listAllSummarySegments(),
            presets = presetRepository.listPresets().filterNot { preset -> preset.builtIn },
            memoryArchive = archive,
            companions = contextTransferRecoveryRepository?.exportPrivateCompanionData(currentSettings.assistants.map { it.id }.toSet()),
        )
    }

    private suspend fun mergeImportedData(bundle: ContextDataBundle): MemoryArchiveImportResult.Imported? {
        contextTransferRecoveryRepository?.let { recovery ->
            val id = pendingRecoveryOperationId ?: (pendingAssistantAvatarImport?.let { avatar ->
                recovery.preparePrivateImportWithAvatar(bundle, pendingMemoryPlan, pendingImportSettings ?: settings.value, ContextTransferAvatarInput(avatar.assistantId, avatar.bytes))
            } ?: recovery.preparePrivateImport(bundle, pendingMemoryPlan, pendingImportSettings ?: settings.value)).operationId.also { pendingRecoveryOperationId = it }
            val result = recovery.resumeOperation(id)
            check(result.stage == ContextTransferStage.COMPLETE) { "资料导入尚未完成" }
            lastVectorsNeedingRebuild = result.vectorsNeedingRebuild
            return null
        }
        val normalizedBundle = bundle.normalizedForContextImport()
        val currentSettings = settings.value
        val mergedAssistants = mergeAssistants(
            currentAssistants = currentSettings.assistants,
            importedAssistants = normalizedBundle.assistants,
        )
        val hasMemory = hasMemoryData(normalizedBundle)
        var memoryResult: MemoryArchiveImportResult.Imported? = null
        dataImportTransaction {
            if (hasMemory) {
                val repository = requireNotNull(memoryTransferRepository) { "记忆恢复服务不可用" }
                val plan = requireNotNull(pendingMemoryPlan) { "请先选择每个记忆空间的目标" }
                memoryResult =
                    when (val result = repository.importArchive(normalizedBundle, plan)) {
                        is MemoryArchiveImportResult.Imported -> result
                        is MemoryArchiveImportResult.Conflict -> error(result.reason)
                    }
            }
            normalizedBundle.worldBookEntries.forEach { entry ->
                worldBookRepository.upsertEntry(entry)
            }
            normalizedBundle.presets.forEach { preset ->
                presetRepository.upsertPreset(preset.asImportedCustomPreset())
            }
        }

        // 外层 Room 事务已提交后才发布本地缓存失效；设置失败仍保留同计划幂等回执。
        if (hasMemory) memoryTransferRepository?.notifyImportCommitted(
            requireNotNull(
                pendingMemoryPlan
            )
        )

        settingsEditor.saveAssistants(
            assistants = mergedAssistants,
            selectedAssistantId = resolveSelectedAssistantId(
                currentSelectedAssistantId = currentSettings.selectedAssistantId,
                assistants = mergedAssistants,
            ),
        )
        return memoryResult
    }

    private suspend fun refreshCounts() {
        val customAssistantCount = settings.value.assistants.size
        val worldBookCount = worldBookRepository.listEntries().size
        val archive = memoryTransferRepository?.exportArchive(includeVectors = false)
        val archiveIds = archive?.records?.map { it.id }?.toSet().orEmpty()
        val memoryCount =
            memoryRepository.listEntries().count { it.id !in archiveIds } + (archive?.itemCount
                ?: 0)
        val summaryCount = conversationSummaryRepository.listSummaries().size +
                conversationSummaryRepository.listAllSummarySegments().size
        val presetCount = presetRepository.listPresets().count { preset -> !preset.builtIn }
        _uiState.update {
            it.copy(
                customAssistantCount = customAssistantCount,
                worldBookCount = worldBookCount,
                memoryCount = memoryCount,
                summaryCount = summaryCount,
                presetCount = presetCount,
                companionCount = settings.value.companionCharacterSettings.keys.count { id -> settings.value.assistants.any { it.id == id } },
            )
        }
        refreshPendingOperations()
    }

    private suspend fun applyImportedAssistantAvatar(bundle: ContextDataBundle): ContextDataBundle {
        val assistantAvatarImport = pendingAssistantAvatarImport ?: return bundle
        if (bundle.assistants.size != 1) {
            return bundle
        }
        val avatarUri = importedAssistantAvatarSaver(assistantAvatarImport)
            ?.trim()
            .orEmpty()
        if (avatarUri.isBlank()) {
            return bundle
        }
        return bundle.copy(
            assistants = listOf(
                bundle.assistants.single().copy(avatarUri = avatarUri),
            ),
        )
    }

    private fun mergeAssistants(
        currentAssistants: List<Assistant>,
        importedAssistants: List<Assistant>,
    ): List<Assistant> {
        val builtinIds = BUILTIN_ASSISTANTS.map { it.id }.toSet() + HIDDEN_BUILTIN_ASSISTANT_IDS
        val importedCustomAssistants = importedAssistants.filter { it.id !in builtinIds }
        val merged = linkedMapOf<String, Assistant>()
        currentAssistants.forEach { assistant ->
            merged[assistant.id] = assistant
        }
        importedCustomAssistants.forEach { assistant ->
            merged[assistant.id] = assistant
        }
        return merged.values.toList()
    }

    private fun resolveSelectedAssistantId(
        currentSelectedAssistantId: String,
        assistants: List<Assistant>,
    ): String {
        val builtinIds = BUILTIN_ASSISTANTS.map { it.id }.toSet() + HIDDEN_BUILTIN_ASSISTANT_IDS
        return when {
            currentSelectedAssistantId in builtinIds -> currentSelectedAssistantId
            assistants.any { it.id == currentSelectedAssistantId } -> currentSelectedAssistantId
            else -> DEFAULT_ASSISTANT_ID
        }
    }

    private suspend fun buildImportPreview(
        sourceLabel: String,
        section: ContextTransferSection,
        bundle: ContextDataBundle,
    ): ContextImportPreview {
        val currentSettings = settings.value
        val currentAssistantIds = currentSettings.assistants.map { it.id }.toSet()
        val currentWorldBookIds = worldBookRepository.listEntries().map { it.id }.toSet()
        val currentPresetIds = presetRepository.listPresets().map { it.id }.toSet()
        val archive =
            if (hasMemoryData(bundle)) MemoryTransferLegacyConversion.materialize(bundle) else null
        val memoryGroups = archive?.let(MemoryTransferLegacyConversion::sourceGroups).orEmpty()
        val targetOptions = if (memoryGroups.isEmpty()) emptyList() else {
            val repository = requireNotNull(memoryTransferRepository) { "记忆恢复服务不可用" }
            currentSettings.assistants.flatMap { assistant ->
                repository.listTargetOptions(assistant.id)
                    .map { it.copy(label = "${assistant.name} · ${it.label}") }
            }
        }

        val conflicts = buildList {
            bundle.assistants
                .filter { it.id in currentAssistantIds }
                .forEach { assistant ->
                    add(
                        ContextImportConflict(
                            "角色卡",
                            assistant.name.ifBlank { assistant.id },
                            assistant.id
                        )
                    )
                }
            bundle.worldBookEntries
                .filter { it.id in currentWorldBookIds }
                .forEach { entry ->
                    add(ContextImportConflict("世界书", entry.title.ifBlank { entry.id }, entry.id))
                }
            bundle.presets
                .filter { it.id in currentPresetIds }
                .forEach { preset ->
                    add(ContextImportConflict("预设", preset.name.ifBlank { preset.id }, preset.id))
                }
        }

        return ContextImportPreview(
            sourceLabel = sourceLabel,
            section = section,
            assistantCount = bundle.assistants.size,
            worldBookCount = bundle.worldBookEntries.size,
            memoryCount = (archive?.itemCount
                ?: 0) - bundle.conversationSummaries.size - bundle.conversationSummarySegments.size,
            summaryCount = bundle.conversationSummaries.size + bundle.conversationSummarySegments.size,
            presetCount = bundle.presets.size,
            conflicts = conflicts,
            noticeMessages = buildImportNoticeMessages(bundle),
            memoryGroups = memoryGroups,
            memoryTargetOptions = targetOptions,
            canConfirmMemory = memoryGroups.isEmpty(),
            memorySourceCount = archive?.sources?.size ?: 0,
            companionCount = bundle.companions?.size ?: 0,
        )
    }

    private suspend fun buildImportNoticeMessages(bundle: ContextDataBundle): List<String> {
        return buildList {
            if (bundle.companions != null) add("陪伴偏好按角色恢复，装扮素材随资料带入。资料包不包含全聊天、音频或声音收藏；失败可继续尚未完成的部分。")
            if (bundle.assistants.size >= CONTEXT_IMPORT_MAX_ASSISTANTS) {
                add("角色卡数量已达到安全上限（最多 ${CONTEXT_IMPORT_MAX_ASSISTANTS} 条），超出部分不会导入。")
            }
            if (bundle.worldBookEntries.size >= CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES) {
                add("世界书数量已达到安全上限（最多 ${CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES} 条），超出部分不会导入。")
            }
            if (hasMemoryData(bundle)) {
                add("记忆将复制到你选择的本机角色、空间和身份，保留版本与导入出处。原始聊天不在资料包中，导入材料不会冒充本机原话。")
                val summaryCount =
                    bundle.conversationSummaries.size + bundle.conversationSummarySegments.size
                if (summaryCount > 0) add("$summaryCount 条摘要及分段会保存为可查看的导入档案材料，原会话不存在时也会保留；不作为当前会话的摘要覆盖依据。")
                if (settings.value.assistants.isEmpty()) add("请先使用角色包入口恢复或创建本机角色，再选择记忆目标。")
                if (bundle.memoryArchive?.vectors?.isNotEmpty() == true) add("匹配本机配置的完整向量缓存可直接恢复；不兼容时保留正文并提示手动重建，不自动调用模型。")
            }
        }
    }

    private fun resolveAssistantAvatarImport(
        payload: ContextImportPayload,
        bundle: ContextDataBundle,
    ): AssistantAvatarImport? {
        val bytes = payload.binaryContent ?: return null
        val assistant = bundle.assistants.singleOrNull() ?: return null
        if (!looksLikePngImage(bytes, payload.fileName, payload.mimeType)) {
            return null
        }
        return AssistantAvatarImport(
            assistantId = assistant.id,
            fileName = payload.fileName,
            mimeType = payload.mimeType,
            bytes = bytes,
        )
    }

    private fun decodeImportBundle(
        payload: ContextImportPayload,
        section: ContextTransferSection,
    ): DecodedImportBundle {
        payload.textContent?.let { rawJson ->
            val root = runCatching { JsonParser.parseString(rawJson) }.getOrNull()
                ?.takeIf { it.isJsonObject }?.asJsonObject
            if (root != null && root.keySet().any {
                    it in setOf(
                        "memoryArchive",
                        "memoryEntries",
                        "conversationSummaries",
                        "conversationSummarySegments",
                        "assistants",
                        "worldBookEntries",
                        "presets",
                        "exportedAt",
                        "companions"
                    )
                }) {
                return DecodedImportBundle(ImportSourceType.CONTEXT_BUNDLE, codec.decode(rawJson))
            }
            val decodedBundle = runCatching {
                codec.decode(rawJson)
            }.getOrNull()?.let { bundle ->
                DecodedImportBundle(
                    sourceType = ImportSourceType.CONTEXT_BUNDLE,
                    bundle = bundle,
                )
            }

            if (decodedBundle != null && !isBundleEmpty(decodedBundle.bundle)) {
                return decodedBundle
            }

            tavernPresetAdapter.decodeAsBundle(
                rawJson = rawJson,
                fileName = payload.fileName,
            )?.let { bundle ->
                return DecodedImportBundle(
                    sourceType = ImportSourceType.TAVERN_PRESET,
                    bundle = bundle,
                )
            }

            tavernWorldBookAdapter.decodeAsBundle(
                rawJson = rawJson,
                fileName = payload.fileName,
            )?.let { bundle ->
                return DecodedImportBundle(
                    sourceType = ImportSourceType.TAVERN_WORLD_BOOK,
                    bundle = bundle,
                )
            }

            tavernCharacterAdapter.decodeAsBundle(rawJson)?.let { bundle ->
                return DecodedImportBundle(
                    sourceType = ImportSourceType.TAVERN_JSON,
                    bundle = attachAssistantScopedWorldBookLinks(bundle),
                )
            }
        }

        payload.binaryContent?.let { bytes ->
            tavernCharacterImageAdapter.decodeAsBundle(
                bytes = bytes,
                fileName = payload.fileName,
                mimeType = payload.mimeType,
            )?.let { bundle ->
                return DecodedImportBundle(
                    sourceType = ImportSourceType.TAVERN_PNG,
                    bundle = attachAssistantScopedWorldBookLinks(bundle),
                )
            }
            throw IllegalArgumentException(
                when {
                    looksLikePngImage(bytes, payload.fileName, payload.mimeType) ->
                        "未在 PNG 图片中识别到 Tavern 角色卡数据"

                    looksLikeImageFile(payload.fileName, payload.mimeType) ->
                        "暂仅支持 PNG Tavern 图片角色卡"

                    else -> "导入文件格式无效"
                },
            )
        }

        throw IllegalArgumentException("导入文件格式无效")
    }

    private fun looksLikePngImage(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Boolean {
        if (mimeType.equals("image/png", ignoreCase = true)) {
            return true
        }
        if (fileName.endsWith(".png", ignoreCase = true)) {
            return true
        }
        return bytes.size >= PNG_SIGNATURE.size &&
                bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)
    }

    private fun looksLikeImageFile(
        fileName: String,
        mimeType: String,
    ): Boolean {
        if (mimeType.startsWith("image/", ignoreCase = true)) {
            return true
        }
        return IMAGE_FILE_REGEX.containsMatchIn(fileName)
    }

    private fun filterBundleBySection(
        section: ContextTransferSection,
        bundle: ContextDataBundle,
        sourceType: ImportSourceType = ImportSourceType.CONTEXT_BUNDLE,
    ): ContextDataBundle {
        return when (section) {
            ContextTransferSection.ALL -> bundle
            ContextTransferSection.ASSISTANTS -> bundle.copy(
                worldBookEntries = if (sourceType.includesAssistantScopedWorldBooks) {
                    assistantScopedWorldBookEntries(bundle)
                } else {
                    emptyList()
                },
                memoryEntries = emptyList(),
                conversationSummaries = emptyList(),
                conversationSummarySegments = emptyList(),
                presets = emptyList(),
                memoryArchive = null,
                companions = bundle.companions?.filter { entry -> bundle.assistants.any { it.id == entry.assistantId } },
            )

            ContextTransferSection.WORLD_BOOK -> bundle.copy(
                companions = null,
                assistants = emptyList(),
                memoryEntries = emptyList(),
                conversationSummaries = emptyList(),
                conversationSummarySegments = emptyList(),
                presets = emptyList(),
                memoryArchive = null,
            )

            ContextTransferSection.MEMORY -> bundle.copy(
                companions = null,
                assistants = emptyList(),
                worldBookEntries = emptyList(),
                presets = emptyList(),
            )

            ContextTransferSection.PRESETS -> bundle.copy(
                companions = null,
                assistants = emptyList(),
                worldBookEntries = emptyList(),
                memoryEntries = emptyList(),
                conversationSummaries = emptyList(),
                conversationSummarySegments = emptyList(),
                memoryArchive = null,
            )
        }
    }

    private fun isBundleEmpty(bundle: ContextDataBundle): Boolean {
        return bundle.assistants.isEmpty() &&
                bundle.worldBookEntries.isEmpty() &&
                bundle.memoryEntries.isEmpty() &&
                bundle.conversationSummaries.isEmpty() &&
                bundle.conversationSummarySegments.isEmpty() &&
                bundle.presets.isEmpty() &&
                bundle.memoryArchive?.isEmpty != false
                && bundle.companions.isNullOrEmpty()
    }

    private fun hasMemoryData(bundle: ContextDataBundle): Boolean =
        bundle.memoryEntries.isNotEmpty() ||
                bundle.conversationSummaries.isNotEmpty() || bundle.conversationSummarySegments.isNotEmpty() || bundle.memoryArchive?.isEmpty == false

    private fun Preset.asImportedCustomPreset(): Preset {
        return normalized().copy(
            builtIn = false,
            userModified = true,
        )
    }

    private fun attachAssistantScopedWorldBookLinks(bundle: ContextDataBundle): ContextDataBundle {
        if (bundle.assistants.isEmpty() || bundle.worldBookEntries.isEmpty()) {
            return bundle
        }

        val assistantScopedEntryIds = bundle.worldBookEntries
            .filter { entry ->
                entry.scopeType == com.example.myapplication.model.WorldBookScopeType.ASSISTANT
            }
            .groupBy { entry -> entry.scopeId }
            .mapValues { (_, entries) -> entries.map { it.id } }

        return bundle.copy(
            assistants = bundle.assistants.map { assistant ->
                val scopedEntryIds = assistantScopedEntryIds[assistant.id].orEmpty()
                if (scopedEntryIds.isEmpty()) {
                    assistant
                } else {
                    assistant.copy(
                        linkedWorldBookIds = (assistant.linkedWorldBookIds + scopedEntryIds).distinct(),
                        linkedWorldBookBookIds = (
                                assistant.linkedWorldBookBookIds +
                                        bundle.worldBookEntries
                                            .filter { entry ->
                                                entry.scopeType == com.example.myapplication.model.WorldBookScopeType.ASSISTANT &&
                                                        entry.scopeId == assistant.id
                                            }
                                            .mapNotNull { entry ->
                                                entry.resolvedBookId().takeIf { it.isNotBlank() }
                                            }
                                ).distinct(),
                    )
                }
            },
        )
    }

    private fun assistantScopedWorldBookEntries(bundle: ContextDataBundle): List<com.example.myapplication.model.WorldBookEntry> {
        val assistantIds = bundle.assistants.map { it.id }.toSet()
        if (assistantIds.isEmpty()) {
            return emptyList()
        }
        return bundle.worldBookEntries.filter { entry ->
            entry.scopeType == com.example.myapplication.model.WorldBookScopeType.ASSISTANT &&
                    entry.scopeId in assistantIds
        }
    }

    private fun Throwable.toContextTransferUiError(fallback: String): String {
        return SensitiveTextRedactor.throwableMessageForUi(
            throwable = this,
            fallback = fallback,
        )
    }

    companion object {
        private val IMAGE_FILE_REGEX =
            Regex("""\.(png|apng|jpg|jpeg|webp|gif|bmp)$""", RegexOption.IGNORE_CASE)
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )

        fun factory(
            settingsRepository: AiSettingsRepository,
            settingsEditor: AiSettingsEditor,
            worldBookRepository: WorldBookRepository,
            memoryRepository: MemoryRepository,
            conversationSummaryRepository: ConversationSummaryRepository,
            presetRepository: PresetRepository = EmptyPresetRepository,
            importedAssistantAvatarSaver: suspend (AssistantAvatarImport) -> String? = { null },
            dataImportTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() },
            existingConversationIdsProvider: suspend () -> Set<String>? = { null },
            memoryTransferRepository: MemoryArchiveTransferRepository? = null,
            contextTransferRecoveryRepository: ContextTransferRecoveryRepository? = null,
        ): ViewModelProvider.Factory {
            return typedViewModelFactory {
                ContextTransferViewModel(
                    settingsRepository = settingsRepository,
                    settingsEditor = settingsEditor,
                    worldBookRepository = worldBookRepository,
                    memoryRepository = memoryRepository,
                    conversationSummaryRepository = conversationSummaryRepository,
                    presetRepository = presetRepository,
                    importedAssistantAvatarSaver = importedAssistantAvatarSaver,
                    dataImportTransaction = dataImportTransaction,
                    existingConversationIdsProvider = existingConversationIdsProvider,
                    memoryTransferRepository = memoryTransferRepository,
                    contextTransferRecoveryRepository = contextTransferRecoveryRepository,
                )
            }
        }
    }
}

private data class DecodedImportBundle(
    val sourceType: ImportSourceType,
    val bundle: ContextDataBundle,
)

private enum class ImportSourceType(
    val sourceLabel: String,
    val includesAssistantScopedWorldBooks: Boolean,
) {
    CONTEXT_BUNDLE(
        sourceLabel = "上下文数据包",
        includesAssistantScopedWorldBooks = false,
    ),
    TAVERN_JSON(
        sourceLabel = "Tavern 角色卡",
        includesAssistantScopedWorldBooks = true,
    ),
    TAVERN_PNG(
        sourceLabel = "Tavern 图片角色卡",
        includesAssistantScopedWorldBooks = true,
    ),
    TAVERN_WORLD_BOOK(
        sourceLabel = "独立世界书",
        includesAssistantScopedWorldBooks = false,
    ),
    TAVERN_PRESET(
        sourceLabel = "SillyTavern 预设",
        includesAssistantScopedWorldBooks = false,
    ),
}
