package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    val palette = rememberSettingsPalette()
    var worldBookMaxEntriesText by rememberSaveable { mutableStateOf(assistant.worldBookMaxEntries.toString()) }
    var worldBookScanDepthText by rememberSaveable {
        mutableStateOf(assistant.worldBookScanDepth.toString())
    }
    var linkedWorldBookIds by rememberSaveable(
        assistant.id,
        assistant.linkedWorldBookIds.joinToString(separator = "|"),
    ) {
        mutableStateOf(assistant.linkedWorldBookIds.toSet())
    }

    val ownedEntries = worldBookEntries
        .filter { entry ->
            entry.scopeType == WorldBookScopeType.ASSISTANT && entry.scopeId == assistant.id
        }
        .sortedBy { it.title.ifBlank { it.id } }
    val globalEntries = worldBookEntries
        .filter { entry ->
            entry.scopeType == WorldBookScopeType.GLOBAL
        }
        .sortedBy { it.title.ifBlank { it.id } }
    val attachableEntries = worldBookEntries
        .filter { entry ->
            entry.scopeType == WorldBookScopeType.ATTACHABLE
        }

    val attachableBooks = remember(attachableEntries) {
        buildAttachableWorldBooks(attachableEntries)
    }
    val legacySelectedBookIds = remember(assistant.linkedWorldBookIds, attachableBooks) {
        attachableBooks.mapNotNull { book ->
            book.bookId.takeIf { currentBookId ->
                book.entries.any { entry -> entry.id in assistant.linkedWorldBookIds } &&
                    currentBookId.isNotBlank()
            }
        }.toSet()
    }
    var linkedWorldBookBookIds by rememberSaveable(
        assistant.id,
        assistant.linkedWorldBookBookIds.joinToString(separator = "|"),
        attachableBooks.joinToString(separator = "|") { it.bookId },
    ) {
        mutableStateOf((assistant.linkedWorldBookBookIds.toSet() + legacySelectedBookIds))
    }

    val looseAttachableEntries = attachableEntries
        .filter { entry -> entry.resolvedBookId().isBlank() }
        .sortedWith(
            compareByDescending<WorldBookEntry> { it.id in linkedWorldBookIds }
                .thenBy { it.title.ifBlank { it.id } },
        )
    val advancedAttachableEntries = remember(attachableBooks, looseAttachableEntries, linkedWorldBookBookIds, linkedWorldBookIds) {
        buildList {
            attachableBooks
                .filterNot { book -> book.bookId in linkedWorldBookBookIds }
                .forEach { book ->
                    addAll(book.entries)
                }
            addAll(looseAttachableEntries)
        }.distinctBy { it.id }
            .sortedWith(
                compareByDescending<WorldBookEntry> { it.id in linkedWorldBookIds }
                    .thenBy { it.sourceBookName.ifBlank { "~" } }
                    .thenBy { it.title.ifBlank { it.id } },
            )
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "扩展管理",
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
            item {
                AssistantSubPageHeader(
                    assistant = assistant,
                    overline = "扩展",
                )
            }

            item {
                AssistantSubsectionTitle(
                    title = "世界书联动",
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
                        OutlinedTextField(
                            value = worldBookScanDepthText,
                            onValueChange = { worldBookScanDepthText = it.filter(Char::isDigit) },
                            label = { Text("世界书扫描深度（scanDepth）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = outlineColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("默认 2，表示拼接最近 N 条消息参与关键词匹配") },
                        )
                    }
                }
            }

            if (globalEntries.isNotEmpty() || ownedEntries.isNotEmpty()) {
                item {
                    SettingsGroup {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "自动生效",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (globalEntries.isNotEmpty()) {
                                Text(
                                    text = "全局条目",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.body,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    globalEntries.forEach { entry ->
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
                            if (globalEntries.isNotEmpty() && ownedEntries.isNotEmpty()) {
                                HorizontalDivider(color = palette.border.copy(alpha = 0.35f))
                            }
                            if (ownedEntries.isNotEmpty()) {
                                Text(
                                    text = "当前助手专属",
                                    style = MaterialTheme.typography.bodySmall,
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
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "按书挂载",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (attachableBooks.isEmpty()) {
                            Text(
                                text = "暂无可挂载世界书",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.body,
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                attachableBooks.forEach { book ->
                                    val selected = book.bookId in linkedWorldBookBookIds
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            linkedWorldBookBookIds = if (selected) {
                                                linkedWorldBookBookIds - book.bookId
                                            } else {
                                                linkedWorldBookIds = linkedWorldBookIds - book.entries.map { it.id }.toSet()
                                                linkedWorldBookBookIds + book.bookId
                                            }
                                        },
                                        label = {
                                            Text("${book.name}（${book.entryCount}）")
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = palette.accentSoft,
                                            selectedLabelColor = palette.accent,
                                        ),
                                    )
                                }
                            }
                        }
                        Text(
                            text = "选择整本世界书后，这本世界书下的全部可挂载条目都会对当前助手生效。",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.body,
                        )
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
                            text = "高级条目控制",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (advancedAttachableEntries.isEmpty()) {
                            Text(
                                text = if (linkedWorldBookBookIds.isNotEmpty()) {
                                    "当前可挂载条目已由整本世界书覆盖。"
                                } else {
                                    "暂无可单独挂载条目"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.body,
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                advancedAttachableEntries.forEach { entry ->
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
                                                    entry.sourceBookName.trim().takeIf { it.isNotBlank() }?.let { bookName ->
                                                        append(" · ")
                                                        append(bookName)
                                                    }
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
                        Text(
                            text = "这里保留逐条挂载，用于兼容旧配置和少量精细控制场景；主入口仍建议按书挂载。",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.body,
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
                                        linkedWorldBookBookIds = linkedWorldBookBookIds.toList(),
                                        worldBookMaxEntries = parsePositiveIntOrDefaultForExtensions(
                                            rawValue = worldBookMaxEntriesText,
                                            defaultValue = assistant.worldBookMaxEntries,
                                        ),
                                        worldBookScanDepth = worldBookScanDepthText.trim().toIntOrNull()
                                            ?.coerceAtLeast(0)
                                            ?: assistant.worldBookScanDepth,
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

private data class AttachableWorldBook(
    val bookId: String,
    val name: String,
    val entries: List<WorldBookEntry>,
) {
    val entryCount: Int
        get() = entries.size
}

private fun buildAttachableWorldBooks(entries: List<WorldBookEntry>): List<AttachableWorldBook> {
    return entries
        .mapNotNull { entry ->
            entry.resolvedBookId()
                .takeIf { it.isNotBlank() }
                ?.let { resolvedBookId -> resolvedBookId to entry }
        }
        .groupBy(
            keySelector = { it.first },
            valueTransform = { it.second },
        )
        .map { (bookId, bookEntries) ->
            AttachableWorldBook(
                bookId = bookId,
                name = bookEntries.firstNotNullOfOrNull { entry ->
                    entry.sourceBookName.trim().takeIf { it.isNotBlank() }
                }.orEmpty().ifBlank { "未命名世界书" },
                entries = bookEntries.sortedWith(
                    compareBy<WorldBookEntry>(
                        { it.insertionOrder },
                        { it.createdAt },
                    ).thenByDescending { it.updatedAt },
                ),
            )
        }
        .sortedBy { it.name.lowercase() }
}

private fun parsePositiveIntOrDefaultForExtensions(
    rawValue: String,
    defaultValue: Int,
): Int {
    return rawValue.trim().toIntOrNull()?.takeIf { it > 0 } ?: defaultValue
}
