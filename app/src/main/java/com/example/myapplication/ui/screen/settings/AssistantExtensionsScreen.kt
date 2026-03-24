package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType

@Composable
fun AssistantExtensionsScreen(
    assistant: Assistant,
    worldBookEntries: List<WorldBookEntry>,
    onSave: (Assistant) -> Unit,
    onOpenWorldBookSettings: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val outlineColors = rememberSettingsOutlineColors()
    var worldBookMaxEntriesText by rememberSaveable { mutableStateOf(assistant.worldBookMaxEntries.toString()) }
    var linkedWorldBookIds by rememberSaveable { mutableStateOf(assistant.linkedWorldBookIds.toSet()) }
    val palette = rememberSettingsPalette()
    val ownedEntries = worldBookEntries
        .filter { entry ->
            entry.scopeType == WorldBookScopeType.ASSISTANT && entry.scopeId == assistant.id
        }
        .sortedBy { it.title.ifBlank { it.id } }
    val linkableEntries = worldBookEntries
        .filterNot { entry ->
            entry.scopeType == WorldBookScopeType.CONVERSATION ||
                (entry.scopeType == WorldBookScopeType.ASSISTANT && entry.scopeId == assistant.id)
        }
        .sortedWith(
            compareByDescending<WorldBookEntry> { it.id in linkedWorldBookIds }
                .thenBy { it.scopeType.ordinal }
                .thenBy { it.sourceBookName.ifBlank { "~" } }
                .thenBy { it.title.ifBlank { it.id } },
        )

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "扩展管理",
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = rememberSettingsPalette().background,
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
            item {
                AssistantWorkspaceIntro(
                    assistant = assistant,
                    overline = "扩展",
                    title = "把世界书挂到助手身上",
                    summary = "这里管理该助手可使用的世界书、显式挂载关系，以及每轮可注入的最大条数。",
                )
            }

            item {
                AssistantSubsectionTitle(
                    title = "世界书联动",
                    subtitle = "角色专属条目会自动生效，其他可挂载条目可以在这里手动绑到这个助手上。",
                )
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OutlinedTextField(
                            value = worldBookMaxEntriesText,
                            onValueChange = { worldBookMaxEntriesText = it.filter(Char::isDigit) },
                            label = { Text("世界书条数上限") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = outlineColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }

            if (ownedEntries.isNotEmpty()) {
                item {
                    SettingsGroup {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "当前角色专属世界书",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "这些条目已经直接绑在当前助手名下，不需要再额外挂载。",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = palette.body,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ownedEntries.forEach { entry ->
                                    FilterChip(
                                        selected = true,
                                        onClick = {},
                                        enabled = false,
                                        label = {
                                            Text(entry.title.ifBlank { "未命名条目" })
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = palette.subtleChip,
                                            selectedLabelColor = palette.subtleChipContent,
                                            disabledSelectedContainerColor = palette.subtleChip,
                                            disabledLabelColor = palette.subtleChipContent,
                                        ),
                                    )
                                }
                            }
                        }
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
                            text = "手动挂载其他世界书",
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "这里会显示全局条目、可挂载条目，以及其他助手的专属条目。挂上后当前助手也能命中它们。",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = palette.body,
                        )
                        if (linkableEntries.isEmpty()) {
                            Text(
                                text = "当前没有可挂载世界书，先去世界书页导入或新建。",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = palette.body,
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                linkableEntries.forEach { entry ->
                                    val selected = entry.id in linkedWorldBookIds
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            linkedWorldBookIds = if (selected) {
                                                linkedWorldBookIds - entry.id
                                            } else {
                                                linkedWorldBookIds + entry.id
                                            }
                                        },
                                        label = {
                                            Text(
                                                buildString {
                                                    append(entry.title.ifBlank { "未命名条目" })
                                                    append(" · ")
                                                    append(entry.scopeType.label)
                                                },
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
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AnimatedSettingButton(
                            text = "管理世界书条目",
                            onClick = onOpenWorldBookSettings,
                            enabled = true,
                            isPrimary = false,
                        )
                        AnimatedSettingButton(
                            text = "保存扩展设置",
                            onClick = {
                                onSave(
                                    assistant.copy(
                                        linkedWorldBookIds = linkedWorldBookIds.toList(),
                                        worldBookMaxEntries = parsePositiveIntOrDefaultForExtensions(
                                            rawValue = worldBookMaxEntriesText,
                                            defaultValue = assistant.worldBookMaxEntries,
                                        ),
                                    ),
                                )
                                onNavigateBack()
                            },
                            enabled = true,
                            isPrimary = true,
                        )
                    }
                }
            }
        }
    }
}

private fun parsePositiveIntOrDefaultForExtensions(
    rawValue: String,
    defaultValue: Int,
): Int {
    return rawValue.trim().toIntOrNull()?.takeIf { it > 0 } ?: defaultValue
}
