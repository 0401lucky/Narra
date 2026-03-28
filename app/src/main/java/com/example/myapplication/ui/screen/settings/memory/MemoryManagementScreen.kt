package com.example.myapplication.ui.screen.settings.memory

import com.example.myapplication.ui.component.*

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsGroup
import com.example.myapplication.ui.screen.settings.SettingsHintCard
import com.example.myapplication.ui.screen.settings.SettingsPageIntro
import com.example.myapplication.ui.screen.settings.SettingsSectionHeader
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsStatusPill
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsOutlineColors
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette
import com.example.myapplication.ui.screen.settings.rememberSettingsSnackbarHostState
import com.example.myapplication.viewmodel.MemoryManagementUiState

@Composable
fun MemoryManagementScreen(
    uiState: MemoryManagementUiState,
    onTogglePinned: (String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onDeleteSummary: (String) -> Unit,
    onRefreshSummaries: () -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiState.message,
        onConsumeMessage = onConsumeMessage,
    )
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableStateOf("memory") }
    val filteredMemories = uiState.memories.filter { entry ->
        val query = searchQuery.trim()
        query.isBlank() ||
            entry.content.contains(query, ignoreCase = true) ||
            entry.scopeId.contains(query, ignoreCase = true)
    }
    val filteredSummaries = uiState.summaries.filter { summary ->
        val query = searchQuery.trim()
        query.isBlank() ||
            summary.summary.contains(query, ignoreCase = true) ||
            summary.conversationId.contains(query, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "记忆与摘要",
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
                        overline = "长期上下文",
                        title = "集中查看自动记忆与摘要",
                        summary = "查看全局自动记忆、手动记忆和会话摘要。搜索、置顶或删除，保持上下文干净可控。",
                    )
                }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    label = { Text("搜索记忆或摘要") },
                    colors = rememberSettingsOutlineColors(),
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = selectedTab == "memory",
                        onClick = { selectedTab = "memory" },
                        label = {
                            Text(
                                "记忆 (${uiState.memories.size})",
                                fontWeight = if (selectedTab == "memory") FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.accentSoft,
                            selectedLabelColor = palette.accent,
                        ),
                    )
                    FilterChip(
                        selected = selectedTab == "summary",
                        onClick = { selectedTab = "summary" },
                        label = {
                            Text(
                                "摘要 (${uiState.summaries.size})",
                                fontWeight = if (selectedTab == "summary") FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.accentSoft,
                            selectedLabelColor = palette.accent,
                        ),
                    )
                }
            }

            if (selectedTab == "memory") {
                if (filteredMemories.isEmpty()) {
                    item {
                        SettingsHintCard(
                            title = "暂无记忆",
                            body = "开启助手记忆后，可自动提取记忆，也可以在聊天消息上手动记忆。",
                            containerColor = palette.accentSoft,
                            contentColor = palette.accent,
                        )
                    }
                } else {
                    items(filteredMemories, key = { it.id }) { entry ->
                        MemoryEntryCard(
                            entry = entry,
                            onTogglePinned = { onTogglePinned(entry.id) },
                            onDelete = { onDeleteMemory(entry.id) },
                        )
                    }
                }
            } else {
                item {
                    AnimatedSettingButton(
                        text = if (uiState.isBusy) "刷新中…" else "刷新摘要",
                        onClick = onRefreshSummaries,
                        enabled = !uiState.isBusy,
                        isPrimary = false,
                    )
                }
                if (filteredSummaries.isEmpty()) {
                    item {
                        SettingsHintCard(
                            title = "暂无摘要",
                            body = "长对话在达到阈值后会自动生成摘要，生成后会显示在这里。",
                            containerColor = palette.accentSoft,
                            contentColor = palette.accent,
                        )
                    }
                } else {
                    items(filteredSummaries, key = { it.conversationId }) { summary ->
                        SummaryCard(
                            summary = summary,
                            onDelete = { onDeleteSummary(summary.conversationId) },
                        )
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
private fun MemoryEntryCard(
    entry: MemoryEntry,
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
            // Content
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.title,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            // Status tags
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                if (entry.scopeId.isNotBlank()) {
                    SettingsStatusPill(
                        text = entry.scopeId,
                        containerColor = palette.surfaceTint,
                        contentColor = palette.body,
                    )
                }
                if (entry.sourceMessageId.isNotBlank()) {
                    SettingsStatusPill(
                        text = "来自消息",
                        containerColor = palette.surfaceTint,
                        contentColor = palette.body,
                    )
                }
            }

            // Action buttons — now use icon buttons instead of bare text
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsStatusPill(
                        text = "覆盖 ${summary.coveredMessageCount} 条",
                        containerColor = palette.subtleChip,
                        contentColor = palette.subtleChipContent,
                    )
                }
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
