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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsGroup
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsSectionHeader
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsOutlineColors
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette

@Composable
fun WorldBookEditScreen(
    entry: WorldBookEntry?,
    isNew: Boolean,
    presetBookName: String = "",
    onSave: (WorldBookEntry) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val outlineColors = rememberSettingsOutlineColors()

    var title by rememberSaveable { mutableStateOf(entry?.title ?: "") }
    var content by rememberSaveable { mutableStateOf(entry?.content ?: "") }
    var keywordsText by rememberSaveable {
        mutableStateOf(entry?.keywords?.joinToString(", ") ?: "")
    }
    var aliasesText by rememberSaveable {
        mutableStateOf(entry?.aliases?.joinToString(", ") ?: "")
    }
    var sourceBookName by rememberSaveable {
        mutableStateOf(entry?.sourceBookName ?: presetBookName)
    }
    var priorityText by rememberSaveable { mutableStateOf((entry?.priority ?: 0).toString()) }
    var enabled by rememberSaveable { mutableStateOf(entry?.enabled ?: true) }
    var alwaysActive by rememberSaveable { mutableStateOf(entry?.alwaysActive ?: false) }
    var scopeType by rememberSaveable { mutableStateOf(entry?.scopeType ?: WorldBookScopeType.GLOBAL) }
    var scopeId by rememberSaveable { mutableStateOf(entry?.scopeId ?: "") }

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
                        OutlinedTextField(
                            value = keywordsText,
                            onValueChange = { keywordsText = it },
                            label = { Text("关键词") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = outlineColors,
                            placeholder = { Text("多个关键词用逗号分隔") },
                        )
                        OutlinedTextField(
                            value = aliasesText,
                            onValueChange = { aliasesText = it },
                            label = { Text("别名") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = outlineColors,
                            placeholder = { Text("用于补充人物简称、地名别称等") },
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
                                        if (candidate == WorldBookScopeType.GLOBAL) {
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
                        OutlinedTextField(
                            value = scopeId,
                            onValueChange = { scopeId = it },
                            label = { Text("作用域 ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = scopeType != WorldBookScopeType.GLOBAL,
                            shape = RoundedCornerShape(16.dp),
                            colors = outlineColors,
                            placeholder = {
                                Text(
                                    when (scopeType) {
                                        WorldBookScopeType.GLOBAL -> "全局条目无需填写"
                                        WorldBookScopeType.ASSISTANT -> "填写 assistantId"
                                        WorldBookScopeType.CONVERSATION -> "填写 conversationId"
                                    },
                                )
                            },
                        )
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
                                    title = title.trim(),
                                    content = content.trim(),
                                    sourceBookName = sourceBookName.trim(),
                                    keywords = parseCommaSeparated(keywordsText),
                                    aliases = parseCommaSeparated(aliasesText),
                                    enabled = enabled,
                                    alwaysActive = alwaysActive,
                                    priority = priorityText.trim().toIntOrNull() ?: 0,
                                    scopeType = scopeType,
                                    scopeId = if (scopeType == WorldBookScopeType.GLOBAL) "" else scopeId.trim(),
                                    updatedAt = now,
                                )
                                onSave(result)
                                onNavigateBack()
                            },
                            enabled = title.isNotBlank() && content.isNotBlank(),
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

private fun parseCommaSeparated(rawValue: String): List<String> {
    return rawValue.split(",", "，")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
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
