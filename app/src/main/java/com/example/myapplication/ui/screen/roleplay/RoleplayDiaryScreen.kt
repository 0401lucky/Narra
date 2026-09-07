package com.example.myapplication.ui.screen.roleplay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.rememberSystemHighTextContrastEnabled
import com.example.myapplication.ui.component.roleplay.rememberRoleplayDiaryAnnotatedString
import com.example.myapplication.ui.component.roleplay.stripRoleplayDiaryMarkers
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoleplayDiaryScreen(
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    settings: AppSettings,
    diaryEntries: List<RoleplayDiaryEntry>,
    isGeneratingDiary: Boolean,
    noticeMessage: String?,
    errorMessage: String?,
    onClearNoticeMessage: () -> Unit,
    onClearErrorMessage: () -> Unit,
    onGenerateDiary: () -> Unit,
    onOpenEntry: (entryId: String) -> Unit,
    onOpenMailbox: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    if (scenario == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = scheme.background) {
            Box(contentAlignment = Alignment.Center) { Text("当前场景不存在") }
        }
        return
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(noticeMessage) {
        noticeMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearNoticeMessage()
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearErrorMessage()
        }
    }
    val systemHighContrast = rememberSystemHighTextContrastEnabled()
    val mutedColor = if (settings.roleplayHighContrast || systemHighContrast) {
        scheme.onSurface
    } else {
        scheme.onSurfaceVariant
    }
    val characterName = scenario.characterDisplayNameOverride.trim()
        .ifBlank { assistant?.name?.trim().orEmpty() }
        .ifBlank { stringResource(R.string.roleplay_character_fallback) }
    val hasDiary = diaryEntries.isNotEmpty()
    var searchQuery by rememberSaveable(scenario.id) { mutableStateOf("") }
    var searchVisible by rememberSaveable(scenario.id) { mutableStateOf(false) }
    val normalizedQuery = searchQuery.trim()
    val filteredEntries = remember(diaryEntries, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            diaryEntries
        } else {
            diaryEntries.filter { entry ->
                entry.title.contains(normalizedQuery, ignoreCase = true) ||
                    entry.content.contains(normalizedQuery, ignoreCase = true) ||
                    entry.mood.contains(normalizedQuery, ignoreCase = true) ||
                    entry.weather.contains(normalizedQuery, ignoreCase = true) ||
                    entry.tags.any { it.contains(normalizedQuery, ignoreCase = true) }
            }
        }
    }
    // 按实际时间排列月份，避免“9 月”被字符串排序到“10 月”之前。
    val grouped = remember(filteredEntries, normalizedQuery) {
        if (normalizedQuery.isNotBlank()) {
            emptyList()
        } else {
            filteredEntries.groupBy { formatMonthBucket(it.createdAt) }
                .toList()
                .sortedByDescending { (_, entries) -> entries.maxOf { it.createdAt } }
        }
    }

    RoleplayDiaryPage(scenario.backgroundUri) {
        Column(Modifier.fillMaxSize().roleplayImmersivePadding(RoleplayImmersiveMode.NONE)) {
            RoleplayDiaryTopBar(
                title = "角色日记",
                subtitle = characterName,
                onNavigateBack = onNavigateBack,
                onOpenMailbox = onOpenMailbox,
            ) {
                if (hasDiary) {
                    DiaryIconButton(
                        icon = if (searchVisible) Lucide.X else Lucide.Search,
                        label = if (searchVisible) "关闭搜索" else "搜索",
                        onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) searchQuery = ""
                        },
                    )
                }
            }
            Surface(color = scheme.surface) {
                Column(Modifier.fillMaxWidth()) {
                    if (hasDiary || isGeneratingDiary) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = NarraUi.pageHorizontal, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when {
                                    isGeneratingDiary -> "正在写日记…"
                                    normalizedQuery.isNotEmpty() -> "找到 ${filteredEntries.size} 篇"
                                    else -> "共 ${diaryEntries.size} 篇"
                                },
                                modifier = Modifier.weight(1f).testTag("diary_count"),
                                style = MaterialTheme.typography.labelLarge,
                                color = mutedColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            NarraIconButton(
                                onClick = onGenerateDiary,
                                enabled = !isGeneratingDiary,
                                modifier = Modifier.size(NarraMinimumTouchTarget),
                            ) {
                                if (isGeneratingDiary) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp).semantics { contentDescription = "日记生成中" },
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Lucide.RefreshCw, "重新生成", modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = searchVisible && hasDiary,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索日记") },
                            leadingIcon = { Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    DiaryIconButton(Lucide.X, "清除搜索", { searchQuery = "" })
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = NarraUi.pageHorizontal, vertical = NarraUi.compactGap)
                                .testTag("diary_search"),
                            shape = RoundedCornerShape(NarraUi.smallRadius),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = scheme.surfaceContainerLow,
                                unfocusedContainerColor = scheme.surfaceContainerLow,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }

            if (!hasDiary) {
                EmptyDiaryPanel(isGeneratingDiary, onGenerateDiary)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("diary_list"),
                    contentPadding = PaddingValues(bottom = NarraUi.sectionGap),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.rowGap),
                ) {
                    if (normalizedQuery.isNotBlank()) {
                        if (filteredEntries.isEmpty()) {
                            item {
                                Surface(color = scheme.surface, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "没有找到相关日记",
                                        modifier = Modifier.padding(NarraUi.pageHorizontal),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = mutedColor,
                                    )
                                }
                            }
                        } else {
                            items(filteredEntries, key = { it.id }) { entry ->
                                RoleplayDiaryEntryCard(entry, mutedColor, { onOpenEntry(entry.id) })
                            }
                        }
                    } else {
                        grouped.forEach { (month, entries) ->
                            stickyHeader(key = "month-$month") {
                                Surface(color = scheme.background, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = month,
                                        modifier = Modifier.fillMaxWidth().padding(
                                            horizontal = NarraUi.pageHorizontal,
                                            vertical = NarraUi.rowGap,
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = mutedColor,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            items(entries, key = { it.id }) { entry ->
                                RoleplayDiaryEntryCard(entry, mutedColor, { onOpenEntry(entry.id) })
                            }
                        }
                    }
                }
            }
        }
        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).roleplayNavigationBarPadding(RoleplayImmersiveMode.NONE)
                .padding(horizontal = NarraUi.rowGap, vertical = NarraUi.compactGap),
        )
    }
}

@Composable
private fun EmptyDiaryPanel(
    isGeneratingDiary: Boolean,
    onGenerateDiary: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NarraUi.pageVertical),
        ) {
            Icon(Lucide.BookOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text("还没有日记", style = MaterialTheme.typography.titleMedium)
            NarraTextButton(onClick = onGenerateDiary, enabled = !isGeneratingDiary) {
                Text(if (isGeneratingDiary) "生成中…" else "写第一篇日记")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleplayDiaryEntryCard(
    entry: RoleplayDiaryEntry,
    mutedColor: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var revealMasked by rememberSaveable(entry.id) { mutableStateOf(false) }
    val annotated = rememberRoleplayDiaryAnnotatedString(
        text = entry.content,
        revealMasked = revealMasked,
        primaryText = scheme.onSurface,
        accent = scheme.primary,
    )
    val hasMaskedContent = remember(entry.content) { entry.content.contains("||") }
    val screenReaderContent = remember(entry.content, revealMasked) {
        stripRoleplayDiaryMarkers(entry.content, revealMasked)
    }
    val effectiveDateLabel = entry.dateLabel.ifBlank { formatFallbackDate(entry.createdAt) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = NarraUi.pageHorizontal),
        color = scheme.surface,
        shape = RoundedCornerShape(NarraUi.cardRadius),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(NarraUi.pageVertical),
            verticalArrangement = Arrangement.spacedBy(NarraUi.rowGap),
        ) {
            Text(
                text = effectiveDateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = mutedColor,
            )
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            RoleplayDiaryMetadata(entry, mutedColor, tagLimit = 4)
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp, letterSpacing = 0.sp),
                color = scheme.onSurface,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { contentDescription = screenReaderContent },
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap, Alignment.End),
            ) {
                if (hasMaskedContent) {
                    NarraTextButton(onClick = { revealMasked = !revealMasked }) {
                        Text(if (revealMasked) "隐藏涂黑" else "显示涂黑")
                    }
                }
                NarraTextButton(onClick = onClick) {
                    Text("阅读全文")
                    Icon(Lucide.ChevronRight, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private val monthFormatter by lazy { SimpleDateFormat("yyyy 年 M 月", Locale.SIMPLIFIED_CHINESE) }
private val fallbackDateFormatter by lazy { SimpleDateFormat("M 月 d 日 EEEE", Locale.SIMPLIFIED_CHINESE) }

internal fun formatMonthBucket(timestamp: Long): String = monthFormatter.format(Date(timestamp))

internal fun formatFallbackDate(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return fallbackDateFormatter.format(Date(timestamp))
}
