package com.example.myapplication.ui.screen.roleplay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.rememberSystemHighTextContrastEnabled
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassPalette
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassSurface
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.rememberRoleplayDiaryAnnotatedString
import com.example.myapplication.ui.component.roleplay.stripRoleplayDiaryMarkers
import java.text.SimpleDateFormat
import java.util.Calendar
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
    onNavigateBack: () -> Unit,
) {
    if (scenario == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("当前场景不存在")
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
    val effectiveHighContrast = settings.roleplayHighContrast || rememberSystemHighTextContrastEnabled()
    val backdropState = rememberImmersiveBackdropState(
        backgroundUri = scenario.backgroundUri,
        highContrast = effectiveHighContrast,
    )
    val palette = backdropState.palette
    val characterName = scenario.characterDisplayNameOverride.trim()
        .ifBlank { assistant?.name?.trim().orEmpty() }
        .ifBlank { stringResource(R.string.roleplay_character_fallback) }

    val scrimAlpha = if (palette.onGlass.luminance() > 0.5f) 0.36f else 0.18f
    val hasDiary = diaryEntries.isNotEmpty()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }

    val normalizedQuery = searchQuery.trim()
    val filteredEntries = remember(diaryEntries, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            diaryEntries
        } else {
            val needle = normalizedQuery.lowercase(Locale.ROOT)
            diaryEntries.filter { entry ->
                entry.title.lowercase(Locale.ROOT).contains(needle) ||
                    entry.content.lowercase(Locale.ROOT).contains(needle) ||
                    entry.mood.lowercase(Locale.ROOT).contains(needle) ||
                    entry.weather.lowercase(Locale.ROOT).contains(needle) ||
                    entry.tags.any { it.lowercase(Locale.ROOT).contains(needle) }
            }
        }
    }
    // 搜索为空时：按月分组（带 sticky header）；有搜索时扁平展示
    val grouped = remember(filteredEntries, normalizedQuery) {
        if (normalizedQuery.isNotBlank()) {
            emptyList()
        } else {
            filteredEntries
                .groupBy { formatMonthBucket(it.createdAt) }
                .toList()
                .sortedByDescending { it.first }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        RoleplaySceneBackground(
            backdropState = backdropState,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部标题栏
            ImmersiveGlassSurface(
                backdropState = backdropState,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                blurRadius = 22.dp,
                overlayColor = palette.panelTintStrong.copy(alpha = 0.76f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NarraIconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.common_back),
                                tint = palette.onGlass,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "角色日记",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.onGlass,
                                )
                                if (hasDiary) {
                                    Text(
                                        text = "· 共 ${diaryEntries.size} 篇",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.onGlassMuted,
                                    )
                                }
                            }
                            Text(
                                text = characterName,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.onGlassMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (hasDiary) {
                            NarraIconButton(
                                onClick = { searchVisible = !searchVisible; if (!searchVisible) searchQuery = "" },
                            ) {
                                Icon(
                                    imageVector = if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = if (searchVisible) "关闭搜索" else "搜索",
                                    tint = palette.onGlass,
                                )
                            }
                        }
                        if (hasDiary || isGeneratingDiary) {
                            NarraTextButton(
                                onClick = onGenerateDiary,
                                enabled = !isGeneratingDiary,
                            ) {
                                if (isGeneratingDiary) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = palette.onGlass,
                                    )
                                    Text(
                                        text = "生成中…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.onGlassMuted,
                                    )
                                } else {
                                    Text("重新生成")
                                }
                            }
                        }
                    }
                    // 搜索框：动画展开
                    AnimatedVisibility(
                        visible = searchVisible && hasDiary,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "搜索标题 / 内容 / 心情 / 标签",
                                    color = palette.onGlassMuted,
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = palette.panelTintStrong.copy(alpha = 0.6f),
                                unfocusedContainerColor = palette.panelTintStrong.copy(alpha = 0.4f),
                                focusedTextColor = palette.onGlass,
                                unfocusedTextColor = palette.onGlass,
                                cursorColor = palette.characterAccent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }

            if (!hasDiary) {
                EmptyDiaryPanel(
                    backdropState = backdropState,
                    isGeneratingDiary = isGeneratingDiary,
                    onGenerateDiary = onGenerateDiary,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (normalizedQuery.isNotBlank()) {
                        if (filteredEntries.isEmpty()) {
                            item {
                                Text(
                                    text = "没有命中的日记。换个关键词试试？",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.onGlassMuted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
                                )
                            }
                        } else {
                            items(filteredEntries) { entry ->
                                RoleplayDiaryEntryCard(
                                    entry = entry,
                                    backdropState = backdropState,
                                    onClick = { onOpenEntry(entry.id) },
                                )
                            }
                        }
                    } else {
                        grouped.forEach { (month, entries) ->
                            stickyHeader(key = "month-$month") {
                                MonthHeader(label = month, backdropState = backdropState)
                            }
                            items(entries, key = { it.id }) { entry ->
                                RoleplayDiaryEntryCard(
                                    entry = entry,
                                    backdropState = backdropState,
                                    onClick = { onOpenEntry(entry.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

// ---------- 日期格式化辅助 ----------

@Composable
private fun EmptyDiaryPanel(
    backdropState: ImmersiveBackdropState,
    isGeneratingDiary: Boolean,
    onGenerateDiary: () -> Unit,
) {
    val palette = backdropState.palette
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        blurRadius = 24.dp,
        overlayColor = palette.readingSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.AutoStories,
                contentDescription = null,
                tint = palette.characterAccent,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "还没有生成过日记",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.onGlass,
            )
            Text(
                text = "会结合当前角色设定、长期上下文和最近剧情，生成一组更私密的角色日记。",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                color = palette.onGlassMuted,
                textAlign = TextAlign.Center,
            )
            NarraTextButton(
                onClick = onGenerateDiary,
                enabled = !isGeneratingDiary,
                modifier = Modifier.fillMaxWidth(0.65f),
            ) {
                if (isGeneratingDiary) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(14.dp),
                        strokeWidth = 2.dp,
                        color = palette.onGlass,
                    )
                    Text("生成中…")
                } else {
                    Text("开始生成")
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    label: String,
    backdropState: ImmersiveBackdropState,
) {
    val palette = backdropState.palette
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = palette.panelTintStrong.copy(alpha = 0.88f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = palette.onGlass,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleplayDiaryEntryCard(
    entry: RoleplayDiaryEntry,
    backdropState: ImmersiveBackdropState,
    onClick: () -> Unit,
) {
    val palette = backdropState.palette
    var revealMasked by rememberSaveable(entry.id) { mutableStateOf(false) }
    val annotated = rememberRoleplayDiaryAnnotatedString(
        text = entry.content,
        revealMasked = revealMasked,
        primaryText = palette.onGlass,
        accent = palette.characterAccent,
    )
    val hasMaskedContent = remember(entry.content) { entry.content.contains("||") }
    val screenReaderContent = remember(entry.content, revealMasked) {
        stripRoleplayDiaryMarkers(entry.content, revealMasked = revealMasked)
    }
    val hasMetadata = entry.mood.isNotBlank() || entry.weather.isNotBlank() || entry.tags.isNotEmpty()
    val effectiveDateLabel = entry.dateLabel.ifBlank { formatFallbackDate(entry.createdAt) }

    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        blurRadius = 24.dp,
        overlayColor = palette.readingSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 标题 + 日期
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.onGlass,
                )
                if (effectiveDateLabel.isNotBlank()) {
                    Text(
                        text = effectiveDateLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onGlassMuted,
                    )
                }
            }
            // 心情 / 天气 / 标签 chip
            if (hasMetadata) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (entry.mood.isNotBlank()) {
                        DiaryChip(text = "· ${entry.mood}", palette = palette, accented = true)
                    }
                    if (entry.weather.isNotBlank()) {
                        DiaryChip(text = entry.weather, palette = palette, accented = false)
                    }
                    entry.tags.take(4).forEach { tag ->
                        DiaryChip(text = "#$tag", palette = palette, accented = false)
                    }
                }
            }
            // 正文（摘要展示，最多 6 行）
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 28.sp,
                    letterSpacing = 0.2.sp,
                ),
                color = palette.onGlass,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics {
                    contentDescription = screenReaderContent
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasMaskedContent) {
                    NarraTextButton(onClick = { revealMasked = !revealMasked }) {
                        Text(if (revealMasked) "隐藏涂黑" else "显示涂黑")
                    }
                }
                NarraTextButton(onClick = onClick) {
                    Text("阅读全文")
                }
            }
        }
    }
}

@Composable
private fun DiaryChip(
    text: String,
    palette: ImmersiveGlassPalette,
    accented: Boolean,
) {
    val bg = if (accented) {
        palette.characterAccent.copy(alpha = 0.22f)
    } else {
        palette.chipTint.copy(alpha = 0.55f)
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = palette.onGlass,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------- 日期格式化辅助 ----------

private val monthFormatter by lazy {
    SimpleDateFormat("yyyy 年 M 月", Locale.SIMPLIFIED_CHINESE)
}

private val fallbackDateFormatter by lazy {
    SimpleDateFormat("M 月 d 日 EEEE", Locale.SIMPLIFIED_CHINESE)
}

internal fun formatMonthBucket(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    return monthFormatter.format(calendar.time)
}

internal fun formatFallbackDate(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return fallbackDateFormatter.format(Date(timestamp))
}
