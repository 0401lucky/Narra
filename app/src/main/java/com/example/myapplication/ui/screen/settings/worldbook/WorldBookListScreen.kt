package com.example.myapplication.ui.screen.settings.worldbook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.ui.component.worldbook.looksLikeWorldBookRegexLiteral
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsHintCard
import com.example.myapplication.ui.screen.settings.SettingsPageIntro
import com.example.myapplication.ui.screen.settings.SettingsPalette
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsSectionHeader
import com.example.myapplication.ui.screen.settings.SettingsStatusPill
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsOutlineColors
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette

@Composable
fun WorldBookListScreen(
    entries: List<WorldBookEntry>,
    onOpenBook: (String) -> Unit,
    onOpenEntryEdit: (String) -> Unit,
    onAddEntry: () -> Unit,
    onOpenImport: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val books = buildWorldBookBooks(entries)
    val standaloneEntries = entries
        .filter { it.sourceBookName.isBlank() }
        .sortedWith(
            compareBy<WorldBookEntry>(
                { it.insertionOrder },
                { it.createdAt },
            ).thenByDescending { it.updatedAt },
        )

    val filteredBooks = books.filter { book ->
        book.matchesSearch(searchQuery)
    }
    val filteredStandaloneEntries = standaloneEntries.filter { entry ->
        entry.matchesSearch(searchQuery)
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = "世界书",
                onNavigateBack = onNavigateBack,
                actionLabel = "新增",
                onAction = onAddEntry,
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
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsPageIntro(
                    title = "世界书",
                )
            }

            item {
                AnimatedSettingButton(
                    text = "导入世界书",
                    onClick = onOpenImport,
                    enabled = true,
                    isPrimary = false,
                    leadingIcon = {
                        Icon(Icons.Default.Upload, contentDescription = null)
                    },
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    label = { Text("搜索世界书") },
                    placeholder = { Text("搜索书名、条目标题、内容或关键词") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    colors = rememberSettingsOutlineColors(),
                )
            }

            if (filteredBooks.isEmpty() && filteredStandaloneEntries.isEmpty()) {
                item {
                    SettingsHintCard(
                        title = if (searchQuery.isBlank()) "还没有世界书" else "没有匹配结果",
                        body = if (searchQuery.isBlank()) {
                            "导入带世界书的角色卡后，这里会先显示整本世界书；也可以直接点右上角新增单条世界书。"
                        } else {
                            "换个关键词试试，或者清空搜索条件。"
                        },
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                    )
                }
            } else {
                if (filteredBooks.isNotEmpty()) {
                    item {
                        SettingsSectionHeader(
                            title = "导入的世界书",
                            description = "",
                        )
                    }
                    items(filteredBooks, key = { it.id }) { book ->
                        WorldBookBookCard(
                            book = book,
                            onClick = { onOpenBook(book.id) },
                        )
                    }
                }

                if (filteredStandaloneEntries.isNotEmpty()) {
                    item {
                        SettingsSectionHeader(
                            title = "独立条目",
                            description = "",
                        )
                    }
                    items(filteredStandaloneEntries, key = { it.id }) { entry ->
                        WorldBookEntryCard(
                            entry = entry,
                            onClick = { onOpenEntryEdit(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorldBookBookCard(
    book: WorldBookBook,
    onClick: () -> Unit,
) {
    val palette = rememberSettingsPalette()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = palette.surface,
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.4f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = palette.subtleChip,
                    contentColor = palette.subtleChipContent,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = book.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = book.previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsStatusPill(
                    text = "${book.entryCount} 条条目",
                    containerColor = palette.surfaceTint,
                    contentColor = palette.body,
                )
                if (book.enabledEntryCount != book.entryCount) {
                    SettingsStatusPill(
                        text = "启用 ${book.enabledEntryCount}",
                        containerColor = palette.surfaceTint,
                        contentColor = palette.body,
                    )
                }
                if (book.regexEntryCount > 0) {
                    SettingsStatusPill(
                        text = "正则 ${book.regexEntryCount}",
                        containerColor = palette.accentSoft,
                        contentColor = palette.accent,
                    )
                }
            }
        }
    }
}

@Composable
internal fun WorldBookEntryCard(
    entry: WorldBookEntry,
    onClick: () -> Unit,
) {
    val palette = rememberSettingsPalette()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = palette.surface,
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.4f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = entry.title.ifBlank { "未命名条目" },
                style = MaterialTheme.typography.titleMedium,
                color = palette.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = entry.content.ifBlank { "暂无内容" },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.body,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            EntryChipRow(entry = entry, palette = palette)
        }
    }
}

/**
 * 条目卡底部 chip 行：最多 3 枚（作用域 / 关键词预览 / 状态）。
 *
 * - 作用域：永远显示，颜色固定为 subtleChip（弱强调）
 * - 关键词预览：有真词时显示，合并为 "关键词：主角 · 配角 · 路人"
 * - 状态：最多 1 枚，优先级 停用 > 常驻注入 > 含正则
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryChipRow(
    entry: WorldBookEntry,
    palette: SettingsPalette,
) {
    val preview = firstRealKeywords(entry).takeIf { it.isNotEmpty() }
        ?.joinToString(separator = " · ")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SettingsStatusPill(
            text = entry.scopeType.label,
            containerColor = palette.subtleChip,
            contentColor = palette.subtleChipContent,
        )
        if (preview != null) {
            SettingsStatusPill(
                text = "关键词：$preview",
                containerColor = palette.surfaceTint,
                contentColor = palette.body,
            )
        }
        when {
            !entry.enabled -> SettingsStatusPill(
                text = "已停用",
                containerColor = palette.surfaceTint,
                contentColor = palette.body,
            )
            entry.alwaysActive -> SettingsStatusPill(
                text = "常驻注入",
                containerColor = palette.accentSoft,
                contentColor = palette.accent,
            )
            entryHasRegexKeyword(entry) -> SettingsStatusPill(
                text = "含正则",
                containerColor = palette.surfaceTint,
                contentColor = palette.body,
            )
        }
    }
}

internal data class WorldBookBook(
    val id: String,
    val name: String,
    val entries: List<WorldBookEntry>,
) {
    val entryCount: Int get() = entries.size
    val enabledEntryCount: Int get() = entries.count { it.enabled }
    val regexEntryCount: Int get() = entries.count(::entryHasRegexKeyword)
    val previewText: String
        get() = entries.take(3)
            .joinToString(separator = " · ") { entry ->
                entry.title.ifBlank { "未命名条目" }
            }
}

internal fun buildWorldBookBooks(entries: List<WorldBookEntry>): List<WorldBookBook> {
    return entries
        .mapNotNull { entry ->
            entry.resolvedBookId()
                .takeIf { it.isNotBlank() && entry.sourceBookName.isNotBlank() }
                ?.let { bookId -> bookId to entry }
        }
        .groupBy(
            keySelector = { it.first },
            valueTransform = { it.second },
        )
        .map { (bookId, bookEntries) ->
            WorldBookBook(
                id = bookId,
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
        .sortedWith(
            compareBy<WorldBookBook>(
                { it.entries.firstOrNull()?.createdAt ?: Long.MAX_VALUE },
                { it.name.lowercase() },
            ),
        )
}

private fun WorldBookBook.matchesSearch(query: String): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) {
        return true
    }
    return name.contains(normalizedQuery, ignoreCase = true) ||
        entries.any { it.matchesSearch(normalizedQuery) }
}

private fun entryHasRegexKeyword(entry: WorldBookEntry): Boolean {
    return (entry.keywords + entry.aliases + entry.secondaryKeywords)
        .any(::looksLikeWorldBookRegexLiteral)
}
