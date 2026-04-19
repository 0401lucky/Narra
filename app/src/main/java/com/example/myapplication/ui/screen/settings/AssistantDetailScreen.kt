package com.example.myapplication.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.Assistant
import com.example.myapplication.ui.component.AssistantAvatar

@Composable
fun AssistantDetailScreen(
    assistant: Assistant,
    linkedWorldBookCount: Int,
    assistantMemoryCount: Int,
    onOpenBasic: () -> Unit,
    onOpenPrompt: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenMemory: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = assistant.name.ifBlank { "助手" },
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                AssistantHeroPanel(
                    assistant = assistant,
                    assistantMemoryCount = assistantMemoryCount,
                    linkedWorldBookCount = linkedWorldBookCount,
                )
            }

            item {
                SettingsGroup {
                    AssistantEntryRow(
                        icon = { EntryGlyph(icon = { Icon(Icons.Default.Face, null) }) },
                        title = "基础设定",
                        onClick = onOpenBasic,
                    )
                    SettingsGroupDivider()
                    AssistantEntryRow(
                        icon = { EntryGlyph(icon = { Icon(Icons.Default.AutoAwesome, null) }) },
                        title = "提示词",
                        onClick = onOpenPrompt,
                    )
                    SettingsGroupDivider()
                    AssistantEntryRow(
                        icon = { EntryGlyph(icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) }) },
                        title = "扩展管理",
                        badge = if (linkedWorldBookCount > 0) "$linkedWorldBookCount 项可用" else "无可用",
                        onClick = onOpenExtensions,
                    )
                    SettingsGroupDivider()
                    AssistantEntryRow(
                        icon = { EntryGlyph(icon = { Icon(Icons.Default.Psychology, null) }) },
                        title = "记忆",
                        badge = if (assistant.memoryEnabled) "已启用" else "未启用",
                        onClick = onOpenMemory,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantHeroPanel(
    assistant: Assistant,
    assistantMemoryCount: Int,
    linkedWorldBookCount: Int,
) {
    val palette = rememberSettingsPalette()
    // 大 hero 的玻璃渐变：accent → accentSoft → surface 的斜向过渡，模拟毛玻璃高光。
    val heroGradient = Brush.linearGradient(
        colors = listOf(
            palette.accent.copy(alpha = 0.22f),
            palette.accentSoft.copy(alpha = 0.14f),
            palette.surface.copy(alpha = 0.95f),
        ),
    )
    // 头像背后的柔光环：径向渐变 + CircleShape 裁剪，营造"发光点"视觉焦点。
    val haloGradient = Brush.radialGradient(
        colors = listOf(
            palette.accent.copy(alpha = 0.38f),
            palette.accent.copy(alpha = 0.12f),
            palette.accent.copy(alpha = 0.0f),
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(heroGradient)
            .border(
                border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.24f)),
                shape = RoundedCornerShape(32.dp),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(124.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .clip(CircleShape)
                        .background(haloGradient),
                )
                AssistantAvatar(
                    name = assistant.name,
                    iconName = assistant.iconName,
                    avatarUri = assistant.avatarUri,
                    size = 88.dp,
                    containerColor = palette.accentSoft.copy(alpha = 0.55f),
                    contentColor = palette.accent,
                    cornerRadius = 28.dp,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = assistant.name.ifBlank { "未命名助手" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.title,
                )
                assistant.description
                    .takeIf { it.isNotBlank() }
                    ?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.body,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsStatusPill(
                    text = if (assistant.memoryEnabled) "记忆开启" else "记忆关闭",
                    containerColor = if (assistant.memoryEnabled) palette.accentSoft else palette.surfaceTint,
                    contentColor = if (assistant.memoryEnabled) palette.accent else palette.body,
                )
                SettingsStatusPill(
                    text = "$assistantMemoryCount 条记忆",
                    containerColor = palette.subtleChip,
                    contentColor = palette.subtleChipContent,
                )
                SettingsStatusPill(
                    text = "$linkedWorldBookCount 本可用",
                    containerColor = palette.surfaceTint,
                    contentColor = palette.body,
                )
            }
        }
    }
}

@Composable
private fun AssistantEntryRow(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String = "",
    badge: String? = null,
    onClick: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.title,
                    )
                    badge?.takeIf { it.isNotBlank() }?.let {
                        SettingsStatusPill(
                            text = it,
                            containerColor = palette.surfaceTint,
                            contentColor = palette.body,
                        )
                    }
                }
                if (supporting.isNotBlank()) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.body,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.accent,
            )
        }
    }
}

@Composable
private fun EntryGlyph(
    icon: @Composable () -> Unit,
) {
    val palette = rememberSettingsPalette()
    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(14.dp),
        color = palette.accentSoft,
        contentColor = palette.accent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}
