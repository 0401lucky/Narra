package com.example.myapplication.ui.screen.settings

import com.example.myapplication.ui.component.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.BUILTIN_ASSISTANTS
import com.example.myapplication.ui.component.AssistantAvatar
import com.example.myapplication.viewmodel.SettingsViewModel
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantListScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAssistantConfig: (String?) -> Unit,
    onDeleteAssistant: (String) -> Unit,
) {
    val storedSettings by viewModel.storedSettings.collectAsStateWithLifecycle()
    val assistants = storedSettings.resolvedAssistants()
    val currentAssistantId = storedSettings.selectedAssistantId
    var searchQuery by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Assistant?>(null) }

    val filteredAssistants = remember(searchQuery, assistants) {
        if (searchQuery.isBlank()) assistants
        else assistants.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val palette = rememberSettingsPalette()

    Scaffold(
        containerColor = palette.background,
        topBar = {
            SettingsTopBar(
                title = "角色",
                onNavigateBack = onNavigateBack,
                actionLabel = "新增",
                onAction = { onNavigateToAssistantConfig(null) },
            )
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 40.dp,
                start = SettingsScreenPadding,
                end = SettingsScreenPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                val searchColors = rememberSettingsOutlineColors()
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "搜索角色",
                            color = palette.body,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Lucide.Search,
                            contentDescription = "搜索",
                            tint = palette.body,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = searchColors,
                    singleLine = true,
                )
            }

            if (filteredAssistants.isEmpty() && searchQuery.isNotBlank()) {
                item {
                    SettingsPlaceholderRow(
                        title = "没有匹配的角色",
                        subtitle = "试试其他关键词，或者新增一个角色。",
                    )
                }
            }

            items(filteredAssistants, key = { it.id }) { assistant ->
                AssistantCardMinimal(
                    assistant = assistant,
                    isSelected = assistant.id == currentAssistantId,
                    palette = palette,
                    onClick = { viewModel.selectAssistant(assistant.id) },
                    onEdit = { onNavigateToAssistantConfig(assistant.id) },
                    onDelete = { deleteTarget = assistant },
                )
            }
        }
    }

    deleteTarget?.let { assistant ->
        AssistantDeleteConfirmDialog(
            assistantName = assistant.name.ifBlank { "未命名角色" },
            extraMessage = if (assistant.id == currentAssistantId) {
                "删除后，当前选择会自动切换回默认角色。"
            } else {
                ""
            },
            onDismissRequest = { deleteTarget = null },
            onConfirm = {
                onDeleteAssistant(assistant.id)
                deleteTarget = null
            },
        )
    }
}

@Composable
internal fun AssistantCardMinimal(
    assistant: Assistant,
    isSelected: Boolean,
    palette: SettingsPalette,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val builtinIds = BUILTIN_ASSISTANTS.map { it.id }.toSet()
    val canDelete = assistant.id !in builtinIds
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .semantics { selected = isSelected }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, if (isSelected) palette.accent else palette.border),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AssistantAvatar(
                name = assistant.name,
                iconName = assistant.iconName,
                avatarUri = assistant.avatarUri,
                size = 48.dp,
                containerColor = palette.accentSoft,
                contentColor = palette.accentStrong,
                cornerRadius = 24.dp,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assistant.name.ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isSelected) {
                        SettingsStatusPill(
                            text = "当前",
                            containerColor = palette.accentStrong,
                            contentColor = palette.accentOnStrong,
                        )
                    }
                    if (assistant.id in builtinIds) {
                        SettingsStatusPill(
                            text = "内置",
                            containerColor = palette.subtleChip,
                            contentColor = palette.subtleChipContent,
                        )
                    }
                }
                
                val descriptionOrTags = buildString {
                    if (assistant.memoryEnabled) append("开启记忆 · ")
                    val limitTags = assistant.tags.take(2).joinToString(" · ")
                    if (limitTags.isNotEmpty()) append(limitTags)
                }.trimEnd(' ', '·')
                
                if (descriptionOrTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = descriptionOrTags,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                NarraIconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Lucide.EllipsisVertical,
                        contentDescription = "更多操作",
                        tint = palette.body,
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("进入设置") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (canDelete) "删除角色" else "内置角色不可删除",
                                color = if (canDelete) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        enabled = canDelete,
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
