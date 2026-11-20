package com.example.myapplication.ui.screen.settings.memory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.model.*
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.*

@Composable
internal fun MemoryArchiveDetailScreen(
    state: MemoryArchiveUiState,
    detail: MemoryArchiveDetailUi,
    callbacks: MemoryArchiveCallbacks,
    snackbar: SnackbarHostState,
) {
    BackHandler(onBack = callbacks.onCloseDetail)
    var confirm by rememberSaveable(detail.item.id) { mutableStateOf<String?>(null) }
    var linking by rememberSaveable(detail.item.id) { mutableStateOf(false) }
    var retryUncertain by rememberSaveable(detail.item.id) { mutableStateOf(false) }
    val current =
        detail.selectedRevision == detail.item.revision() && !detail.isLoading && detail.error == null
    val enabled = current && !state.isSaving && !state.isWorking
    Scaffold(
        topBar = {
            SettingsTopBar(
                title = if (linking) "关联同一事件" else "记忆详情",
                subtitle = detail.item.label(),
                titleMaxLines = 2,
                onNavigateBack = {
                    if (linking) linking = false else callbacks.onCloseDetail()
                })
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("memory_detail_list"),
            contentPadding = PaddingValues(NarraUi.space16),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space12)
        ) {
            if (linking) {
                item {
                    Text("选择同一空间和身份的一条经历。关联不会合并或删除原正文。")
                    OutlinedTextField(
                        state.linkSearch,
                        { callbacks.onLoadLinkCandidates(it, false) },
                        Modifier.fillMaxWidth(),
                        label = { Text("搜索可关联的经历") })
                }
                items(state.linkCandidates, key = { it.id }) { record ->
                    ArchiveCard(onClick = { callbacks.onLinkRecords(record); linking = false }) {
                        Text(record.content)
                        Text("版本 ${record.revision}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    if (state.linkLoading) Text("正在查找…")
                    if (state.linkHasMore) ArchiveAction(
                        "更多可关联经历",
                        { callbacks.onLoadLinkCandidates(state.linkSearch, true) },
                        !state.linkLoading
                    )
                    ArchiveAction("返回详情", { linking = false })
                }
            } else {
                item {
                    SelectionContainer {
                        Text(
                            "ID：${detail.item.id}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(if (current) detail.item.statusLabel() else detail.knowledgeRevision?.let {
                        "本版本状态：${
                            knowledgeStatus(
                                it.status
                            )
                        }"
                    }
                        ?: "当前记录状态：${detail.item.statusLabel()}")
                    ArchiveChoices(
                        detail.revisions,
                        { it.toString() },
                        { "版本 $it" },
                        detail.selectedRevision.toString(),
                        { callbacks.onSelectRevision(it.toLong()) },
                        "memory_revision"
                    )
                    if (!current && !detail.isLoading) Text("正在查看历史版本。编辑操作请先选择当前版本 ${detail.item.revision()}。")
                    if (detail.historyLabel.isNotBlank()) Text(
                        "${detail.historyLabel} · ${
                            archiveDate(
                                detail.revisionTime
                            )
                        }", style = MaterialTheme.typography.bodySmall
                    )
                    if (detail.subjective) Text(
                        "主观看法 / 关系感受",
                        style = MaterialTheme.typography.labelLarge
                    )
                    when (val item = detail.item) {
                        is MemoryArchiveItem.Record -> {
                            if (!current) Text(
                                "以下时间为当前记录信息，历史正文与来源见本版本。",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "发生时间：${archiveDate(item.record.occurredAt)} · ${if (item.record.datePrecision == "unknown") "精度未知" else item.record.datePrecision}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "入库：${archiveDate(item.record.createdAt)} · 更新：${
                                    archiveDate(
                                        item.record.updatedAt
                                    )
                                }", style = MaterialTheme.typography.bodySmall
                            )
                        }

                        is MemoryArchiveItem.State -> {
                            if (current) {
                                Text(
                                    "观察：${archiveDate(item.state.observedAt)} · 到期：${
                                        archiveDate(
                                            item.state.expiresAt
                                        )
                                    }", style = MaterialTheme.typography.bodySmall
                                )
                                item.state.endedAt?.let {
                                    Text(
                                        "结束：${archiveDate(it)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else detail.knowledgeRevision?.let { revision ->
                                Text(
                                    "本版本到期：${archiveDate(revision.expiresAt)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                revision.endedAt?.let {
                                    Text(
                                        "本版本结束：${archiveDate(it)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        else -> Unit
                    }
                }
                item {
                    detail.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (detail.isLoading) Text("正在读取所选版本…")
                    else ArchiveCard { SelectionContainer { Text(detail.content.ifBlank { "暂无已发布摘要" }) } }
                }
                item {
                    ArchiveActionsRow {
                        if (detail.item !is MemoryArchiveItem.Event) ArchiveAction(
                            "编辑当前版本",
                            callbacks.onEdit,
                            enabled,
                            "memory_edit"
                        )
                        if (detail.item is MemoryArchiveItem.Record) {
                            ArchiveAction(
                                if (detail.item.record.pinned) "取消置顶" else "置顶",
                                callbacks.onTogglePinned,
                                enabled
                            )
                            ArchiveAction(
                                "关联事件",
                                { callbacks.onLoadLinkCandidates("", false); linking = true },
                                enabled
                            )
                        }
                        if (detail.item is MemoryArchiveItem.State) ArchiveAction(
                            "结束状态",
                            { confirm = "end" },
                            enabled
                        )
                        else {
                            val archived = when (val item = detail.item) {
                                is MemoryArchiveItem.Record -> item.record.status == MemoryRecordStatus.ARCHIVED
                                is MemoryArchiveItem.Resident -> item.entry.status == "ARCHIVED"
                                is MemoryArchiveItem.Event -> item.event.status == MemoryEventStatus.ARCHIVED
                                else -> false
                            }
                            ArchiveAction(
                                if (archived) "恢复" else "归档",
                                { callbacks.onSetArchived(!archived) },
                                enabled
                            )
                        }
                        if (detail.item is MemoryArchiveItem.Event) ArchiveAction(
                            "重新整理事件",
                            { retryUncertain = false; confirm = "rebuild" },
                            enabled
                        )
                        else ArchiveAction("删除记忆", { confirm = "delete" }, enabled)
                    }
                    if (state.isWorking) ArchiveAction("取消本次整理", callbacks.onCancelWork)
                }
                if (detail.item is MemoryArchiveItem.Event) {
                    item {
                        Text("事件成员", style = MaterialTheme.typography.titleMedium)
                        if (detail.item.event.currentState.isNotBlank()) Text("摘要覆盖时的进展：${detail.item.event.currentState}")
                        if (detail.members.any { it.status == MemoryEventMemberStatus.LIVE }) Text("仍有未纳入摘要的新进展，请以成员原文为准。")
                    }
                    items(detail.members, key = { "member:${it.record.id}" }) { member ->
                        ArchiveCard {
                            Text(member.record.content)
                            Text("版本 ${member.record.revision} · ${member.coveredRevision?.let { "摘要覆盖版本 $it" } ?: "未纳入摘要"}")
                            ArchiveActionsRow {
                                ArchiveAction(
                                    "查看经历",
                                    { callbacks.onOpenItem(MemoryArchiveItem.Record(member.record)) })
                                ArchiveAction(
                                    "解除关联",
                                    { callbacks.onUnlinkMember(member.record.id) },
                                    enabled
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        if (detail.item is MemoryArchiveItem.Event) "事件成员来源" else "本版本来源",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (detail.item is MemoryArchiveItem.Event) Text(
                        "来源对应成员的当前版本；摘要实际覆盖的版本见各成员标记。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    detail.knowledgeRevision?.supportingRecords?.takeIf { it.isNotEmpty() }
                        ?.let { records ->
                            Text("本版本关联的经历", style = MaterialTheme.typography.titleSmall)
                            records.forEach { record ->
                                SelectionContainer {
                                    Text(
                                        "${record.recordId} · 版本 ${record.revision}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Text(
                                "按关联的历史版本查源，缺失时不借用经历的新版本。",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                }
                items(
                    detail.importOrigins.withIndex().toList(),
                    key = { "origin:${it.index}" }) { (_, link) ->
                    ArchiveCard {
                        Text(
                            "导入出处 · ${archiveDate(link.origin.importedAt)}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text("原 ID：${link.origin.originalId ?: "未知"} · 原版本：${link.origin.originalRevision ?: "未知"}")
                        Text(
                            "原空间：${link.origin.originalSpaceId ?: "未知"} · 原身份：${link.origin.originalPersonaKey ?: "未知"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text("这些是资料中的历史出处；原始聊天未随资料恢复。")
                    }
                }
                if (!detail.isLoading && detail.sources.isEmpty()) item { Text("没有可核实的来源记录。") }
                items(
                    detail.sources,
                    key = { "${it.ref.sourceId}:${it.ref.sourceRevision}:${it.ref.start}:${it.ref.end}" }) { source ->
                    MemoryArchiveSourceCard(source, callbacks.onReadOriginal)
                }
            }
        }
    }
    confirm?.let { action ->
        NarraAlertDialog(
            title = when (action) {
                "delete" -> "删除这条记忆"; "end" -> "结束此状态"; else -> "重新整理事件"
            },
            message = when (action) {
                "delete" -> "将删除此条正文、修订及相关派生材料，原始对话和其它空间保留。删除无法撤销。"
                "end" -> "此状态将退出当前注入，历史和来源仍保留。"
                else -> "这会基于全部有效成员发起新的整理，可能产生模型费用。继续已保存任务请回到“整理与任务”。"
            },
            isDestructive = action == "delete", shape = NarraShapes.large,
            actionContentColor = MaterialTheme.colorScheme.onSurface,
            onDismiss = { confirm = null },
            onConfirm = {
                confirm = null; when (action) {
                "delete" -> callbacks.onDelete(); "end" -> callbacks.onEndState(); else -> callbacks.onRebuildEvent(
                    retryUncertain
                )
            }
            },
            content = if (action == "rebuild") ({
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(retryUncertain, { retryUncertain = it })
                    Text("即使旧请求费用未知，也明确重新请求")
                }
            }) else null,
        )
    }
}

@Composable
internal fun MemoryArchiveSourceCard(
    source: MemoryArchiveSourceUi,
    onReadOriginal: (String, String) -> Unit
) {
    val evidence = source.evidence
    ArchiveCard(tag = "memory_source_${source.ref.sourceId}") {
        Text(
            when (evidence?.status) {
                MemorySourceStatus.AVAILABLE -> "来源可用"
                MemorySourceStatus.UNKNOWN -> "来源未知（UNKNOWN）"
                MemorySourceStatus.CHANGED -> "原文已改变（CHANGED）"
                MemorySourceStatus.MISSING, null -> "原文缺失（MISSING）"
                MemorySourceStatus.DELETED -> "原文已删除"
            }, style = MaterialTheme.typography.labelLarge
        )
        Text(
            "来源版本 ${source.ref.sourceRevision} · 原文范围 ${source.ref.start}–${source.ref.end}",
            style = MaterialTheme.typography.bodySmall
        )
        if (evidence != null) {
            Text(
                when (evidence.kind) {
                    MemorySourceKind.MESSAGE -> "历史对话"; MemorySourceKind.MANUAL -> "人工声明"; MemorySourceKind.IMPORTED -> "导入资料：原对话未随资料恢复"; MemorySourceKind.LEGACY_HINT -> "旧来源线索，未经核实"
                }
            )
            Text(
                "当时身份：${evidence.personaKey} · ${archiveDate(evidence.occurredAt)}",
                style = MaterialTheme.typography.bodySmall
            )
            if (evidence.content.isNotBlank()) SelectionContainer { Text(evidence.content) }
            if (evidence.kind == MemorySourceKind.MESSAGE && evidence.conversationId.isNotBlank() &&
                evidence.status != MemorySourceStatus.MISSING && evidence.status != MemorySourceStatus.DELETED
            ) {
                ArchiveAction(
                    "查看只读原文",
                    { onReadOriginal(evidence.conversationId, evidence.personaKey) })
            }
        }
    }
}

@Composable
internal fun MemoryArchiveEditorScreen(
    editor: MemoryArchiveEditorUi,
    saving: Boolean,
    callbacks: MemoryArchiveCallbacks,
    snackbar: SnackbarHostState,
) {
    BackHandler(enabled = !saving, onBack = callbacks.onCloseEditor)
    Scaffold(
        modifier = Modifier.imePadding().testTag("memory_archive_editor"),
        topBar = {
            SettingsTopBar(
                title = if (editor.id.isBlank()) "新增记忆" else "编辑记忆",
                onNavigateBack = callbacks.onCloseEditor,
                titleMaxLines = 2
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                ArchiveActionsRow {
                    ArchiveAction(
                        if (saving) "保存中…" else "保存",
                        callbacks.onSaveEditor,
                        !saving,
                        "memory_editor_save"
                    )
                    ArchiveAction("取消", callbacks.onCloseEditor, !saving)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("memory_editor_list"),
            contentPadding = PaddingValues(NarraUi.space16),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space12)
        ) {
            item {
                if (editor.id.isNotBlank()) Text(
                    "ID：${editor.id} · 基于版本 ${editor.expectedRevision}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "正文作为一条完整记忆保存，可以包含多段。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (editor.id.isBlank()) item {
                when (editor.kind) {
                    MemoryMaterialKind.RECORD -> {
                        ArchiveChoices(
                            MemoryCategory.entries,
                            { it.name },
                            { it.label() },
                            editor.category.name,
                            {
                                callbacks.onUpdateEditor(
                                    editor.copy(
                                        category = MemoryCategory.valueOf(
                                            it
                                        )
                                    )
                                )
                            },
                            "memory_editor_category"
                        )
                        ArchiveChoices(
                            MemoryRecordKind.entries, { it.name }, {
                            when (it) {
                                MemoryRecordKind.EPISODE -> "经历"; MemoryRecordKind.FACT -> "事实原料"; MemoryRecordKind.OPINION -> "主观看法"; MemoryRecordKind.WISH -> "愿望"; MemoryRecordKind.LEGACY_UNCLASSIFIED -> "未分类"
                            }
                        }, editor.recordKind.name,
                            {
                                callbacks.onUpdateEditor(
                                    editor.copy(
                                        recordKind = MemoryRecordKind.valueOf(
                                            it
                                        )
                                    )
                                )
                            }, "memory_editor_kind"
                        )
                    }

                    MemoryMaterialKind.RESIDENT -> ArchiveChoices(
                        MemoryResidentCategory.entries,
                        { it.name },
                        { it.label() },
                        editor.residentCategory.name,
                        {
                            callbacks.onUpdateEditor(
                                editor.copy(
                                    residentCategory = MemoryResidentCategory.valueOf(
                                        it
                                    )
                                )
                            )
                        },
                        "memory_editor_resident"
                    )

                    MemoryMaterialKind.STATE -> ArchiveChoices(
                        MemoryStateKey.entries,
                        { it.name },
                        { it.label },
                        editor.stateKey.name,
                        { callbacks.onUpdateEditor(editor.copy(stateKey = MemoryStateKey.valueOf(it))) },
                        "memory_editor_state"
                    )

                    else -> Unit
                }
            }
            item {
                OutlinedTextField(
                    editor.content,
                    { callbacks.onUpdateEditor(editor.copy(content = it, error = null)) },
                    Modifier.fillMaxWidth().testTag("memory_editor_content"),
                    enabled = !saving,
                    minLines = 5,
                    label = { Text("完整正文") })
                if (editor.kind == MemoryMaterialKind.RESIDENT) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        editor.subjective,
                        { callbacks.onUpdateEditor(editor.copy(subjective = it)) },
                        enabled = !saving,
                        modifier = Modifier.sizeIn(
                            minWidth = NarraMinimumTouchTarget,
                            minHeight = NarraMinimumTouchTarget
                        )
                    )
                    Text("主观看法或关系感受")
                }
                editor.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("memory_editor_error")
                    )
                }
            }
        }
    }
}
