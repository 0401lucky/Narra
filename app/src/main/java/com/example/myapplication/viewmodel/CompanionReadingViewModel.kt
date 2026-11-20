package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.roleplay.CompanionReadingRepository
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionReadingSupport
import com.example.myapplication.roleplay.companion.CompanionSpeechSupport
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CompanionReadingUiState(
    val scope: CompanionReadingScope? = null,
    val records: List<CompanionReadingRecord> = emptyList(),
    val items: List<CompanionReadingItem> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingOlder: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasOlder: Boolean = false,
    val nextBefore: CompanionReadingCursor? = null,
    val oldestLoaded: CompanionReadingCursor? = null,
    val anchor: CompanionReadingAnchor? = null,
    val selection: CompanionSpeechSelection? = null,
    val errorMessage: String? = null,
)

/** 读取只持有自己的授权和窗口，完全不依赖 RoleplayViewModel 的当前场次/messages。 */
class CompanionReadingViewModel(
    private val scenarioId: String,
    private val characterId: String,
    private val repository: CompanionReadingRepository,
    private val settingsStore: SettingsStore,
    private val pageSize: Int = 40,
    private val focusMessageId: String? = null,
    private val archiveSpaceId: String? = null,
    private val archiveRunId: String? = null,
) : ViewModel() {
    constructor(
        scenarioId: String,
        characterId: String,
        repository: CompanionReadingRepository,
        settingsStore: SettingsStore,
        pageSize: Int = 40,
    ) : this(scenarioId, characterId, repository, settingsStore, pageSize, null, null, null)

    private val mutableState = MutableStateFlow(CompanionReadingUiState())
    val uiState = mutableState.asStateFlow()
    private val operations = Mutex()
    private var changes: Job? = null
    private var settings = AppSettings()
    private var opening: Job? = null
    private var readGeneration = 0L
    private val readJobs = mutableSetOf<Job>()

    init {
        open()
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { latest ->
                settings = latest
                if (latest.resolvedAssistants().none { it.id == characterId }) {
                    readGeneration++
                    readJobs.toList().forEach { it.cancel() }
                    changes?.cancel()
                    mutableState.update {
                        CompanionReadingUiState(
                            isLoading = false,
                            errorMessage = "角色已不可用，请返回。"
                        )
                    }
                } else replaceRecords(mutableState.value.records)
            }
        }
    }

    fun open() {
        if (opening?.isActive == true) return
        val generation = ++readGeneration
        changes?.cancel()
        readJobs.toList().forEach { it.cancel() }
        opening = launchRead {
            operations.withLock {
                val previousState = mutableState.value
                mutableState.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    settings = settingsStore.settingsFlow.first()
                    val currentScope = repository.authorize(scenarioId, characterId)
                    ensureRead(generation)
                    if (currentScope == null) {
                        invalidateScope()
                        return@withLock
                    }
                    val scope = if (archiveSpaceId != null || archiveRunId != null) {
                        val spaceId = requireNotNull(archiveSpaceId) { "旧档案空间不可用" }
                        val runId = requireNotNull(archiveRunId) { "旧档案运行不可用" }
                        repository.authorizeArchive(currentScope, spaceId, runId)
                    } else currentScope
                    ensureRead(generation)
                    if (scope == null) {
                        invalidateScope()
                        return@withLock
                    }
                    val page = focusMessageId?.let { repository.locate(scope, it, pageSize) }
                        ?: repository.page(scope, limit = pageSize)
                    val confirmedScope = requireReadScope(scope, generation)
                    val items = CompanionReadingSupport.items(page.records, settings)
                    val focusItem = items.firstOrNull { it.record.message.id == focusMessageId }
                    mutableState.value = CompanionReadingUiState(
                        scope = confirmedScope,
                        records = page.records,
                        items = items,
                        isLoading = false,
                        hasOlder = page.hasOlder,
                        nextBefore = page.nextBefore,
                        oldestLoaded = page.records.firstOrNull()?.cursor,
                        anchor = focusItem?.let {
                            CompanionReadingAnchor(
                                it.record.message.id, it.id, it.record.cursor, 0,
                                followingBottom = false,
                                beatRawStart = it.ui.companionBeat?.rawSpan?.start,
                            )
                        },
                    )
                    startObserving(confirmedScope)
                } catch (cancelled: CancellationException) {
                    if (generation == readGeneration) mutableState.update { it.copy(isLoading = false) }
                    throw cancelled
                } catch (failure: Exception) {
                    if (previousState.scope != null) failForScope(failure, previousState.scope, generation, resumeObservation = true)
                    else if (generation == readGeneration) fail(failure)
                }
            }
        }
    }

    fun loadOlder() {
        val state = mutableState.value
        if (state.isLoading || state.isLoadingOlder || !state.hasOlder) return
        mutableState.update { it.copy(isLoadingOlder = true, errorMessage = null) }
        val generation = readGeneration
        launchRead {
            operations.withLock {
                val current = mutableState.value
                try {
                    val scope = current.scope?.let { repository.refreshScope(it) }
                    ensureRead(generation)
                    if (scope == null) {
                        invalidateScope()
                        return@withLock
                    }
                    val page = repository.page(scope, current.nextBefore, pageSize)
                    val confirmedScope = requireReadScope(scope, generation)
                    val merged =
                        CompanionReadingSupport.refreshedWindow(page.records + current.records)
                    mutableState.update {
                        it.copy(
                            scope = confirmedScope, hasOlder = page.hasOlder, nextBefore = page.nextBefore,
                            oldestLoaded = minOfOrNull(
                                current.oldestLoaded,
                                page.records.firstOrNull()?.cursor
                            ), isLoadingOlder = false
                        )
                    }
                    replaceRecords(merged)
                } catch (cancelled: CancellationException) {
                    if (generation == readGeneration) mutableState.update { it.copy(isLoadingOlder = false) }; throw cancelled
                } catch (failure: Exception) {
                    failForScope(failure, current.scope, generation)
                }
            }
        }
    }

    fun refresh() {
        if (mutableState.value.scope == null) return
        val generation = readGeneration
        launchRead {
            operations.withLock {
                val current = mutableState.value
                val scope = current.scope ?: return@withLock
                mutableState.update { it.copy(isRefreshing = true, errorMessage = null) }
                try {
                    val refreshed = repository.refreshScope(scope)
                    ensureRead(generation)
                    if (refreshed == null) {
                        invalidateScope()
                        return@withLock
                    }
                    val from = current.oldestLoaded
                    val records = if (from == null) repository.page(
                        refreshed,
                        limit = pageSize
                    ).records else repository.readWindow(refreshed, from)
                    val oldest = from ?: records.firstOrNull()?.cursor
                    val older =
                        oldest?.let { repository.page(refreshed, it, 1).records.isNotEmpty() }
                            ?: false
                    val confirmedScope = requireReadScope(refreshed, generation)
                    mutableState.update {
                        it.copy(
                            scope = confirmedScope, isRefreshing = false, hasOlder = older,
                            nextBefore = oldest, oldestLoaded = oldest
                        )
                    }
                    replaceRecords(records)
                } catch (cancelled: CancellationException) {
                    if (generation == readGeneration) mutableState.update { it.copy(isRefreshing = false) }; throw cancelled
                } catch (failure: Exception) {
                    failForScope(failure, scope, generation)
                }
            }
        }
    }

    fun updateAnchor(anchor: CompanionReadingAnchor) =
        mutableState.update { it.copy(anchor = anchor) }

    private fun launchRead(block: suspend () -> Unit): Job {
        lateinit var task: Job
        task = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                readJobs.remove(task)
            }
        }
        readJobs += task
        task.start()
        return task
    }

    private suspend fun ensureRead(generation: Long) {
        coroutineContext.ensureActive()
        if (generation != readGeneration || settings.resolvedAssistants()
                .none { it.id == characterId }
        )
            throw CancellationException("阅读所有权已改变")
    }

    fun select(selection: CompanionSpeechSelection?) = mutableState.update { state ->
        state.copy(selection = selection?.takeIf {
            CompanionSpeechSupport.isCurrent(
                it,
                CompanionSpeechSupport.units(state.items)
            )
        })
    }

    private fun replaceRecords(records: List<CompanionReadingRecord>) {
        val sorted = CompanionReadingSupport.refreshedWindow(records)
        val items = CompanionReadingSupport.items(sorted, settings)
        mutableState.update { state ->
            state.copy(
                records = sorted, items = items,
                selection = state.selection?.takeIf {
                    CompanionSpeechSupport.isCurrent(
                        it,
                        CompanionSpeechSupport.units(items)
                    )
                })
        }
    }

    private fun fail(failure: Exception) = mutableState.update {
        it.copy(
            isLoading = false,
            isLoadingOlder = false,
            isRefreshing = false,
            errorMessage = SensitiveTextRedactor.throwableMessageForUi(failure, "阅读失败，请重试。")
        )
    }

    private fun invalidateScope() {
        readGeneration++
        changes?.cancel()
        mutableState.value = CompanionReadingUiState(
            isLoading = false,
            errorMessage = "角色、身份或阅读范围已改变，请重新打开。",
        )
    }

    private suspend fun requireReadScope(scope: CompanionReadingScope, generation: Long): CompanionReadingScope {
        ensureRead(generation)
        val confirmed = repository.refreshScope(scope)
        ensureRead(generation)
        if (confirmed == null) {
            invalidateScope()
            throw CancellationException("阅读授权已失效")
        }
        return confirmed
    }

    private fun startObserving(scope: CompanionReadingScope) {
        changes?.cancel()
        changes = viewModelScope.launch { repository.observeChanges(scope).collect { refresh() } }
    }

    private suspend fun failForScope(
        failure: Exception,
        scope: CompanionReadingScope?,
        generation: Long,
        resumeObservation: Boolean = false,
    ) {
        ensureRead(generation)
        val confirmed = try {
            scope?.let { repository.refreshScope(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        ensureRead(generation)
        if (confirmed == null) invalidateScope()
        else {
            mutableState.update { it.copy(scope = confirmed) }
            fail(failure)
            if (resumeObservation) startObserving(confirmed)
        }
    }

    private fun minOfOrNull(
        first: CompanionReadingCursor?,
        second: CompanionReadingCursor?
    ): CompanionReadingCursor? =
        when {
            first == null -> second; second == null -> first; else -> minOf(first, second)
        }

    companion object {
        @JvmOverloads
        fun factory(
            scenarioId: String,
            characterId: String,
            repository: CompanionReadingRepository,
            settingsStore: SettingsStore,
            focusMessageId: String? = null,
            archiveSpaceId: String? = null,
            archiveRunId: String? = null,
        ) =
            typedViewModelFactory {
                CompanionReadingViewModel(
                    scenarioId,
                    characterId,
                    repository,
                    settingsStore,
                    focusMessageId = focusMessageId,
                    archiveSpaceId = archiveSpaceId,
                    archiveRunId = archiveRunId,
                )
            }
    }
}
