package com.example.myapplication.ui.screen.settings.contextlog

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.model.CONTEXT_LOG_CAPACITY_MAX
import com.example.myapplication.model.CONTEXT_LOG_CAPACITY_MIN
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.ui.component.ContextLogDetailBody
import com.example.myapplication.ui.component.copyPlainTextToClipboard
import com.example.myapplication.viewmodel.ContextLogViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 上下文日志全屏页面。承载列表 + 详情两级视图，通过内部 state 切换。
 * 长记忆按钮（详情模式右上角）通过 [onOpenMemoryManagement] 上抛给调用方处理导航。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextLogScreen(
    viewModel: ContextLogViewModel,
    contextLogEnabled: Boolean,
    contextLogCapacity: Int,
    onUpdateContextLogEnabled: (Boolean) -> Unit,
    onUpdateContextLogCapacity: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenMemoryManagement: () -> Unit,
) {
    val snapshots by viewModel.snapshots.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSnapshot = selectedId?.let { id -> snapshots.firstOrNull { it.id == id } }

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()

    var pendingExport by remember { mutableStateOf<ContextGovernanceSnapshot?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val target = uri
        val snapshot = pendingExport
        pendingExport = null
        if (target == null || snapshot == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(target)?.use { stream ->
                stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(AppJson.gson.toJson(snapshot))
                }
            }
            Toast.makeText(context, "已导出上下文日志", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(context, "导出失败：${error.message ?: "未知原因"}", Toast.LENGTH_LONG).show()
        }
    }

    BackHandler(enabled = selectedSnapshot != null) {
        selectedId = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("上下文日志") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedSnapshot != null) {
                            selectedId = null
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (selectedSnapshot != null) {
                        IconButton(onClick = onOpenMemoryManagement) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "打开长记忆管理",
                            )
                        }
                        IconButton(
                            enabled = selectedSnapshot.rawDebugDump.isNotBlank(),
                            onClick = {
                                clipboardScope.copyPlainTextToClipboard(
                                    clipboard = clipboard,
                                    label = "context-governance",
                                    text = selectedSnapshot.rawDebugDump,
                                )
                                Toast.makeText(context, "原始上下文已复制", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "复制原文",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (selectedSnapshot == null) {
            ContextLogListView(
                snapshots = snapshots,
                enabled = contextLogEnabled,
                capacity = contextLogCapacity,
                onEnabledChange = onUpdateContextLogEnabled,
                onCapacityChange = onUpdateContextLogCapacity,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onSelect = { selectedId = it.id },
                onDelete = { viewModel.delete(it.id) },
                onExport = { snapshot ->
                    pendingExport = snapshot
                    exportLauncher.launch(suggestedFileName(snapshot))
                },
            )
        } else {
            ContextLogDetailBody(
                snapshot = selectedSnapshot,
                rawDebugDump = selectedSnapshot.rawDebugDump,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun ContextLogListView(
    snapshots: List<ContextGovernanceSnapshot>,
    enabled: Boolean,
    capacity: Int,
    onEnabledChange: (Boolean) -> Unit,
    onCapacityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onSelect: (ContextGovernanceSnapshot) -> Unit,
    onDelete: (ContextGovernanceSnapshot) -> Unit,
    onExport: (ContextGovernanceSnapshot) -> Unit,
) {
    Column(modifier = modifier) {
        ContextLogConfigCard(
            enabled = enabled,
            capacity = capacity,
            onEnabledChange = onEnabledChange,
            onCapacityChange = onCapacityChange,
        )
        if (snapshots.isEmpty()) {
            ContextLogEmptyState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(snapshots, key = { it.id }) { snapshot ->
                    ContextLogListItem(
                        snapshot = snapshot,
                        onClick = { onSelect(snapshot) },
                        onDelete = { onDelete(snapshot) },
                        onExport = { onExport(snapshot) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
        Text(
            text = if (enabled) {
                "每次发送请求到模型时的上下文日志，最多保留 $capacity 条"
            } else {
                "上下文日志已禁用；调整下方开关重新启用"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ContextLogConfigCard(
    enabled: Boolean,
    capacity: Int,
    onEnabledChange: (Boolean) -> Unit,
    onCapacityChange: (Int) -> Unit,
) {
    var capacityDraft by rememberSaveable(capacity) { mutableIntStateOf(capacity) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "启用上下文日志",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "关闭后不再写入新条目；已保存的快照保留以便回看",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "保留条数",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$capacityDraft 条",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = capacityDraft.toFloat(),
                    onValueChange = { raw ->
                        capacityDraft = raw.toInt().coerceIn(CONTEXT_LOG_CAPACITY_MIN, CONTEXT_LOG_CAPACITY_MAX)
                    },
                    onValueChangeFinished = { onCapacityChange(capacityDraft) },
                    valueRange = CONTEXT_LOG_CAPACITY_MIN.toFloat()..CONTEXT_LOG_CAPACITY_MAX.toFloat(),
                    steps = CONTEXT_LOG_CAPACITY_MAX - CONTEXT_LOG_CAPACITY_MIN - 1,
                    enabled = enabled,
                )
            }
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
            .clickable(onClick = onClick)
            .padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = summarizeForList(snapshot),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatLogTimestamp(snapshot.generatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("导出") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onExport()
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun ContextLogEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Box(
                modifier = Modifier.padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        Text(
            text = "暂无上下文日志",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "发送一条消息后再回来这里",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun summarizeForList(snapshot: ContextGovernanceSnapshot): String {
    val source = snapshot.rawDebugDump.ifBlank {
        snapshot.contextSections.joinToString("\n") { it.content }
    }
    val firstLine = source.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
    return firstLine ?: "(空 prompt)"
}

private fun formatLogTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "M月d日 HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

private fun suggestedFileName(snapshot: ContextGovernanceSnapshot): String {
    val timestamp = if (snapshot.generatedAt > 0L) {
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date(snapshot.generatedAt))
    } else {
        "snapshot"
    }
    return "context-log-$timestamp.json"
}
