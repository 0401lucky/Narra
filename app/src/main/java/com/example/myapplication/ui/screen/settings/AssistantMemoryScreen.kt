package com.example.myapplication.ui.screen.settings

import com.example.myapplication.ui.component.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType

@Composable
fun AssistantMemoryScreen(
    assistant: Assistant,
    memories: List<MemoryEntry>,
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
                    overline = "记忆",
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

            if (memories.isEmpty()) {
                item {
                    SettingsHintCard(
                        title = "还没有专属记忆",
                        body = "暂无内容",
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                    )
                }
            } else {
                item {
                    Text(
                        text = "管理记忆",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.title,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                items(memories, key = { it.id }) { memory ->
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
