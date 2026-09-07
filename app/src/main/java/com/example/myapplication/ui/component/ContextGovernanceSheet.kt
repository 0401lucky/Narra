package com.example.myapplication.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Brain
import com.composables.icons.lucide.Contact
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.User
import com.example.myapplication.model.ContextGovernanceItem
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.ContextLogSection
import com.example.myapplication.model.ContextLogSourceType
import com.example.myapplication.ui.screen.settings.SettingsHelpButton
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.theme.NarraUi
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 上下文日志的可滚动阅读面；标题栏、复制和导航由页面负责。 */
@Composable
fun ContextLogDetailBody(
    snapshot: ContextGovernanceSnapshot?,
    rawDebugDump: String,
    modifier: Modifier = Modifier,
) {
    val resolvedRawDump = snapshot?.rawDebugDump?.ifBlank { rawDebugDump } ?: rawDebugDump
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var selectedFilter by rememberSaveable(snapshot?.id) { mutableStateOf<String?>(null) }
    val sections = snapshot?.contextSections.orEmpty()
    val activeTypes = sections.map { it.sourceType }.distinct()
    val selectedSourceType = activeTypes.firstOrNull { it.name == selectedFilter }
    val filteredSections = if (selectedSourceType == null) {
        sections
    } else {
        sections.filter { it.sourceType == selectedSourceType }
    }
    val hasLegacyItems = snapshot?.let {
        it.worldBookItems.isNotEmpty() || it.memoryItems.isNotEmpty()
    } == true

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("context_log_detail_list"),
            state = listState,
            contentPadding = PaddingValues(
                horizontal = NarraUi.pageHorizontal,
                vertical = NarraUi.pageVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            snapshot?.let { governance ->
                item(key = "api_card") { ContextLogApiCard(snapshot = governance) }
            }
            if (filteredSections.isNotEmpty()) {
                items(filteredSections, key = { it.id }) { section ->
                    ContextLogSectionBlock(section = section)
                }
            } else if (sections.isEmpty() && hasLegacyItems) {
                snapshot?.let { governance ->
                    if (governance.worldBookItems.isNotEmpty()) {
                        item(key = "legacy_world_book") {
                            LegacyGovernanceCard(title = "世界书") {
                                governance.worldBookItems.forEach { GovernanceItemBlock(it) }
                            }
                        }
                    }
                    if (governance.memoryItems.isNotEmpty()) {
                        item(key = "legacy_memory") {
                            LegacyGovernanceCard(title = "记忆") {
                                governance.memoryItems.forEach { GovernanceItemBlock(it) }
                            }
                        }
                    }
                }
            } else if (sections.isEmpty() && resolvedRawDump.isNotBlank()) {
                item(key = "raw_dump") {
                    LegacyGovernanceCard(title = "原始上下文") {
                        SelectionContainer {
                            Text(
                                text = resolvedRawDump,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("context_log_raw_content"),
                            )
                        }
                    }
                }
            }
            if (snapshot == null && resolvedRawDump.isBlank()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Lucide.FileText,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                        Text("暂无上下文日志", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "发送一条消息后再查看这里",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        if (activeTypes.isNotEmpty()) {
            ContextLogSourceTabBar(
                activeTypes = activeTypes,
                selectedType = selectedSourceType,
                onSelectType = { type ->
                    selectedFilter = if (type == selectedSourceType) null else type?.name
                    scope.launch { listState.animateScrollToItem(0) }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextLogApiCard(snapshot: ContextGovernanceSnapshot) {
    val componentSummary = remember(snapshot) {
        snapshot.contextSections.groupBy { it.sourceType }.map { (sourceType, typeSections) ->
            val displayValue = if (sourceType == ContextLogSourceType.PROMPT_PRESET) {
                snapshot.activePresetName.takeIf { it.isNotBlank() }
            } else {
                null
            } ?: typeSections.firstOrNull()?.title?.takeIf { it.isNotBlank() } ?: sourceType.label
            "${sourceType.label}：$displayValue"
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NarraUi.cardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
            ) {
                Icon(Lucide.Link, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(
                    text = "请求信息",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                SettingsHelpButton(
                    title = "日志信息",
                    description = "这里显示本次请求使用的提供商、模型和上下文来源。\n\n" +
                        "tokens 仅为预估值，请以模型实际消耗为准。日志分段保留实际发送的上下文，" +
                        "底部可按来源筛选，再次点击当前来源或选择“全部”可恢复完整视图。",
                )
            }
            Text(
                text = snapshot.providerLabel.ifBlank { "本地" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (snapshot.modelLabel.isNotBlank()) {
                Text(
                    text = snapshot.modelLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
            ) {
                Text(
                    text = snapshot.promptModeLabel.ifBlank { "会话" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (snapshot.generatedAt > 0L) {
                    Text(
                        text = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault())
                            .format(Date(snapshot.generatedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (snapshot.estimatedContextTokens > 0) {
                    Text(
                        text = "${snapshot.estimatedContextTokens} tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (componentSummary.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                    ) {
                        Text("上下文组成", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${componentSummary.size} 类来源 · ${snapshot.contextSections.size} 段内容",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingsHelpButton(
                        title = "上下文组成",
                        description = componentSummary.joinToString("\n\n"),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextLogSectionBlock(section: ContextLogSection) {
    val tint = sourceTypeTint(section.sourceType)
    Column(
        modifier = Modifier.fillMaxWidth().testTag("context_log_section_${section.id}"),
        verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
            verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
        ) {
            Icon(
                imageVector = sourceTypeIcon(section.sourceType),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = section.sourceType.label,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
            )
            if (section.tokenEstimate > 0) {
                Text(
                    text = "${section.tokenEstimate} tokens",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (section.title.isNotBlank() && section.title != section.sourceType.label) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        SelectionContainer {
            Text(
                text = section.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("context_log_content_${section.id}"),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun ContextLogSourceTabBar(
    activeTypes: List<ContextLogSourceType>,
    selectedType: ContextLogSourceType?,
    onSelectType: (ContextLogSourceType?) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyRow(
                modifier = Modifier.fillMaxWidth().testTag("context_log_filters"),
                contentPadding = PaddingValues(horizontal = NarraUi.pageHorizontal, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(NarraUi.compactGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = selectedType == null,
                        onClick = { onSelectType(null) },
                        label = { Text("全部") },
                        shape = RoundedCornerShape(NarraUi.smallRadius),
                        modifier = Modifier
                            .heightIn(min = NarraMinimumTouchTarget)
                            .testTag("context_log_filter_all"),
                    )
                }
                items(activeTypes, key = { it.name }) { type ->
                    FilterChip(
                        selected = type == selectedType,
                        onClick = { onSelectType(type) },
                        label = { Text(type.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = sourceTypeIcon(type),
                                contentDescription = null,
                                tint = if (type == selectedType) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    sourceTypeTint(type)
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        shape = RoundedCornerShape(NarraUi.smallRadius),
                        modifier = Modifier
                            .heightIn(min = NarraMinimumTouchTarget)
                            .testTag("context_log_filter_${type.name}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegacyGovernanceCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NarraUi.cardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun GovernanceItemBlock(item: ContextGovernanceItem) {
    Column(verticalArrangement = Arrangement.spacedBy(NarraUi.compactGap)) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
        SelectionContainer {
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun sourceTypeTint(sourceType: ContextLogSourceType): Color {
    val scheme = MaterialTheme.colorScheme
    return when (sourceType) {
        ContextLogSourceType.ROLE_CARD,
        ContextLogSourceType.USER_PERSONA,
        ContextLogSourceType.LONG_MEMORY,
        -> scheme.onPrimaryContainer

        ContextLogSourceType.CHAT_HISTORY,
        ContextLogSourceType.PHONE_CONTEXT,
        ContextLogSourceType.SUMMARY,
        -> scheme.onSecondaryContainer

        ContextLogSourceType.ROLE_EXTRAS,
        ContextLogSourceType.WORLD_BOOK,
        ContextLogSourceType.PROMPT_PRESET,
        -> scheme.onTertiaryContainer

        ContextLogSourceType.SYSTEM_RULE -> scheme.onSurfaceVariant
    }
}

private fun sourceTypeIcon(sourceType: ContextLogSourceType): ImageVector {
    return when (sourceType) {
        ContextLogSourceType.ROLE_CARD -> Lucide.User
        ContextLogSourceType.CHAT_HISTORY -> Lucide.MessageCircle
        ContextLogSourceType.ROLE_EXTRAS -> Lucide.Sparkles
        ContextLogSourceType.WORLD_BOOK -> Lucide.BookOpen
        ContextLogSourceType.LONG_MEMORY -> Lucide.Brain
        ContextLogSourceType.SUMMARY -> Lucide.FileText
        ContextLogSourceType.USER_PERSONA -> Lucide.Contact
        ContextLogSourceType.PHONE_CONTEXT -> Lucide.Smartphone
        ContextLogSourceType.PROMPT_PRESET -> Lucide.SlidersHorizontal
        ContextLogSourceType.SYSTEM_RULE -> Lucide.Settings
    }
}
