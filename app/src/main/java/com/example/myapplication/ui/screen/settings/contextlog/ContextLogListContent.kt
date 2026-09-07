package com.example.myapplication.ui.screen.settings.contextlog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.FileDown
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.example.myapplication.model.CONTEXT_LOG_CAPACITY_MAX
import com.example.myapplication.model.CONTEXT_LOG_CAPACITY_MIN
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.screen.settings.SettingsGroup
import com.example.myapplication.ui.screen.settings.SettingsGroupDivider
import com.example.myapplication.ui.screen.settings.SettingsListRow
import com.example.myapplication.ui.screen.settings.SettingsSectionHeader
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraUi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun ContextLogListContent(
    snapshots: List<ContextGovernanceSnapshot>,
    enabled: Boolean,
    capacity: Int,
    onEnabledChange: (Boolean) -> Unit,
    onCapacityChange: (Int) -> Unit,
    modifier: Modifier,
    listState: LazyListState,
    onSelect: (ContextGovernanceSnapshot) -> Unit,
    onDelete: (ContextGovernanceSnapshot) -> Unit,
    onExport: (ContextGovernanceSnapshot) -> Unit,
) {
    LazyColumn(
        modifier = modifier.testTag("context_log_list"),
        state = listState,
        contentPadding = PaddingValues(
            horizontal = NarraUi.pageHorizontal,
            vertical = NarraUi.pageVertical,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "config") {
            ContextLogConfigGroup(
                enabled = enabled,
                capacity = capacity,
                onEnabledChange = onEnabledChange,
                onCapacityChange = onCapacityChange,
            )
        }
        item(key = "saved_header") {
            SettingsSectionHeader(title = "已保存 · ${snapshots.size} 条", description = "")
        }
        if (snapshots.isEmpty()) {
            item(key = "empty") { ContextLogEmptyState(enabled = enabled) }
        } else {
            items(snapshots, key = { "snapshot_${it.id}" }) { snapshot ->
                SettingsGroup {
                    ContextLogListItem(
                        snapshot = snapshot,
                        onClick = { onSelect(snapshot) },
                        onDelete = { onDelete(snapshot) },
                        onExport = { onExport(snapshot) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextLogConfigGroup(
    enabled: Boolean,
    capacity: Int,
    onEnabledChange: (Boolean) -> Unit,
    onCapacityChange: (Int) -> Unit,
) {
    var capacityDraft by rememberSaveable(capacity) {
        mutableIntStateOf(capacity.coerceIn(CONTEXT_LOG_CAPACITY_MIN, CONTEXT_LOG_CAPACITY_MAX))
    }
    SettingsGroup(
        title = "记录设置",
        subtitle = "每次发送请求到模型时记录一条上下文日志。关闭后不再写入新条目，已有快照仍可回看。\n\n" +
            "最多可保留 $CONTEXT_LOG_CAPACITY_MAX 条，超出上限时移除最早的记录；减少保留条数会立即清理超出的旧日志。\n\n" +
            "打开日志可查看上下文分段、复制原文或进入长记忆管理。每条日志的菜单可导出完整快照或删除该条日志。",
    ) {
        SettingsListRow(
            title = "启用上下文日志",
            supportingText = if (enabled) "正在记录新请求" else "已暂停记录，已有日志仍可查看",
            trailingContent = {
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier
                        .heightIn(min = NarraMinimumTouchTarget)
                        .testTag("context_log_enabled"),
                )
            },
        )
        SettingsGroupDivider()
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
        ) {
            Text(
                text = "保留条数 · $capacityDraft 条",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = capacityDraft.toFloat(),
                onValueChange = { raw ->
                    capacityDraft = raw.toInt().coerceIn(CONTEXT_LOG_CAPACITY_MIN, CONTEXT_LOG_CAPACITY_MAX)
                },
                onValueChangeFinished = { onCapacityChange(capacityDraft) },
                valueRange = CONTEXT_LOG_CAPACITY_MIN.toFloat()..CONTEXT_LOG_CAPACITY_MAX.toFloat(),
                steps = CONTEXT_LOG_CAPACITY_MAX - CONTEXT_LOG_CAPACITY_MIN - 1,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = NarraMinimumTouchTarget)
                    .testTag("context_log_capacity"),
            )
            Text(
                text = if (enabled) {
                    "减少条数会清理超出上限的旧日志"
                } else {
                    "启用记录后可调整保留条数"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContextLogListItem(
    snapshot: ContextGovernanceSnapshot,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NarraMinimumTouchTarget)
            .testTag("context_log_${snapshot.id}")
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
        ) {
            Text(
                text = summarizeForList(snapshot),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (snapshot.generatedAt > 0L) {
                Text(
                    text = formatLogTimestamp(snapshot.generatedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            NarraIconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.testTag("context_log_menu_${snapshot.id}"),
            ) {
                Icon(
                    imageVector = Lucide.EllipsisVertical,
                    contentDescription = "日志操作",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("导出") },
                    leadingIcon = { Icon(Lucide.FileDown, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onExport()
                    },
                    modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
                )
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Lucide.Trash2, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                    modifier = Modifier.heightIn(min = NarraMinimumTouchTarget),
                )
            }
        }
    }
}

@Composable
private fun ContextLogEmptyState(enabled: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(NarraUi.smallRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Icon(
                imageVector = Lucide.FileText,
                contentDescription = null,
                modifier = Modifier.padding(16.dp).size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "暂无上下文日志",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (enabled) "发送一条消息后再回来这里" else "启用记录后，新请求会显示在这里",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun summarizeForList(snapshot: ContextGovernanceSnapshot): String {
    val source = snapshot.rawDebugDump.ifBlank {
        snapshot.contextSections.joinToString("\n") { it.content }
    }
    return source.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "空上下文"
}

private fun formatLogTimestamp(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "M月d日 HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}
