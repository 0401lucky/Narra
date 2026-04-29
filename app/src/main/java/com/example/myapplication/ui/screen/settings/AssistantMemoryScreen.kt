package com.example.myapplication.ui.screen.settings

import com.example.myapplication.ui.component.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AssistantMemorySection(val label: String) {
    CORE("核心记忆"),
    SCENE("情景记忆"),
    SUMMARY("剧情摘要"),
    TIMELINE("关系时间线"),
}

private sealed interface AssistantMemoryTimelineItem {
    val stableKey: String
    val timestamp: Long

    data class Memory(
        val entry: MemoryEntry,
    ) : AssistantMemoryTimelineItem {
        override val stableKey: String = "memory:${entry.id}"
        override val timestamp: Long = entry.updatedAt.takeIf { it > 0L } ?: entry.createdAt
    }

    data class Summary(
        val summary: ConversationSummary,
    ) : AssistantMemoryTimelineItem {
        override val stableKey: String = "summary:${summary.conversationId}"
        override val timestamp: Long = summary.updatedAt
    }
}

@Composable
fun AssistantMemoryScreen(
    assistant: Assistant,
    memories: List<MemoryEntry>,
    summaries: List<ConversationSummary>,
    onSaveAssistant: (Assistant) -> Unit,
    onUpsertMemory: (MemoryEntry) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onTogglePinned: (String) -> Unit,
    onOpenGlobalMemorySettings: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val outlineColors = rememberSettingsOutlineColors()
    var memoryEnabled by rememberSaveable { mutableStateOf(assistant.memoryEnabled) }
    var useGlobalMemory by rememberSaveable { mutableStateOf(assistant.useGlobalMemory) }
    var memoryMaxItemsText by rememberSaveable { mutableStateOf(assistant.memoryMaxItems.toString()) }
    var editingMemoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var isCreatingMemory by rememberSaveable { mutableStateOf(false) }
    var memoryDraftContent by rememberSaveable { mutableStateOf("") }
    var selectedSectionName by rememberSaveable { mutableStateOf(AssistantMemorySection.CORE.name) }
    val selectedSection = runCatching {
        AssistantMemorySection.valueOf(selectedSectionName)
    }.getOrDefault(AssistantMemorySection.CORE)
    val coreMemories = remember(memories) {
        memories
            .filter { it.scopeType != MemoryScopeType.CONVERSATION }
            .sortedForAssistantMemoryDisplay()
    }
    val sceneMemories = remember(memories) {
        memories
            .filter { it.scopeType == MemoryScopeType.CONVERSATION }
            .sortedForAssistantMemoryDisplay()
    }
    val sortedSummaries = remember(summaries) {
        summaries.sortedByDescending { it.updatedAt }
    }
    val timelineItems = remember(coreMemories, sceneMemories, sortedSummaries) {
        buildList {
            addAll(coreMemories.map(AssistantMemoryTimelineItem::Memory))
            addAll(sceneMemories.map(AssistantMemoryTimelineItem::Memory))
            addAll(sortedSummaries.map(AssistantMemoryTimelineItem::Summary))
        }.sortedByDescending { item -> item.timestamp }
    }

    // 编辑对话框展示的 MemoryEntry：新建时按当前作用域拼装一个临时实例，编辑时按 id 从列表解析。
    // 旋转后由 id + isCreatingMemory 重新推导，确保与 memoryDraftContent 的 rememberSaveable 生命周期一致。
    val editingMemory: MemoryEntry? = when {
        isCreatingMemory -> MemoryEntry(
            scopeType = if (useGlobalMemory) MemoryScopeType.GLOBAL else MemoryScopeType.ASSISTANT,
            scopeId = if (useGlobalMemory) "" else assistant.id,
            characterId = assistant.id,
            pinned = true,
            importance = 80,
        )
        editingMemoryId != null -> memories.firstOrNull { it.id == editingMemoryId }
        else -> null
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "记忆",
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
                AssistantSubPageHeader(
                    assistant = assistant,
                    overline = "记忆档案",
                )
            }

            item {
                AssistantMemoryArchiveOverviewCard(
                    assistant = assistant,
                    memoryEnabled = memoryEnabled,
                    useGlobalMemory = useGlobalMemory,
                    memoryMaxItemsText = memoryMaxItemsText,
                    coreMemoryCount = coreMemories.size,
                    sceneMemoryCount = sceneMemories.size,
                    summaryCount = sortedSummaries.size,
                )
            }

            item {
                SettingsGroup {
                    AssistantMemorySettingRow(
                        title = "记忆",
                        checked = memoryEnabled,
                        onCheckedChange = { memoryEnabled = it },
                    )
                    SettingsGroupDivider()
                    AssistantMemorySettingRow(
                        title = "共享全局长期记忆",
                        checked = useGlobalMemory,
                        onCheckedChange = { useGlobalMemory = it },
                    )
                    SettingsGroupDivider()
                    AssistantMemoryStaticRow(
                        title = "每轮注入条数",
                        trailing = {
                            OutlinedTextField(
                                value = memoryMaxItemsText,
                                onValueChange = { memoryMaxItemsText = it.filter(Char::isDigit) },
                                modifier = Modifier.fillMaxWidth(0.34f),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = outlineColors,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        },
                    )
                    SettingsGroupDivider()
                    AssistantMemoryStaticRow(
                        title = "管理记忆",
                        supporting = if (useGlobalMemory) {
                            "全局 ${memories.size} 条"
                        } else {
                            "当前 ${memories.size} 条"
                        },
                        trailing = {
                            Surface(
                                modifier = Modifier.clickable {
                                    isCreatingMemory = true
                                    editingMemoryId = null
                                    memoryDraftContent = ""
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = palette.accentSoft,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = palette.accent)
                                    Text("新增", color = palette.accent)
                                }
                            }
                        },
                    )
                }
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AnimatedSettingButton(
                            text = "保存记忆设置",
                            onClick = {
                                onSaveAssistant(
                                    assistant.copy(
                                        memoryEnabled = memoryEnabled,
                                        useGlobalMemory = useGlobalMemory,
                                        memoryMaxItems = parsePositiveIntOrDefaultForMemory(
                                            rawValue = memoryMaxItemsText,
                                            defaultValue = assistant.memoryMaxItems,
                                        ),
                                    ),
                                )
                                onNavigateBack()
                            },
                            enabled = true,
                            isPrimary = true,
                        )
                        AnimatedSettingButton(
                            text = "打开全局记忆页",
                            onClick = onOpenGlobalMemorySettings,
                            enabled = true,
                            isPrimary = false,
                        )
                    }
                }
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "关系档案",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.title,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AssistantMemorySection.entries.forEach { section ->
                                Surface(
                                    modifier = Modifier.clickable { selectedSectionName = section.name },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selectedSection == section) {
                                        palette.accentSoft
                                    } else {
                                        palette.surfaceTint
                                    },
                                ) {
                                    Text(
                                        text = section.label,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selectedSection == section) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedSection == section) palette.accent else palette.body,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (selectedSection) {
                AssistantMemorySection.CORE -> {
                    if (coreMemories.isEmpty()) {
                        item {
                            SettingsHintCard(
                                title = "还没有核心记忆",
                                body = "这里会记录角色长期记住的关系设定、偏好和重要约定。",
                                containerColor = palette.accentSoft,
                                contentColor = palette.accent,
                            )
                        }
                    } else {
                        item {
                            AssistantMemorySectionHeader("核心记忆")
                        }
                        items(coreMemories, key = { it.id }) { memory ->
                            AssistantMemoryEntryCard(
                                memory = memory,
                                onEdit = {
                                    isCreatingMemory = false
                                    editingMemoryId = memory.id
                                    memoryDraftContent = memory.content
                                },
                                onTogglePinned = { onTogglePinned(memory.id) },
                                onDelete = { onDeleteMemory(memory.id) },
                            )
                        }
                    }
                }
                AssistantMemorySection.SCENE -> {
                    if (sceneMemories.isEmpty()) {
                        item {
                            SettingsHintCard(
                                title = "还没有情景记忆",
                                body = "剧情模式和工具写入的场景状态会沉淀在这里。",
                                containerColor = palette.surfaceTint,
                                contentColor = palette.title,
                            )
                        }
                    } else {
                        item {
                            AssistantMemorySectionHeader("情景记忆")
                        }
                        items(sceneMemories, key = { it.id }) { memory ->
                            AssistantMemoryEntryCard(
                                memory = memory,
                                onEdit = {
                                    isCreatingMemory = false
                                    editingMemoryId = memory.id
                                    memoryDraftContent = memory.content
                                },
                                onTogglePinned = { onTogglePinned(memory.id) },
                                onDelete = { onDeleteMemory(memory.id) },
                            )
                        }
                    }
                }
                AssistantMemorySection.SUMMARY -> {
                    if (sortedSummaries.isEmpty()) {
                        item {
                            SettingsHintCard(
                                title = "还没有剧情摘要",
                                body = "对话自动总结后，会在这里形成可回看的关系脉络。",
                                containerColor = palette.surfaceTint,
                                contentColor = palette.title,
                            )
                        }
                    } else {
                        item {
                            AssistantMemorySectionHeader("剧情摘要")
                        }
                        items(sortedSummaries, key = { it.conversationId }) { summary ->
                            AssistantMemorySummaryCard(summary = summary)
                        }
                    }
                }
                AssistantMemorySection.TIMELINE -> {
                    if (timelineItems.isEmpty()) {
                        item {
                            SettingsHintCard(
                                title = "关系时间线还没有内容",
                                body = "核心记忆、情景记忆和剧情摘要会按时间合并到这里。",
                                containerColor = palette.accentSoft,
                                contentColor = palette.accent,
                            )
                        }
                    } else {
                        items(timelineItems, key = { it.stableKey }) { item ->
                            AssistantMemoryTimelineHeader(
                                label = when (item) {
                                    is AssistantMemoryTimelineItem.Memory -> {
                                        if (item.entry.scopeType == MemoryScopeType.CONVERSATION) "情景记忆" else "核心记忆"
                                    }
                                    is AssistantMemoryTimelineItem.Summary -> "剧情摘要"
                                },
                                timestamp = item.timestamp,
                            )
                            when (item) {
                                is AssistantMemoryTimelineItem.Memory -> {
                                    AssistantMemoryEntryCard(
                                        memory = item.entry,
                                        onEdit = {
                                            isCreatingMemory = false
                                            editingMemoryId = item.entry.id
                                            memoryDraftContent = item.entry.content
                                        },
                                        onTogglePinned = { onTogglePinned(item.entry.id) },
                                        onDelete = { onDeleteMemory(item.entry.id) },
                                    )
                                }
                                is AssistantMemoryTimelineItem.Summary -> {
                                    AssistantMemorySummaryCard(summary = item.summary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingMemory?.let { targetMemory ->
        AlertDialog(
            onDismissRequest = {
                editingMemoryId = null
                isCreatingMemory = false
                memoryDraftContent = ""
            },
            title = { Text(if (isCreatingMemory) "新增记忆" else "编辑记忆") },
            text = {
                OutlinedTextField(
                    value = memoryDraftContent,
                    onValueChange = { memoryDraftContent = it },
                    label = { Text("记忆内容") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6,
                    shape = RoundedCornerShape(16.dp),
                    colors = outlineColors,
                )
            },
            confirmButton = {
                NarraTextButton(
                    onClick = {
                        val content = memoryDraftContent.trim()
                        if (content.isNotBlank()) {
                            onUpsertMemory(
                                targetMemory.copy(
                                    scopeType = if (useGlobalMemory &&
                                        targetMemory.scopeType == MemoryScopeType.ASSISTANT
                                    ) {
                                        MemoryScopeType.GLOBAL
                                    } else {
                                        targetMemory.scopeType
                                    },
                                    scopeId = when {
                                        useGlobalMemory && targetMemory.scopeType != MemoryScopeType.CONVERSATION -> ""
                                        targetMemory.scopeType == MemoryScopeType.ASSISTANT -> assistant.id
                                        else -> targetMemory.scopeId
                                    },
                                    content = content,
                                    importance = targetMemory.importance.takeIf { it > 0 } ?: 80,
                                    pinned = targetMemory.pinned || isCreatingMemory,
                                ),
                            )
                            memoryDraftContent = ""
                            editingMemoryId = null
                            isCreatingMemory = false
                        }
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                NarraTextButton(
                    onClick = {
                        memoryDraftContent = ""
                        editingMemoryId = null
                        isCreatingMemory = false
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun AssistantMemoryArchiveOverviewCard(
    assistant: Assistant,
    memoryEnabled: Boolean,
    useGlobalMemory: Boolean,
    memoryMaxItemsText: String,
    coreMemoryCount: Int,
    sceneMemoryCount: Int,
    summaryCount: Int,
) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.border.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${assistant.name.ifBlank { "角色" }}的记忆档案",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.title,
            )
            Text(
                text = "把长期关系、剧情状态和自动摘要集中在一个地方回看。",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.body,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsStatusPill(
                    text = if (memoryEnabled) "长记忆开启" else "长记忆关闭",
                    containerColor = if (memoryEnabled) palette.accentSoft else palette.surfaceTint,
                    contentColor = if (memoryEnabled) palette.accent else palette.body,
                )
                SettingsStatusPill(
                    text = if (useGlobalMemory) "共享全局记忆" else "角色专属记忆",
                    containerColor = palette.surfaceTint,
                    contentColor = palette.body,
                )
                SettingsStatusPill(
                    text = "注入 ${memoryMaxItemsText.ifBlank { assistant.memoryMaxItems.toString() }} 条",
                    containerColor = palette.surfaceTint,
                    contentColor = palette.body,
                )
                SettingsStatusPill(
                    text = "核心 $coreMemoryCount",
                    containerColor = palette.subtleChip,
                    contentColor = palette.subtleChipContent,
                )
                SettingsStatusPill(
                    text = "情景 $sceneMemoryCount",
                    containerColor = palette.subtleChip,
                    contentColor = palette.subtleChipContent,
                )
                SettingsStatusPill(
                    text = "摘要 $summaryCount",
                    containerColor = palette.subtleChip,
                    contentColor = palette.subtleChipContent,
                )
            }
        }
    }
}

@Composable
private fun AssistantMemorySectionHeader(
    title: String,
) {
    val palette = rememberSettingsPalette()
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = palette.title,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun AssistantMemorySettingRow(
    title: String,
    supporting: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = rememberSettingsPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 84.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.title,
            )
            if (supporting.isNotBlank()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.body,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun AssistantMemoryStaticRow(
    title: String,
    supporting: String = "",
    trailing: @Composable () -> Unit,
) {
    val palette = rememberSettingsPalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.title,
        )
        if (supporting.isNotBlank()) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = palette.body,
            )
        }
        trailing()
    }
}

@Composable
private fun AssistantMemorySummaryCard(
    summary: ConversationSummary,
) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.border.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = summary.summary.ifBlank { "暂无摘要内容" },
                style = MaterialTheme.typography.bodyLarge,
                color = palette.title,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsStatusPill(
                    text = "覆盖 ${summary.coveredMessageCount} 条",
                    containerColor = palette.subtleChip,
                    contentColor = palette.subtleChipContent,
                )
                if (summary.updatedAt > 0L) {
                    SettingsStatusPill(
                        text = formatAssistantMemoryTime(summary.updatedAt),
                        containerColor = palette.surfaceTint,
                        contentColor = palette.body,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMemoryTimelineHeader(
    label: String,
    timestamp: Long,
) {
    val palette = rememberSettingsPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = palette.accent,
        )
        Text(
            text = formatAssistantMemoryTime(timestamp),
            style = MaterialTheme.typography.labelMedium,
            color = palette.body.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun AssistantMemoryEntryCard(
    memory: MemoryEntry,
    onEdit: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.border.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.title,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (memory.pinned) {
                    SettingsStatusPill(
                        text = "置顶",
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                    )
                }
                SettingsStatusPill(
                    text = "重要度 ${memory.importance}",
                    containerColor = palette.surfaceTint,
                    contentColor = palette.body,
                )
                SettingsStatusPill(
                    text = when (memory.scopeType) {
                        MemoryScopeType.GLOBAL -> "全局"
                        MemoryScopeType.ASSISTANT -> "核心"
                        MemoryScopeType.CONVERSATION -> "情景"
                    },
                    containerColor = palette.surfaceTint,
                    contentColor = palette.body,
                )
                if (memory.updatedAt > 0L || memory.createdAt > 0L) {
                    SettingsStatusPill(
                        text = formatAssistantMemoryTime(
                            memory.updatedAt.takeIf { it > 0L } ?: memory.createdAt,
                        ),
                        containerColor = palette.surfaceTint,
                        contentColor = palette.body,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "编辑",
                    color = palette.accent,
                    modifier = Modifier.clickable(onClick = onEdit),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (memory.pinned) "取消置顶" else "置顶",
                    color = palette.accent,
                    modifier = Modifier.clickable(onClick = onTogglePinned),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onDelete),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun parsePositiveIntOrDefaultForMemory(
    rawValue: String,
    defaultValue: Int,
): Int {
    return rawValue.trim().toIntOrNull()?.takeIf { it > 0 } ?: defaultValue
}

private fun List<MemoryEntry>.sortedForAssistantMemoryDisplay(): List<MemoryEntry> {
    return sortedWith(
        compareByDescending<MemoryEntry> { it.pinned }
            .thenByDescending { it.updatedAt.takeIf { timestamp -> timestamp > 0L } ?: it.createdAt }
            .thenByDescending { it.createdAt },
    )
}

private fun formatAssistantMemoryTime(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "未知时间"
    }
    return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
