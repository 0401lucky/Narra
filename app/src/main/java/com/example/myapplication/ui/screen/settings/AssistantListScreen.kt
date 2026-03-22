package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.BUILTIN_ASSISTANTS
import com.example.myapplication.ui.component.AssistantAvatar
import com.example.myapplication.viewmodel.SettingsViewModel

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                // Return and Add button in a clean row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onNavigateBack,
                        shape = CircleShape,
                        color = palette.surface.copy(alpha = 0.5f),
                        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.3f)),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = palette.title,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 重新设计的大标题
                    Text(
                        text = "助手设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp,
                        color = palette.title,
                    )

                    Surface(
                        onClick = { onNavigateToAssistantConfig(null) },
                        shape = CircleShape,
                        color = palette.accentStrong,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "新建",
                                tint = palette.accentOnStrong,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 40.dp,
                start = 24.dp,
                end = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { 
                        Text(
                            text = "搜索助手", 
                            color = palette.body.copy(alpha = 0.5f),
                        ) 
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "搜索",
                            tint = palette.body.copy(alpha = 0.5f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.surface.copy(alpha = 0.5f),
                        unfocusedContainerColor = palette.surface.copy(alpha = 0.5f),
                        focusedBorderColor = palette.accent.copy(alpha = 0.5f),
                        unfocusedBorderColor = palette.border.copy(alpha = 0.2f),
                        cursorColor = palette.accent,
                    ),
                    singleLine = true,
                )
            }

            // Cards
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
            assistantName = assistant.name.ifBlank { "未命名助手" },
            extraMessage = if (assistant.id == currentAssistantId) {
                "删除后，当前选择会自动切换回默认助手。"
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
private fun AssistantCardMinimal(
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
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = palette.surface.copy(alpha = 0.7f),
        border = BorderStroke(0.5.dp, if(isSelected) palette.accent.copy(alpha=0.4f) else palette.border.copy(alpha=0.2f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Avatar (还原我们特有的丰富Avatar展示能力)
            AssistantAvatar(
                name = assistant.name,
                iconName = assistant.iconName,
                avatarUri = assistant.avatarUri,
                size = 56.dp,
                containerColor = palette.accentSoft.copy(alpha = 0.5f),
                contentColor = palette.accentStrong,
                cornerRadius = 20.dp, 
            )

            // Info Section
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = assistant.name.ifBlank { "未命名" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill=false) // Wrap content allowing truncation
                    )
                    
                    // Essential Status Badges (找回丢失的内置提示和选中状态)
                    if (isSelected) {
                        SettingsStatusPill(
                            text = "当前",
                            containerColor = palette.accentStrong,
                            contentColor = palette.accentOnStrong,
                        )
                    } else if (assistant.id in builtinIds) {
                        SettingsStatusPill(
                            text = "内置",
                            containerColor = palette.subtleChip,
                            contentColor = palette.subtleChipContent,
                        )
                    }
                }
                
                // Very clean secondary tags (避免复杂的文字描述，只轻柔列出特性)
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
                        color = palette.body.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // More Options (Edit trigger)
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多操作",
                        tint = palette.body.copy(alpha = 0.6f)
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
                                text = if (canDelete) "删除助手" else "内置助手不可删除",
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
