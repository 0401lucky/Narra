package com.example.myapplication.ui.screen.settings.worldbook

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_CONVERSATION_TITLE
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookMatchMode
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.worldbook.KeywordChipInput
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsGroup
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsSectionHeader
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsOutlineColors
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette

private val StringListSaver = listSaver<List<String>, String>(
    save = { it.toList() },
    restore = { it },
)

private val WorldBookMatchModeSaver: androidx.compose.runtime.saveable.Saver<WorldBookMatchMode, *> =
    androidx.compose.runtime.saveable.Saver(
        save = { it.name },
        restore = { raw -> WorldBookMatchMode.valueOf(raw) },
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldBookEditScreen(
    entry: WorldBookEntry?,
    isNew: Boolean,
    assistants: List<Assistant>,
    conversations: List<Conversation> = emptyList(),
    presetBookName: String = "",
    existingBookNames: List<String> = emptyList(),
    onSave: (WorldBookEntry) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenAssistantMount: () -> Unit = {},
) {
    val palette = rememberSettingsPalette()
    val outlineColors = rememberSettingsOutlineColors()

    var title by rememberSaveable { mutableStateOf(entry?.title ?: "") }
    var content by rememberSaveable { mutableStateOf(entry?.content ?: "") }
    var keywords by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(entry?.keywords ?: emptyList())
    }
    var aliases by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(entry?.aliases ?: emptyList())
    }
    var secondaryKeywords by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(entry?.secondaryKeywords ?: emptyList())
    }
    var sourceBookName by rememberSaveable {
        mutableStateOf(entry?.sourceBookName ?: presetBookName)
    }
    var priorityText by rememberSaveable { mutableStateOf((entry?.priority ?: 0).toString()) }
    var insertionOrderText by rememberSaveable {
        mutableStateOf((entry?.insertionOrder ?: 0).toString())
    }
    var enabled by rememberSaveable { mutableStateOf(entry?.enabled ?: true) }
    var alwaysActive by rememberSaveable { mutableStateOf(entry?.alwaysActive ?: false) }
    var selective by rememberSaveable { mutableStateOf(entry?.selective ?: false) }
    var caseSensitive by rememberSaveable { mutableStateOf(entry?.caseSensitive ?: false) }
    var matchMode by rememberSaveable(stateSaver = WorldBookMatchModeSaver) {
        mutableStateOf(entry?.matchMode ?: WorldBookMatchMode.WORD_CJK)
    }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var scopeType by rememberSaveable { mutableStateOf(entry?.scopeType ?: WorldBookScopeType.GLOBAL) }
    var scopeId by rememberSaveable { mutableStateOf(entry?.scopeId ?: "") }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showContentSheet by rememberSaveable { mutableStateOf(false) }
    val expandedStateHolder = rememberWorldBookEditExpandedState()
    val expandedState by expandedStateHolder

    val canSave = title.isNotBlank() &&
        content.isNotBlank() &&
        when (scopeType) {
            WorldBookScopeType.GLOBAL,
            WorldBookScopeType.ATTACHABLE -> true
            else -> scopeId.isNotBlank()
        }

    val onSaveClick: () -> Unit = {
        if (canSave) {
            val now = System.currentTimeMillis()
            val result = (entry ?: WorldBookEntry(createdAt = now, updatedAt = now)).copy(
                title = title,
                content = content,
                sourceBookName = sourceBookName.trim(),
                keywords = keywords,
                aliases = aliases,
                secondaryKeywords = secondaryKeywords,
                selective = selective,
                caseSensitive = caseSensitive,
                matchMode = matchMode,
                insertionOrder = insertionOrderText.trim().toIntOrNull() ?: 0,
                enabled = enabled,
                alwaysActive = alwaysActive,
                priority = priorityText.trim().toIntOrNull() ?: 0,
                scopeType = scopeType,
                scopeId = when (scopeType) {
                    WorldBookScopeType.GLOBAL,
                    WorldBookScopeType.ATTACHABLE -> ""
                    else -> scopeId.trim()
                },
                updatedAt = now,
            )
            onSave(result)
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = if (isNew) "新建世界书" else "编辑世界书",
                onNavigateBack = onNavigateBack,
                actionLabel = if (canSave) "保存" else null,
                onAction = if (canSave) onSaveClick else null,
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                WorldBookCollapsibleSection(
                    title = "基本信息",
                    description = "标题、正文、所属世界书",
                    expanded = expandedState.basicInfo,
                    onToggle = { expandedStateHolder.value = expandedState.toggleBasicInfo() },
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("标题") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = outlineColors,
                        )
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("内容") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp),
                            maxLines = 12,
                            shape = RoundedCornerShape(16.dp),
                            colors = outlineColors,
                            trailingIcon = {
                                IconButton(onClick = { showContentSheet = true }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "全屏编辑正文")
                                }
                            },
                        )
                        SourceBookNameDropdown(
                            value = sourceBookName,
                            onValueChange = { sourceBookName = it },
                            existingBookNames = existingBookNames,
                            outlineColors = outlineColors,
                        )
                    }
                }
            }

            item {
                WorldBookCollapsibleSection(
                    title = "命中规则",
                    description = "关键词命中后，该条目会注入到 prompt",
                    expanded = expandedState.hitRule,
                    onToggle = { expandedStateHolder.value = expandedState.toggleHitRule() },
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "匹配模式",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.title,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                WorldBookMatchMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = matchMode == mode,
                                        onClick = { matchMode = mode },
                                        label = { Text(mode.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = palette.accentSoft,
                                            selectedLabelColor = palette.accent,
                                        ),
                                    )
                                }
                            }
                            Text(
                                text = when (matchMode) {
                                    WorldBookMatchMode.CONTAINS -> "子串匹配，任何出现都算命中"
                                    WorldBookMatchMode.WORD_CJK -> "CJK 感知整词：中文保持 contains，英文要求词边界"
                                    WorldBookMatchMode.REGEX -> "关键词整条作为正则直接匹配"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.body,
                            )
                        }
                        KeywordChipInput(
                            label = "主关键词",
                            values = keywords,
                            onValuesChange = { keywords = it },
                            placeholder = "回车 / 逗号提交；支持 /pattern/flags 正则",
                        )
                        KeywordChipInput(
                            label = "别名",
                            values = aliases,
                            onValuesChange = { aliases = it },
                            placeholder = "补充人物简称、地名别称等",
                        )
                        OutlinedTextField(
                            value = priorityText,
                            onValueChange = { priorityText = filterIntegerInput(it) },
                            label = { Text("优先级") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = outlineColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("默认 0，越大越优先") },
                        )
                        SettingsSectionHeader("次级关键词", "selective 打开后，次级关键词也要命中才会注入")
                        ToggleField(
                            title = "启用次级匹配（selective）",
                            subtitle = "开启后需主关键词 + 次级关键词同时命中才注入",
                            checked = selective,
                            onCheckedChange = { selective = it },
                        )
                        KeywordChipInput(
                            label = "次级关键词",
                            values = secondaryKeywords,
                            onValuesChange = { secondaryKeywords = it },
                            placeholder = "留空则等同主关键词命中即注入",
                            enabled = selective,
                        )
                        ToggleField(
                            title = "区分大小写",
                            subtitle = "对英文关键词生效；中文天然无大小写概念",
                            checked = caseSensitive,
                            onCheckedChange = { caseSensitive = it },
                        )
                    }
                }
            }

            item {
                WorldBookCollapsibleSection(
                    title = "作用域",
                    description = "控制该条目在哪些会话环境中可被命中",
                    expanded = expandedState.scope,
                    onToggle = { expandedStateHolder.value = expandedState.toggleScope() },
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            WorldBookScopeType.entries.forEach { candidate ->
                                FilterChip(
                                    selected = scopeType == candidate,
                                    onClick = {
                                        scopeType = candidate
                                        if (candidate == WorldBookScopeType.GLOBAL ||
                                            candidate == WorldBookScopeType.ATTACHABLE
                                        ) {
                                            scopeId = ""
                                        }
                                    },
                                    label = { Text(candidate.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.accentSoft,
                                        selectedLabelColor = palette.accent,
                                    ),
                                )
                            }
                        }
                        when (scopeType) {
                            WorldBookScopeType.GLOBAL -> {
                                Text(
                                    text = "自动按关键词作用于所有会话。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.body,
                                )
                            }

                            WorldBookScopeType.ATTACHABLE -> {
                                Text(
                                    text = "需要手动在助手中挂载才生效。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.body,
                                )
                                androidx.compose.material3.TextButton(onClick = onOpenAssistantMount) {
                                    Text("去助手页挂载")
                                }
                            }

                            WorldBookScopeType.ASSISTANT -> {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    assistants.forEach { assistant ->
                                        FilterChip(
                                            selected = scopeId == assistant.id,
                                            onClick = { scopeId = assistant.id },
                                            label = {
                                                Text(assistant.name.ifBlank { "未命名助手" })
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = palette.accentSoft,
                                                selectedLabelColor = palette.accent,
                                            ),
                                        )
                                    }
                                }
                            }

                            WorldBookScopeType.CONVERSATION -> {
                                ConversationScopePicker(
                                    conversations = conversations,
                                    selectedId = scopeId,
                                    onSelected = { scopeId = it },
                                    outlineColors = outlineColors,
                                )
                            }
                        }
                    }
                }
            }

            item {
                WorldBookCollapsibleSection(
                    title = "状态与高级",
                    description = "启用 / 常驻注入 / 插入顺序",
                    expanded = expandedState.status,
                    onToggle = { expandedStateHolder.value = expandedState.toggleStatus() },
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ToggleField(
                            title = "启用条目",
                            subtitle = "关闭后不会参与匹配和注入",
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                        )
                        ToggleField(
                            title = "常驻注入",
                            subtitle = "开启后即使没有命中关键词也会优先注入",
                            checked = alwaysActive,
                            onCheckedChange = { alwaysActive = it },
                        )
                        ToggleField(
                            title = "展开高级参数",
                            subtitle = "控制插入顺序等进阶字段",
                            checked = advancedExpanded,
                            onCheckedChange = { advancedExpanded = it },
                        )
                        if (advancedExpanded) {
                            OutlinedTextField(
                                value = insertionOrderText,
                                onValueChange = { insertionOrderText = filterIntegerInput(it) },
                                label = { Text("插入顺序 (insertionOrder)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = outlineColors,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                placeholder = { Text("越小越靠前（默认 0）") },
                            )
                        }
                    }
                }
            }

            if (!isNew && entry != null) {
                item { SettingsSectionHeader("危险操作", "删除后无法恢复") }
                item {
                    SettingsGroup {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AnimatedSettingButton(
                                text = "删除这条",
                                onClick = { showDeleteDialog = true },
                                enabled = true,
                                isPrimary = false,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && entry != null) {
        NarraAlertDialog(
            title = "删除世界书条目",
            message = "将删除《${entry.title.ifBlank { "未命名条目" }}》，这个操作不可撤销。",
            confirmLabel = "确认删除",
            dismissLabel = "取消",
            isDestructive = true,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete(entry.id)
                onNavigateBack()
            },
        )
    }

    if (showContentSheet) {
        WorldBookContentFullscreenSheet(
            initialContent = content,
            onConfirm = { content = it },
            onDismiss = { showContentSheet = false },
        )
    }
}

@Composable
private fun ToggleField(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = rememberSettingsPalette()

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 72.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.title,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.body,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

private fun filterIntegerInput(rawValue: String): String {
    return buildString {
        rawValue.forEachIndexed { index, char ->
            if (char.isDigit() || (char == '-' && index == 0)) {
                append(char)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceBookNameDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    existingBookNames: List<String>,
    outlineColors: androidx.compose.material3.TextFieldColors,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasCandidates = existingBookNames.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded && hasCandidates,
        onExpandedChange = { next -> if (hasCandidates) expanded = next },
    ) {
        @Suppress("DEPRECATION")
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("所属世界书") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = outlineColors,
            placeholder = { Text("留空则作为独立条目显示") },
            trailingIcon = {
                if (hasCandidates) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
        )
        if (hasCandidates) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                existingBookNames.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onValueChange(name)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScopePicker(
    conversations: List<Conversation>,
    selectedId: String,
    onSelected: (String) -> Unit,
    outlineColors: androidx.compose.material3.TextFieldColors,
) {
    val palette = rememberSettingsPalette()
    val sortedConversations = remember(conversations) {
        conversations.sortedByDescending { it.updatedAt }
    }
    val selected = sortedConversations.firstOrNull { it.id == selectedId }
    val now = remember(selected?.id, sortedConversations) { System.currentTimeMillis() }
    var expanded by remember { mutableStateOf(false) }

    if (sortedConversations.isEmpty()) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            label = { Text("所属会话") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = true,
            shape = RoundedCornerShape(16.dp),
            colors = outlineColors,
            placeholder = { Text("暂无会话，先到聊天页新建") },
        )
        return
    }

    val displayText = selected?.title?.ifBlank { DEFAULT_CONVERSATION_TITLE } ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        @Suppress("DEPRECATION")
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            label = { Text("所属会话") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            singleLine = true,
            readOnly = true,
            shape = RoundedCornerShape(16.dp),
            colors = outlineColors,
            placeholder = { Text("请选择会话") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            sortedConversations.forEach { conversation ->
                val title = conversation.title.ifBlank { DEFAULT_CONVERSATION_TITLE }
                val updatedLabel = DateUtils.getRelativeTimeSpanString(
                    conversation.updatedAt,
                    now,
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString()
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.title,
                            )
                            Text(
                                text = updatedLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.body,
                            )
                        }
                    },
                    onClick = {
                        onSelected(conversation.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
