package com.example.myapplication.ui.screen.settings.worldbook

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType
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

@Composable
fun WorldBookEditScreen(
    entry: WorldBookEntry?,
    isNew: Boolean,
    assistants: List<Assistant>,
    presetBookName: String = "",
    onSave: (WorldBookEntry) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateBack: () -> Unit,
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
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var scopeType by rememberSaveable { mutableStateOf(entry?.scopeType ?: WorldBookScopeType.GLOBAL) }
    var scopeId by rememberSaveable { mutableStateOf(entry?.scopeId ?: "") }
    val canSave = title.isNotBlank() &&
        content.isNotBlank() &&
        when (scopeType) {
            WorldBookScopeType.GLOBAL,
            WorldBookScopeType.ATTACHABLE -> true
            else -> scopeId.isNotBlank()
        }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = if (isNew) "新建世界书" else "编辑世界书",
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { SettingsSectionHeader("基本信息", "标题和正文会作为注入内容的主体") }
            item {
                SettingsGroup {
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
                        )
                        OutlinedTextField(
                            value = sourceBookName,
                            onValueChange = { sourceBookName = it },
                            label = { Text("所属世界书") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = outlineColors,
                            placeholder = { Text("留空则作为独立条目显示") },
                        )
                    }
                }
            }

            item { SettingsSectionHeader("命中规则", "关键词或别名命中后会把该条目注入 prompt") }
            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
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
                    }
                }
            }

            item { SettingsSectionHeader("次级关键词", "selective 打开后，次级关键词也要命中才会注入") }
            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
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

            item { SettingsSectionHeader("作用域", "控制该条目在哪些会话环境中可被命中") }
            item {
                SettingsGroup {
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
                                OutlinedTextField(
                                    value = scopeId,
                                    onValueChange = { scopeId = it },
                                    label = { Text("会话 ID") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = outlineColors,
                                    placeholder = { Text("填写 conversationId") },
                                )
                            }
                        }
                    }
                }
            }

            item { SettingsSectionHeader("状态", "控制条目是否启用，以及是否常驻注入") }
            item {
                SettingsGroup {
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
                    }
                }
            }

            item { SettingsSectionHeader("高级", "通常使用默认值即可，微调注入顺序时再展开") }
            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
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

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AnimatedSettingButton(
                            text = "保存",
                            onClick = {
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
                            },
                            enabled = canSave,
                            isPrimary = true,
                        )

                        if (!isNew && entry != null) {
                            AnimatedSettingButton(
                                text = "删除条目",
                                onClick = {
                                    onDelete(entry.id)
                                    onNavigateBack()
                                },
                                enabled = true,
                                isPrimary = false,
                            )
                        }
                    }
                }
            }
        }
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
