package com.example.myapplication.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.*
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MemoryArchiveViewModel(
    private val repository: MemoryArchiveRepository,
    private val characterId: String,
    private val initialConversationId: String?,
    private val tasks: MemoryArchiveTaskActions,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val extras: MemoryArchiveReadExtras? = null,
) : ViewModel() {
    private val stateKey = memoryArchiveSavedStateKey(characterId, initialConversationId)
    private val _uiState = MutableStateFlow(
        MemoryArchiveUiState(
            characterId = characterId,
            query = MemoryArchiveQuery(
                tab = restoredEnum("tab", MemoryArchiveTab.RECORDS),
                search = savedState["$stateKey:search"] ?: "",
                category = savedState.get<String>("$stateKey:category")
                    ?.let { name -> MemoryCategory.entries.find { it.name == name } },
                residentCategory = savedState.get<String>("$stateKey:residentCategory")
                    ?.let { name -> MemoryResidentCategory.entries.find { it.name == name } },
                stateKey = savedState.get<String>("$stateKey:stateKey")
                    ?.let { name -> MemoryStateKey.entries.find { it.name == name } },
                status = restoredEnum("status", MemoryArchiveStatusFilter.ALL),
            ),
            panel = restoredEnum("panel", MemoryArchivePanel.ARCHIVE),
        )
    )
    val uiState = _uiState.asStateFlow()
    private var scopeEpoch = 0L
    private var detailEpoch = 0L
    private var messageEpoch = 0L
    private var receiptEpoch = 0L
    private var pageEpoch = 0L
    private var workEpoch = 0L
    private var jobsEpoch = 0L
    private var indexEpoch = 0L
    private var linkEpoch = 0L
    private var countEpoch = 0L
    private var selectionJob: Job? = null
    private var pageJob: Job? = null
    private var detailJob: Job? = null
    private var messagesJob: Job? = null
    private var receiptJob: Job? = null
    private var mutationJob: Job? = null
    private var workJob: Job? = null
    private var jobsJob: Job? = null
    private var indexJob: Job? = null
    private var linkJob: Job? = null
    private var countJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                repository.observeSpaces(characterId).collect { spaces ->
                    _uiState.update { it.copy(spaces = spaces) }
                    val selected = _uiState.value.context
                    if (selected == null || spaces.none { it.id == selected.spaceId && it.generation == selected.generation }) {
                        val savedSpace = savedState.get<String>("$stateKey:space")
                        val runtime = initialConversationId?.let {
                            repository.getRuntimeContext(
                                it,
                                characterId
                            )
                        }
                        val preferred = spaces.firstOrNull { it.id == selected?.spaceId }
                            ?: spaces.firstOrNull { it.id == savedSpace }
                            ?: spaces.firstOrNull { it.id == runtime?.spaceId }
                            ?: spaces.firstOrNull { space ->
                                initialConversationId != null && repository.getManagementContext(
                                    characterId,
                                    space.id
                                )
                                    ?.let { repository.listArchiveConversations(it) }
                                    ?.any { it.id == initialConversationId } == true
                            }
                            ?: spaces.firstOrNull()
                        if (preferred == null) {
                            invalidateSelection()
                            _uiState.update { MemoryArchiveUiState(characterId, isLoading = false) }
                        } else selectSpace(preferred.id, runtime?.personaKey)
                    }
                    refreshCount()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _uiState.update { it.copy(isLoading = false, pageError = safeError(failure)) }
            }
        }
    }

    fun selectSpace(spaceId: String) = selectSpace(spaceId, null)

    private fun selectSpace(spaceId: String, preferredPersona: String?) {
        invalidateSelection()
        val epoch = scopeEpoch
        _uiState.update { current ->
            MemoryArchiveUiState(
                characterId, spaces = current.spaces, characterCount = current.characterCount,
                query = current.query.copy(offset = 0), panel = current.panel
            )
        }
        selectionJob = viewModelScope.launch {
            try {
                val initial = repository.getManagementContext(characterId, spaceId)
                    ?: return@launch selectionUnavailable(epoch)
                val personas = repository.listArchivePersonas(initial)
                val previousPersona =
                    preferredPersona ?: savedState.get<String>("$stateKey:persona")
                val persona = personas.firstOrNull { it.key == previousPersona }?.key
                    ?: personas.firstOrNull()?.key ?: MEMORY_UNKNOWN_PERSONA
                val context = repository.getManagementContext(characterId, spaceId, persona)
                    ?: return@launch selectionUnavailable(epoch)
                if (epoch != scopeEpoch) return@launch
                savedState["$stateKey:space"] = spaceId
                savedState["$stateKey:persona"] = persona
                _uiState.update {
                    it.copy(
                        context = context,
                        personas = personas,
                        editor = restoreEditor(context)
                    )
                }
                loadSelection(context, epoch)
                if (epoch == scopeEpoch && savedState.get<Boolean>("$stateKey:pendingAdd") == true) {
                    savedState.remove<Boolean>("$stateKey:pendingAdd")
                    beginAdd(MemoryMaterialKind.RECORD)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (epoch == scopeEpoch) _uiState.update {
                    it.copy(
                        isLoading = false,
                        pageError = safeError(failure)
                    )
                }
            }
        }
    }

    fun selectPersona(personaKey: String) {
        val current = _uiState.value
        val context = current.context ?: return
        if (context.personaKey == personaKey || current.personas.none { it.key == personaKey }) return
        savedState["$stateKey:persona"] = personaKey
        clearSavedEditor()
        selectSpace(context.spaceId, personaKey)
    }

    fun createDailyArchive() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        savedState["$stateKey:pendingAdd"] = true
        viewModelScope.launch {
            try {
                val context = repository.ensureDailySpace(characterId)
                selectSpace(context.spaceId, context.personaKey)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                savedState.remove<Boolean>("$stateKey:pendingAdd")
                _uiState.update { it.copy(message = safeError(failure)) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun loadSelection(context: MemoryRuntimeContext, epoch: Long) {
        val conversations = repository.listArchiveConversations(context)
        if (epoch != scopeEpoch) return
        _uiState.update { it.copy(conversations = conversations) }
        reload()
        val previous = savedState.get<String>("$stateKey:conversation")
        val conversation = conversations.firstOrNull { it.id == previous }
            ?: conversations.firstOrNull { it.id == initialConversationId }
            ?: conversations.firstOrNull { it.exists }
        conversation?.let { loadConversation(it.id, preserveExplicitRound = true) }
    }

    private fun invalidateSelection() {
        scopeEpoch++
        selectionJob?.cancel()
        pageJob?.cancel()
        detailJob?.cancel()
        messagesJob?.cancel()
        receiptJob?.cancel()
        mutationJob?.cancel()
        workJob?.cancel()
        jobsJob?.cancel()
        indexJob?.cancel()
        linkJob?.cancel()
    }

    private fun selectionUnavailable(epoch: Long) {
        if (scopeEpoch == epoch) _uiState.update {
            it.copy(
                isLoading = false,
                pageError = "此档案已变化，请重新选择。"
            )
        }
    }

    fun setPanel(panel: MemoryArchivePanel) {
        savedState["$stateKey:panel"] = panel.name
        _uiState.update { it.copy(panel = panel) }
    }

    fun setQuery(query: MemoryArchiveQuery) {
        savedState["$stateKey:tab"] = query.tab.name
        savedState["$stateKey:search"] = query.search
        savedState["$stateKey:category"] = query.category?.name
        savedState["$stateKey:residentCategory"] = query.residentCategory?.name
        savedState["$stateKey:stateKey"] = query.stateKey?.name
        savedState["$stateKey:status"] = query.status.name
        _uiState.update {
            it.copy(
                query = query.copy(offset = 0),
                items = emptyList(),
                nextOffset = 0,
                hasMore = false,
                detail = null
            )
        }
        detailEpoch++
        loadPage(false)
    }

    fun reload() {
        loadPage(false)
        loadJobs(false)
        refreshCount()
        loadIndexProgress()
    }

    fun onRouteEntered() {
        reload()
        val state = _uiState.value
        if (state.editor == null && state.detail == null) state.conversationId?.let {
            loadConversation(
                it,
                preserveExplicitRound = true
            )
        }
    }

    private fun loadIndexProgress() {
        val context = _uiState.value.context ?: return
        val reader = extras ?: return
        val epoch = scopeEpoch
        val ticket = ++indexEpoch
        indexJob?.cancel()
        indexJob = viewModelScope.launch {
            try {
                val progress = reader.indexProgress(context)
                val canRebuild = activeIndexRuntime(context) != null
                if (epoch == scopeEpoch && ticket == indexEpoch) _uiState.update {
                    it.copy(
                        indexProgress = progress,
                        indexError = null,
                        indexCanRebuild = canRebuild
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (epoch == scopeEpoch && ticket == indexEpoch) _uiState.update {
                    it.copy(
                        indexError = safeError(failure),
                        indexCanRebuild = false
                    )
                }
            }
        }
    }

    private suspend fun activeIndexRuntime(context: MemoryRuntimeContext): MemoryRuntimeContext? {
        for (candidate in repository.listActiveRecallContexts(context.characterId)) {
            if (candidate.spaceId != context.spaceId || candidate.personaKey != context.personaKey) continue
            val current =
                repository.getRuntimeContext(candidate.conversationId, context.characterId)
            if (current != null && current == candidate && current.generation == context.generation) return current
        }
        return null
    }

    fun rebuildIndex(retryUncertain: Boolean) {
        val reader = extras ?: return
        work { context ->
            val runtime = activeIndexRuntime(context)
            if (runtime == null) "当前档案没有活跃对话，暂不可重建；本地资料仍可查看。"
            else when (val result = reader.rebuildIndex(runtime, retryUncertain)) {
                is MemoryVectorBuildResult.Ready -> "向量索引已就绪：${result.records} 条记忆，${result.chunks} 段" +
                        if (result.pendingImportedRecords > 0) "；还有 ${result.pendingImportedRecords} 条导入记忆需明确重建" else ""

                is MemoryVectorBuildResult.NeedsReview -> "索引有未决请求，已保留进度。明确选择费用未知重试后才会重新请求。"
                is MemoryVectorBuildResult.Failed -> if (result.code == "USER_PAUSED") "记忆处理已暂停，已保存的索引进度保留。" else "索引重建未完成，已保存进度保留。"
                MemoryVectorBuildResult.Disabled -> "请检查角色记忆开关、向量配置和使用条款。"
                MemoryVectorBuildResult.Unavailable -> "当前运行或向量服务不可用，请刷新后重试。"
            }
        }
    }

    fun loadMore() = loadPage(true)

    private fun loadPage(append: Boolean) {
        val state = _uiState.value
        val context = state.context ?: return
        if (append && (state.isLoading || !state.hasMore)) return
        pageJob?.cancel()
        val epoch = scopeEpoch
        val ticket = ++pageEpoch
        val query = state.query.copy(offset = if (append) state.nextOffset else 0)
        _uiState.update { it.copy(isLoading = true, pageError = null) }
        pageJob = viewModelScope.launch {
            try {
                val page = repository.queryArchive(context, query)
                if (epoch != scopeEpoch || ticket != pageEpoch || _uiState.value.query != query.copy(
                        offset = 0
                    )
                ) return@launch
                _uiState.update {
                    it.copy(
                        items = if (append) (it.items + page.items).distinctBy { item -> item.id } else page.items,
                        totalCount = page.totalCount,
                        nextOffset = page.nextOffset,
                        hasMore = page.hasMore,
                        isLoading = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (epoch == scopeEpoch && ticket == pageEpoch && _uiState.value.query == query.copy(
                        offset = 0
                    )
                ) {
                    _uiState.update { it.copy(isLoading = false, pageError = safeError(failure)) }
                }
            }
        }
    }

    fun openItem(item: MemoryArchiveItem) = loadDetail(item, item.revision())

    fun selectRevision(revision: Long) {
        val detail = _uiState.value.detail ?: return
        if (revision in detail.revisions) loadDetail(detail.item, revision)
    }

    fun closeDetail() {
        detailEpoch++
        detailJob?.cancel()
        linkJob?.cancel()
        linkEpoch++
        _uiState.update { it.copy(detail = null) }
    }

    private fun loadDetail(item: MemoryArchiveItem, revision: Long) {
        val context = _uiState.value.context ?: return
        detailJob?.cancel()
        linkJob?.cancel()
        linkEpoch++
        val epoch = scopeEpoch
        val ticket = ++detailEpoch
        _uiState.update {
            it.copy(
                detail = MemoryArchiveDetailUi(item, revision),
                linkSearch = "",
                linkCandidates = emptyList(),
                linkNextOffset = 0,
                linkHasMore = false,
                linkLoading = false
            )
        }
        detailJob = viewModelScope.launch {
            try {
                val detail = when (item) {
                    is MemoryArchiveItem.Record -> {
                        val all = repository.getRecordDetails(context, item.id)
                            ?: error("记忆已不存在")
                        val exact = repository.getRecordRevisionDetails(context, item.id, revision)
                            ?: error("此版本已不可用")
                        MemoryArchiveDetailUi(
                            MemoryArchiveItem.Record(all.record),
                            revision,
                            exact.revision.content,
                            all.revisions.map { it.revision },
                            exact.sourceRefs.map { ref ->
                                MemoryArchiveSourceUi(
                                    ref,
                                    exact.sources.find { it.id == ref.sourceId })
                            },
                            subjective = all.record.kind == MemoryRecordKind.OPINION,
                            historyLabel = exact.revision.origin.historyLabel(),
                            revisionTime = exact.revision.createdAt,
                            isLoading = false,
                        )
                    }

                    is MemoryArchiveItem.Event -> {
                        val event =
                            repository.getEventDetails(context, item.id) ?: error("事件已不可用")
                        MemoryArchiveDetailUi(
                            MemoryArchiveItem.Event(event.event),
                            event.event.summaryRevision,
                            event.event.summary,
                            members = event.members,
                            sources = event.members.flatMap { member ->
                                member.sourceRefs.map { ref ->
                                    MemoryArchiveSourceUi(
                                        ref,
                                        member.sources.find { it.id == ref.sourceId })
                                }
                            }.distinctBy { it.ref },
                            isLoading = false
                        )
                    }

                    is MemoryArchiveItem.Resident -> {
                        val all =
                            repository.getResidentDetails(context, item.id) ?: error("认知已不可用")
                        knowledgeDetail(
                            context,
                            MemoryArchiveItem.Resident(all.entry),
                            revision,
                            all.revisions
                        )
                    }

                    is MemoryArchiveItem.State -> {
                        val all = repository.getCurrentStateDetails(context, item.id)
                            ?: error("状态已不可用")
                        knowledgeDetail(
                            context,
                            MemoryArchiveItem.State(all.state),
                            revision,
                            all.revisions
                        )
                    }
                }
                val originKind = when (item) {
                    is MemoryArchiveItem.Record -> MemoryTransferObjectKind.RECORD
                    is MemoryArchiveItem.Event -> MemoryTransferObjectKind.EVENT
                    is MemoryArchiveItem.Resident -> MemoryTransferObjectKind.RESIDENT
                    is MemoryArchiveItem.State -> MemoryTransferObjectKind.STATE
                }
                val origins = extras?.origins?.invoke(context, originKind, item.id).orEmpty()
                    .filter { it.localRevision == detail.selectedRevision || it.localRevision == 0L }
                if (epoch == scopeEpoch && ticket == detailEpoch) _uiState.update {
                    it.copy(
                        detail = detail.copy(
                            importOrigins = origins
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (epoch == scopeEpoch && ticket == detailEpoch) _uiState.update {
                    it.copy(detail = it.detail?.copy(isLoading = false, error = safeError(failure)))
                }
            }
        }
    }

    private suspend fun knowledgeDetail(
        context: MemoryRuntimeContext,
        item: MemoryArchiveItem,
        revision: Long,
        revisions: List<MemoryKnowledgeRevision>,
    ): MemoryArchiveDetailUi {
        val exact = revisions.find { it.revision == revision }
        if (exact == null) {
            check(revisions.isEmpty() && revision == item.revision()) { "此版本已不可用" }
            // 旧库可能只有当前头；如实显示它，不把读取操作变成补造历史或来源。
            return MemoryArchiveDetailUi(
                item, revision, item.content(), listOf(revision),
                subjective = (item as? MemoryArchiveItem.Resident)?.entry?.subjective == true,
                historyLabel = "旧资料：尚未记录修订历史", isLoading = false
            )
        }
        val directSources = exact.sourceRefs.map {
            MemoryArchiveSourceUi(
                it,
                repository.getSource(context, it.sourceId)
            )
        }
        val supportingSources = exact.supportingRecords.flatMap { supporting ->
            repository.getRecordRevisionDetails(context, supporting.recordId, supporting.revision)
                ?.let { saved ->
                    saved.sourceRefs.map { ref ->
                        MemoryArchiveSourceUi(
                            ref,
                            saved.sources.find { it.id == ref.sourceId })
                    }
                }.orEmpty()
        }
        return MemoryArchiveDetailUi(
            item,
            revision,
            exact.content,
            revisions.map { it.revision },
            (directSources + supportingSources).distinctBy { it.ref },
            subjective = exact.subjective,
            historyLabel = exact.origin.historyLabel(),
            revisionTime = exact.createdAt,
            isLoading = false,
            knowledgeRevision = exact
        )
    }

    fun beginAdd(kind: MemoryMaterialKind) {
        if (_uiState.value.context == null || _uiState.value.isSaving || kind == MemoryMaterialKind.EVENT) return
        updateEditor(MemoryArchiveEditorUi(kind))
    }

    fun beginEdit() {
        val detail = _uiState.value.detail ?: return
        if (detail.isLoading || detail.error != null || detail.selectedRevision != detail.item.revision()) return
        val editor = when (val item = detail.item) {
            is MemoryArchiveItem.Record -> MemoryArchiveEditorUi(
                MemoryMaterialKind.RECORD, item.id, item.record.revision,
                item.record.content, category = item.record.category, recordKind = item.record.kind
            )

            is MemoryArchiveItem.Resident -> MemoryArchiveEditorUi(
                MemoryMaterialKind.RESIDENT,
                item.id,
                item.entry.revision,
                item.entry.content,
                residentCategory = item.entry.category,
                subjective = item.entry.subjective
            )

            is MemoryArchiveItem.State -> MemoryArchiveEditorUi(
                MemoryMaterialKind.STATE, item.id, item.state.revision,
                item.state.content, stateKey = item.state.key, expiresAt = item.state.expiresAt
            )

            is MemoryArchiveItem.Event -> return
        }
        updateEditor(editor)
    }

    fun updateEditor(editor: MemoryArchiveEditorUi) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(editor = editor) }
        saveEditor(editor)
    }

    fun closeEditor() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(editor = null) }
        clearSavedEditor()
    }

    fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        if (editor.content.isBlank()) {
            updateEditor(editor.copy(error = "请填写正文。"))
            return
        }
        mutate("已保存", closeEditor = true, closeDetail = true) { context ->
            when (editor.kind) {
                MemoryMaterialKind.RECORD -> if (editor.id.isBlank()) {
                    repository.addRecord(
                        context,
                        ManualMemoryRecordDraft(editor.content, editor.recordKind, editor.category)
                    ).failure()
                } else repository.editRecord(
                    context,
                    editor.id,
                    editor.expectedRevision,
                    editor.content
                ).failure()

                MemoryMaterialKind.RESIDENT -> if (editor.id.isBlank()) {
                    repository.addResidentEntry(
                        context,
                        editor.residentCategory,
                        editor.content,
                        editor.subjective
                    ).failure()
                } else repository.editResidentEntry(
                    context,
                    editor.id,
                    editor.expectedRevision,
                    editor.content,
                    editor.subjective
                ).failure()

                MemoryMaterialKind.STATE -> repository.setCurrentState(
                    context, editor.stateKey, editor.expectedRevision,
                    editor.content, editor.expiresAt
                ).failure()

                MemoryMaterialKind.EVENT -> "事件摘要请使用重整操作。"
            }
        }
    }

    fun togglePinned() {
        val item = _uiState.value.detail?.item as? MemoryArchiveItem.Record ?: return
        mutate("已更新置顶", closeDetail = true) { context ->
            repository.setRecordPinned(context, item.id, item.record.revision, !item.record.pinned)
                .failure()
        }
    }

    fun setArchived(archived: Boolean) {
        val item = _uiState.value.detail?.item ?: return
        mutate(if (archived) "已归档" else "已恢复", closeDetail = true) { context ->
            when (item) {
                is MemoryArchiveItem.Record -> repository.setRecordArchived(
                    context,
                    item.id,
                    item.record.revision,
                    archived
                ).failure()

                is MemoryArchiveItem.Resident -> repository.setResidentArchived(
                    context,
                    item.id,
                    item.entry.revision,
                    archived
                ).failure()

                is MemoryArchiveItem.Event -> repository.setEventArchived(
                    context,
                    item.id,
                    item.event.generation,
                    archived
                ).failure()

                is MemoryArchiveItem.State -> "状态请使用结束操作。"
            }
        }
    }

    fun endState() {
        val item = _uiState.value.detail?.item as? MemoryArchiveItem.State ?: return
        mutate("状态已结束", closeDetail = true) {
            repository.endCurrentState(
                it,
                item.id,
                item.state.revision
            ).failure()
        }
    }

    fun deleteSelected() {
        val item = _uiState.value.detail?.item ?: return
        mutate("已删除所选记忆，原对话仍保留", closeDetail = true) { context ->
            when (item) {
                is MemoryArchiveItem.Record -> repository.deleteRecord(
                    context,
                    item.id,
                    item.record.revision
                ).failure()

                is MemoryArchiveItem.Resident -> repository.deleteKnowledge(
                    context,
                    MemoryMaterialKind.RESIDENT,
                    item.id,
                    item.entry.revision
                ).failure()

                is MemoryArchiveItem.State -> repository.deleteKnowledge(
                    context,
                    MemoryMaterialKind.STATE,
                    item.id,
                    item.state.revision
                ).failure()

                is MemoryArchiveItem.Event -> "事件可归档或解绑成员。"
            }
        }
    }

    fun setSpaceArchived(archived: Boolean) = mutate(if (archived) "档案已归档" else "档案已恢复") {
        repository.setSpaceArchived(it, archived).failure()
    }

    fun deleteSpace() = mutate("档案已删除，原始对话保留") { repository.deleteSpace(it).failure() }

    fun linkRecords(target: MemoryRecord) {
        val item = _uiState.value.detail?.item as? MemoryArchiveItem.Record ?: return
        mutate("已关联到同一事件", closeDetail = true) { context ->
            repository.linkEventMembers(
                context, MemoryRecordVersion(item.id, item.record.revision),
                MemoryRecordVersion(target.id, target.revision)
            ).failure()
        }
    }

    fun loadLinkCandidates(search: String, append: Boolean = false) {
        val state = _uiState.value
        val context = state.context ?: return
        if (append && (state.linkLoading || !state.linkHasMore)) return
        val epoch = scopeEpoch
        val detailTicket = detailEpoch
        val ticket = ++linkEpoch
        linkJob?.cancel()
        val offset = if (append) state.linkNextOffset else 0
        _uiState.update {
            it.copy(
                linkSearch = search, linkLoading = true,
                linkCandidates = if (append) it.linkCandidates else emptyList()
            )
        }
        linkJob = viewModelScope.launch {
            try {
                val page = repository.queryArchive(
                    context,
                    MemoryArchiveQuery(search = search, offset = offset)
                )
                if (epoch == scopeEpoch && detailTicket == detailEpoch && ticket == linkEpoch) _uiState.update {
                    val records = page.items.filterIsInstance<MemoryArchiveItem.Record>()
                        .map { row -> row.record }
                        .filter { row -> row.id != it.detail?.item?.id }
                    it.copy(linkCandidates = if (append) (it.linkCandidates + records).distinctBy { row -> row.id } else records,
                        linkNextOffset = page.nextOffset,
                        linkHasMore = page.hasMore,
                        linkLoading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (epoch == scopeEpoch && detailTicket == detailEpoch && ticket == linkEpoch) {
                    _uiState.update { it.copy(linkLoading = false, message = safeError(failure)) }
                }
            }
        }
    }

    fun unlinkMember(recordId: String) {
        val item = _uiState.value.detail?.item as? MemoryArchiveItem.Event ?: return
        mutate("已解绑，原经历保留", closeDetail = true) { context ->
            repository.unlinkEventMember(context, item.id, recordId, item.event.generation)
                .failure()
        }
    }

    private fun mutate(
        success: String, closeEditor: Boolean = false, closeDetail: Boolean = false,
        operation: suspend (MemoryRuntimeContext) -> String?,
    ) {
        val state = _uiState.value
        val context = state.context ?: return
        if (state.isSaving) return
        val epoch = scopeEpoch
        val currentDetail = detailEpoch
        _uiState.update { it.copy(isSaving = true) }
        mutationJob = viewModelScope.launch {
            try {
                val error = operation(context)
                if (epoch != scopeEpoch) return@launch
                if (error == null) {
                    if (closeEditor && _uiState.value.editor == state.editor) clearSavedEditor()
                    _uiState.update {
                        it.copy(
                            isSaving = false, message = success,
                            editor = if (closeEditor && it.editor == state.editor) null else it.editor,
                            detail = if (closeDetail && detailEpoch == currentDetail) null else it.detail
                        )
                    }
                    reload()
                } else _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = error,
                        editor = it.editor?.copy(error = error)
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (epoch == scopeEpoch) _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = safeError(failure),
                        editor = it.editor?.copy(error = safeError(failure))
                    )
                }
            } finally {
                if (epoch == scopeEpoch) _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun selectConversation(id: String) {
        savedState.remove<HashMap<String, String>>("$stateKey:round")
        loadConversation(id, preserveExplicitRound = false)
    }

    private fun loadConversation(id: String, preserveExplicitRound: Boolean) {
        val state = _uiState.value
        val context = state.context ?: return
        if (state.conversations.none { it.id == id }) return
        val restoredRound = savedState.get<HashMap<String, String>>("$stateKey:round")?.takeIf {
            it["space"] == context.spaceId && it["persona"] == context.personaKey && it["conversation"] == id
        }?.get("message")
        val explicitMessageId = if (preserveExplicitRound) {
            state.selectedMessageId?.takeIf { state.selectedMessageExplicit && state.conversationId == id }
                ?: restoredRound
        } else null
        messagesJob?.cancel()
        receiptJob?.cancel()
        workJob?.cancel()
        workEpoch++
        val epoch = scopeEpoch
        val ticket = ++messageEpoch
        receiptEpoch++
        savedState["$stateKey:conversation"] = id
        _uiState.update {
            it.copy(
                conversationId = id,
                messages = emptyList(),
                selectedMessageId = null,
                latestRoundMessage = null,
                selectedMessageExplicit = false,
                receipt = null,
                receiptError = null,
                receiptLoading = false,
                messagesLoading = true,
                messagesError = null,
                selectedMessageIds = emptySet(),
                inspection = null,
                olderMessageOffset = 0,
                isWorking = false
            )
        }
        messagesJob = viewModelScope.launch {
            try {
                val latestRound = repository.getLatestArchiveAssistantMessage(context, id)
                val first = repository.listArchiveMessages(context, id, 1, 0)
                var offset = (first.totalCount - MESSAGE_PAGE_SIZE).coerceAtLeast(0)
                var messages =
                    repository.listArchiveMessages(context, id, MESSAGE_PAGE_SIZE, offset).items
                // 默认回合使用整段对话的真实最新助手；身份分页仅用于显式旧轮与原文选区。
                while (offset > 0 && explicitMessageId != null && latestRound?.message?.id != explicitMessageId &&
                    messages.none { it.message.id == explicitMessageId }
                ) {
                    val nextOffset = (offset - MESSAGE_PAGE_SIZE).coerceAtLeast(0)
                    messages = repository.listArchiveMessages(
                        context,
                        id,
                        offset - nextOffset,
                        nextOffset
                    ).items + messages
                    offset = nextOffset
                }
                if (epoch != scopeEpoch || ticket != messageEpoch) return@launch
                _uiState.update {
                    it.copy(
                        messages = messages,
                        latestRoundMessage = latestRound,
                        olderMessageOffset = offset,
                        messagesLoading = false
                    )
                }
                val explicitlySelected =
                    (messages + listOfNotNull(latestRound)).firstOrNull { it.message.id == explicitMessageId && it.message.role == MessageRole.ASSISTANT }
                if (explicitlySelected != null) selectMessage(
                    explicitlySelected.message.id,
                    explicit = true
                )
                else latestRound?.let { selectMessage(it.message.id, explicit = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (epoch == scopeEpoch && ticket == messageEpoch) _uiState.update {
                    it.copy(
                        messagesLoading = false,
                        messagesError = safeError(failure)
                    )
                }
            }
        }
    }

    fun loadOlderMessages() {
        val state = _uiState.value
        val context = state.context ?: return
        val conversationId = state.conversationId ?: return
        if (state.messagesLoading || state.olderMessageOffset <= 0) return
        val epoch = scopeEpoch
        val ticket = messageEpoch
        val offset = (state.olderMessageOffset - MESSAGE_PAGE_SIZE).coerceAtLeast(0)
        _uiState.update { it.copy(messagesLoading = true) }
        messagesJob = viewModelScope.launch {
            try {
                val page = repository.listArchiveMessages(
                    context,
                    conversationId,
                    state.olderMessageOffset - offset,
                    offset
                )
                if (epoch == scopeEpoch && ticket == messageEpoch) _uiState.update {
                    it.copy(
                        messages = (page.items + it.messages).distinctBy { row -> row.message.id },
                        olderMessageOffset = offset, messagesLoading = false
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (epoch == scopeEpoch && ticket == messageEpoch) _uiState.update {
                    it.copy(
                        messagesLoading = false,
                        messagesError = safeError(failure)
                    )
                }
            }
        }
    }

    fun selectMessage(messageId: String) = selectMessage(messageId, explicit = true)

    private fun selectMessage(messageId: String, explicit: Boolean) {
        val state = _uiState.value
        val context = state.context ?: return
        val conversationId = state.conversationId ?: return
        val message =
            (state.messages + listOfNotNull(state.latestRoundMessage)).firstOrNull { it.message.id == messageId }?.message
                ?.takeIf { it.role == MessageRole.ASSISTANT } ?: return
        val ticket = ++receiptEpoch
        val epoch = scopeEpoch
        receiptJob?.cancel()
        _uiState.update {
            it.copy(
                selectedMessageId = message.id,
                selectedMessageExplicit = explicit,
                receipt = null,
                receiptError = null,
                receiptLoading = false
            )
        }
        if (explicit) savedState["$stateKey:round"] = hashMapOf(
            "space" to context.spaceId, "persona" to context.personaKey,
            "conversation" to conversationId, "message" to message.id
        )
        else savedState.remove<HashMap<String, String>>("$stateKey:round")
        val requestId = message.memoryRecallRequestId.takeIf { it.isNotBlank() } ?: return
        _uiState.update { it.copy(receiptLoading = true) }
        receiptJob = viewModelScope.launch {
            try {
                val receipt = repository.getArchivedRecallReceipt(
                    context,
                    conversationId,
                    message.id,
                    requestId
                )
                if (epoch == scopeEpoch && ticket == receiptEpoch) _uiState.update {
                    it.copy(
                        receipt = receipt,
                        receiptLoading = false
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (epoch == scopeEpoch && ticket == receiptEpoch) _uiState.update {
                    it.copy(
                        receiptLoading = false,
                        receiptError = safeError(failure)
                    )
                }
            }
        }
    }

    fun toggleSelection(messageId: String) {
        if (_uiState.value.isWorking) return
        if (_uiState.value.messages.none { it.message.id == messageId }) return
        _uiState.update {
            it.copy(
                selectedMessageIds = if (messageId in it.selectedMessageIds) it.selectedMessageIds - messageId else it.selectedMessageIds + messageId,
                inspection = null
            )
        }
    }

    fun inspectSelection() {
        val state = _uiState.value
        val context = state.context ?: return
        val conversationId = state.conversationId ?: return
        if (state.selectedMessageIds.isEmpty() || state.isWorking) return
        val selection = MemoryMessageSelection(conversationId, state.selectedMessageIds.toList())
        val epoch = scopeEpoch
        val ticket = ++workEpoch
        _uiState.update { it.copy(isWorking = true) }
        workJob = viewModelScope.launch {
            try {
                val inspection = repository.inspectSelection(context, selection)
                if (epoch == scopeEpoch && ticket == workEpoch && _uiState.value.conversationId == conversationId && _uiState.value.selectedMessageIds == selection.messageIds.toSet()) {
                    _uiState.update { it.copy(inspection = inspection) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (epoch == scopeEpoch && ticket == workEpoch) _uiState.update {
                    it.copy(
                        message = safeError(
                            failure
                        )
                    )
                }
            } finally {
                if (epoch == scopeEpoch && ticket == workEpoch) _uiState.update { it.copy(isWorking = false) }
            }
        }
    }

    fun processSelection(reprocessExisting: Boolean, retryUncertain: Boolean) {
        val inspection = _uiState.value.inspection ?: return
        if (inspection.unavailableMessageIds.isNotEmpty() || inspection.failureCode.isNotBlank()) return
        work { context ->
            tasks.processSelection(context, inspection.selection, reprocessExisting, retryUncertain)
                .let {
                    if (it.failureCode.isBlank()) "整理完成：${it.createdRecords} 条经历，${it.committedJobs} 个批次" else processingFailure(
                        it.failureCode
                    )
                }
        }
    }

    fun resumeJob(jobId: String) {
        val job = _uiState.value.jobs.find { it.id == jobId } ?: return
        work { context ->
            if (job.purpose == MemoryJobPurpose.EVENT_SUMMARY || job.purpose == MemoryJobPurpose.EVENT_REBUILD) {
                tasks.resumeEvent(context, jobId).let {
                    if (it.failureCode.isBlank()) "已继续原任务：${it.completedBatches} 个批次" else processingFailure(
                        it.failureCode
                    )
                }
            } else tasks.resumeExtraction(context, jobId).let {
                if (it.failureCode.isBlank()) "已继续原任务：${it.committedJobs} 个批次" else processingFailure(
                    it.failureCode
                )
            }
        }
    }

    fun rebuildEvent(retryUncertain: Boolean) {
        val event = (_uiState.value.detail?.item as? MemoryArchiveItem.Event)?.event ?: return
        work { context ->
            tasks.rebuildEvent(context, event.id, event.generation, retryUncertain).let {
                if (it.failureCode.isBlank()) "事件已重整" else processingFailure(it.failureCode)
            }
        }
    }

    private fun work(operation: suspend (MemoryRuntimeContext) -> String) {
        val state = _uiState.value
        val context = state.context ?: return
        if (state.isWorking) return
        val epoch = scopeEpoch
        val ticket = ++workEpoch
        _uiState.update { it.copy(isWorking = true) }
        workJob = viewModelScope.launch {
            try {
                val message = operation(context)
                if (epoch == scopeEpoch && ticket == workEpoch) _uiState.update {
                    it.copy(
                        message = message,
                        inspection = null
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (epoch == scopeEpoch && ticket == workEpoch) _uiState.update {
                    it.copy(
                        message = safeError(
                            failure
                        )
                    )
                }
            } finally {
                if (epoch == scopeEpoch && ticket == workEpoch) {
                    _uiState.update { it.copy(isWorking = false) }
                    reload()
                }
            }
        }
    }

    fun cancelWork() {
        workEpoch++
        workJob?.cancel()
        _uiState.update {
            it.copy(
                isWorking = false,
                message = "本次操作已取消，已保存进度保留。暂停后台处理请使用暂停按钮。"
            )
        }
        loadJobs(false)
    }

    fun setPaused(jobId: String, paused: Boolean) {
        val state = _uiState.value
        val control = if (jobId.isBlank()) state.processingControl else state.jobControls[jobId]
        control ?: return
        mutate(if (paused) "已暂停后续处理，已保存产物保留" else "已解除暂停；可按原任务继续") { context ->
            when (val result =
                repository.setProcessingPaused(context, control.version, paused, jobId)) {
                is MemoryProcessingControlResult.Updated -> null
                is MemoryProcessingControlResult.Conflict -> conflictMessage(result.reason)
            }
        }
    }

    fun loadMoreJobs() = loadJobs(true)

    private fun loadJobs(append: Boolean) {
        val state = _uiState.value
        val context = state.context ?: return
        if (append && (state.jobsLoading || !state.hasMoreJobs)) return
        val epoch = scopeEpoch
        val ticket = ++jobsEpoch
        jobsJob?.cancel()
        _uiState.update { it.copy(jobsLoading = true) }
        jobsJob = viewModelScope.launch {
            try {
                val jobs =
                    repository.listJobHistory(context, 50, if (append) state.jobs.size else 0)
                val control = repository.getProcessingControl(context)
                val controls = jobs.mapNotNull { job ->
                    repository.getProcessingControl(context, job.id)?.let { job.id to it }
                }.toMap()
                if (epoch == scopeEpoch && ticket == jobsEpoch) _uiState.update {
                    it.copy(jobs = if (append) (it.jobs + jobs).distinctBy { job -> job.id } else jobs,
                        hasMoreJobs = jobs.size == 50,
                        jobsLoading = false,
                        processingControl = control,
                        jobControls = if (append) it.jobControls + controls else controls)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (epoch == scopeEpoch && ticket == jobsEpoch) _uiState.update {
                    it.copy(
                        jobsLoading = false,
                        message = safeError(failure)
                    )
                }
            }
        }
    }

    private fun refreshCount() {
        val ticket = ++countEpoch
        countJob?.cancel()
        countJob = viewModelScope.launch {
            try {
                val count = repository.countArchiveItems(characterId)
                if (ticket == countEpoch) _uiState.update { it.copy(characterCount = count) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (ticket == countEpoch) _uiState.update { it.copy(message = safeError(failure)) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun saveEditor(editor: MemoryArchiveEditorUi) {
        val context = _uiState.value.context ?: return
        savedState["$stateKey:editor"] = hashMapOf<String, Any?>(
            "space" to context.spaceId, "persona" to context.personaKey, "kind" to editor.kind.name,
            "id" to editor.id, "revision" to editor.expectedRevision, "content" to editor.content,
            "category" to editor.category.name, "recordKind" to editor.recordKind.name,
            "residentCategory" to editor.residentCategory.name, "stateKey" to editor.stateKey.name,
            "subjective" to editor.subjective, "expiresAt" to editor.expiresAt,
        )
    }

    private fun restoreEditor(context: MemoryRuntimeContext): MemoryArchiveEditorUi? {
        val draft = savedState.get<HashMap<String, Any?>>("$stateKey:editor") ?: return null
        if (draft["space"] != context.spaceId || draft["persona"] != context.personaKey) return null
        return MemoryArchiveEditorUi(
            kind = MemoryMaterialKind.valueOf(draft["kind"] as? String ?: return null),
            id = draft["id"] as? String ?: "",
            expectedRevision = draft["revision"] as? Long ?: 0L,
            content = draft["content"] as? String ?: "",
            category = MemoryCategory.valueOf(
                draft["category"] as? String ?: MemoryCategory.DAILY.name
            ),
            recordKind = MemoryRecordKind.valueOf(
                draft["recordKind"] as? String ?: MemoryRecordKind.EPISODE.name
            ),
            residentCategory = MemoryResidentCategory.valueOf(
                draft["residentCategory"] as? String ?: MemoryResidentCategory.USER.name
            ),
            stateKey = MemoryStateKey.valueOf(
                draft["stateKey"] as? String ?: MemoryStateKey.MOOD.name
            ),
            subjective = draft["subjective"] as? Boolean ?: false,
            expiresAt = draft["expiresAt"] as? Long,
        )
    }

    private fun clearSavedEditor() {
        savedState.remove<HashMap<String, Any?>>("$stateKey:editor")
    }

    private inline fun <reified T : Enum<T>> restoredEnum(key: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == savedState.get<String>("$stateKey:$key") }
            ?: fallback

    companion object {
        private const val MESSAGE_PAGE_SIZE = 50
        fun factory(
            repository: MemoryArchiveRepository,
            characterId: String,
            conversationId: String?,
            tasks: MemoryArchiveTaskActions,
            savedState: SavedStateHandle = SavedStateHandle(),
            extras: MemoryArchiveReadExtras? = null
        ): ViewModelProvider.Factory =
            typedViewModelFactory {
                MemoryArchiveViewModel(
                    repository,
                    characterId,
                    conversationId,
                    tasks,
                    savedState,
                    extras
                )
            }
    }
}

private fun safeError(failure: Throwable) =
    SensitiveTextRedactor.throwableMessageForUi(failure, "操作失败，请重试；草稿和已保存进度仍保留。")

internal fun memoryArchiveSavedStateKey(characterId: String, conversationId: String?): String =
    "archive:${characterId.length}:$characterId:${conversationId.orEmpty().length}:${conversationId.orEmpty()}"

private fun MemoryChangeOrigin.historyLabel() = when (this) {
    MemoryChangeOrigin.MANUAL -> "人工修改"
    MemoryChangeOrigin.EXTRACTION -> "原文整理"
    MemoryChangeOrigin.CORRECTION -> "来源纠正"
    MemoryChangeOrigin.IMPORTED -> "导入资料"
    MemoryChangeOrigin.MIGRATION -> "旧记忆迁移"
}

private fun conflictMessage(reason: MemoryConflict): String = when (reason) {
    MemoryConflict.RECORD_CHANGED -> "内容已有新版本。草稿已保留，请关闭编辑并查看最新版本后合并修改。"
    MemoryConflict.SOURCE_CHANGED -> "原文或来源已改变，请刷新后重试。"
    MemoryConflict.SPACE_CHANGED -> "档案或身份已改变，请重新选择；当前草稿仍保留。"
    MemoryConflict.JOB_CHANGED -> "任务进度已改变，请刷新后查看。"
    MemoryConflict.INVALID_INPUT -> "输入不符合要求，请检查正文和所选范围。"
}

private fun MemoryWriteResult.failure(): String? =
    (this as? MemoryWriteResult.Conflict)?.let { conflictMessage(it.reason) }

private fun MemoryKnowledgeWriteResult.failure(): String? =
    (this as? MemoryKnowledgeWriteResult.Conflict)?.let { conflictMessage(it.reason) }

private fun MemoryEventMutationResult.failure(): String? =
    (this as? MemoryEventMutationResult.Conflict)?.let { "关联或成员版本已改变，请刷新后重试。" }

private fun processingFailure(code: String): String = when {
    "UNCERTAIN" in code || "REVIEW" in code || "STARTED" in code -> "原任务包含未决请求，已保留进度。继续不会自动重发；重新请求需要明确选择。"
    "PAUSED" in code -> "处理已暂停，已保存进度保留。"
    "DISABLED" in code || "CONSENT" in code -> "当前记忆开关或使用条款不允许整理，请检查设置。"
    else -> "此次处理未完成，原文与已保存进度保留。请刷新任务状态后重试。"
}
