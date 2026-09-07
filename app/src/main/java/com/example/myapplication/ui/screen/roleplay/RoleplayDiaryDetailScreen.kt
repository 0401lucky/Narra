package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.rememberSystemHighTextContrastEnabled
import com.example.myapplication.ui.component.roleplay.rememberRoleplayDiaryAnnotatedString
import com.example.myapplication.ui.component.roleplay.stripRoleplayDiaryMarkers
import com.example.myapplication.ui.theme.NarraUi

@Composable
fun RoleplayDiaryDetailScreen(
    scenario: RoleplayScenario?,
    settings: AppSettings,
    diaryEntries: List<RoleplayDiaryEntry>,
    entryId: String,
    onOpenMailbox: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    if (scenario == null || diaryEntries.isEmpty()) {
        Surface(modifier = Modifier.fillMaxSize(), color = scheme.background) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (scenario == null) "当前场景不存在" else "日记列表为空")
            }
        }
        return
    }

    // 以条目身份保存阅读位置，重新生成或列表缩短时不会使用过期下标。
    var currentEntryId by rememberSaveable(scenario.id, entryId) { mutableStateOf(entryId) }
    val currentIndex = diaryEntries.indexOfFirst { it.id == currentEntryId }.coerceAtLeast(0)
    val entry = diaryEntries[currentIndex]
    val listState = rememberLazyListState()
    LaunchedEffect(entry.id) { listState.scrollToItem(0) }

    val systemHighContrast = rememberSystemHighTextContrastEnabled()
    val mutedColor = if (settings.roleplayHighContrast || systemHighContrast) {
        scheme.onSurface
    } else {
        scheme.onSurfaceVariant
    }
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

    RoleplayDiaryPage(scenario.backgroundUri) {
        Column(Modifier.fillMaxSize().roleplayImmersivePadding(RoleplayImmersiveMode.NONE)) {
            RoleplayDiaryTopBar(
                title = "角色日记",
                subtitle = effectiveDateLabel,
                onNavigateBack = onNavigateBack,
                onOpenMailbox = onOpenMailbox,
            )
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                color = scheme.surface,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("diary_reader"),
                    contentPadding = PaddingValues(
                        horizontal = NarraUi.pageHorizontal,
                        vertical = NarraUi.sectionGap,
                    ),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.sectionGap),
                ) {
                    item(key = "title-${entry.id}") {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            modifier = Modifier.testTag("diary_entry_title"),
                        )
                    }
                    if (entry.weather.isNotBlank() || entry.mood.isNotBlank() || entry.tags.isNotEmpty()) {
                        item(key = "metadata-${entry.id}") {
                            RoleplayDiaryMetadata(entry, mutedColor, tagLimit = 5)
                        }
                    }
                    if (hasMaskedContent) {
                        item(key = "mask-${entry.id}") {
                            NarraTextButton(
                                onClick = { revealMasked = !revealMasked },
                                contentPadding = PaddingValues(horizontal = 0.dp),
                            ) {
                                Icon(
                                    if (revealMasked) Lucide.EyeOff else Lucide.Eye,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = NarraUi.compactGap).size(18.dp),
                                )
                                Text(if (revealMasked) "隐藏涂黑" else "显示涂黑")
                            }
                        }
                    }
                    item(key = "body-${entry.id}") {
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 17.sp,
                                lineHeight = 30.sp,
                                letterSpacing = 0.sp,
                            ),
                            color = scheme.onSurface,
                            modifier = Modifier.testTag("diary_entry_body")
                                .semantics { contentDescription = screenReaderContent },
                        )
                    }
                }
            }
            Surface(color = scheme.surface) {
                Column {
                    HorizontalDivider(color = scheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = NarraUi.compactGap, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        NarraTextButton(
                            onClick = { diaryEntries.getOrNull(currentIndex - 1)?.let { currentEntryId = it.id } },
                            enabled = currentIndex > 0,
                        ) {
                            Icon(Lucide.ChevronLeft, null, modifier = Modifier.size(18.dp))
                            Text("上一篇")
                        }
                        Text(
                            text = "${currentIndex + 1} / ${diaryEntries.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = mutedColor,
                            modifier = Modifier.testTag("diary_page_index"),
                        )
                        NarraTextButton(
                            onClick = { diaryEntries.getOrNull(currentIndex + 1)?.let { currentEntryId = it.id } },
                            enabled = currentIndex < diaryEntries.lastIndex,
                        ) {
                            Text("下一篇")
                            Icon(Lucide.ChevronRight, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
