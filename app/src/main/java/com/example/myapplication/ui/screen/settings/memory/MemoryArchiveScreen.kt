package com.example.myapplication.ui.screen.settings.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.model.*
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.roleplay.memoryPurposeLabel
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.SettingsHelpButton
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MemoryArchiveCallbacks(
    val onSelectSpace: (String) -> Unit,
    val onSelectPersona: (String) -> Unit,
    val onSetPanel: (MemoryArchivePanel) -> Unit,
    val onSetQuery: (MemoryArchiveQuery) -> Unit,
    val onReload: () -> Unit,
    val onLoadMore: () -> Unit,
    val onOpenItem: (MemoryArchiveItem) -> Unit,
    val onCloseDetail: () -> Unit,
    val onSelectRevision: (Long) -> Unit,
    val onAdd: (MemoryMaterialKind) -> Unit,
    val onCreateDailyArchive: () -> Unit,
    val onEdit: () -> Unit,
    val onUpdateEditor: (MemoryArchiveEditorUi) -> Unit,
    val onCloseEditor: () -> Unit,
    val onSaveEditor: () -> Unit,
    val onTogglePinned: () -> Unit,
    val onSetArchived: (Boolean) -> Unit,
    val onDelete: () -> Unit,
    val onEndState: () -> Unit,
    val onLinkRecords: (MemoryRecord) -> Unit,
    val onLoadLinkCandidates: (String, Boolean) -> Unit,
    val onUnlinkMember: (String) -> Unit,
    val onRebuildEvent: (Boolean) -> Unit,
    val onRebuildIndex: (Boolean) -> Unit,
    val onSetSpaceArchived: (Boolean) -> Unit,
    val onDeleteSpace: () -> Unit,
    val onSelectConversation: (String) -> Unit,
    val onSelectMessage: (String) -> Unit,
    val onLoadOlderMessages: () -> Unit,
    val onToggleSelection: (String) -> Unit,
    val onInspectSelection: () -> Unit,
    val onProcessSelection: (Boolean, Boolean) -> Unit,
    val onResumeJob: (String) -> Unit,
    val onSetPaused: (String, Boolean) -> Unit,
    val onLoadMoreJobs: () -> Unit,
    val onCancelWork: () -> Unit,
    val onConsumeMessage: () -> Unit,
    val onReadOriginal: (String, String) -> Unit,
)

@Composable
fun MemoryArchiveScreen(
    uiState: MemoryArchiveUiState,
    assistant: Assistant?,
    settings: AppSettings,
    callbacks: MemoryArchiveCallbacks,
    onUpdateAssistant: (Assistant) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenOtherMode: () -> Unit,
    onNavigateBack: () -> Unit,
    simple: Boolean = false,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbar.showSnackbar(it); callbacks.onConsumeMessage() }
    }
    uiState.editor?.let { editor ->
        MemoryArchiveEditorScreen(editor, uiState.isSaving, callbacks, snackbar)
        return
    }
    uiState.detail?.let { detail ->
        MemoryArchiveDetailScreen(uiState, detail, callbacks, snackbar)
        return
    }
    var spaceAction by rememberSaveable(uiState.context?.spaceId) { mutableStateOf<String?>(null) }
    val context = uiState.context
    val space = uiState.spaces.find { it.id == context?.spaceId }
    Scaffold(
        topBar = {
            SettingsTopBar(
                title = if (simple) "简洁记忆编辑" else "记忆档案",
                subtitle = assistant?.name?.ifBlank { "未命名角色" }
                    ?: "历史角色 · ${uiState.characterId}",
                onNavigateBack = onNavigateBack,
                titleMaxLines = 2,
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("memory_archive_list"),
            contentPadding = PaddingValues(NarraUi.space16),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
        ) {
            item {
                ArchiveActionsRow {
                    ArchiveAction(if (simple) "高级管理" else "简洁编辑", onOpenOtherMode)
                    ArchiveAction("设置", onOpenSettings)
                    ArchiveAction("导入导出", onOpenTransfer)
                    ArchiveAction("刷新", callbacks.onReload)
                }
            }
            if (assistant != null) item {
                var showRoleOptions by rememberSaveable(assistant.id) { mutableStateOf(false) }
                var maxItems by rememberSaveable(
                    assistant.id,
                    assistant.memoryMaxItems
                ) { mutableStateOf(assistant.memoryMaxItems.toString()) }
                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("角色记忆", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        assistant.memoryEnabled,
                        { onUpdateAssistant(assistant.copy(memoryEnabled = it)) },
                        modifier = Modifier.sizeIn(
                            minWidth = NarraMinimumTouchTarget,
                            minHeight = NarraMinimumTouchTarget
                        )
                    )
                }
                Text(
                    if (assistant.memoryEnabled) "每轮最多召回 ${assistant.memoryMaxItems} 条经历，存档保留。" else "已关闭自动整理和注入，现有档案仍可查看。",
                    style = MaterialTheme.typography.bodySmall
                )
                ArchiveAction("角色记忆设置", { showRoleOptions = !showRoleOptions })
                if (showRoleOptions) {
                    OutlinedTextField(
                        maxItems, { maxItems = it }, Modifier.fillMaxWidth(),
                        label = { Text("每轮经历条数上限") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                    ArchiveAction(
                        "保存角色上限",
                        {
                            maxItems.toIntOrNull()?.takeIf { it > 0 }
                                ?.let { onUpdateAssistant(assistant.copy(memoryMaxItems = it)) }
                        },
                        maxItems.toIntOrNull()?.let { it > 0 } == true)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            assistant.useGlobalMemory,
                            { onUpdateAssistant(assistant.copy(useGlobalMemory = it)) })
                        Text("读取旧格式的全局兼容记忆")
                    }
                    Text(
                        "该选项用于旧版记忆范围；新日常与独立剧情按所选空间管理。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            item {
                Text("记忆空间", style = MaterialTheme.typography.titleSmall)
                ArchiveChoices(uiState.spaces, { it.id }, { candidate ->
                    "${candidate.purpose.memoryPurposeLabel()} · ${archiveDate(candidate.createdAt)}${if (candidate.status == MemorySpaceStatus.ARCHIVED) " · 已归档" else ""} · ${
                        candidate.id.takeLast(
                            6
                        )
                    }"
                }, context?.spaceId, callbacks.onSelectSpace, "memory_space")
                if (uiState.spaces.isEmpty() && !uiState.isLoading) {
                    Text("还没有记忆档案。可以先手动添加，或在开始角色会话后自动建立对应空间。")
                    if (assistant != null) ArchiveAction(
                        "建立日常档案并添加",
                        callbacks.onCreateDailyArchive,
                        !uiState.isSaving
                    )
                }
            }
            if (context != null) {
                item {
                    Text("当时的扮演身份", style = MaterialTheme.typography.titleSmall)
                    ArchiveChoices(
                        uiState.personas,
                        { it.key },
                        { it.label },
                        context.personaKey,
                        callbacks.onSelectPersona,
                        "memory_persona"
                    )
                    ArchiveActionsRow {
                        ArchiveAction(
                            if (space?.status == MemorySpaceStatus.ARCHIVED) "恢复档案" else "归档此空间",
                            { spaceAction = "archive" },
                            !uiState.isSaving
                        )
                        ArchiveAction("删除此档案", { spaceAction = "delete" }, !uiState.isSaving)
                    }
                }
                if (!simple) item {
                    ArchiveChoices(
                        MemoryArchivePanel.entries,
                        { it.name },
                        { it.label() },
                        uiState.panel.name,
                        { callbacks.onSetPanel(MemoryArchivePanel.valueOf(it)) },
                        "memory_panel"
                    )
                }
                if (simple || uiState.panel == MemoryArchivePanel.ARCHIVE) {
                    if (!simple) item {
                        ArchiveChoices(
                            MemoryArchiveTab.entries,
                            { it.name },
                            { it.label() },
                            uiState.query.tab.name,
                            {
                                callbacks.onSetQuery(
                                    uiState.query.copy(
                                        tab = MemoryArchiveTab.valueOf(
                                            it
                                        ), category = null, residentCategory = null, stateKey = null
                                    )
                                )
                            },
                            "memory_tab"
                        )
                    }
                    item {
                        OutlinedTextField(
                            uiState.query.search,
                            { callbacks.onSetQuery(uiState.query.copy(search = it)) },
                            Modifier.fillMaxWidth().testTag("memory_archive_search"),
                            label = { Text("搜索整个空间的所选分类") },
                            singleLine = true
                        )
                    }
                    item {
                        if (uiState.query.tab == MemoryArchiveTab.RECORDS || uiState.query.tab == MemoryArchiveTab.EVENTS) {
                            ArchiveChoices(
                                listOf<MemoryCategory?>(null) + MemoryCategory.entries,
                                { it?.name ?: "all" },
                                { it?.label() ?: "全部分类" },
                                uiState.query.category?.name ?: "all",
                                { value -> callbacks.onSetQuery(uiState.query.copy(category = MemoryCategory.entries.find { it.name == value })) },
                                "memory_category"
                            )
                        }
                        if (uiState.query.tab == MemoryArchiveTab.RESIDENTS) {
                            ArchiveChoices(
                                listOf<MemoryResidentCategory?>(null) + MemoryResidentCategory.entries,
                                { it?.name ?: "all" },
                                { it?.label() ?: "四类认知" },
                                uiState.query.residentCategory?.name ?: "all",
                                { value -> callbacks.onSetQuery(uiState.query.copy(residentCategory = MemoryResidentCategory.entries.find { it.name == value })) },
                                "memory_resident"
                            )
                        }
                        if (uiState.query.tab == MemoryArchiveTab.STATES) {
                            ArchiveChoices(
                                listOf<MemoryStateKey?>(null) + MemoryStateKey.entries,
                                { it?.name ?: "all" },
                                { it?.label ?: "全部状态" },
                                uiState.query.stateKey?.name ?: "all",
                                { value -> callbacks.onSetQuery(uiState.query.copy(stateKey = MemoryStateKey.entries.find { it.name == value })) },
                                "memory_state_key"
                            )
                        }
                        ArchiveChoices(
                            MemoryArchiveStatusFilter.entries,
                            { it.name },
                            { it.label() },
                            uiState.query.status.name,
                            {
                                callbacks.onSetQuery(
                                    uiState.query.copy(
                                        status = MemoryArchiveStatusFilter.valueOf(
                                            it
                                        )
                                    )
                                )
                            },
                            "memory_status"
                        )
                    }
                    item {
                        ArchiveActionsRow {
                            Text(
                                "${uiState.totalCount} 项",
                                style = MaterialTheme.typography.labelLarge
                            )
                            if (uiState.query.tab != MemoryArchiveTab.EVENTS) {
                                ArchiveAction(
                                    "新增",
                                    { callbacks.onAdd(uiState.query.tab.materialKind()) },
                                    !uiState.isSaving
                                )
                            }
                            SettingsHelpButton(
                                "存档与本轮额度",
                                "活动经历池为 ${settings.memoryCapacity} 条，本轮预算约 ${settings.memoryRecallTokenBudget} token。超额经历转归档，仍可查找；不会因为召回条数而删除存档。长期认知四类上限为 ${settings.memoryResidentLimits.user}/${settings.memoryResidentLimits.character}/${settings.memoryResidentLimits.relationship}/${settings.memoryResidentLimits.shared}，可在设置中调整。",
                                dialogShape = NarraShapes.large,
                                actionContentColor = MaterialTheme.colorScheme.onSurface,
                                actionMinSize = NarraMinimumTouchTarget
                            )
                        }
                    }
                    items(uiState.items, key = { "item:${it.id}" }) { item ->
                        ArchiveCard(
                            onClick = { callbacks.onOpenItem(item) },
                            tag = "memory_item_${item.id}"
                        ) {
                            Text(item.label(), style = MaterialTheme.typography.labelLarge)
                            Text(
                                item.content().ifBlank { "暂无已发布摘要" },
                                maxLines = if (simple) 6 else 4
                            )
                            Text(
                                "版本 ${item.revision()} · ${item.statusLabel()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    item {
                        uiState.pageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (uiState.isLoading) Text("正在读取档案…")
                        else if (uiState.items.isEmpty()) Text("当前筛选没有记录。")
                        if (uiState.hasMore) ArchiveAction(
                            "加载更多",
                            callbacks.onLoadMore,
                            !uiState.isLoading
                        )
                        if (uiState.pageError != null) ArchiveAction("重试", callbacks.onReload)
                    }
                } else if (uiState.panel == MemoryArchivePanel.RECEIPT) {
                    archiveReceiptItems(uiState, callbacks)
                } else {
                    archiveProcessingItems(
                        uiState,
                        callbacks,
                        assistant != null,
                        settings.resolveEmbeddingProvider() != null
                    )
                }
            } else if (uiState.pageError != null) item {
                Text(
                    uiState.pageError,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    spaceAction?.let { action ->
        val delete = action == "delete"
        NarraAlertDialog(
            title = if (delete) "删除此记忆档案" else if (space?.status == MemorySpaceStatus.ARCHIVED) "恢复此档案" else "归档此空间",
            message = if (delete) "只删除此角色在当前空间的记忆、修订和派生资料。原始对话及其它空间保留；删除无法撤销。"
            else "已有记忆和原文保留。归档或恢复不会重新开始剧情，也不会自动重新运行旧任务。",
            confirmLabel = if (delete) "删除档案" else "确认",
            isDestructive = delete,
            onDismiss = { spaceAction = null },
            onConfirm = {
                spaceAction =
                    null; if (delete) callbacks.onDeleteSpace() else callbacks.onSetSpaceArchived(
                space?.status != MemorySpaceStatus.ARCHIVED
            )
            },
            shape = NarraShapes.large, actionContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun ArchiveActionsRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
        verticalArrangement = Arrangement.spacedBy(NarraUi.space4),
        content = content
    )
}

@Composable
internal fun ArchiveAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tag: String = label
) {
    TextButton(
        onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = NarraMinimumTouchTarget).testTag(tag),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) { Text(label) }
}

@Composable
internal fun <T> ArchiveChoices(
    options: List<T>,
    key: (T) -> String,
    label: (T) -> String,
    selected: String?,
    onSelect: (String) -> Unit,
    tag: String
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
        modifier = Modifier.testTag(tag)
    ) {
        items(options, key = key) { option ->
            FilterChip(
                selected == key(option), { onSelect(key(option)) },
                label = { Text(label(option)) }, shape = NarraShapes.small,
                modifier = Modifier.heightIn(min = NarraMinimumTouchTarget)
                    .testTag("${tag}_${key(option)}")
            )
        }
    }
}

@Composable
internal fun ArchiveCard(
    onClick: (() -> Unit)? = null,
    tag: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = NarraShapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().testTag(tag)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            Modifier.padding(NarraUi.space16),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
            content = content
        )
    }
}

internal fun MemoryArchivePanel.label() = when (this) {
    MemoryArchivePanel.ARCHIVE -> "档案"; MemoryArchivePanel.RECEIPT -> "本轮材料"; MemoryArchivePanel.PROCESSING -> "整理与任务"
}

internal fun MemoryArchiveTab.label() = when (this) {
    MemoryArchiveTab.RECORDS -> "经历"; MemoryArchiveTab.EVENTS -> "事件"; MemoryArchiveTab.RESIDENTS -> "长期认知"; MemoryArchiveTab.STATES -> "当前状态"
}

internal fun MemoryCategory.label() = when (this) {
    MemoryCategory.DAILY -> "日常"; MemoryCategory.SHARED -> "共同经历"; MemoryCategory.WORK_STUDY -> "学习工作"; MemoryCategory.USER_SOCIAL -> "对方与重要他人"; MemoryCategory.CHARACTER_SELF -> "角色自身"; MemoryCategory.UNRESOLVED -> "未解决事项"; MemoryCategory.FUTURE -> "未来约定"
}

internal fun MemoryResidentCategory.label() = when (this) {
    MemoryResidentCategory.USER -> "关于对方"; MemoryResidentCategory.CHARACTER -> "关于自己"; MemoryResidentCategory.RELATIONSHIP -> "彼此关系"; MemoryResidentCategory.SHARED -> "共同背景"
}

internal fun MemoryArchiveStatusFilter.label() = when (this) {
    MemoryArchiveStatusFilter.ALL -> "全部状态"; MemoryArchiveStatusFilter.ACTIVE -> "有效"; MemoryArchiveStatusFilter.ARCHIVED -> "已归档"; MemoryArchiveStatusFilter.STALE -> "待更新"; MemoryArchiveStatusFilter.ENDED -> "已结束或过期"
}

internal fun MemoryArchiveTab.materialKind() = when (this) {
    MemoryArchiveTab.RECORDS -> MemoryMaterialKind.RECORD; MemoryArchiveTab.EVENTS -> MemoryMaterialKind.EVENT; MemoryArchiveTab.RESIDENTS -> MemoryMaterialKind.RESIDENT; MemoryArchiveTab.STATES -> MemoryMaterialKind.STATE
}

internal fun MemoryArchiveItem.label() = when (this) {
    is MemoryArchiveItem.Record -> record.category.label(); is MemoryArchiveItem.Event -> event.title.ifBlank { "事件" }; is MemoryArchiveItem.Resident -> entry.category.label(); is MemoryArchiveItem.State -> state.key.label
}

internal fun MemoryArchiveItem.statusLabel(): String = when (this) {
    is MemoryArchiveItem.Record -> when (record.status) {
        MemoryRecordStatus.ACTIVE -> if (record.pinned) "已置顶" else "活动经历"; MemoryRecordStatus.ARCHIVED -> "已归档"; MemoryRecordStatus.STALE -> "来源待核对"; MemoryRecordStatus.DELETED -> "已删除"
    }

    is MemoryArchiveItem.Event -> "${if (event.status == MemoryEventStatus.ARCHIVED) "已归档 · " else ""}${
        when (event.summaryStatus) {
            MemoryEventSummaryStatus.READY -> "摘要就绪"; MemoryEventSummaryStatus.STALE -> "摘要待重整"; MemoryEventSummaryStatus.EMPTY -> "未整理摘要"
        }
    }"

    is MemoryArchiveItem.Resident -> knowledgeStatus(entry.status)
    is MemoryArchiveItem.State -> knowledgeStatus(state.status)
}

internal fun knowledgeStatus(value: String) = when (value) {
    "READY", "ACTIVE" -> "有效"; "ARCHIVED" -> "已归档"; "STALE", "UNVERIFIED" -> "来源待核对"; "ENDED" -> "已结束"; "EXPIRED" -> "已过期"; "DELETED" -> "已删除"; else -> "待确认"
}

internal fun archiveDate(time: Long?): String =
    if (time == null || time <= 0) "时间未知" else SimpleDateFormat(
        "yyyy-MM-dd HH:mm",
        Locale.CHINA
    ).format(Date(time))
