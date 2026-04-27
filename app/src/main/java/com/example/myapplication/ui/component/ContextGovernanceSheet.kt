package com.example.myapplication.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Psychology
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.ContextGovernanceItem
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.ContextLogSection
import com.example.myapplication.model.ContextLogSourceType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 每种来源类型对应的颜色
private fun sourceTypeTint(sourceType: ContextLogSourceType): Color {
    return when (sourceType) {
        ContextLogSourceType.ROLE_CARD -> Color(0xFF4C78E6)
        ContextLogSourceType.CHAT_HISTORY -> Color(0xFFC44DE0)
        ContextLogSourceType.ROLE_EXTRAS -> Color(0xFF53B857)
        ContextLogSourceType.WORLD_BOOK -> Color(0xFFD65353)
        ContextLogSourceType.LONG_MEMORY -> Color(0xFFC9B83E)
        ContextLogSourceType.SUMMARY -> Color(0xFF58A8B4)
        ContextLogSourceType.USER_PERSONA -> Color(0xFFDD8C4B)
        ContextLogSourceType.PHONE_CONTEXT -> Color(0xFF43A6A3)
        ContextLogSourceType.SYSTEM_RULE -> Color(0xFF7A86A8)
    }
}

/**
 * 上下文日志详情主体（API 信息卡 + 组件摘要 + 彩色竖线分段块 + 底部彩色圆点标签栏）。
 * 由外层 [com.example.myapplication.ui.screen.settings.contextlog.ContextLogScreen]
 * 的 Scaffold/TopAppBar 负责标题栏与右上角操作按钮，本组件只负责详情内容。
 */
@Composable
fun ContextLogDetailBody(
    snapshot: ContextGovernanceSnapshot?,
    rawDebugDump: String,
    modifier: Modifier = Modifier,
) {
    val resolvedRawDump = snapshot?.rawDebugDump?.ifBlank { rawDebugDump } ?: rawDebugDump

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 当前筛选的来源类型
    var selectedFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSourceType = selectedFilter?.let { name ->
        runCatching { ContextLogSourceType.valueOf(name) }.getOrNull()
    }

    val sections = snapshot?.contextSections.orEmpty()
    val filteredSections = if (selectedSourceType == null) {
        sections
    } else {
        sections.filter { it.sourceType == selectedSourceType }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = listState,
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 4.dp,
                end = 20.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // API 信息卡
            snapshot?.let { governance ->
                item(key = "api_card") {
                    ContextLogApiCard(snapshot = governance)
                }

                // 提示文案
                if (governance.estimatedContextTokens > 0) {
                    item(key = "token_hint") {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "ⓘ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                            Text(
                                text = "tokens 仅为预估值，请以模型实际消耗为准",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }

                // 分段内容 — 彩色竖线风格
                if (filteredSections.isNotEmpty()) {
                    items(
                        items = filteredSections,
                        key = { it.id },
                    ) { section ->
                        ContextLogSectionBlock(section = section)
                    }
                } else if (governance.worldBookItems.isNotEmpty() || governance.memoryItems.isNotEmpty()) {
                    // 旧数据兼容：无 contextSections 时使用 items
                    if (governance.worldBookItems.isNotEmpty()) {
                        item(key = "legacy_wb") {
                            LegacyGovernanceCard(title = "世界书") {
                                for (item in governance.worldBookItems) {
                                    GovernanceItemBlock(item)
                                }
                            }
                        }
                    }
                    if (governance.memoryItems.isNotEmpty()) {
                        item(key = "legacy_mem") {
                            LegacyGovernanceCard(title = "记忆") {
                                for (item in governance.memoryItems) {
                                    GovernanceItemBlock(item)
                                }
                            }
                        }
                    }
                }
            }

            // 无数据时的占位
            if (snapshot == null && resolvedRawDump.isBlank()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Box(
                                modifier = Modifier.padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        Text(
                            text = "暂无上下文日志",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "发送一条消息后再查看这里",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 底部标签栏 — 彩色圆点 + 来源类型名
        if (sections.isNotEmpty()) {
            val activeTypes = sections.map { it.sourceType }.distinct()
            ContextLogSourceTabBar(
                activeTypes = activeTypes,
                selectedType = selectedSourceType,
                onSelectType = { type ->
                    if (type == selectedSourceType) {
                        selectedFilter = null
                    } else {
                        selectedFilter = type.name
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                },
            )
        }
    }
}

// API 来源信息卡（对标 Tavo 顶部卡片）
@Composable
private fun ContextLogApiCard(
    snapshot: ContextGovernanceSnapshot,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 第一行：来源 + 标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = snapshot.providerLabel.ifBlank { "本地" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (snapshot.modelLabel.isNotBlank()) {
                        Text(
                            text = snapshot.modelLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = snapshot.promptModeLabel.ifBlank { "聊天" },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // 第二行：时间 + tokens
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (snapshot.generatedAt > 0L) {
                        SimpleDateFormat("M月d日  HH:mm", Locale.getDefault())
                            .format(Date(snapshot.generatedAt))
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (snapshot.estimatedContextTokens > 0) {
                    Text(
                        text = "${snapshot.estimatedContextTokens} tokens",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 第三行：组件摘要列表
            val componentSummary = remember(snapshot) {
                buildList {
                    val sectionsByType = snapshot.contextSections.groupBy { it.sourceType }
                    for ((sourceType, typeSections) in sectionsByType) {
                        val displayValue = typeSections.firstOrNull()?.title?.takeIf { it.isNotBlank() }
                            ?: sourceType.label
                        add(sourceType to displayValue)
                    }
                }
            }
            if (componentSummary.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    for ((sourceType, displayValue) in componentSummary) {
                        val tint = sourceTypeTint(sourceType)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(28.dp),
                                shape = RoundedCornerShape(7.dp),
                                color = tint.copy(alpha = 0.15f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = sourceTypeIcon(sourceType),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 12.sp,
                                        color = tint,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = sourceType.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = displayValue,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

// 单个分段内容块 — 左侧彩色竖线 + 内容 + 底部元数据
@Composable
private fun ContextLogSectionBlock(
    section: ContextLogSection,
) {
    val tint = sourceTypeTint(section.sourceType)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(tint),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = section.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = section.sourceType.label.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                if (section.tokenEstimate > 0) {
                    Text(
                        text = "${section.tokenEstimate} tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

// 底部标签栏 — 彩色圆点 + 来源类型名
@Composable
private fun ContextLogSourceTabBar(
    activeTypes: List<ContextLogSourceType>,
    selectedType: ContextLogSourceType?,
    onSelectType: (ContextLogSourceType) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (type in activeTypes) {
                val tint = sourceTypeTint(type)
                val isSelected = type == selectedType
                val animatedColor by animateColorAsState(
                    targetValue = if (isSelected) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    animationSpec = tween(200),
                    label = "tab_color",
                )
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectType(type) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(tint),
                    )
                    Text(
                        text = type.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = animatedColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// 旧数据兼容用的卡片
@Composable
private fun LegacyGovernanceCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
private fun GovernanceItemBlock(
    item: ContextGovernanceItem,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = item.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// 来源类型的简短图标文字
private fun sourceTypeIcon(sourceType: ContextLogSourceType): String {
    return when (sourceType) {
        ContextLogSourceType.ROLE_CARD -> "👤"
        ContextLogSourceType.CHAT_HISTORY -> "💬"
        ContextLogSourceType.ROLE_EXTRAS -> "🎭"
        ContextLogSourceType.WORLD_BOOK -> "🌐"
        ContextLogSourceType.LONG_MEMORY -> "🧠"
        ContextLogSourceType.SUMMARY -> "📋"
        ContextLogSourceType.USER_PERSONA -> "🪪"
        ContextLogSourceType.PHONE_CONTEXT -> "📱"
        ContextLogSourceType.SYSTEM_RULE -> "⚙"
    }
}
