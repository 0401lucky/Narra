package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.roleplay.CompanionHistoryManagementRepository
import com.example.myapplication.data.repository.roleplay.CompanionTextExportContent
import com.example.myapplication.data.repository.roleplay.CompanionTextExporter
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionReadingSupport
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class CompanionHistoryUiState(
    val scope: CompanionReadingScope? = null,
    val query: CompanionHistoryQuery = CompanionHistoryQuery(),
    val groups: List<CompanionHistoryGroup> = emptyList(),
    val archives: List<CompanionHistoryArchiveChoice> = emptyList(),
    val selectedArchive: CompanionHistoryArchiveChoice? = null,
    val records: List<CompanionReadingRecord> = emptyList(),
    val totalCount: Int = 0,
    val hasOlder: Boolean = false,
    val nextBefore: CompanionReadingCursor? = null,
    val oldestLoaded: CompanionReadingCursor? = null,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val editingDrafts: Map<String, String> = emptyMap(),
    val exportRequest: CompanionTextExportRequest? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
) {
    val readOnly: Boolean get() = scope?.readOnly == true
}

/** 只读回看拥有独立 scope；写动作冻结所见修订，不把刷新后的修订偷换进旧操作。 */
class CompanionHistoryViewModel(
    private val scenarioId: String,
    private val characterId: String,
    private val repository: CompanionHistoryManagementRepository,
    private val textExporter: CompanionTextExporter,
    private val settingsStore: SettingsStore,
    private val pageSize: Int = 50,
    deviceZoneId: () -> String = { ZoneId.systemDefault().id },
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(CompanionHistoryUiState(query = CompanionHistoryQuery(zoneId = deviceZoneId())))
    val uiState = mutableState.asStateFlow()
    private val operations = Mutex()
    private val jobs = mutableSetOf<Job>()
    private var observer: Job? = null
    private var generation = 0L
    private var settings = AppSettings()
    private var selectedRevisions = emptyMap<String, CompanionMessageRevision>()
    private var editingRevisions = emptyMap<String, CompanionMessageRevision>()

    init {
        open()
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { latest ->
                settings = latest
                if (latest.resolvedAssistants().none { it.id == characterId }) {
                    invalidateOperations()
                    mutableState.value = CompanionHistoryUiState(
                        query = mutableState.value.query,
                        isLoading = false, errorMessage = "角色已不可用，请返回。"
                    )
                }
            }
        }
    }

    fun open() = openArchive(mutableState.value.selectedArchive)

    fun selectArchive(choice: CompanionHistoryArchiveChoice?) {
        if (choice != null && choice !in mutableState.value.archives) return
        openArchive(choice)
    }

    private fun openArchive(choice: CompanionHistoryArchiveChoice?) {
        invalidateOperations()
        val owner = generation
        val query = mutableState.value.query.copy(encounterId = null, conversationId = null)
        mutableState.value = CompanionHistoryUiState(query = query, selectedArchive = choice)
        launchOperation {
            operations.withLock {
                try {
                    settings = settingsStore.settingsFlow.first()
                    ensureOwner(owner)
                    val scope = repository.authorize(
                        scenarioId,
                        characterId,
                        choice?.spaceId,
                        choice?.runId
                    )
                        ?: error("当前角色、身份或历史范围不可用，请返回后重新打开")
                    val archives = repository.archiveChoices(scope)
                    val page = repository.page(scope, query, limit = pageSize)
                    ensureOwner(owner)
                    val currentScope = requireScope(scope, owner)
                    ensureOwner(owner)
                    mutableState.value = CompanionHistoryUiState(
                        scope = currentScope,
                        query = query,
                        groups = page.groups,
                        archives = archives,
                        selectedArchive = choice,
                        records = page.records,
                        totalCount = page.totalCount,
                        hasOlder = page.hasOlder,
                        nextBefore = page.nextBefore,
                        oldestLoaded = page.records.firstOrNull()?.cursor,
                        isLoading = false
                    )
                    observer = viewModelScope.launch {
                        repository.observeChanges(currentScope).collect { refresh() }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (owner == generation) fail(failure)
                }
            }
        }
    }

    fun setQuery(query: CompanionHistoryQuery) {
        if (mutableState.value.isWorking || query == mutableState.value.query) return
        try {
            query.validate()
        } catch (failure: Exception) {
            fail(failure)
            return
        }
        val scope = mutableState.value.scope ?: return
        invalidateOperations()
        val owner = generation
        mutableState.update {
            it.copy(
                query = query,
                records = emptyList(),
                groups = emptyList(),
                selectedIds = emptySet(),
                editingDrafts = emptyMap(),
                exportRequest = null,
                oldestLoaded = null,
                nextBefore = null,
                isLoading = true,
                errorMessage = null,
                noticeMessage = null
            )
        }
        launchOperation {
            operations.withLock {
                try {
                    ensureOwner(owner)
                    replaceWindow(scope, owner, expanded = false)
                    observer = viewModelScope.launch {
                        repository.observeChanges(scope).collect { refresh() }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (owner == generation) fail(failure)
                }
            }
        }
    }

    fun refresh() {
        val state = mutableState.value
        val scope = state.scope ?: return
        if (state.isWorking || state.isLoading) return
        val owner = generation
        launchOperation {
            operations.withLock {
                try {
                    ensureOwner(owner)
                    replaceWindow(scope, owner, expanded = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (owner == generation) failForScope(failure, scope, owner)
                }
            }
        }
    }

    fun loadOlder() {
        val state = mutableState.value
        val scope = state.scope ?: return
        if (state.isLoading || state.isWorking || !state.hasOlder) return
        mutableState.update { it.copy(isWorking = true, errorMessage = null) }
        val owner = generation
        launchOperation {
            operations.withLock {
                try {
                    ensureOwner(owner)
                    val refreshed = requireScope(scope, owner)
                    val page = repository.page(refreshed, state.query, state.nextBefore, pageSize)
                    ensureOwner(owner)
                    val currentScope = requireScope(refreshed, owner)
                    ensureOwner(owner)
                    val records =
                        CompanionReadingSupport.refreshedWindow(page.records + mutableState.value.records)
                    mutableState.update {
                        it.copy(
                            scope = currentScope,
                            groups = page.groups,
                            records = records,
                            totalCount = page.totalCount,
                            hasOlder = page.hasOlder,
                            nextBefore = page.nextBefore,
                            oldestLoaded = minOfOrNull(
                                state.oldestLoaded,
                                page.records.firstOrNull()?.cursor
                            ),
                            isWorking = false
                        )
                    }
                } catch (cancelled: CancellationException) {
                    if (owner == generation) mutableState.update { it.copy(isWorking = false) }
                    throw cancelled
                } catch (failure: Exception) {
                    if (owner == generation) failForScope(failure, scope, owner)
                }
            }
        }
    }

    fun toggleSelection(messageId: String) {
        val state = mutableState.value
        if (state.isWorking || state.isLoading || state.editingDrafts.isNotEmpty()) return
        val message = state.records.firstOrNull { it.message.id == messageId }?.message ?: return
        selectedRevisions = if (messageId in selectedRevisions) selectedRevisions - messageId
        else selectedRevisions + (messageId to CompanionMessageRevision(
            messageId,
            message.memoryRevision,
            message.conversationId
        ))
        mutableState.update { it.copy(selectedIds = selectedRevisions.keys) }
    }

    fun clearSelection() {
        if (mutableState.value.isWorking) return
        selectedRevisions = emptyMap()
        mutableState.update { it.copy(selectedIds = emptySet()) }
    }

    fun beginEdit(ids: Set<String> = mutableState.value.selectedIds) {
        val state = mutableState.value
        if (state.isWorking || state.isLoading || state.readOnly || ids.isEmpty()) return
        val records = state.records.filter { it.message.id in ids }
        if (records.any { it.binding.readOnly }) {
            mutableState.update { it.copy(errorMessage = "旧档仅供回看，不能修改。") }
            return
        }
        if (records.size != ids.size || records.any { it.message.companionMemoryState == CompanionMemoryState.CONTROL }) {
            mutableState.update { it.copy(errorMessage = "所选原文不能编辑；继续控制行只能删除。") }
            return
        }
        editingRevisions = records.associate { record ->
            val message = record.message
            message.id to (selectedRevisions[message.id]
                ?: CompanionMessageRevision(
                    message.id,
                    message.memoryRevision,
                    message.conversationId
                ))
        }
        mutableState.update {
            it.copy(
                editingDrafts = records.associate { record ->
                    record.message.id to CompanionTextExportContent.editableText(
                        record
                    )
                },
                errorMessage = null
            )
        }
    }

    fun updateDraft(messageId: String, content: String) {
        if (mutableState.value.isWorking || messageId !in editingRevisions) return
        mutableState.update { it.copy(editingDrafts = it.editingDrafts + (messageId to content)) }
    }

    fun cancelEdit() {
        if (mutableState.value.isWorking) return
        editingRevisions = emptyMap()
        mutableState.update { it.copy(editingDrafts = emptyMap()) }
    }

    fun saveEdits() {
        val drafts = mutableState.value.editingDrafts
        if (drafts.isEmpty()) return
        val edits = drafts.map { (id, content) ->
            CompanionHistoryEdit(id, editingRevisions[id]?.revision ?: 0L, content)
        }
        mutate { scope -> repository.edit(scope, edits) }
    }

    fun deleteSelected() {
        val sources = selectedRevisions.values.toList()
        if (sources.isEmpty()) return
        if (mutableState.value.records.any { it.message.id in selectedRevisions && it.binding.readOnly }) {
            mutableState.update { it.copy(errorMessage = "旧档仅供回看，不能修改。") }
            return
        }
        mutate { scope -> repository.delete(scope, sources) }
    }

    private fun mutate(action: suspend (CompanionReadingScope) -> CompanionHistoryMutationResult) {
        val state = mutableState.value
        val scope = state.scope ?: return
        if (state.isLoading || state.isWorking || state.readOnly) return
        val owner = generation
        mutableState.update { it.copy(isWorking = true, errorMessage = null, noticeMessage = null) }
        launchOperation {
            operations.withLock {
                try {
                    ensureOwner(owner)
                    val result = action(requireScope(scope, owner))
                    requireScope(scope, owner)
                    ensureOwner(owner)
                    when (result) {
                        is CompanionHistoryMutationResult.Applied -> {
                            selectedRevisions = emptyMap()
                            editingRevisions = emptyMap()
                            mutableState.update {
                                it.copy(
                                    selectedIds = emptySet(), editingDrafts = emptyMap(),
                                    noticeMessage = "已更新 ${result.messageIds.size} 条原文；独立人工记忆与声音收藏按原规则保留。"
                                )
                            }
                            replaceWindow(scope, owner, expanded = true)
                        }

                        CompanionHistoryMutationResult.Conflict -> mutableState.update {
                            it.copy(
                                errorMessage = "原文或授权范围已改变，本次操作未写入。请刷新后重新选择。",
                                isWorking = false
                            )
                        }

                        CompanionHistoryMutationResult.ReadOnly -> mutableState.update {
                            it.copy(
                                errorMessage = "旧档仅供回看，不能修改。",
                                isWorking = false
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    if (owner == generation) mutableState.update { it.copy(isWorking = false) }
                    throw cancelled
                } catch (failure: Exception) {
                    if (owner == generation) failForScope(failure, scope, owner)
                }
            }
        }
    }

    fun requestExport() {
        val state = mutableState.value
        val scope = state.scope ?: return
        if (state.isWorking || state.isLoading || state.exportRequest != null) return
        val name =
            settings.resolvedAssistants().firstOrNull { it.id == characterId }?.name.orEmpty()
        mutableState.update {
            it.copy(
                exportRequest = CompanionTextExportRequest(
                    UUID.randomUUID().toString(),
                    CompanionTextExporter.fileName(name, state.query), scope, state.query
                ), errorMessage = null, noticeMessage = null
            )
        }
    }

    /** launcher 必须带回实际打开时的 requestId；迟到结果不能套用新的筛选/导出计划。 */
    fun completeExport(documentUri: String?, requestId: String) {
        val request = mutableState.value.exportRequest?.takeIf { it.id == requestId } ?: return
        mutableState.update { it.copy(exportRequest = null) }
        if (documentUri == null) return
        val owner = generation
        mutableState.update { it.copy(isWorking = true) }
        launchOperation {
            operations.withLock {
                try {
                    ensureOwner(owner)
                    requireScope(request.scope, owner)
                    val result = textExporter.export(request.scope, request.query, documentUri)
                    requireScope(request.scope, owner)
                    ensureOwner(owner)
                    mutableState.update {
                        it.copy(
                            isWorking = false,
                            noticeMessage = (result as? CompanionTextExportResult.Saved)?.let { saved -> "已导出 ${saved.recordCount} 条原文。" },
                            errorMessage = (result as? CompanionTextExportResult.Failed)?.message
                        )
                    }
                } catch (cancelled: CancellationException) {
                    if (owner == generation) mutableState.update { it.copy(isWorking = false) }
                    throw cancelled
                } catch (failure: Exception) {
                    if (owner == generation) failForScope(failure, request.scope, owner)
                }
            }
        }
    }

    fun consumeMessage() =
        mutableState.update { it.copy(errorMessage = null, noticeMessage = null) }

    private suspend fun replaceWindow(
        scope: CompanionReadingScope,
        owner: Long,
        expanded: Boolean
    ) {
        val state = mutableState.value
        val refreshed = requireScope(scope, owner)
        val page = repository.page(refreshed, state.query, limit = pageSize)
        val oldest = state.oldestLoaded.takeIf { expanded }
        val records =
            if (oldest == null) page.records else repository.readAll(refreshed, state.query)
                .filter { it.cursor >= oldest }
        val boundary = oldest ?: records.firstOrNull()?.cursor
        val older =
            boundary?.let { repository.page(refreshed, state.query, it, 1).records.isNotEmpty() }
                ?: false
        ensureOwner(owner)
        val currentScope = requireScope(refreshed, owner)
        ensureOwner(owner)
        mutableState.update {
            it.copy(
                scope = currentScope,
                groups = page.groups,
                records = records,
                totalCount = page.totalCount,
                hasOlder = older,
                nextBefore = boundary,
                oldestLoaded = boundary,
                isLoading = false,
                isWorking = false
            )
        }
    }

    private fun invalidateOperations() {
        generation++
        observer?.cancel()
        jobs.toList().forEach { it.cancel() }
        selectedRevisions = emptyMap()
        editingRevisions = emptyMap()
    }

    private suspend fun requireScope(
        scope: CompanionReadingScope,
        owner: Long,
    ): CompanionReadingScope {
        ensureOwner(owner)
        val refreshed = repository.refreshScope(scope)
        ensureOwner(owner)
        if (refreshed != null) return refreshed
        invalidateScope()
        throw CancellationException("历史授权已失效")
    }

    private fun invalidateScope(message: String = "角色、身份或历史范围已改变，请重新打开。") {
        val query = mutableState.value.query
        invalidateOperations()
        mutableState.value = CompanionHistoryUiState(
            query = query, isLoading = false,
            errorMessage = message
        )
    }

    /** 仓储第二次校验也可能失效；普通 IO 错误仍保留经过重新确认的原文窗口。 */
    private suspend fun failForScope(
        failure: Exception,
        scope: CompanionReadingScope,
        owner: Long
    ) {
        try {
            ensureOwner(owner)
            requireScope(scope, owner)
            ensureOwner(owner)
            fail(failure)
        } catch (cancelled: CancellationException) {
            if (owner == generation) mutableState.update {
                it.copy(
                    isLoading = false,
                    isWorking = false
                )
            }
            throw cancelled
        } catch (validationFailure: Exception) {
            if (owner == generation) invalidateScope(
                SensitiveTextRedactor.throwableMessageForUi(
                    validationFailure, "无法重新确认历史范围，请重新打开。"
                )
            )
        }
    }

    private fun launchOperation(block: suspend () -> Unit): Job {
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                jobs.remove(job)
            }
        }
        jobs += job
        job.start()
        return job
    }

    private suspend fun ensureOwner(owner: Long) {
        coroutineContext.ensureActive()
        if (owner != generation || settings.resolvedAssistants().none { it.id == characterId }) {
            throw CancellationException("历史所有权已改变")
        }
    }

    private fun fail(failure: Exception) = mutableState.update {
        it.copy(
            isLoading = false, isWorking = false,
            errorMessage = SensitiveTextRedactor.throwableMessageForUi(
                failure,
                "历史操作失败，请重试。"
            )
        )
    }

    private fun minOfOrNull(
        first: CompanionReadingCursor?,
        second: CompanionReadingCursor?
    ): CompanionReadingCursor? =
        if (first == null) second else if (second == null) first else minOf(first, second)

    companion object {
        fun factory(
            scenarioId: String,
            characterId: String,
            repository: CompanionHistoryManagementRepository,
            textExporter: CompanionTextExporter,
            settingsStore: SettingsStore
        ) = typedViewModelFactory {
            CompanionHistoryViewModel(
                scenarioId,
                characterId,
                repository,
                textExporter,
                settingsStore
            )
        }
    }
}
