package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.MemoryProposalStatus
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassPalette
import com.example.myapplication.ui.component.roleplay.ImmersiveTabRow
import com.example.myapplication.ui.component.roleplay.RoleplayDialogueBubbleStyle
import com.example.myapplication.ui.component.roleplay.rememberRoleplayNoBackgroundSkinSpec
import com.example.myapplication.ui.component.roleplay.shape
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsListRow
import com.example.myapplication.ui.screen.settings.SettingsStatusPill
import kotlin.math.roundToInt

/**
 * 可视性开关组：身份条 / 状态条 / AI 帮写，以及线上模式特有的心声和网络热梗。
 */
@Composable
internal fun RoleplaySettingsToggleSection(
    backdropState: ImmersiveBackdropState,
    scenario: RoleplayScenario?,
    settings: AppSettings,
    onUpdateShowRoleplayPresenceStrip: (Boolean) -> Unit,
    onUpdateShowRoleplayStatusStrip: (Boolean) -> Unit,
    onUpdateShowRoleplayAiHelper: (Boolean) -> Unit,
    onUpdateShowOnlineRoleplayNarration: (Boolean) -> Unit,
) {
    val palette = backdropState.palette
    ImmersiveSettingsCard(backdropState) {
        RoleplaySettingSwitchRow(
            palette = palette,
            icon = {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = RoleplaySettingsPanelAccentColor,
                )
            },
            title = stringResource(id = R.string.roleplay_settings_show_presence_strip),
            checked = settings.showRoleplayPresenceStrip,
            onCheckedChange = onUpdateShowRoleplayPresenceStrip,
        )
        SectionDivider(palette = palette)
        RoleplaySettingSwitchRow(
            palette = palette,
            icon = {
                Icon(
                    imageVector = Icons.Default.ViewAgenda,
                    contentDescription = null,
                    tint = RoleplaySettingsPanelAccentColor,
                )
            },
            title = stringResource(id = R.string.roleplay_settings_show_status_strip),
            checked = settings.showRoleplayStatusStrip,
            onCheckedChange = onUpdateShowRoleplayStatusStrip,
        )
        SectionDivider(palette = palette)
        RoleplaySettingSwitchRow(
            palette = palette,
            icon = {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = RoleplaySettingsPanelAccentColor,
                )
            },
            title = stringResource(id = R.string.roleplay_settings_show_ai_helper),
            checked = settings.showRoleplayAiHelper,
            onCheckedChange = onUpdateShowRoleplayAiHelper,
        )
        if (scenario?.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
            SectionDivider(palette = palette)
            RoleplaySettingSwitchRow(
                palette = palette,
                icon = {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = RoleplaySettingsPanelAccentColor,
                    )
                },
                title = stringResource(id = R.string.roleplay_settings_show_online_narration),
                supportingText = stringResource(id = R.string.roleplay_settings_show_online_narration_hint),
                checked = settings.showOnlineRoleplayNarration,
                onCheckedChange = onUpdateShowOnlineRoleplayNarration,
            )
        }
    }
}

@Composable
internal fun RoleplaySettingsPluginSection(
    backdropState: ImmersiveBackdropState,
    scenario: RoleplayScenario?,
    onUpdateScenarioNarrationEnabled: (Boolean) -> Unit,
    onUpdateScenarioDeepImmersionEnabled: (Boolean) -> Unit,
    onUpdateScenarioTimeAwarenessEnabled: (Boolean) -> Unit,
    onUpdateScenarioNetMemeEnabled: (Boolean) -> Unit,
) {
    val palette = backdropState.palette
    ImmersiveSettingsCard(backdropState) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "场景插件",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RoleplaySettingsPanelTitleColor,
            )
            Text(
                text = "这些开关只影响当前场景的提示词注入，不会改动其他场景。",
                style = MaterialTheme.typography.bodySmall,
                color = RoleplaySettingsPanelBodyColor,
            )
        }
        SectionDivider(palette = palette)
        RoleplaySettingSwitchRow(
            palette = palette,
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = RoleplaySettingsPanelAccentColor,
                )
            },
            title = if (scenario?.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
                "启用心声生成"
            } else {
                "启用旁白/心声生成"
            },
            supportingText = if (scenario?.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
                "控制线上模式是否允许生成 thought 心声对象；仅影响当前场景。"
            } else {
                "控制线下模式里的旁白与心声标记；仅影响当前场景。"
            },
            checked = scenario?.enableNarration == true,
            onCheckedChange = onUpdateScenarioNarrationEnabled,
        )
        SectionDivider(palette = palette)
        RoleplaySettingSwitchRow(
            palette = palette,
            icon = {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = RoleplaySettingsPanelAccentColor,
                )
            },
            title = "深度沉浸",
            supportingText = "注入更强的角色表达自由度与文风戒律；仅影响当前场景。",
            checked = scenario?.enableDeepImmersion == true,
            onCheckedChange = onUpdateScenarioDeepImmersionEnabled,
        )
        SectionDivider(palette = palette)
        RoleplaySettingSwitchRow(
            palette = palette,
            icon = {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = RoleplaySettingsPanelAccentColor,
                )
            },
            title = "时间感知",
            supportingText = "让角色感知当前日期、昼夜和作息；线上模式还会感知断联后的时间后效。",
            checked = scenario?.enableTimeAwareness != false,
            onCheckedChange = onUpdateScenarioTimeAwarenessEnabled,
        )
        if (scenario?.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
            SectionDivider(palette = palette)
            RoleplaySettingSwitchRow(
                palette = palette,
                icon = {
                    Icon(
                        imageVector = Icons.Default.SettingsSuggest,
                        contentDescription = null,
                        tint = RoleplaySettingsPanelAccentColor,
                    )
                },
                title = "网络热梗",
                supportingText = "允许当前场景在合适的时候使用更鲜活的线上梗感表达。",
                checked = scenario.enableNetMeme,
                onCheckedChange = onUpdateScenarioNetMemeEnabled,
            )
        }
    }
}

/**
 * 交互模式 Tab + 长剧情默认字数输入。
 */
@Composable
internal fun RoleplaySettingsInteractionSection(
    backdropState: ImmersiveBackdropState,
    scenario: RoleplayScenario?,
    longformCharsText: String,
    onLongformCharsTextChange: (String) -> Unit,
    onUpdateScenarioInteractionMode: (RoleplayInteractionMode) -> Unit,
) {
    val palette = backdropState.palette
    ImmersiveSettingsCard(backdropState) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.roleplay_settings_interaction_mode_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RoleplaySettingsPanelTitleColor,
            )
            Text(
                text = stringResource(id = R.string.roleplay_settings_interaction_mode_hint),
                style = MaterialTheme.typography.bodySmall,
                color = RoleplaySettingsPanelBodyColor,
            )
            ImmersiveTabRow(
                entries = RoleplayInteractionMode.entries,
                selected = scenario?.interactionMode,
                label = { it.displayName },
                keyOf = { it.name },
                palette = palette,
                onSelect = onUpdateScenarioInteractionMode,
                enabled = scenario != null,
                itemHorizontalPadding = 8.dp,
            )
        }
    }

    ImmersiveSettingsCard(backdropState) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.TextFields,
                    contentDescription = null,
                    tint = RoleplaySettingsPanelAccentColor,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(id = R.string.roleplay_settings_longform_default_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = RoleplaySettingsPanelTitleColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedTextField(
                value = longformCharsText,
                onValueChange = onLongformCharsTextChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                label = { Text(stringResource(id = R.string.roleplay_settings_longform_default_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedBorderColor = RoleplaySettingsPanelBodyColor.copy(alpha = 0.3f),
                    focusedBorderColor = RoleplaySettingsPanelAccentColor.copy(alpha = 0.5f),
                    unfocusedTextColor = RoleplaySettingsPanelTitleColor,
                    focusedTextColor = RoleplaySettingsPanelTitleColor,
                    unfocusedLabelColor = RoleplaySettingsPanelBodyColor,
                    focusedLabelColor = RoleplaySettingsPanelBodyColor,
                    unfocusedSupportingTextColor = RoleplaySettingsPanelBodyColor,
                    focusedSupportingTextColor = RoleplaySettingsPanelBodyColor,
                ),
                singleLine = true,
            )
        }
    }
}

@Composable
internal fun RoleplayNoBackgroundThemeSection(
    backdropState: ImmersiveBackdropState,
    settings: AppSettings,
    systemHighContrastEnabled: Boolean,
    onUpdateRoleplayNoBackgroundSkin: (RoleplayNoBackgroundSkinSettings) -> Unit,
    onUpdateRoleplayImmersiveMode: (RoleplayImmersiveMode) -> Unit,
    onUpdateRoleplayHighContrast: (Boolean) -> Unit,
    onUpdateRoleplayLineHeightScale: (RoleplayLineHeightScale) -> Unit,
) {
    val skin = settings.roleplayNoBackgroundSkin.normalized()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RoleplaySkinPreviewCard(
            backdropState = backdropState,
            skin = skin,
        )
        RoleplaySkinPresetSection(
            backdropState = backdropState,
            selected = skin.preset,
            onSelect = { preset ->
                onUpdateRoleplayNoBackgroundSkin(skin.copy(preset = preset).normalized())
            },
        )
        RoleplaySkinBubbleSection(
            backdropState = backdropState,
            skin = skin,
            onUpdate = { next -> onUpdateRoleplayNoBackgroundSkin(next.normalized()) },
        )
        RoleplaySettingsReadabilitySection(
            backdropState = backdropState,
            settings = settings,
            systemHighContrastEnabled = systemHighContrastEnabled,
            onUpdateRoleplayImmersiveMode = onUpdateRoleplayImmersiveMode,
            onUpdateRoleplayHighContrast = onUpdateRoleplayHighContrast,
            onUpdateRoleplayLineHeightScale = onUpdateRoleplayLineHeightScale,
        )
    }
}

@Composable
private fun RoleplaySkinPreviewCard(
    backdropState: ImmersiveBackdropState,
    skin: RoleplayNoBackgroundSkinSettings,
) {
    val spec = rememberRoleplayNoBackgroundSkinSpec(skin)
    val bubbleStyle = RoleplayDialogueBubbleStyle(
        maxWidthFraction = spec.maxWidthFraction,
        radius = spec.radius,
        paddingHorizontal = spec.paddingHorizontal,
        paddingVertical = spec.paddingVertical,
        showTail = spec.showTail,
    )
    ImmersiveSettingsCard(backdropState) {
        Column(
            modifier = Modifier
                .testTag("roleplay_skin_preview")
                .background(spec.background.copy(alpha = spec.background.alpha.coerceAtLeast(0.78f)))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "实时预览 · 无背景",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = spec.characterText,
                )
                Text(
                    text = "${spec.displayName} · ${(spec.maxWidthFraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = spec.mutedText,
                )
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val previewMaxWidth = maxWidth * spec.maxWidthFraction
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Surface(
                            modifier = Modifier.widthIn(max = previewMaxWidth),
                            shape = bubbleStyle.shape(isUser = false),
                            color = spec.characterBubble,
                            border = BorderStroke(1.dp, spec.border.copy(alpha = 0.26f)),
                        ) {
                            Text(
                                text = "你好呀，这是预览消息",
                                modifier = Modifier.padding(
                                    horizontal = spec.paddingHorizontal,
                                    vertical = spec.paddingVertical,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = spec.characterText,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Surface(
                            modifier = Modifier.widthIn(max = previewMaxWidth),
                            shape = bubbleStyle.shape(isUser = true),
                            color = spec.userBubble,
                            border = BorderStroke(1.dp, spec.border.copy(alpha = 0.22f)),
                        ) {
                            Text(
                                text = "短句会按内容收缩",
                                modifier = Modifier.padding(
                                    horizontal = spec.paddingHorizontal,
                                    vertical = spec.paddingVertical,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = spec.userText,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleplaySkinPresetSection(
    backdropState: ImmersiveBackdropState,
    selected: RoleplayNoBackgroundSkinPreset,
    onSelect: (RoleplayNoBackgroundSkinPreset) -> Unit,
) {
    ImmersiveSettingsCard(backdropState) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "预设皮肤",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RoleplaySettingsPanelTitleColor,
            )
            RoleplayNoBackgroundSkinPreset.entries.chunked(2).forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowPresets.forEach { preset ->
                        RoleplaySkinPresetTile(
                            preset = preset,
                            selected = preset == selected,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(preset) },
                        )
                    }
                    if (rowPresets.size == 1) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleplaySkinPresetTile(
    preset: RoleplayNoBackgroundSkinPreset,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val spec = rememberRoleplayNoBackgroundSkinSpec(
        RoleplayNoBackgroundSkinSettings(preset = preset),
    )
    Surface(
        modifier = modifier
            .testTag("roleplay_skin_preset_${preset.storageValue}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) spec.panelStrong.copy(alpha = 0.98f) else Color.White.copy(alpha = 0.58f),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) spec.accent.copy(alpha = 0.62f) else RoleplaySettingsPanelBodyColor.copy(alpha = 0.16f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = preset.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = RoleplaySettingsPanelTitleColor,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(spec.userBubble, spec.characterBubble, spec.accent).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleplaySkinBubbleSection(
    backdropState: ImmersiveBackdropState,
    skin: RoleplayNoBackgroundSkinSettings,
    onUpdate: (RoleplayNoBackgroundSkinSettings) -> Unit,
) {
    ImmersiveSettingsCard(backdropState) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "气泡样式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RoleplaySettingsPanelTitleColor,
                )
                Surface(
                    modifier = Modifier.clickable { onUpdate(RoleplayNoBackgroundSkinSettings()) },
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.62f),
                    border = BorderStroke(1.dp, RoleplaySettingsPanelBodyColor.copy(alpha = 0.16f)),
                ) {
                    Text(
                        text = "重置",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = RoleplaySettingsPanelAccentColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            RoleplaySkinSliderRow(
                label = "最大宽度",
                value = skin.maxWidthPercent,
                suffix = "%",
                range = 64f..92f,
                testTag = "roleplay_skin_width_slider",
                onValueChange = { onUpdate(skin.copy(maxWidthPercent = it)) },
            )
            RoleplaySkinSliderRow(
                label = "圆角",
                value = skin.bubbleRadiusDp,
                suffix = "dp",
                range = 4f..28f,
                testTag = "roleplay_skin_radius_slider",
                onValueChange = { onUpdate(skin.copy(bubbleRadiusDp = it)) },
            )
            RoleplaySkinSliderRow(
                label = "横向内边距",
                value = skin.bubblePaddingHorizontalDp,
                suffix = "dp",
                range = 8f..20f,
                testTag = "roleplay_skin_padding_x_slider",
                onValueChange = { onUpdate(skin.copy(bubblePaddingHorizontalDp = it)) },
            )
            RoleplaySkinSliderRow(
                label = "纵向内边距",
                value = skin.bubblePaddingVerticalDp,
                suffix = "dp",
                range = 6f..18f,
                testTag = "roleplay_skin_padding_y_slider",
                onValueChange = { onUpdate(skin.copy(bubblePaddingVerticalDp = it)) },
            )
            RoleplaySettingSwitchRow(
                palette = backdropState.palette,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(RoleplaySettingsPanelAccentColor.copy(alpha = 0.22f), CircleShape),
                    )
                },
                title = "显示气泡尾巴",
                checked = skin.showBubbleTail,
                switchModifier = Modifier.testTag("roleplay_skin_tail_switch"),
                onCheckedChange = { enabled -> onUpdate(skin.copy(showBubbleTail = enabled)) },
            )
        }
    }
}

@Composable
private fun RoleplaySkinSliderRow(
    label: String,
    value: Int,
    suffix: String,
    range: ClosedFloatingPointRange<Float>,
    testTag: String,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = RoleplaySettingsPanelTitleColor,
            )
            Text(
                text = "$value$suffix",
                style = MaterialTheme.typography.labelMedium,
                color = RoleplaySettingsPanelBodyColor,
            )
        }
        Slider(
            modifier = Modifier.testTag(testTag),
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range,
        )
    }
}

@Composable
internal fun RoleplaySettingsReadabilitySection(
    backdropState: ImmersiveBackdropState,
    settings: AppSettings,
    systemHighContrastEnabled: Boolean,
    onUpdateRoleplayImmersiveMode: (RoleplayImmersiveMode) -> Unit,
    onUpdateRoleplayHighContrast: (Boolean) -> Unit,
    onUpdateRoleplayLineHeightScale: (RoleplayLineHeightScale) -> Unit,
) {
    val palette = backdropState.palette
    ImmersiveSettingsCard(backdropState) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.roleplay_readability_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RoleplaySettingsPanelTitleColor,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(id = R.string.roleplay_immersive_mode_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = RoleplaySettingsPanelTitleColor,
                )
                ImmersiveTabRow(
                    entries = RoleplayImmersiveMode.entries,
                    selected = settings.roleplayImmersiveMode,
                    label = { roleplayImmersiveModeLabel(it) },
                    keyOf = { it.storageValue },
                    palette = palette,
                    onSelect = onUpdateRoleplayImmersiveMode,
                    testTagPrefix = "roleplay_immersive",
                )
            }
            HorizontalDivider(color = RoleplaySettingsPanelBodyColor.copy(alpha = 0.18f))
            RoleplaySettingSwitchRow(
                palette = palette,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = RoleplaySettingsPanelAccentColor,
                    )
                },
                title = stringResource(id = R.string.roleplay_settings_high_contrast_title),
                supportingText = if (systemHighContrastEnabled && !settings.roleplayHighContrast) {
                    stringResource(id = R.string.roleplay_high_contrast_system_enabled)
                } else {
                    ""
                },
                checked = settings.roleplayHighContrast,
                switchModifier = Modifier.testTag("roleplay_high_contrast_switch"),
                onCheckedChange = onUpdateRoleplayHighContrast,
            )
            HorizontalDivider(color = RoleplaySettingsPanelBodyColor.copy(alpha = 0.18f))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(id = R.string.roleplay_line_height_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = RoleplaySettingsPanelTitleColor,
                )
                ImmersiveTabRow(
                    entries = RoleplayLineHeightScale.entries,
                    selected = settings.roleplayLineHeightScale,
                    label = { roleplayLineHeightScaleLabel(it) },
                    keyOf = { it.storageValue },
                    palette = palette,
                    onSelect = onUpdateRoleplayLineHeightScale,
                    testTagPrefix = "roleplay_line_height",
                )
            }
        }
    }
}

/**
 * 最近记忆提议历史（仅在非空时渲染）。
 */
@Composable
internal fun RoleplaySettingsMemoryHistorySection(
    backdropState: ImmersiveBackdropState,
    history: List<MemoryProposalHistoryItem>,
) {
    if (history.isEmpty()) return
    val palette = backdropState.palette
    ImmersiveSettingsCard(backdropState) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.roleplay_settings_memory_history_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RoleplaySettingsPanelTitleColor,
            )
            history.take(5).forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(color = RoleplaySettingsPanelBodyColor.copy(alpha = 0.18f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsStatusPill(
                            text = item.status.label,
                            containerColor = when (item.status) {
                                MemoryProposalStatus.PENDING -> RoleplaySettingsPanelAccentColor.copy(alpha = 0.24f)
                                MemoryProposalStatus.APPROVED -> Color(0xFF4CAF50).copy(alpha = 0.24f)
                                MemoryProposalStatus.REJECTED -> Color(0xFFFF5252).copy(alpha = 0.24f)
                            },
                            contentColor = RoleplaySettingsPanelTitleColor,
                        )
                        SettingsStatusPill(
                            text = item.scopeType.label,
                            containerColor = RoleplaySettingsPanelBodyColor.copy(alpha = 0.12f),
                            contentColor = RoleplaySettingsPanelBodyColor,
                        )
                    }
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoleplaySettingsPanelTitleColor,
                    )
                    item.reason.takeIf { it.isNotBlank() }?.let { reason ->
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = RoleplaySettingsPanelBodyColor,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 工具入口：阅读模式 / 切换模型 / 上下文治理。
 */
@Composable
internal fun RoleplaySettingsActionsSection(
    backdropState: ImmersiveBackdropState,
    currentModel: String,
    contextGovernance: ContextGovernanceSnapshot?,
    latestPromptDebugDump: String,
    onOpenReadingMode: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenContextLog: () -> Unit,
) {
    val palette = backdropState.palette
    ImmersiveSettingsCard(backdropState) {
        SettingsListRow(
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = RoleplaySettingsPanelAccentColor,
                )
            },
            title = stringResource(id = R.string.roleplay_settings_action_reading_mode),
            onClick = onOpenReadingMode,
        )
        SectionDivider(palette = palette)
        SettingsListRow(
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = RoleplaySettingsPanelAccentColor,
                )
            },
            title = stringResource(id = R.string.roleplay_settings_action_model_picker),
            supportingText = currentModel.ifBlank { stringResource(id = R.string.roleplay_settings_action_model_empty) },
            onClick = onOpenModelPicker,
        )
        SectionDivider(palette = palette)
        SettingsListRow(
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.SettingsSuggest,
                    contentDescription = null,
                    tint = RoleplaySettingsPanelAccentColor,
                )
            },
            title = stringResource(id = R.string.roleplay_settings_action_context_governance),
            supportingText = contextGovernance?.summarySupportingText.orEmpty(),
            onClick = onOpenContextLog,
        )
    }
}

/**
 * 危险操作：重开剧情 / 清空剧情。
 */
@Composable
internal fun RoleplaySettingsDangerousSection(
    backdropState: ImmersiveBackdropState,
    scenario: RoleplayScenario?,
    onShowRestartDialog: () -> Unit,
    onShowResetDialog: () -> Unit,
) {
    val palette = backdropState.palette
    ImmersiveSettingsCard(backdropState) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedSettingButton(
                text = stringResource(id = R.string.roleplay_settings_action_restart),
                onClick = onShowRestartDialog,
                enabled = scenario != null,
                isPrimary = false,
                leadingIcon = {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                },
            )
            HorizontalDivider(color = RoleplaySettingsPanelBodyColor.copy(alpha = 0.18f))
            AnimatedSettingButton(
                text = stringResource(id = R.string.roleplay_settings_action_reset),
                onClick = onShowResetDialog,
                enabled = scenario != null,
                isPrimary = false,
                leadingIcon = {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                    )
                },
            )
        }
    }
}

/** 图标左对齐的缩进分割线，专用于开关卡片。 */
@Composable
private fun SectionDivider(palette: ImmersiveGlassPalette) {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 18.dp),
        color = RoleplaySettingsPanelBodyColor.copy(alpha = 0.18f),
    )
}

@Composable
internal fun roleplayImmersiveModeLabel(mode: RoleplayImmersiveMode): String {
    return when (mode) {
        RoleplayImmersiveMode.EDGE_TO_EDGE -> stringResource(id = R.string.roleplay_immersive_mode_edge_to_edge)
        RoleplayImmersiveMode.HIDE_SYSTEM_BARS -> stringResource(id = R.string.roleplay_immersive_mode_fullscreen)
        RoleplayImmersiveMode.NONE -> stringResource(id = R.string.roleplay_immersive_mode_standard)
    }
}

@Composable
internal fun roleplayImmersiveModeDescription(mode: RoleplayImmersiveMode): String {
    return when (mode) {
        RoleplayImmersiveMode.EDGE_TO_EDGE -> stringResource(id = R.string.roleplay_immersive_mode_edge_to_edge_desc)
        RoleplayImmersiveMode.HIDE_SYSTEM_BARS -> stringResource(id = R.string.roleplay_immersive_mode_fullscreen_desc)
        RoleplayImmersiveMode.NONE -> stringResource(id = R.string.roleplay_immersive_mode_standard_desc)
    }
}

@Composable
internal fun roleplayLineHeightScaleLabel(scale: RoleplayLineHeightScale): String {
    return when (scale) {
        RoleplayLineHeightScale.COMPACT -> stringResource(id = R.string.roleplay_line_height_compact)
        RoleplayLineHeightScale.NORMAL -> stringResource(id = R.string.roleplay_line_height_normal)
        RoleplayLineHeightScale.RELAXED -> stringResource(id = R.string.roleplay_line_height_relaxed)
    }
}

@Composable
internal fun roleplayLineHeightScaleDescription(scale: RoleplayLineHeightScale): String {
    return when (scale) {
        RoleplayLineHeightScale.COMPACT -> stringResource(id = R.string.roleplay_line_height_compact_desc)
        RoleplayLineHeightScale.NORMAL -> stringResource(id = R.string.roleplay_line_height_normal_desc)
        RoleplayLineHeightScale.RELAXED -> stringResource(id = R.string.roleplay_line_height_relaxed_desc)
    }
}
