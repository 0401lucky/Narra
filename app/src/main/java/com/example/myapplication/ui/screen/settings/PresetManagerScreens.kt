package com.example.myapplication.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.repository.context.TavernPresetAdapter
import com.example.myapplication.model.DEFAULT_PRESET_ID
import com.example.myapplication.model.Preset
import com.example.myapplication.model.PresetPromptEntry
import com.example.myapplication.model.PresetPromptEntryKind
import com.example.myapplication.model.PresetPromptInjectionPosition
import com.example.myapplication.model.PresetPromptRole
import com.example.myapplication.ui.component.NarraFilledTonalButton
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraOutlinedButton
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.system.security.SensitiveTextRedactor
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

@Composable
fun PresetListScreen(
    presets: List<Preset>,
    defaultPresetId: String,
    onOpenPreset: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onCopyPreset: (Preset) -> Unit,
    onImportPreset: (Preset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = rememberSettingsPalette()
    val tavernPresetAdapter = remember { TavernPresetAdapter() }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Preset?>(null) }
    var pendingImportPreset by remember { mutableStateOf<Preset?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var pendingExportPreset by remember { mutableStateOf<Preset?>(null) }
    val normalizedPresets = remember(presets) {
        presets.map(Preset::normalized).sortedWith(compareBy<Preset> { !it.builtIn }.thenBy { it.name })
    }
    val filteredPresets = normalizedPresets.filter { preset ->
        val query = searchQuery.trim()
        query.isBlank() ||
            preset.name.contains(query, ignoreCase = true) ||
            preset.description.contains(query, ignoreCase = true)
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val rawJson = readTextFromUri(context, uri)
                tavernPresetAdapter.decodeAsBundle(
                    rawJson = rawJson,
                    fileName = resolveDisplayName(context, uri),
                )?.presets?.singleOrNull()
                    ?: error("未识别到 SillyTavern Chat 预设")
            }.onSuccess { preset ->
                pendingImportPreset = preset
            }.onFailure { throwable ->
                importError = SensitiveTextRedactor.throwableMessageForUi(
                    throwable = throwable,
                    fallback = "导入失败",
                )
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val preset = pendingExportPreset
        pendingExportPreset = null
        if (uri == null || preset == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                writeTextToUri(
                    context = context,
                    uri = uri,
                    text = tavernPresetAdapter.encodePreset(preset),
                )
            }.onFailure { throwable ->
                importError = SensitiveTextRedactor.throwableMessageForUi(
                    throwable = throwable,
                    fallback = "导出失败",
                )
            }
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "预设管理",
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = palette.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("preset_list")
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                top = 4.dp,
                end = SettingsScreenPadding,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SettingsPageIntro(
                    title = "提示词预设",
                    summary = "内置预设只读；复制或导入后才能编辑条目、开关和发送顺序。",
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NarraOutlinedButton(
                        onClick = { importLauncher.launch(PRESET_IMPORT_MIME_TYPES) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Lucide.Upload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("导入酒馆预设")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Lucide.Search, contentDescription = null) },
                    label = { Text("搜索预设") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = rememberSettingsOutlineColors(),
                )
            }
            if (filteredPresets.isEmpty()) {
                item {
                        SettingsPlaceholderRow(
                            title = if (searchQuery.isBlank()) "暂无预设" else "没有匹配的预设",
                            subtitle = "",
                        )
                }
            } else {
                items(filteredPresets, key = { it.id }) { preset ->
                            PresetListRow(
                                preset = preset,
                                isDefault = preset.id == defaultPresetId,
                                onOpen = { onOpenPreset(preset.id) },
                                onSetDefault = { onSetDefault(preset.id) },
                                onCopy = { onCopyPreset(preset) },
                                onExport = {
                                    pendingExportPreset = preset
                                    exportLauncher.launch(buildTavernPresetExportFileName(preset))
                                },
                                onDelete = {
                                    if (!preset.builtIn) {
                                        pendingDelete = preset
                                    }
                                },
                            )
                }
            }
        }
    }

    pendingDelete?.let { preset ->
        NarraAlertDialog(
            onDismiss = { pendingDelete = null },
            onConfirm = { onDeletePreset(preset.id); pendingDelete = null },
            title = "删除预设",
            message = "将删除「${preset.name}」，这个操作无法撤销。内置预设不会受影响。",
            confirmLabel = "确认删除",
            dismissLabel = "取消",
            isDestructive = true,
        )
    }

    pendingImportPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingImportPreset = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        onImportPreset(preset)
                        pendingImportPreset = null
                    },
                ) {
                    Text("确认导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportPreset = null }) {
                    Text("取消")
                }
            },
            title = { Text("导入酒馆预设") },
            text = { Text(buildTavernPresetPreviewText(preset), modifier = Modifier.verticalScroll(rememberScrollState())) },
            containerColor = palette.surface,
            shape = RoundedCornerShape(8.dp),
        )
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            confirmButton = {
                TextButton(onClick = { importError = null }) {
                    Text("知道了")
                }
            },
            title = { Text("预设导入导出") },
            text = { Text(message, modifier = Modifier.verticalScroll(rememberScrollState())) },
            containerColor = palette.surface,
            shape = RoundedCornerShape(8.dp),
        )
    }
}

@Composable
internal fun PresetListRow(
    preset: Preset,
    isDefault: Boolean,
    onOpen: () -> Unit,
    onSetDefault: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
    ) {
    Column {
    SettingsListRow(
        leadingContent = {
            Icon(
                imageVector = if (preset.builtIn) Lucide.Lock else Lucide.SlidersHorizontal,
                contentDescription = null,
                tint = palette.title,
            )
        },
        title = preset.name.ifBlank { "未命名预设" },
        supportingText = buildString {
            append(if (preset.builtIn) "内置只读" else "我的预设")
            append(" · ${preset.entries.size} 个条目")
        },
        onClick = onOpen,
        trailingContent = {
            Box {
                NarraIconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Lucide.EllipsisVertical, contentDescription = "${preset.name}：更多操作", modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("复制预设") },
                        leadingIcon = { Icon(Lucide.Copy, contentDescription = null) },
                        onClick = { menuExpanded = false; onCopy() },
                    )
                    DropdownMenuItem(
                        text = { Text("导出酒馆 JSON") },
                        leadingIcon = { Icon(Lucide.Download, contentDescription = null) },
                        onClick = { menuExpanded = false; onExport() },
                    )
                    if (!preset.builtIn) {
                        DropdownMenuItem(
                            text = { Text("删除预设", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Lucide.Trash2, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
        },
    )
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isDefault) "当前全局默认" else "",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = palette.accent,
            )
            if (!isDefault) {
                TextButton(onClick = onSetDefault, modifier = Modifier.heightIn(min = 48.dp)) { Text("设为默认") }
            }
        }
    }
    }
}

@Composable
fun PresetEditScreen(
    preset: Preset,
    isGlobalDefault: Boolean,
    onSavePreset: (Preset) -> Unit,
    onCopyPreset: (Preset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val readOnly = preset.builtIn
    var draft by remember(preset.id) { mutableStateOf(preset.normalized()) }
    var searchQuery by rememberSaveable(preset.id) { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf(false) }
    val orderedEntries = draft.entries.sortedBy(PresetPromptEntry::order)
    val visibleEntries = orderedEntries.filter { entry ->
        val query = searchQuery.trim()
        query.isBlank() ||
            entry.title.contains(query, ignoreCase = true) ||
            entry.content.contains(query, ignoreCase = true) ||
            entry.kind.label.contains(query, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "预设详情",
                subtitle = if (readOnly) "内置只读" else "我的预设",
                onNavigateBack = onNavigateBack,
                actionLabel = if (readOnly) "复制" else "保存",
                onAction = {
                    if (readOnly) {
                        onCopyPreset(draft)
                    } else {
                        onSavePreset(draft.normalized().copy(builtIn = false, userModified = true, updatedAt = System.currentTimeMillis()))
                    }
                },
            )
        },
        containerColor = palette.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("preset_editor")
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                top = 4.dp,
                end = SettingsScreenPadding,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SettingsPageIntro(
                    title = if (readOnly) "只读内置预设" else "编辑我的预设",
                    summary = if (readOnly) {
                        "可以查看条目和最终顺序；需要修改时请先复制为我的预设。"
                    } else {
                        "条目顺序决定最终发送顺序，近期记录插入点后面的条目会作为后置指令发送。"
                    },
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetTextFieldRow(
                        label = "名称",
                        value = draft.name,
                        enabled = !readOnly,
                        onValueChange = { value -> draft = draft.copy(name = value) },
                    )
                    PresetTextFieldRow(
                        label = "说明",
                        value = draft.description,
                        enabled = !readOnly,
                        minLines = 2,
                        onValueChange = { value -> draft = draft.copy(description = value) },
                    )
                    SettingsListRow(
                        title = "全局默认",
                        supportingText = if (isGlobalDefault) "当前全局默认预设" else "设为新角色继承的默认预设",
                        trailingContent = {
                            if (isGlobalDefault) {
                                SettingsStatusPill("默认", palette.accentSoft, palette.accent)
                            } else {
                                TextButton(onClick = { onSetDefault(draft.id) }) {
                                    Text("设默认")
                                }
                            }
                        },
                        showArrow = false,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Lucide.Search, contentDescription = null) },
                        label = { Text("搜索条目") },
                        singleLine = true,
                        colors = rememberSettingsOutlineColors(),
                        shape = RoundedCornerShape(8.dp),
                    )
                    NarraIconButton(
                        onClick = {
                            if (!readOnly) {
                                draft = draft.copy(
                                    entries = draft.entries + PresetPromptEntry(
                                        title = "新条目",
                                        kind = PresetPromptEntryKind.CUSTOM,
                                        role = PresetPromptRole.SYSTEM,
                                        order = (draft.entries.maxOfOrNull(PresetPromptEntry::order) ?: 0) + 10,
                                    ),
                                )
                            }
                        },
                        enabled = !readOnly,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Lucide.Plus, contentDescription = "新增预设条目", modifier = Modifier.size(24.dp))
                    }
                }
            }

            if (visibleEntries.isEmpty()) {
                item {
                    Text("没有匹配的条目", style = MaterialTheme.typography.bodyMedium, color = palette.body)
                }
            }
            items(visibleEntries, key = { it.id }) { entry ->
                        PresetEntryCard(
                            entry = entry,
                            readOnly = readOnly,
                            canMoveUp = orderedEntries.indexOfFirst { it.id == entry.id } > 0,
                            canMoveDown = orderedEntries.indexOfFirst { it.id == entry.id } in 0 until orderedEntries.lastIndex,
                            onEntryChange = { updated ->
                                draft = draft.copy(entries = draft.entries.map { if (it.id == updated.id) updated else it })
                            },
                            onDuplicate = {
                                draft = draft.copy(
                                    entries = draft.entries + entry.copy(
                                        id = UUID.randomUUID().toString(),
                                        title = "${entry.title} 副本",
                                        locked = false,
                                        order = (draft.entries.maxOfOrNull(PresetPromptEntry::order) ?: 0) + 10,
                                    ),
                                )
                            },
                            onDelete = {
                                if (!entry.locked) {
                                    draft = draft.copy(entries = draft.entries.filterNot { it.id == entry.id })
                                }
                            },
                            onMove = { direction ->
                                draft = draft.copy(entries = moveEntry(draft.entries, entry.id, direction))
                            },
                        )
            }

            item {
                PresetPreviewCard(entries = orderedEntries)
            }

            if (!readOnly) {
                item {
                    NarraOutlinedButton(
                        onClick = { pendingDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Lucide.Trash2, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("删除这个预设")
                    }
                }
            }
        }
    }

    if (pendingDelete) {
        NarraAlertDialog(
            onDismiss = { pendingDelete = false },
            onConfirm = { onDeletePreset(draft.id); pendingDelete = false },
            title = "删除预设",
            message = "将删除「${draft.name}」，这个操作无法撤销。内置预设不会受影响。",
            confirmLabel = "确认删除",
            dismissLabel = "取消",
            isDestructive = true,
        )
    }
}

@Composable
private fun PresetTextFieldRow(
    label: String,
    value: String,
    enabled: Boolean,
    minLines: Int = 1,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            minLines = minLines,
            label = { Text(label) },
            colors = rememberSettingsOutlineColors(),
            shape = RoundedCornerShape(8.dp),
        )
    }
}

@Composable
internal fun PresetEntryCard(
    entry: PresetPromptEntry,
    readOnly: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEntryChange: (PresetPromptEntry) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val palette = rememberSettingsPalette()
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable(entry.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("preset_entry_${entry.id}"),
        shape = RoundedCornerShape(8.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = entry.title.ifBlank { entry.kind.label },
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${entry.role.label} · ${if (entry.enabled) "已启用" else "已关闭"}" + if (entry.locked) " · 锁定名称" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.body,
                    )
                }
                Switch(
                    checked = entry.enabled,
                    enabled = !readOnly,
                    onCheckedChange = { checked -> onEntryChange(entry.copy(enabled = checked)) },
                    modifier = Modifier.testTag("preset_entry_enabled_${entry.id}"),
                )
            }
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(if (expanded) "收起条目" else "查看条目", modifier = Modifier.weight(1f))
                Icon(if (expanded) Lucide.ChevronUp else Lucide.ChevronDown, contentDescription = null)
            }
            if (expanded) {
            OutlinedTextField(
                value = entry.title,
                onValueChange = { onEntryChange(entry.copy(title = it)) },
                enabled = !readOnly && !entry.locked,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                colors = rememberSettingsOutlineColors(),
                shape = RoundedCornerShape(8.dp),
            )
            SettingsSectionHeader(
                "消息身份",
                buildList {
                    add("${entry.kind.label} · 顺序 ${entry.order}")
                    buildPresetEntryCompatLine(entry).takeIf { it.isNotBlank() }?.let { add(it) }
                    if (entry.locked) add("名称已锁定，不能删除此条目；仍可调整内容、启用状态和顺序。")
                }.joinToString("\n"),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PresetPromptRole.entries.forEach { role ->
                    FilterChip(
                        selected = entry.role == role,
                        onClick = { onEntryChange(entry.copy(role = role)) },
                        enabled = !readOnly,
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text(role.label) },
                    )
                }
            }

            OutlinedTextField(
                value = entry.content,
                onValueChange = { onEntryChange(entry.copy(content = it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !readOnly,
                label = { Text("内容模板") },
                minLines = 4,
                colors = rememberSettingsOutlineColors(),
                shape = RoundedCornerShape(8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NarraIconButton(
                        onClick = { onMove(-1) },
                        enabled = !readOnly && canMoveUp,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Lucide.ArrowUp, contentDescription = "上移")
                    }
                    NarraIconButton(
                        onClick = { onMove(1) },
                        enabled = !readOnly && canMoveDown,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Lucide.ArrowDown, contentDescription = "下移")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NarraIconButton(
                        onClick = onDuplicate,
                        enabled = !readOnly,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Lucide.Copy, contentDescription = "复制条目")
                    }
                    NarraIconButton(
                        onClick = { showDeleteConfirmation = true },
                        enabled = !readOnly && !entry.locked,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Lucide.Trash2, contentDescription = "删除条目", tint = if (readOnly || entry.locked) palette.body else MaterialTheme.colorScheme.error)
                    }
                }
            }
            }
        }
    }
    if (showDeleteConfirmation) {
        NarraAlertDialog(
            title = "删除条目",
            message = "将从当前预设移除「${entry.title}」，保存预设后生效。",
            confirmLabel = "确认删除",
            dismissLabel = "取消",
            isDestructive = true,
            onDismiss = { showDeleteConfirmation = false },
            onConfirm = { showDeleteConfirmation = false; onDelete() },
        )
    }
}

@Composable
private fun PresetPreviewCard(
    entries: List<PresetPromptEntry>,
) {
    val palette = rememberSettingsPalette()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
            SettingsSectionHeader(
                title = "发送顺序",
                description = "条目按当前顺序发送；近期记录插入点后的条目作为后置指令。关闭的条目不参与发送。",
            )
            entries.sortedBy(PresetPromptEntry::order).forEach { entry ->
                val enabledLabel = if (entry.enabled) "" else "（关闭）"
                val line = if (entry.kind == PresetPromptEntryKind.CHAT_HISTORY) {
                    "近期记录插入点$enabledLabel"
                } else {
                    "${entry.role.label} · ${entry.title.ifBlank { entry.kind.label }}$enabledLabel"
                }
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.enabled) palette.body else palette.body.copy(alpha = 0.54f),
                    fontFamily = if (entry.kind == PresetPromptEntryKind.CHAT_HISTORY) FontFamily.Monospace else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
}

internal fun copyPresetForUser(source: Preset): Preset {
    val now = System.currentTimeMillis()
    val normalizedSource = source.normalized()
    return normalizedSource.copy(
        id = UUID.randomUUID().toString(),
        name = "${normalizedSource.name.ifBlank { "预设" }} 副本",
        builtIn = false,
        userModified = true,
        createdAt = now,
        updatedAt = now,
        entries = normalizedSource.entries.map { entry ->
            entry.copy(
                id = UUID.randomUUID().toString(),
                locked = false,
            )
        },
        renderConfig = normalizedSource.renderConfig,
        compatMetadata = normalizedSource.compatMetadata,
    )
}

private fun moveEntry(
    entries: List<PresetPromptEntry>,
    entryId: String,
    direction: Int,
): List<PresetPromptEntry> {
    val ordered = entries.sortedBy(PresetPromptEntry::order).toMutableList()
    val index = ordered.indexOfFirst { it.id == entryId }
    if (index < 0) return entries
    val targetIndex = (index + direction).coerceIn(0, ordered.lastIndex)
    if (targetIndex == index) return entries
    val item = ordered.removeAt(index)
    ordered.add(targetIndex, item)
    return ordered.mapIndexed { newIndex, entry ->
        entry.copy(order = newIndex * 10)
    }
}

private fun buildPresetEntryCompatLine(entry: PresetPromptEntry): String {
    val parts = buildList {
        entry.sourceIdentifier.takeIf { it.isNotBlank() }?.let { add("来源 $it") }
        if (entry.marker) add("动态槽位")
        if (entry.injectionPosition == PresetPromptInjectionPosition.ABSOLUTE) {
            add("历史深度 ${entry.injectionDepth ?: 4}")
            add("优先级 ${entry.injectionOrder ?: 100}")
        }
        if (entry.injectionTriggers.isNotEmpty()) {
            add("触发 ${entry.injectionTriggers.joinToString("/")}")
        }
    }
    return parts.joinToString(" · ")
}

private fun buildTavernPresetPreviewText(preset: Preset): String {
    val normalized = preset.normalized()
    val absoluteCount = normalized.entries.count {
        it.injectionPosition == PresetPromptInjectionPosition.ABSOLUTE
    }
    val unsupportedTriggerCount = normalized.entries.count { it.injectionTriggers.isNotEmpty() }
    val preservedFieldCount = normalized.entries.count { it.extrasJson.trim() != "{}" } +
        if (normalized.compatMetadata.rootExtrasJson.trim() == "{}") 0 else 1
    return buildString {
        append("名称：")
        append(normalized.name.ifBlank { "未命名预设" })
        append("\n条目：")
        append(normalized.entries.size)
        append(" 个")
        append("\n采样：")
        append(buildSamplerSummary(normalized.sampler))
        append("\n历史内插入：")
        append(absoluteCount)
        append(" 个")
        if (unsupportedTriggerCount > 0) {
            append("\n触发类型：保留 ")
            append(unsupportedTriggerCount)
            append(" 个；首版只在普通主回复运行。")
        }
        if (preservedFieldCount > 0) {
            append("\n兼容字段：已保留 ")
            append(preservedFieldCount)
            append(" 处未知字段，导出时会尽量还原。")
        }
    }
}

private fun buildSamplerSummary(sampler: com.example.myapplication.model.PresetSamplerConfig): String {
    val parts = buildList {
        sampler.temperature?.let { add("temp=$it") }
        sampler.topP?.let { add("top_p=$it") }
        sampler.topK?.let { add("top_k=$it") }
        sampler.minP?.let { add("min_p=$it") }
        sampler.repetitionPenalty?.let { add("rep=$it") }
        sampler.frequencyPenalty?.let { add("freq=$it") }
        sampler.presencePenalty?.let { add("pres=$it") }
        sampler.maxOutputTokens?.let { add("max=$it") }
    }
    return parts.ifEmpty { listOf("沿用模型默认") }.joinToString(" · ")
}

private fun buildTavernPresetExportFileName(preset: Preset): String {
    val safeName = preset.name
        .ifBlank { "narra-preset" }
        .replace(Regex("""[\\/:*?"<>|]+"""), "-")
        .take(48)
        .ifBlank { "narra-preset" }
    return "$safeName.sillytavern.json"
}

private suspend fun readTextFromUri(
    context: Context,
    uri: Uri,
    maxBytes: Long = 4L * 1024L * 1024L,
): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { input ->
        readTextWithLimit(input, maxBytes)
    } ?: error("无法读取导入文件")
}

private suspend fun writeTextToUri(
    context: Context,
    uri: Uri,
    text: String,
) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri)?.use { output ->
        output.write(text.toByteArray(Charsets.UTF_8))
    } ?: error("无法写入导出文件")
}

private fun readTextWithLimit(
    input: InputStream,
    maxBytes: Long,
): String {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = ByteArrayOutputStream()
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        total += read
        if (total > maxBytes) {
            throw IllegalArgumentException("导入文件过大，请选择不超过 ${maxBytes / 1024 / 1024}MB 的文件")
        }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}

private fun resolveDisplayName(
    context: Context,
    uri: Uri,
): String {
    return uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: "import.json"
}

private val PRESET_IMPORT_MIME_TYPES = arrayOf(
    "application/json",
    "text/json",
    "text/plain",
    "*/*",
)
