package com.example.myapplication.ui.screen.roleplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.model.CompanionHistoryArchiveChoice
import com.example.myapplication.model.CompanionHistoryQuery
import com.example.myapplication.model.CompanionReadingRecord
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.CompanionHistoryUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CompanionHistoryCallbacks(
    val onSetQuery: (CompanionHistoryQuery) -> Unit,
    val onSelectArchive: (CompanionHistoryArchiveChoice?) -> Unit,
    val onLoadOlder: () -> Unit,
    val onToggleSelection: (String) -> Unit,
    val onClearSelection: () -> Unit,
    val onBeginEdit: (Set<String>) -> Unit,
    val onUpdateDraft: (String, String) -> Unit,
    val onCancelEdit: () -> Unit,
    val onSaveEdits: () -> Unit,
    val onDeleteSelected: () -> Unit,
    val onRequestExport: () -> Unit,
    val onRefresh: () -> Unit,
    val onDismiss: () -> Unit,
    val onReadMessage: (String) -> Unit,
    val onOpenReader: () -> Unit,
    val onOpenVoice: () -> Unit,
    val onOpenAppearance: () -> Unit,
)

/** 独立历史管理页；显示窗口与导出的完整仓储范围分别展示。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanionHistoryScreen(
    state: CompanionHistoryUiState,
    characterName: String,
    callbacks: CompanionHistoryCallbacks,
    modifier: Modifier = Modifier,
) {
    var date by rememberSaveable(state.query.date) { mutableStateOf(state.query.date.orEmpty()) }
    var month by rememberSaveable(state.query.month) { mutableStateOf(state.query.month.orEmpty()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val selectedWritable = state.selectedIds.all { id ->
        state.records.firstOrNull { it.message.id == id }?.binding?.readOnly == false
    }
    val editingWritable = state.editingDrafts.keys.all { id ->
        state.records.firstOrNull { it.message.id == id }?.binding?.readOnly == false
    }
    BackHandler {
        when {
            confirmDelete -> confirmDelete = false
            state.editingDrafts.isNotEmpty() -> callbacks.onCancelEdit()
            state.selectedIds.isNotEmpty() -> callbacks.onClearSelection()
            else -> callbacks.onDismiss()
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().testTag("companion_history")
        ) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = NarraUi.space12),
                horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
            ) {
                NarraTextButton(
                    onClick = callbacks.onDismiss,
                    modifier = Modifier.testTag("companion_history_close")
                ) { Text("返回") }
                NarraTextButton(
                    onClick = callbacks.onOpenReader,
                    enabled = state.scope != null
                ) { Text("连续阅读") }
                NarraTextButton(onClick = callbacks.onOpenVoice) { Text("声音库") }
                NarraTextButton(onClick = callbacks.onOpenAppearance) { Text("本地装扮包") }
            }
            if (state.isLoading || state.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("companion_history_list"),
                contentPadding = PaddingValues(NarraUi.space16),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
            ) {
                item(key = "summary") {
                    Column(verticalArrangement = Arrangement.spacedBy(NarraUi.space4)) {
                        Text(
                            if (characterName.isBlank()) "历史与导出" else "$characterName · 历史与导出",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "可读记录共 ${state.totalCount} 条 · 已载入 ${state.records.size} 条",
                            modifier = Modifier.testTag("companion_history_count")
                        )
                        Text(
                            if (state.readOnly) "当前查看只读旧档，当前会话保持不变。" else "查看和导出不会改变当前会话。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                state.errorMessage?.let { message ->
                    item(key = "error") {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("companion_history_error")
                        )
                        NarraTextButton(
                            onClick = callbacks.onRefresh,
                            enabled = !state.isWorking
                        ) { Text("刷新重试") }
                    }
                }
                state.noticeMessage?.let { message ->
                    item(key = "notice") {
                        Text(
                            message,
                            modifier = Modifier.testTag("companion_history_notice")
                        )
                    }
                }
                item(key = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                        OutlinedTextField(
                            value = month, onValueChange = { month = it },
                            label = { Text("年月（YYYY-MM）") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("companion_history_month"),
                        )
                        OutlinedTextField(
                            value = date, onValueChange = { date = it },
                            label = { Text("日期（YYYY-MM-DD）") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("companion_history_date"),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                            NarraTextButton(
                                onClick = {
                                    callbacks.onSetQuery(
                                        state.query.copy(
                                            date = date.trim().ifEmpty { null },
                                            month = month.trim().ifEmpty { null })
                                    )
                                },
                                enabled = !state.isWorking,
                                modifier = Modifier.testTag("companion_history_apply_filter"),
                            ) { Text("按日期查询") }
                            NarraTextButton(onClick = {
                                callbacks.onSetQuery(
                                    CompanionHistoryQuery(
                                        zoneId = state.query.zoneId
                                    )
                                )
                            }, enabled = !state.isWorking) { Text("全部可读记录") }
                            NarraTextButton(
                                onClick = {
                                    callbacks.onSetQuery(
                                        state.query.copy(
                                            encounterId = null,
                                            legacyOnly = true
                                        )
                                    )
                                }, enabled = !state.isWorking,
                                modifier = Modifier.testTag("companion_history_legacy_filter")
                            ) { Text("旧记录，未分场") }
                        }
                        Text(
                            "日期按 ${state.query.zoneId} 分组；同一场跨午夜仍保留原场次。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        NarraTextButton(
                            onClick = callbacks.onRequestExport,
                            enabled = state.scope != null && !state.isLoading && !state.isWorking,
                            modifier = Modifier.fillMaxWidth().testTag("companion_history_export"),
                        ) { Text("导出当前查询全部 TXT（${state.totalCount} 条）") }
                    }
                }
                if (state.archives.isNotEmpty() || state.readOnly) {
                    item(key = "archives") {
                        Text("允许查看的旧档", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                            NarraTextButton(
                                onClick = { callbacks.onSelectArchive(null) },
                                enabled = !state.isWorking
                            ) { Text("回到当前范围") }
                            state.archives.forEach { archive ->
                                NarraTextButton(
                                    onClick = { callbacks.onSelectArchive(archive) },
                                    enabled = !state.isWorking
                                ) { Text(archive.title) }
                            }
                        }
                    }
                }
                items(state.groups, key = { "group:${it.id}" }) { group ->
                    Surface(
                        shape = NarraShapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(NarraUi.space12),
                            verticalArrangement = Arrangement.spacedBy(NarraUi.space4)
                        ) {
                            Text(group.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${group.dateLabels.joinToString(" / ")} · ${group.recordCount} 条",
                                style = MaterialTheme.typography.bodySmall
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                                if (group.encounterId != null) NarraTextButton(onClick = {
                                    callbacks.onSetQuery(
                                        state.query.copy(
                                            encounterId = group.encounterId,
                                            legacyOnly = false
                                        )
                                    )
                                }, enabled = !state.isWorking) { Text("查看本场") }
                                group.firstMessageId?.let { id ->
                                    NarraTextButton(onClick = {
                                        callbacks.onReadMessage(
                                            id
                                        )
                                    }) { Text("定位首条") }
                                }
                            }
                        }
                    }
                }
                if (state.selectedIds.isNotEmpty()) {
                    item(key = "selected") {
                        Text(
                            "已选择 ${state.selectedIds.size} 条",
                            modifier = Modifier.testTag("companion_history_selected")
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                            NarraTextButton(
                                onClick = { callbacks.onBeginEdit(state.selectedIds) },
                                enabled = selectedWritable && !state.readOnly && !state.isWorking,
                                modifier = Modifier.testTag("companion_history_edit_selected")
                            ) { Text("编辑所选") }
                            NarraTextButton(
                                onClick = { confirmDelete = true },
                                enabled = selectedWritable && !state.readOnly && !state.isWorking,
                                modifier = Modifier.testTag("companion_history_delete_selected")
                            ) { Text("删除所选") }
                            NarraTextButton(
                                onClick = callbacks.onClearSelection,
                                enabled = !state.isWorking
                            ) { Text("取消多选") }
                        }
                    }
                }
                items(state.records, key = { "record:${it.message.id}" }) { record ->
                    CompanionHistoryRecordCard(
                        record,
                        state.selectedIds.contains(record.message.id),
                        state.readOnly || record.binding.readOnly,
                        state.isWorking,
                        state.query.zoneId,
                        callbacks
                    )
                }
                item(key = "older") {
                    if (state.hasOlder) NarraTextButton(
                        onClick = callbacks.onLoadOlder,
                        enabled = !state.isLoading && !state.isWorking,
                        modifier = Modifier.fillMaxWidth().testTag("companion_history_load_older")
                    ) { Text("加载更早记录") }
                    else if (!state.isLoading) Text(
                        if (state.records.isEmpty()) "当前查询暂无记录" else "已到最早记录",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    if (state.editingDrafts.isNotEmpty()) {
        NarraAlertDialog(
            title = "编辑 ${state.editingDrafts.size} 条原文",
            modifier = Modifier.imePadding().testTag("companion_history_editor"),
            onDismiss = callbacks.onCancelEdit,
            onConfirm = callbacks.onSaveEdits,
            confirmLabel = "保存编辑",
            confirmEnabled = editingWritable && !state.isWorking && !state.readOnly,
            shape = NarraShapes.large,
            actionContentColor = MaterialTheme.colorScheme.onSurface,
            content = {
                Text("保存会使旧来源及其派生内容失效。独立人工记忆和声音收藏保留。")
                state.editingDrafts.forEach { (id, draft) ->
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { callbacks.onUpdateDraft(id, it) },
                        label = { Text("原文") },
                        modifier = Modifier.fillMaxWidth().testTag("companion_history_edit_$id"),
                        enabled = !state.isWorking,
                    )
                }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            },
        )
    }
    if (confirmDelete) {
        NarraAlertDialog(
            title = "删除所选原文？",
            message = "将删除 ${state.selectedIds.size} 条原文，并使其派生内容失效。独立人工记忆和声音收藏保留，收藏会注明来源已不可用。",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; callbacks.onDeleteSelected() },
            confirmLabel = "删除原文",
            confirmEnabled = selectedWritable && !state.isWorking && !state.readOnly,
            isDestructive = true,
            shape = NarraShapes.large,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanionHistoryRecordCard(
    record: CompanionReadingRecord,
    selected: Boolean,
    readOnly: Boolean,
    busy: Boolean,
    zoneId: String,
    callbacks: CompanionHistoryCallbacks,
) {
    Surface(shape = NarraShapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().padding(NarraUi.space12)
                .testTag("companion_history_record_${record.message.id}"),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space8)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { callbacks.onToggleSelection(record.message.id) },
                    enabled = !busy,
                    modifier = Modifier.testTag("companion_history_select_${record.message.id}")
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        record.binding.title.ifBlank { "陪伴记录" },
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        if (record.message.createdAt == 0L) "时间未知"
                        else DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .format(
                                Instant.ofEpochMilli(record.message.createdAt)
                                    .atZone(ZoneId.of(zoneId))
                            ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        record.message.speakerName.ifBlank { if (record.message.role == com.example.myapplication.model.MessageRole.USER) "我" else "角色" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(record.message.content, style = MaterialTheme.typography.bodyMedium)
            if (!record.readerEligible) Text(
                "回合待完成，原文可管理/导出",
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
                NarraTextButton(
                    onClick = { callbacks.onReadMessage(record.message.id) },
                    enabled = record.readerEligible,
                    modifier = Modifier.testTag("companion_history_locate_${record.message.id}")
                ) { Text("定位阅读") }
                NarraTextButton(
                    onClick = { callbacks.onBeginEdit(setOf(record.message.id)) },
                    enabled = !readOnly && !busy,
                    modifier = Modifier.testTag("companion_history_edit_one_${record.message.id}")
                ) { Text("编辑原文") }
            }
        }
    }
}
