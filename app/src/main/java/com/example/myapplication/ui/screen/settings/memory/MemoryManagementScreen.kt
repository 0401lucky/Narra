package com.example.myapplication.ui.screen.settings.memory

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.TopAppSnackbarHost
import com.example.myapplication.ui.screen.settings.SettingsHintCard
import com.example.myapplication.ui.screen.settings.SettingsPageIntro
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsStatusPill
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsOutlineColors
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette
import com.example.myapplication.ui.screen.settings.rememberSettingsSnackbarHostState
import com.example.myapplication.viewmodel.MemoryManagementUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val AllAssistantFilterKey = "__all__"

private enum class MemoryViewerSection(val label: String) {
    ALL("全部"),
    CORE("核心记忆"),
    SCENE("情景记忆"),
    SUMMARY("剧情总结"),
    TIMELINE("时间线"),
}

private data class MemoryViewerRoleOption(
    val id: String,
    val label: String,
)

private sealed interface MemoryViewerTimelineItem {
    val stableKey: String
    val timestamp: Long

    data class Memory(
        val entry: MemoryEntry,
    ) : MemoryViewerTimelineItem {
        override val stableKey: String = "memory:${entry.id}"
        override val timestamp: Long = entry.updatedAt.takeIf { it > 0L } ?: entry.createdAt
    }

    data class Summary(
        val summary: ConversationSummary,
    ) : MemoryViewerTimelineItem {
        override val stableKey: String = "summary:${summary.conversationId}"
        override val timestamp: Long = summary.updatedAt
    }
}

@Composable
fun MemoryManagementScreen(
    uiState: MemoryManagementUiState,
    assistants: List<Assistant>,
    onTogglePinned: (String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onDeleteSummary: (String) -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
    initialAssistantId: String = "",
) {
    val palette = rememberSettingsPalette()
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiState.message,
        onConsumeMessage = onConsumeMessage,
    )
    val assistantOptions = remember(assistants) {
        buildList {
            add(MemoryViewerRoleOption(id = AllAssistantFilterKey, label = "全部角色"))
            addAll(
                assistants
                    .distinctBy { it.id }
                    .sortedBy { it.name }
                    .map { assistant ->
                        MemoryViewerRoleOption(
                            id = assistant.id,
                            label = assistant.name.ifBlank { assistant.id },
                        )
                    },
            )
        }
    }
    val assistantNamesById = remember(assistants) {
        assistants.associate { assistant ->
            assistant.id to assistant.name.ifBlank { assistant.id }
        }
    }

    var searchQuery by rememberSaveable(initialAssistantId) { mutableStateOf("") }
    var selectedSectionName by rememberSaveable(initialAssistantId) {
        mutableStateOf(MemoryViewerSection.ALL.name)
    }
    var selectedAssistantId by rememberSaveable(initialAssistantId) {
        mutableStateOf(initialAssistantId)
    }
    var showAssistantMenu by rememberSaveable { mutableStateOf(false) }

    val selectedSection = runCatching { MemoryViewerSection.valueOf(selectedSectionName) }
        .getOrDefault(MemoryViewerSection.ALL)
    val selectedAssistantLabel = assistantOptions
        .firstOrNull { option -> option.id == selectedAssistantId }
        ?.label
        .orEmpty()

    val roleReady = selectedAssistantId.isNotBlank()
    val memoriesForRole = if (roleReady) {
        uiState.memories.filterMemoriesForAssistant(selectedAssistantId)
    } else {
        emptyList()
    }
    val summariesForRole = if (roleReady) {
        uiState.summaries.filterSummariesForAssistant(selectedAssistantId)
    } else {
        emptyList()
    }

    val coreMemories = memoriesForRole
        .filter { it.scopeType != MemoryScopeType.CONVERSATION }
        .filterMemoriesByQuery(searchQuery)
        .sortedForDisplay()
    val sceneMemories = memoriesForRole
        .filter { it.scopeType == MemoryScopeType.CONVERSATION }
        .filterMemoriesByQuery(searchQuery)
        .sortedForDisplay()
    val storySummaries = summariesForRole
        .filterSummariesByQuery(searchQuery)
        .sortedByDescending { it.updatedAt }
    val allMemories = memoriesForRole
        .filterMemoriesByQuery(searchQuery)
        .sortedForDisplay()
    val timelineItems = buildList {
        addAll(allMemories.map(MemoryViewerTimelineItem::Memory))
        addAll(storySummaries.map(MemoryViewerTimelineItem::Summary))
    }.sortedByDescending { item ->
        item.timestamp
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "记忆查看器",
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = palette.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = SettingsScreenPadding,
                    top = 4.dp,
                    end = SettingsScreenPadding,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SettingsPageIntro(
                        title = "记忆查看器",
                        summary = "按角色筛选核心记忆、情景记忆与剧情摘要，避免不同角色内容混在一起。",
                    )
                }

                item {
                    SettingsHintCard(
                        title = "摘要刷新入口",
                        body = "当前摘要支持手动刷新，但入口仍在对应聊天或沉浸式会话里的“上下文治理”面板，不在这个查看页内。",
                        containerColor = palette.surfaceTint,
                        contentColor = palette.title,
                    )
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        color = palette.surface,
                        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.36f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "选择角色：",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.title,
                                )
                                Box {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showAssistantMenu = true },
                                        shape = RoundedCornerShape(18.dp),
                                        color = palette.surfaceTint,
                                        border = BorderStroke(
                                            0.5.dp,
                                            palette.border.copy(alpha = 0.32f),
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = selectedAssistantLabel.ifBlank { "请选择角色" },
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (selectedAssistantLabel.isBlank()) {
                                                    palette.body.copy(alpha = 0.56f)
                                                } else {
                                                    palette.title
                                                },
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "展开角色列表",
                                                tint = palette.body,
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showAssistantMenu,
                                        onDismissRequest = { showAssistantMenu = false },
                                    ) {
                                        assistantOptions.forEach { option: MemoryViewerRoleOption ->
                                            DropdownMenuItem(
                                                text = { Text(option.label) },
                                                onClick = {
                                                    selectedAssistantId = option.id
                                                    showAssistantMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(18.dp),
                                    placeholder = { Text("搜索记忆内容...") },
                                    colors = rememberSettingsOutlineColors(),
                                )
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = palette.accent,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "搜索",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MemoryViewerSection.entries.forEach { section ->
                                    FilterChip(
                                        selected = selectedSection == section,
                                        onClick = { selectedSectionName = section.name },
                                        label = {
                                            Text(
                                                text = section.label,
                                                fontWeight = if (selectedSection == section) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = palette.accentSoft,
                                            selectedLabelColor = palette.accent,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                if (!roleReady) {
                    item {
                        MemoryViewerEmptyState(
                            title = "请选择角色查看记忆",
                            body = "先选一个角色，再查看它的核心记忆、情景记忆和剧情摘要。",
                        )
                    }
                } else {
                    when (selectedSection) {
                        MemoryViewerSection.ALL -> {
                            val hasMemory = allMemories.isNotEmpty()
                            val hasSummary = storySummaries.isNotEmpty()
                            if (!hasMemory && !hasSummary) {
                                item {
                                    MemoryViewerEmptyState(
                                        title = "暂无内容",
                                        body = "当前筛选下还没有记忆或剧情摘要。",
                                    )
                                }
                            } else {
                                if (hasMemory) {
                                    item {
                                        MemoryViewerSectionHeader(
                                            title = "记忆",
                                            subtitle = "包含核心记忆与情景记忆",
                                        )
                                    }
                                    items(allMemories, key = { it.id }) { entry ->
                                        MemoryEntryCard(
                                            entry = entry,
                                            assistantLabel = resolveMemoryAssistantLabel(entry, assistantNamesById),
                                            showAssistantLabel = selectedAssistantId == AllAssistantFilterKey,
                                            onTogglePinned = { onTogglePinned(entry.id) },
                                            onDelete = { onDeleteMemory(entry.id) },
                                        )
                                    }
                                }
                                if (hasSummary) {
                                    item {
                                        MemoryViewerSectionHeader(
                                            title = "剧情总结",
                                            subtitle = "会话摘要与阶段性回顾",
                                        )
                                    }
                                    items(storySummaries, key = { it.conversationId }) { summary ->
                                        SummaryCard(
                                            summary = summary,
                                            assistantLabel = resolveSummaryAssistantLabel(summary, assistantNamesById),
                                            showAssistantLabel = selectedAssistantId == AllAssistantFilterKey,
                                            onDelete = { onDeleteSummary(summary.conversationId) },
                                        )
                                    }
                                }
                            }
                        }

                        MemoryViewerSection.CORE -> {
                            if (coreMemories.isEmpty()) {
                                item {
                                    MemoryViewerEmptyState(
                                        title = "暂无核心记忆",
                                        body = "当前角色还没有长期记忆或助手记忆。",
                                    )
                                }
                            } else {
                                items(coreMemories, key = { it.id }) { entry ->
                                    MemoryEntryCard(
                                        entry = entry,
                                        assistantLabel = resolveMemoryAssistantLabel(entry, assistantNamesById),
                                        showAssistantLabel = selectedAssistantId == AllAssistantFilterKey,
                                        onTogglePinned = { onTogglePinned(entry.id) },
                                        onDelete = { onDeleteMemory(entry.id) },
                                    )
                                }
                            }
                        }

                        MemoryViewerSection.SCENE -> {
                            if (sceneMemories.isEmpty()) {
                                item {
                                    MemoryViewerEmptyState(
                                        title = "暂无情景记忆",
                                        body = "当前角色还没有会话级或场景级记忆。",
                                    )
                                }
                            } else {
                                items(sceneMemories, key = { it.id }) { entry ->
                                    MemoryEntryCard(
                                        entry = entry,
                                        assistantLabel = resolveMemoryAssistantLabel(entry, assistantNamesById),
                                        showAssistantLabel = selectedAssistantId == AllAssistantFilterKey,
                                        onTogglePinned = { onTogglePinned(entry.id) },
                                        onDelete = { onDeleteMemory(entry.id) },
                                    )
                                }
                            }
                        }

                        MemoryViewerSection.SUMMARY -> {
                            if (storySummaries.isEmpty()) {
                                item {
                                    MemoryViewerEmptyState(
                                        title = "暂无剧情总结",
                                        body = "当前角色还没有生成过会话摘要。",
                                    )
                                }
                            } else {
                                items(storySummaries, key = { it.conversationId }) { summary ->
                                    SummaryCard(
                                        summary = summary,
                                        assistantLabel = resolveSummaryAssistantLabel(summary, assistantNamesById),
                                        showAssistantLabel = selectedAssistantId == AllAssistantFilterKey,
                                        onDelete = { onDeleteSummary(summary.conversationId) },
                                    )
                                }
                            }
                        }

                        MemoryViewerSection.TIMELINE -> {
                            if (timelineItems.isEmpty()) {
                                item {
                                    MemoryViewerEmptyState(
                                        title = "暂无时间线",
                                        body = "当前筛选下还没有可以按时间排列的内容。",
                                    )
                                }
                            } else {
                                items(timelineItems, key = { it.stableKey }) { item ->
                                    MemoryViewerTimelineHeader(
                                        kind = when (item) {
                                            is MemoryViewerTimelineItem.Memory -> "记忆"
                                            is MemoryViewerTimelineItem.Summary -> "摘要"
                                        },
                                        timestamp = item.timestamp,
                                    )
                                    when (item) {
                                        is MemoryViewerTimelineItem.Memory -> {
                                            MemoryEntryCard(
                                                entry = item.entry,
                                                assistantLabel = resolveMemoryAssistantLabel(item.entry, assistantNamesById),
                                                showAssistantLabel = true,
                                                onTogglePinned = { onTogglePinned(item.entry.id) },
                                                onDelete = { onDeleteMemory(item.entry.id) },
                                            )
                                        }

                                        is MemoryViewerTimelineItem.Summary -> {
                                            SummaryCard(
                                                summary = item.summary,
                                                assistantLabel = resolveSummaryAssistantLabel(item.summary, assistantNamesById),
                                                showAssistantLabel = true,
                                                onDelete = { onDeleteSummary(item.summary.conversationId) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            TopAppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentTopInset = innerPadding.calculateTopPadding(),
            )
        }
    }
}

@Composable
private fun MemoryViewerSectionHeader(
    title: String,
    subtitle: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MemoryViewerEmptyState(
    title: String,
    body: String,
) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = palette.surface,
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.36f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = palette.surfaceTint,
            ) {
                Box(
                    modifier = Modifier.padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint = palette.body.copy(alpha = 0.72f),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.title,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.body,
            )
        }
    }
}

@Composable
private fun MemoryViewerTimelineHeader(
    kind: String,
    timestamp: Long,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = kind,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = formatMemoryViewerTime(timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MemoryEntryCard(
    entry: MemoryEntry,
    assistantLabel: String?,
    showAssistantLabel: Boolean,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.surface,
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.title,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (entry.pinned) {
                    SettingsStatusPill(
                        text = "置顶",
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                    )
                }
                SettingsStatusPill(
                    text = entry.scopeType.label,
                    containerColor = palette.subtleChip,
                    contentColor = palette.subtleChipContent,
                )
                if (showAssistantLabel && !assistantLabel.isNullOrBlank()) {
                    SettingsStatusPill(
                        text = assistantLabel,
                        containerColor = palette.surfaceTint,
                        contentColor = palette.body,
                    )
                }
                if (entry.scopeId.isNotBlank()) {
                    SettingsStatusPill(
                        text = entry.scopeId,
                        containerColor = palette.surfaceTint,
                        contentColor = palette.body,
                    )
                }
                SettingsStatusPill(
                    text = formatMemoryViewerTime(entry.updatedAt.takeIf { it > 0L } ?: entry.createdAt),
                    containerColor = palette.surfaceTint,
                    contentColor = palette.body,
                )
                if (entry.sourceMessageId.isNotBlank()) {
                    SettingsStatusPill(
                        text = "来自消息",
                        containerColor = palette.surfaceTint,
                        contentColor = palette.body,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NarraIconButton(onClick = onTogglePinned, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = if (entry.pinned) "取消置顶" else "置顶",
                        tint = if (entry.pinned) palette.accent else palette.body.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                NarraIconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: ConversationSummary,
    assistantLabel: String?,
    showAssistantLabel: Boolean,
    onDelete: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.surface,
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = summary.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.title,
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
                if (showAssistantLabel && !assistantLabel.isNullOrBlank()) {
                    SettingsStatusPill(
                        text = assistantLabel,
                        containerColor = palette.surfaceTint,
                        contentColor = palette.body,
                    )
                }
                if (summary.updatedAt > 0L) {
                    SettingsStatusPill(
                        text = formatMemoryViewerTime(summary.updatedAt),
                        containerColor = palette.surfaceTint,
                        contentColor = palette.body,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NarraIconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除摘要",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

internal fun List<MemoryEntry>.filterMemoriesForAssistant(assistantId: String): List<MemoryEntry> {
    return when {
        assistantId.isBlank() -> emptyList()
        assistantId == AllAssistantFilterKey -> this
        else -> filter { entry ->
            entry.characterId == assistantId ||
                (
                    entry.characterId.isBlank() &&
                        entry.scopeType == MemoryScopeType.ASSISTANT &&
                        entry.scopeId == assistantId
                    )
        }
    }
}

internal fun List<ConversationSummary>.filterSummariesForAssistant(assistantId: String): List<ConversationSummary> {
    return when {
        assistantId.isBlank() -> emptyList()
        assistantId == AllAssistantFilterKey -> this
        else -> filter { summary -> summary.assistantId == assistantId }
    }
}

private fun List<MemoryEntry>.filterMemoriesByQuery(query: String): List<MemoryEntry> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) {
        return this
    }
    return filter { entry ->
        entry.content.contains(normalizedQuery, ignoreCase = true) ||
            entry.scopeId.contains(normalizedQuery, ignoreCase = true)
    }
}

private fun List<ConversationSummary>.filterSummariesByQuery(query: String): List<ConversationSummary> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) {
        return this
    }
    return filter { summary ->
        summary.summary.contains(normalizedQuery, ignoreCase = true) ||
            summary.conversationId.contains(normalizedQuery, ignoreCase = true)
    }
}

private fun List<MemoryEntry>.sortedForDisplay(): List<MemoryEntry> {
    return sortedWith(
        compareByDescending<MemoryEntry> { it.pinned }
            .thenByDescending { it.updatedAt.takeIf { timestamp -> timestamp > 0L } ?: it.createdAt }
            .thenByDescending { it.createdAt },
    )
}

private fun resolveMemoryAssistantLabel(
    entry: MemoryEntry,
    assistantNamesById: Map<String, String>,
): String? {
    return when {
        entry.characterId.isNotBlank() -> assistantNamesById[entry.characterId] ?: entry.characterId
        entry.scopeType == MemoryScopeType.ASSISTANT && entry.scopeId.isNotBlank() -> {
            assistantNamesById[entry.scopeId] ?: entry.scopeId
        }
        entry.scopeType == MemoryScopeType.GLOBAL -> "全局"
        else -> null
    }
}

private fun resolveSummaryAssistantLabel(
    summary: ConversationSummary,
    assistantNamesById: Map<String, String>,
): String? {
    return when {
        summary.assistantId.isNotBlank() -> assistantNamesById[summary.assistantId] ?: summary.assistantId
        else -> null
    }
}

private fun formatMemoryViewerTime(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "未知时间"
    }
    return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
