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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.MomentAutoPostFrequency
import com.example.myapplication.model.MomentCommentStyle
import com.example.myapplication.model.Preset
import com.example.myapplication.model.isAssistantPresetFollowingGlobal
import com.example.myapplication.model.resolveActivePresetId
import com.example.myapplication.ui.component.AssistantAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantDetailScreen(
    assistant: Assistant,
    presets: List<Preset>,
    globalDefaultPresetId: String,
    linkedWorldBookCount: Int,
    assistantMemoryCount: Int,
    onOpenBasic: () -> Unit,
    onOpenArtStudio: () -> Unit,
    onOpenPrompt: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenMemory: () -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onSelectPreset: (String) -> Unit,
    onOpenPresetManager: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    var showPresetSheet by rememberSaveable { mutableStateOf(false) }
    val normalizedPresets = presets.map(Preset::normalized)
    val activePresetId = resolveActivePresetId(
        globalDefaultPresetId = globalDefaultPresetId,
        assistantDefaultPresetId = assistant.defaultPresetId,
    )
    val globalPresetId = resolveActivePresetId(
        globalDefaultPresetId = globalDefaultPresetId,
        assistantDefaultPresetId = null,
    )
    val followsGlobalPreset = isAssistantPresetFollowingGlobal(
        globalDefaultPresetId = globalDefaultPresetId,
        assistantDefaultPresetId = assistant.defaultPresetId,
    )
    val activePreset = normalizedPresets.firstOrNull { it.id == activePresetId }
    val globalPreset = normalizedPresets.firstOrNull { it.id == globalPresetId }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = assistant.name.ifBlank { "角色" },
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
                        icon = { EntryGlyph(icon = { Icon(Icons.Outlined.Image, null) }) },
                        title = "角色图工作台",
                        supporting = "提取角色特性，生成非真人风格头像",
                        badge = if (assistant.avatarUri.isNotBlank()) "已有头像" else "可生成",
                        onClick = onOpenArtStudio,
                    )
                    SettingsGroupDivider()
                    AssistantEntryRow(
                        icon = { EntryGlyph(icon = { Icon(Icons.Default.AutoAwesome, null) }) },
                        title = "角色人设",
                        onClick = onOpenPrompt,
                    )
                    SettingsGroupDivider()
                    AssistantEntryRow(
                        icon = { EntryGlyph(icon = { Icon(Icons.Default.Tune, null) }) },
                        title = "默认预设",
                        supporting = if (followsGlobalPreset) {
                            activePreset?.name?.let { "$it · 跟随全局" } ?: "跟随全局默认"
                        } else {
                            activePreset?.name ?: "未找到预设"
                        },
                        badge = if (followsGlobalPreset) "全局" else "角色",
                        onClick = { showPresetSheet = true },
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

            item {
                SettingsGroup {
                    AssistantMomentSwitchRow(
                        icon = { EntryGlyph(icon = { Icon(Icons.Default.Forum, null) }) },
                        title = "主动评论朋友圈",
                        supporting = "用户发朋友圈或评论后，角色会按人设参与互动",
                        checked = assistant.momentAutoCommentEnabled,
                        onCheckedChange = {
                            onUpdateAssistant(assistant.copy(momentAutoCommentEnabled = it))
                        },
                    )
                    SettingsGroupDivider()
                    AssistantMomentSwitchRow(
                        icon = { EntryGlyph(icon = { Icon(Icons.Default.AutoAwesome, null) }) },
                        title = "主动发朋友圈",
                        supporting = "后台定时生成角色自己的生活动态",
                        checked = assistant.momentAutoPostEnabled,
                        onCheckedChange = {
                            onUpdateAssistant(assistant.copy(momentAutoPostEnabled = it))
                        },
                    )
                    SettingsGroupDivider()
                    AssistantMomentSwitchRow(
                        icon = { EntryGlyph(icon = { Icon(Icons.Outlined.Image, null) }) },
                        title = "主动朋友圈配图",
                        supporting = "开启后角色发动态时可调用默认生图模型",
                        checked = assistant.momentAutoImageEnabled,
                        enabled = assistant.momentAutoPostEnabled,
                        onCheckedChange = {
                            onUpdateAssistant(assistant.copy(momentAutoImageEnabled = it))
                        },
                    )
                    SettingsGroupDivider()
                    AssistantMomentCycleRow(
                        title = "发帖频率",
                        value = assistant.momentAutoPostFrequency.label,
                        onClick = {
                            onUpdateAssistant(
                                assistant.copy(
                                    momentAutoPostFrequency = assistant.momentAutoPostFrequency.next(),
                                ),
                            )
                        },
                    )
                    SettingsGroupDivider()
                    AssistantMomentCycleRow(
                        title = "评论风格",
                        value = assistant.momentCommentStyle.label,
                        onClick = {
                            onUpdateAssistant(
                                assistant.copy(
                                    momentCommentStyle = assistant.momentCommentStyle.next(),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    if (showPresetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPresetSheet = false },
            containerColor = palette.elevatedSurface,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = SettingsScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "选择角色预设",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.title,
                )
                SettingsGroup {
                    SettingsListRow(
                        title = "跟随全局默认",
                        supportingText = globalPreset?.name ?: "使用设置页选定的全局默认预设",
                        highlighted = followsGlobalPreset,
                        onClick = {
                            onSelectPreset("")
                            showPresetSheet = false
                        },
                        trailingContent = {
                            if (followsGlobalPreset) {
                                SettingsStatusPill("当前", palette.accentSoft, palette.accent)
                            }
                        },
                    )
                    if (normalizedPresets.isNotEmpty()) {
                        SettingsGroupDivider()
                    }
                    normalizedPresets.forEachIndexed { index, preset ->
                        val presetSelected = !followsGlobalPreset && preset.id == activePresetId
                        SettingsListRow(
                            title = preset.name.ifBlank { "未命名预设" },
                            supportingText = if (preset.builtIn) "内置只读" else "我的预设",
                            highlighted = presetSelected,
                            onClick = {
                                onSelectPreset(preset.id)
                                showPresetSheet = false
                            },
                            trailingContent = {
                                if (presetSelected) {
                                    SettingsStatusPill("当前", palette.accentSoft, palette.accent)
                                }
                            },
                        )
                        if (index != normalizedPresets.lastIndex) {
                            SettingsGroupDivider()
                        }
                    }
                }
                TextButton(
                    onClick = {
                        showPresetSheet = false
                        onOpenPresetManager()
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("打开预设管理")
                }
                Spacer(modifier = Modifier.height(16.dp))
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
                    text = assistant.name.ifBlank { "未命名角色" },
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

@Composable
private fun AssistantMomentSwitchRow(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = rememberSettingsPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) palette.title else palette.body,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = palette.body,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun AssistantMomentCycleRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = palette.title,
        )
        SettingsStatusPill(
            text = value,
            containerColor = palette.accentSoft,
            contentColor = palette.accent,
        )
    }
}

private fun MomentAutoPostFrequency.next(): MomentAutoPostFrequency {
    val values = MomentAutoPostFrequency.entries
    return values[(ordinal + 1) % values.size]
}

private fun MomentCommentStyle.next(): MomentCommentStyle {
    val values = MomentCommentStyle.entries
    return values[(ordinal + 1) % values.size]
}
