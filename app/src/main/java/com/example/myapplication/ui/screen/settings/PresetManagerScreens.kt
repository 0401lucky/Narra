package com.example.myapplication.ui.screen.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.DEFAULT_PRESET_ID
import com.example.myapplication.model.Preset
import com.example.myapplication.model.PresetPromptEntry
import com.example.myapplication.model.PresetPromptEntryKind
import com.example.myapplication.model.PresetPromptRole
import com.example.myapplication.ui.component.NarraFilledTonalButton
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraOutlinedButton
import java.util.UUID

@Composable
fun PresetListScreen(
    presets: List<Preset>,
    defaultPresetId: String,
    onOpenPreset: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onCopyPreset: (Preset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Preset?>(null) }
    val normalizedPresets = remember(presets) {
        presets.map(Preset::normalized).sortedWith(compareBy<Preset> { !it.builtIn }.thenBy { it.name })
    }
    val filteredPresets = normalizedPresets.filter { preset ->
        val query = searchQuery.trim()
        query.isBlank() ||
            preset.name.contains(query, ignoreCase = true) ||
            preset.description.contains(query, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "预设管理",
                subtitle = "Prompt Manager",
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = palette.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
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
                    title = "真预设",
                    summary = "内置预设只读；复制或导入后才能编辑条目、开关、顺序和状态卡规则。",
                )
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("搜索预设") },
                    singleLine = true,
                )
            }
            item {
                SettingsGroup {
                    if (filteredPresets.isEmpty()) {
                        SettingsPlaceholderRow(
                            title = "暂无预设",
                            subtitle = "内置预设会在启动时自动补齐。",
                        )
                    } else {
                        filteredPresets.forEachIndexed { index, preset ->
                            PresetListRow(
                                preset = preset,
                                isDefault = preset.id == defaultPresetId,
                                onOpen = { onOpenPreset(preset.id) },
                                onSetDefault = { onSetDefault(preset.id) },
                                onCopy = { onCopyPreset(preset) },
                                onDelete = {
                                    if (!preset.builtIn) {
                                        pendingDelete = preset
                                    }
                                },
                            )
                            if (index != filteredPresets.lastIndex) {
                                SettingsGroupDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePreset(preset.id)
                        pendingDelete = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
            title = { Text("删除预设") },
            text = { Text("将删除「${preset.name}」。内置预设不会受影响。") },
        )
    }
}

@Composable
private fun PresetListRow(
    preset: Preset,
    isDefault: Boolean,
    onOpen: () -> Unit,
    onSetDefault: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    SettingsListRow(
        leadingContent = {
            Icon(
                imageVector = if (preset.builtIn) Icons.Default.Lock else Icons.Default.Tune,
                contentDescription = null,
                tint = palette.title,
            )
        },
        title = preset.name.ifBlank { "未命名预设" },
        supportingText = buildString {
            append(if (preset.builtIn) "内置只读" else "我的预设")
            append(" · ${preset.entries.size} 个条目")
            if (preset.renderConfig.statusCardsEnabled) append(" · 状态卡开启")
        },
        onClick = onOpen,
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isDefault) {
                    SettingsStatusPill(
                        text = "默认",
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                    )
                } else {
                    TextButton(onClick = onSetDefault) {
                        Text("设默认")
                    }
                }
                NarraIconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制预设", modifier = Modifier.size(17.dp))
                }
                if (!preset.builtIn) {
                    NarraIconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "删除预设", modifier = Modifier.size(17.dp))
                    }
                }
            }
        },
    )
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
                title = draft.name.ifBlank { "预设" },
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
                        "可以查看条目、最终顺序和状态卡规则；需要修改时请先复制为我的预设。"
                    } else {
                        "条目顺序决定最终发送顺序，Chat History 后面的条目会作为后置指令发送。"
                    },
                )
            }

            item {
                SettingsGroup {
                    PresetTextFieldRow(
                        label = "名称",
                        value = draft.name,
                        enabled = !readOnly,
                        onValueChange = { value -> draft = draft.copy(name = value) },
                    )
                    SettingsGroupDivider()
                    PresetTextFieldRow(
                        label = "说明",
                        value = draft.description,
                        enabled = !readOnly,
                        minLines = 2,
                        onValueChange = { value -> draft = draft.copy(description = value) },
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        leadingContent = { Icon(Icons.Default.Tune, null, tint = palette.title) },
                        title = "状态卡识别",
                        supportingText = "识别 status / 状态栏块并渲染为安全卡片",
                        enabled = !readOnly,
                        trailingContent = {
                            Switch(
                                checked = draft.renderConfig.statusCardsEnabled,
                                enabled = !readOnly,
                                onCheckedChange = { checked ->
                                    draft = draft.copy(renderConfig = draft.renderConfig.copy(statusCardsEnabled = checked))
                                },
                            )
                        },
                    )
                    SettingsGroupDivider()
                    SettingsListRow(
                        title = "隐藏原始状态块",
                        supportingText = "开启后正文气泡只显示状态卡，避免状态栏重复刷屏",
                        enabled = !readOnly && draft.renderConfig.statusCardsEnabled,
                        trailingContent = {
                            Switch(
                                checked = draft.renderConfig.hideStatusBlocksInBubble,
                                enabled = !readOnly && draft.renderConfig.statusCardsEnabled,
                                onCheckedChange = { checked ->
                                    draft = draft.copy(renderConfig = draft.renderConfig.copy(hideStatusBlocksInBubble = checked))
                                },
                            )
                        },
                    )
                    SettingsGroupDivider()
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
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("搜索条目") },
                        singleLine = true,
                    )
                    NarraFilledTonalButton(
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
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    visibleEntries.forEach { entry ->
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
                }
            }

            item {
                PresetPreviewCard(entries = orderedEntries)
            }

            if (!readOnly) {
                item {
                    NarraOutlinedButton(
                        onClick = { pendingDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("删除这个预设")
                    }
                }
            }
        }
    }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePreset(draft.id)
                        pendingDelete = false
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = false }) {
                    Text("取消")
                }
            },
            title = { Text("删除预设") },
            text = { Text("删除后不会影响内置默认预设。") },
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
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            minLines = minLines,
        )
    }
}

@Composable
private fun PresetEntryCard(
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = palette.elevatedSurface,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = entry.title,
                        onValueChange = { onEntryChange(entry.copy(title = it)) },
                        enabled = !readOnly && !entry.locked,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("名称") },
                        singleLine = true,
                    )
                    Text(
                        text = "${entry.kind.label} · ${entry.role.label} · order ${entry.order}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.body,
                    )
                }
                Switch(
                    checked = entry.enabled,
                    enabled = !readOnly,
                    onCheckedChange = { checked -> onEntryChange(entry.copy(enabled = checked)) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PresetPromptRole.entries.forEach { role ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (entry.role == role) palette.accentSoft else palette.surfaceTint,
                        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.36f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(enabled = !readOnly) { onEntryChange(entry.copy(role = role)) },
                    ) {
                        Text(
                            text = role.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (entry.role == role) palette.accent else palette.body,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = entry.content,
                onValueChange = { onEntryChange(entry.copy(content = it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !readOnly,
                label = { Text("内容模板") },
                minLines = 4,
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
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                    }
                    NarraIconButton(
                        onClick = { onMove(1) },
                        enabled = !readOnly && canMoveDown,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NarraIconButton(
                        onClick = onDuplicate,
                        enabled = !readOnly,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制条目")
                    }
                    NarraIconButton(
                        onClick = onDelete,
                        enabled = !readOnly && !entry.locked,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "删除条目")
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetPreviewCard(
    entries: List<PresetPromptEntry>,
) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "最终渲染顺序",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.title,
            )
            entries.sortedBy(PresetPromptEntry::order).forEach { entry ->
                val enabledLabel = if (entry.enabled) "" else "（关闭）"
                val line = if (entry.kind == PresetPromptEntryKind.CHAT_HISTORY) {
                    "──── Chat History 插入点 ────"
                } else {
                    "${entry.role.label} · ${entry.title.ifBlank { entry.kind.label }}$enabledLabel"
                }
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.enabled) palette.body else palette.body.copy(alpha = 0.54f),
                    fontFamily = if (entry.kind == PresetPromptEntryKind.CHAT_HISTORY) FontFamily.Monospace else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
