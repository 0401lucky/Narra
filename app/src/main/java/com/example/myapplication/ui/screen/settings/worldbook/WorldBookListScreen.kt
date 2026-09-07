package com.example.myapplication.ui.screen.settings.worldbook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.ui.component.TopAppSnackbarHost
import com.example.myapplication.ui.component.worldbook.looksLikeWorldBookRegexLiteral
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsPageIntro
import com.example.myapplication.ui.screen.settings.SettingsHelpButton
import com.example.myapplication.ui.screen.settings.SettingsPalette
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsSectionHeader
import com.example.myapplication.ui.screen.settings.SettingsStatusPill
import com.example.myapplication.ui.screen.settings.SettingsTopBar
import com.example.myapplication.ui.screen.settings.rememberSettingsOutlineColors
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette
import com.example.myapplication.ui.screen.settings.rememberSettingsSnackbarHostState
import kotlinx.coroutines.delay
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Upload
import com.composables.icons.lucide.X

@Composable
fun WorldBookListScreen(
    entries: List<WorldBookEntry>,
    onOpenBook: (String) -> Unit,
    onOpenEntryEdit: (String) -> Unit,
    onAddEntry: () -> Unit,
    onOpenImport: () -> Unit,
    onNavigateBack: () -> Unit,
    uiMessage: String? = null,
    onConsumeMessage: () -> Unit = {},
) {
    val palette = rememberSettingsPalette()
    // 列表页只处理"删除"关键字的消息；"重命名"由书详情页显示
    val snackbarHostState = rememberSettingsSnackbarHostState(
        message = uiMessage?.takeIf { it.contains("删除") },
        onConsumeMessage = onConsumeMessage,
    )
    // 不会在本页显示的 message（如"世界书已保存"）3s 后兜底 consume，
    // 避免 ViewModel 事件通道被卡死（见 T15-C8）
    LaunchedEffect(uiMessage) {
        val message = uiMessage ?: return@LaunchedEffect
        if (!message.contains("删除")) {
            delay(3_000)
            onConsumeMessage()
        }
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var scopeFilter by rememberSaveable(stateSaver = WorldBookListScopeFilterSaver) {
        mutableStateOf(WorldBookListScopeFilter.ALL)
    }
    var statusFilter by rememberSaveable(stateSaver = WorldBookListStatusFilterSaver) {
        mutableStateOf(WorldBookListStatusFilter.ALL)
    }
    var bookIdFilter by rememberSaveable { mutableStateOf("") }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    val postFilterEntries = remember(entries, searchQuery, scopeFilter, statusFilter, bookIdFilter) {
        filterEntries(
            entries = entries,
            search = searchQuery,
            scope = scopeFilter,
            status = statusFilter,
            bookIdFilter = bookIdFilter,
        )
    }
    val filteredBooks = remember(postFilterEntries) { buildWorldBookBooks(postFilterEntries) }
    val filteredStandaloneEntries = remember(postFilterEntries) {
        postFilterEntries
            .filter { it.resolvedBookId().isBlank() }
            .sortedWith(
                compareBy<WorldBookEntry>(
                    { it.insertionOrder },
                    { it.createdAt },
                ).thenByDescending { it.updatedAt },
            )
    }
    val allBookOptions = remember(entries) {
        buildList {
            add("" to "全部")
            buildWorldBookBooks(entries).forEach { book -> add(book.id to book.name) }
        }
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
        snackbarHost = { TopAppSnackbarHost(hostState = snackbarHostState) },
        containerColor = palette.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("worldbook_list")
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = SettingsScreenPadding,
                top = 4.dp,
                end = SettingsScreenPadding,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "importButton") {
                AnimatedSettingButton(
                    text = "导入世界书",
                    onClick = onOpenImport,
                    enabled = true,
                    isPrimary = false,
                    leadingIcon = {
                        Icon(Lucide.Upload, contentDescription = null)
                    },
                )
            }

            stickyHeader(key = "searchAndFilter") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.background)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("搜索世界书") },
                        placeholder = { Text("书名、内容或关键词") },
                        leadingIcon = {
                            Icon(Lucide.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Lucide.X,
                                        contentDescription = "清空搜索",
                                    )
                                }
                            }
                        },
                        colors = rememberSettingsOutlineColors(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${postFilterEntries.size} 条条目",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.body,
                        )
                        TextButton(
                            onClick = { showFilters = !showFilters },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(Lucide.SlidersHorizontal, contentDescription = null, modifier = Modifier.size(20.dp))
                            val count = activeFilterCount(scopeFilter, statusFilter, bookIdFilter)
                            Text(if (count > 0) "筛选 ($count)" else "筛选", modifier = Modifier.padding(start = 8.dp))
                        }
                        SettingsHelpButton("世界书", "搜索包含书名、条目标题、正文与关键词。作用域、状态和所属世界书筛选同时生效。")
                    }
                }
            }
            if (showFilters) {
                item(key = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorldBookFilterChipRow(
                        label = "作用域",
                        options = WorldBookListScopeFilter.values()
                            .map { it.label to (it == scopeFilter) },
                        onSelect = { scopeFilter = WorldBookListScopeFilter.values()[it] },
                    )
                    WorldBookFilterChipRow(
                        label = "状态",
                        options = WorldBookListStatusFilter.values()
                            .map { it.label to (it == statusFilter) },
                        onSelect = { statusFilter = WorldBookListStatusFilter.values()[it] },
                    )
                    if (allBookOptions.size > 1) {
                        WorldBookFilterChipRow(
                            label = "书",
                            options = allBookOptions.map { (id, name) -> name to (id == bookIdFilter) },
                            onSelect = { bookIdFilter = allBookOptions[it].first },
                        )
                    }
                    if (activeFilterCount(scopeFilter, statusFilter, bookIdFilter) > 0) {
                        TextButton(onClick = {
                            scopeFilter = WorldBookListScopeFilter.ALL
                            statusFilter = WorldBookListStatusFilter.ALL
                            bookIdFilter = ""
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("清除筛选") }
                    }
                    }
                }
            }

            if (filteredBooks.isEmpty() && filteredStandaloneEntries.isEmpty()) {
                val variant = when {
                    entries.isEmpty() -> WorldBookEmptyVariant.GLOBAL_EMPTY
                    searchQuery.isNotBlank() -> WorldBookEmptyVariant.SEARCH_EMPTY
                    activeFilterCount(scopeFilter, statusFilter, bookIdFilter) > 0 ->
                        WorldBookEmptyVariant.FILTERED_EMPTY
                    else -> WorldBookEmptyVariant.GLOBAL_EMPTY
                }
                item(key = "emptyState") {
                    WorldBookListEmptyState(
                        variant = variant,
                        onClear = when (variant) {
                            WorldBookEmptyVariant.SEARCH_EMPTY -> { { searchQuery = "" } }
                            WorldBookEmptyVariant.FILTERED_EMPTY -> {
                                {
                                    scopeFilter = WorldBookListScopeFilter.ALL
                                    statusFilter = WorldBookListStatusFilter.ALL
                                    bookIdFilter = ""
                                }
                            }
                            WorldBookEmptyVariant.GLOBAL_EMPTY -> { {} }
                        },
                    )
                }
            } else {
                if (filteredBooks.isNotEmpty()) {
                    stickyHeader(key = "booksHeader") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.background)
                                .padding(vertical = 2.dp),
                        ) {
                            SettingsSectionHeader(
                                title = "导入的世界书",
                                description = "",
                            )
                        }
                    }
                    items(filteredBooks, key = { "book-${it.id}" }) { book ->
                        WorldBookBookCard(
                            book = book,
                            onClick = { onOpenBook(book.id) },
                        )
                    }
                }

                if (filteredStandaloneEntries.isNotEmpty()) {
                    stickyHeader(key = "standaloneHeader") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.background)
                                .padding(vertical = 2.dp),
                        ) {
                            SettingsSectionHeader(
                                title = "独立条目",
                                description = "",
                            )
                        }
                    }
                    items(filteredStandaloneEntries, key = { "entry-${it.id}" }) { entry ->
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

/**
 * 横向可滑动的一行筛选 chip：左侧 label + 右侧 LazyRow of FilterChip。
 * 互斥单选，onSelect 传回选中索引。
 */
@Composable
private fun WorldBookFilterChipRow(
    label: String,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    val palette = rememberSettingsPalette()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.body,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(options) { index, (optionLabel, selected) ->
                FilterChip(
                    modifier = Modifier.heightIn(min = 48.dp),
                    selected = selected,
                    onClick = { onSelect(index) },
                    label = { Text(optionLabel) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.accentSoft,
                        selectedLabelColor = palette.accent,
                    ),
                )
            }
        }
    }
}

private val WorldBookListScopeFilterSaver: Saver<WorldBookListScopeFilter, String> = Saver(
    save = { it.name },
    restore = { WorldBookListScopeFilter.valueOf(it) },
)

private val WorldBookListStatusFilterSaver: Saver<WorldBookListStatusFilter, String> = Saver(
    save = { it.name },
    restore = { WorldBookListStatusFilter.valueOf(it) },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorldBookBookCard(
    book: WorldBookBook,
    onClick: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val enabledCount = book.enabledEntryCount
    val hasRegex = book.regexEntryCount > 0
    val latestUpdate = book.entries.maxOfOrNull { it.updatedAt } ?: 0L
    val relative = formatRelativeTime(latestUpdate)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = palette.surface,
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.4f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Icon(
                imageVector = Lucide.BookOpen,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.padding(start = 16.dp, top = 18.dp).size(24.dp),
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.previewText.isNotBlank()) {
                    Text(
                        text = book.previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("${book.entryCount} 条条目", style = MaterialTheme.typography.labelMedium, color = palette.body)
                    if (enabledCount != book.entryCount) {
                        SettingsStatusPill(
                            text = "启用 $enabledCount",
                            containerColor = palette.accentSoft,
                            contentColor = palette.accent,
                        )
                    }
                    if (relative.isNotEmpty()) {
                        Text(relative, style = MaterialTheme.typography.labelMedium, color = palette.body)
                    }
                    if (hasRegex) {
                        SettingsStatusPill(
                            text = "含正则",
                            containerColor = palette.accentSoft,
                            contentColor = palette.accent,
                        )
                    }
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
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
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
                maxLines = 2,
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
            Text(
                text = "关键词：$preview",
                style = MaterialTheme.typography.labelMedium,
                color = palette.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
                .takeIf { it.isNotBlank() }
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

private fun entryHasRegexKeyword(entry: WorldBookEntry): Boolean {
    return (entry.keywords + entry.aliases + entry.secondaryKeywords)
        .any(::looksLikeWorldBookRegexLiteral)
}

/**
 * 列表页空态的三种来源。
 * - GLOBAL_EMPTY：整个仓库无世界书
 * - FILTERED_EMPTY：设了筛选但没数据
 * - SEARCH_EMPTY：搜索无结果
 */
private enum class WorldBookEmptyVariant { GLOBAL_EMPTY, FILTERED_EMPTY, SEARCH_EMPTY }

@Composable
private fun WorldBookListEmptyState(
    variant: WorldBookEmptyVariant,
    onClear: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val spec = when (variant) {
        WorldBookEmptyVariant.GLOBAL_EMPTY -> EmptyStateSpec(
            icon = Lucide.BookOpen,
            title = "还没有世界书",
            body = "",
            actionLabel = null,
        )
        WorldBookEmptyVariant.FILTERED_EMPTY -> EmptyStateSpec(
            icon = Lucide.SlidersHorizontal,
            title = "当前筛选下没有条目",
            body = "",
            actionLabel = "清筛选",
        )
        WorldBookEmptyVariant.SEARCH_EMPTY -> EmptyStateSpec(
            icon = Lucide.Search,
            title = "没有匹配结果",
            body = "",
            actionLabel = "清空搜索",
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = spec.icon,
            contentDescription = spec.title,
            tint = palette.body,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = spec.title,
            style = MaterialTheme.typography.titleSmall,
            color = palette.title,
        )
        Text(
            text = spec.body,
            style = MaterialTheme.typography.bodySmall,
            color = palette.body,
            textAlign = TextAlign.Center,
        )
        if (spec.actionLabel != null) {
            TextButton(onClick = onClear) {
                Text(text = spec.actionLabel, color = palette.accent)
            }
        }
    }
}

private data class EmptyStateSpec(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val body: String,
    val actionLabel: String?,
)
