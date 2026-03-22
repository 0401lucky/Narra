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
                    summary = "这里管理该助手关联的世界书和每轮可注入的最大条数。",
                )
            }

            item {
                AssistantSubsectionTitle(
                    title = "世界书联动",
                    subtitle = "把与这个助手强相关的设定挂进来，让命中更稳定。",
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
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            worldBookEntries.forEach { entry ->
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
                                        Text(entry.title.ifBlank { "未命名条目" })
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = rememberSettingsPalette().accentSoft,
                                        selectedLabelColor = rememberSettingsPalette().accent,
                                    ),
                                )
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
