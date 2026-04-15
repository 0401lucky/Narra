package com.example.myapplication.ui.screen.roleplay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.MemoryProposalStatus
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassChip
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassPalette
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassSurface
import com.example.myapplication.ui.screen.chat.ModelPickerSheet
import com.example.myapplication.ui.screen.settings.AnimatedSettingButton
import com.example.myapplication.ui.screen.settings.SettingsListRow
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding
import com.example.myapplication.ui.screen.settings.SettingsStatusPill

@Composable
internal fun RoleplaySettingsContent(
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    settings: AppSettings,
    contextStatus: RoleplayContextStatus,
    currentModel: String,
    backdropState: ImmersiveBackdropState,
    latestPromptDebugDump: String,
    contextGovernance: ContextGovernanceSnapshot?,
    recentMemoryProposalHistory: List<MemoryProposalHistoryItem>,
    longformCharsText: String,
    onLongformCharsTextChange: (String) -> Unit,
    onOpenReadingMode: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenPromptDebugSheet: () -> Unit,
    onUpdateShowRoleplayPresenceStrip: (Boolean) -> Unit,
    onUpdateShowRoleplayStatusStrip: (Boolean) -> Unit,
    onUpdateShowOnlineRoleplayNarration: (Boolean) -> Unit,
    onUpdateShowRoleplayAiHelper: (Boolean) -> Unit,
    onUpdateScenarioInteractionMode: (RoleplayInteractionMode) -> Unit,
    systemHighContrastEnabled: Boolean,
    onUpdateRoleplayImmersiveMode: (RoleplayImmersiveMode) -> Unit,
    onUpdateRoleplayHighContrast: (Boolean) -> Unit,
    onUpdateRoleplayLineHeightScale: (RoleplayLineHeightScale) -> Unit,
    onShowRestartDialog: () -> Unit,
    onShowResetDialog: () -> Unit,
) {
    val palette = backdropState.palette
    val scenarioTitle = scenario?.title?.trim().orEmpty().ifBlank { "沉浸扮演" }
    val characterName = scenario?.characterDisplayNameOverride
        ?.trim()
        .orEmpty()
        .ifBlank { assistant?.name?.trim().orEmpty() }
        .ifBlank { "角色" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsScreenPadding,
            top = 4.dp,
            end = SettingsScreenPadding,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            RoleplaySettingsHero(
                backdropState = backdropState,
                scenarioTitle = scenarioTitle,
                characterName = characterName,
                currentModel = currentModel,
                contextStatus = contextStatus,
                contextGovernance = contextGovernance,
                onOpenReadingMode = onOpenReadingMode,
            )
        }

        item {
            ImmersiveSettingsCard(backdropState) {
                RoleplaySettingSwitchRow(
                    palette = palette,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = palette.onGlass,
                        )
                    },
                    title = "显示身份条",
                    checked = settings.showRoleplayPresenceStrip,
                    onCheckedChange = onUpdateShowRoleplayPresenceStrip,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 18.dp),
                    color = palette.panelBorder.copy(alpha = 0.44f),
                )
                RoleplaySettingSwitchRow(
                    palette = palette,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ViewAgenda,
                            contentDescription = null,
                            tint = palette.onGlass,
                        )
                    },
                    title = "显示状态条",
                    checked = settings.showRoleplayStatusStrip,
                    onCheckedChange = onUpdateShowRoleplayStatusStrip,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 18.dp),
                    color = palette.panelBorder.copy(alpha = 0.44f),
                )
                RoleplaySettingSwitchRow(
                    palette = palette,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = palette.onGlass,
                        )
                    },
                    title = "显示 AI 帮写",
                    checked = settings.showRoleplayAiHelper,
                    onCheckedChange = onUpdateShowRoleplayAiHelper,
                )
                if (scenario?.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp, end = 18.dp),
                        color = palette.panelBorder.copy(alpha = 0.44f),
                    )
                    RoleplaySettingSwitchRow(
                        palette = palette,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = palette.onGlass,
                            )
                        },
                        title = "显示心声与状态提示",
                        supportingText = "关闭后不会继续生成或显示线上心声星标，仍保留必要的系统状态提示。",
                        checked = settings.showOnlineRoleplayNarration,
                        onCheckedChange = onUpdateShowOnlineRoleplayNarration,
                    )
                }
            }
        }

        item {
            ImmersiveSettingsCard(backdropState) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "剧情交互模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onGlass,
                    )
                    Text(
                        text = "切换后当前场景会立即改成对应模式：长文、普通对白或线上手机聊天。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onGlassMuted,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoleplayInteractionMode.entries.forEach { mode ->
                            val selected = scenario?.interactionMode == mode
                            val borderColor by animateColorAsState(
                                targetValue = if (selected) {
                                    palette.characterAccent.copy(alpha = 0.72f)
                                } else {
                                    Color.Transparent
                                },
                                label = "interaction_tab_border_${mode.name}",
                            )
                            val bgAlpha by animateFloatAsState(
                                targetValue = if (selected) 0.38f else 0.18f,
                                label = "interaction_tab_bg_${mode.name}",
                            )
                            Surface(
                                modifier = Modifier.weight(1f),
                                onClick = { onUpdateScenarioInteractionMode(mode) },
                                shape = RoundedCornerShape(16.dp),
                                color = palette.panelTintStrong.copy(alpha = bgAlpha),
                                enabled = scenario != null,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 0.dp,
                                    color = borderColor,
                                ),
                            ) {
                                Text(
                                    text = mode.displayName,
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp,
                                        vertical = 12.dp,
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) palette.characterAccent else palette.onGlassMuted,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
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
                            tint = palette.onGlass,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = "长剧情默认字数",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.onGlass,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    OutlinedTextField(
                        value = longformCharsText,
                        onValueChange = onLongformCharsTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        label = { Text("默认字数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedBorderColor = palette.panelBorder.copy(alpha = 0.3f),
                            focusedBorderColor = palette.panelBorder.copy(alpha = 0.5f),
                            unfocusedTextColor = palette.onGlass,
                            focusedTextColor = palette.onGlass,
                            unfocusedLabelColor = palette.onGlassMuted,
                            focusedLabelColor = palette.onGlassMuted,
                            unfocusedSupportingTextColor = palette.onGlassMuted,
                            focusedSupportingTextColor = palette.onGlassMuted,
                        ),
                        singleLine = true,
                    )
                }
            }
        }

        item {
            ImmersiveSettingsCard(backdropState) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.roleplay_readability_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.onGlass,
                    )
                    Text(
                        text = stringResource(id = R.string.roleplay_readability_section_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onGlassMuted,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.roleplay_immersive_mode_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onGlass,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RoleplayImmersiveMode.entries.forEach { mode ->
                                val selected = settings.roleplayImmersiveMode == mode
                                val borderColor by animateColorAsState(
                                    targetValue = if (selected) {
                                        palette.characterAccent.copy(alpha = 0.72f)
                                    } else {
                                        Color.Transparent
                                    },
                                    label = "immersive_tab_border_${mode.storageValue}",
                                )
                                val bgAlpha by animateFloatAsState(
                                    targetValue = if (selected) 0.38f else 0.18f,
                                    label = "immersive_tab_bg_${mode.storageValue}",
                                )
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("roleplay_immersive_${mode.storageValue}"),
                                    onClick = { onUpdateRoleplayImmersiveMode(mode) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = palette.panelTintStrong.copy(alpha = bgAlpha),
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 0.dp,
                                        color = borderColor,
                                    ),
                                ) {
                                    Text(
                                        text = roleplayImmersiveModeLabel(mode),
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 12.dp,
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) palette.characterAccent else palette.onGlassMuted,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Text(
                            text = roleplayImmersiveModeDescription(settings.roleplayImmersiveMode),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onGlassMuted,
                        )
                    }
                    HorizontalDivider(color = palette.panelBorder.copy(alpha = 0.44f))
                    RoleplaySettingSwitchRow(
                        palette = palette,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = palette.onGlass,
                            )
                        },
                        title = "高对比度模式",
                        supportingText = if (systemHighContrastEnabled && !settings.roleplayHighContrast) {
                            stringResource(id = R.string.roleplay_high_contrast_system_enabled)
                        } else {
                            stringResource(id = R.string.roleplay_high_contrast_supporting)
                        },
                        checked = settings.roleplayHighContrast,
                        switchModifier = Modifier.testTag("roleplay_high_contrast_switch"),
                        onCheckedChange = onUpdateRoleplayHighContrast,
                    )
                    HorizontalDivider(color = palette.panelBorder.copy(alpha = 0.44f))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.roleplay_line_height_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onGlass,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RoleplayLineHeightScale.entries.forEach { scale ->
                                val selected = settings.roleplayLineHeightScale == scale
                                val borderColor by animateColorAsState(
                                    targetValue = if (selected) {
                                        palette.characterAccent.copy(alpha = 0.72f)
                                    } else {
                                        Color.Transparent
                                    },
                                    label = "line_height_tab_border_${scale.storageValue}",
                                )
                                val bgAlpha by animateFloatAsState(
                                    targetValue = if (selected) 0.38f else 0.18f,
                                    label = "line_height_tab_bg_${scale.storageValue}",
                                )
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("roleplay_line_height_${scale.storageValue}"),
                                    onClick = { onUpdateRoleplayLineHeightScale(scale) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = palette.panelTintStrong.copy(alpha = bgAlpha),
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 0.dp,
                                        color = borderColor,
                                    ),
                                ) {
                                    Text(
                                        text = roleplayLineHeightScaleLabel(scale),
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 12.dp,
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) palette.characterAccent else palette.onGlassMuted,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Text(
                            text = roleplayLineHeightScaleDescription(settings.roleplayLineHeightScale),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onGlassMuted,
                        )
                    }
                }
            }
        }

        if (recentMemoryProposalHistory.isNotEmpty()) {
            item {
                ImmersiveSettingsCard(backdropState) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "最近记忆提议",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.onGlass,
                        )
                        recentMemoryProposalHistory
                            .take(5)
                            .forEachIndexed { index, item ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = palette.panelBorder.copy(alpha = 0.44f),
                                    )
                                }
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        SettingsStatusPill(
                                            text = item.status.label,
                                            containerColor = when (item.status) {
                                                MemoryProposalStatus.PENDING -> palette.panelTintStrong
                                                MemoryProposalStatus.APPROVED -> palette.chipTint
                                                MemoryProposalStatus.REJECTED -> palette.panelTint
                                            },
                                            contentColor = palette.onGlass,
                                        )
                                        SettingsStatusPill(
                                            text = item.scopeType.label,
                                            containerColor = palette.panelTint,
                                            contentColor = palette.onGlassMuted,
                                        )
                                    }
                                    Text(
                                        text = item.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.onGlass,
                                    )
                                    item.reason.takeIf { it.isNotBlank() }?.let { reason ->
                                        Text(
                                            text = reason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = palette.onGlassMuted,
                                        )
                                    }
                                }
                            }
                    }
                }
            }
        }

        item {
            ImmersiveSettingsCard(backdropState) {
                SettingsListRow(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = palette.onGlass,
                        )
                    },
                    title = "阅读模式",
                    onClick = onOpenReadingMode,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 18.dp),
                    color = palette.panelBorder.copy(alpha = 0.44f),
                )
                SettingsListRow(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = palette.onGlass,
                        )
                    },
                    title = "切换模型",
                    supportingText = currentModel.ifBlank { "当前未选择模型" },
                    onClick = onOpenModelPicker,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 18.dp),
                    color = palette.panelBorder.copy(alpha = 0.44f),
                )
                SettingsListRow(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.SettingsSuggest,
                            contentDescription = null,
                            tint = palette.onGlass,
                        )
                    },
                    title = "上下文治理",
                    supportingText = contextGovernance?.summarySupportingText.orEmpty(),
                    onClick = onOpenPromptDebugSheet,
                    enabled = latestPromptDebugDump.isNotBlank() || contextGovernance != null,
                )
            }
        }

        item {
            ImmersiveSettingsCard(backdropState) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AnimatedSettingButton(
                        text = "重开剧情",
                        onClick = onShowRestartDialog,
                        enabled = scenario != null,
                        isPrimary = false,
                        leadingIcon = {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        },
                    )
                    HorizontalDivider(color = palette.panelBorder.copy(alpha = 0.30f))
                    AnimatedSettingButton(
                        text = "清空剧情",
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoleplaySettingsModelSheet(
    showModelSheet: Boolean,
    providerOptions: List<ProviderSettings>,
    currentProviderId: String,
    currentModel: String,
    isLoadingModels: Boolean,
    loadingProviderId: String,
    isSavingModel: Boolean,
    onDismissRequest: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onOpenProviderDetail: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
) {
    if (!showModelSheet) return

    ModelPickerSheet(
        providerOptions = providerOptions,
        currentProviderId = currentProviderId,
        currentModel = currentModel,
        isLoadingModels = isLoadingModels,
        loadingProviderId = loadingProviderId,
        isSavingModel = isSavingModel,
        onDismissRequest = onDismissRequest,
        onSelectProvider = onSelectProvider,
        onOpenProviderDetail = { providerId ->
            onDismissRequest()
            onOpenProviderDetail(providerId)
        },
        onSelectModel = { providerId, model ->
            onSelectModel(providerId, model)
            onDismissRequest()
        },
    )
}

@Composable
private fun roleplayImmersiveModeLabel(mode: RoleplayImmersiveMode): String {
    return when (mode) {
        RoleplayImmersiveMode.EDGE_TO_EDGE -> stringResource(id = R.string.roleplay_immersive_mode_edge_to_edge)
        RoleplayImmersiveMode.HIDE_SYSTEM_BARS -> stringResource(id = R.string.roleplay_immersive_mode_fullscreen)
        RoleplayImmersiveMode.NONE -> stringResource(id = R.string.roleplay_immersive_mode_standard)
    }
}

@Composable
private fun roleplayImmersiveModeDescription(mode: RoleplayImmersiveMode): String {
    return when (mode) {
        RoleplayImmersiveMode.EDGE_TO_EDGE -> stringResource(id = R.string.roleplay_immersive_mode_edge_to_edge_desc)
        RoleplayImmersiveMode.HIDE_SYSTEM_BARS -> stringResource(id = R.string.roleplay_immersive_mode_fullscreen_desc)
        RoleplayImmersiveMode.NONE -> stringResource(id = R.string.roleplay_immersive_mode_standard_desc)
    }
}

@Composable
private fun roleplayLineHeightScaleLabel(scale: RoleplayLineHeightScale): String {
    return when (scale) {
        RoleplayLineHeightScale.COMPACT -> stringResource(id = R.string.roleplay_line_height_compact)
        RoleplayLineHeightScale.NORMAL -> stringResource(id = R.string.roleplay_line_height_normal)
        RoleplayLineHeightScale.RELAXED -> stringResource(id = R.string.roleplay_line_height_relaxed)
    }
}

@Composable
private fun roleplayLineHeightScaleDescription(scale: RoleplayLineHeightScale): String {
    return when (scale) {
        RoleplayLineHeightScale.COMPACT -> stringResource(id = R.string.roleplay_line_height_compact_desc)
        RoleplayLineHeightScale.NORMAL -> stringResource(id = R.string.roleplay_line_height_normal_desc)
        RoleplayLineHeightScale.RELAXED -> stringResource(id = R.string.roleplay_line_height_relaxed_desc)
    }
}

@Composable
internal fun RoleplayRestartConfirmDialog(
    showConfirmRestartDialog: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!showConfirmRestartDialog) return

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("重开剧情") },
        text = { Text("将创建一条新的剧情线，旧剧情历史不会继续沿用。是否继续？") },
        confirmButton = {
            NarraTextButton(onClick = onConfirm) {
                Text("重开")
            }
        },
        dismissButton = {
            NarraTextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        },
    )
}

@Composable
internal fun RoleplayResetConfirmDialog(
    showConfirmResetDialog: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!showConfirmResetDialog) return

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("清空剧情") },
        text = { Text("会保留场景配置，只清空当前剧情消息。此操作不可撤销。") },
        confirmButton = {
            NarraTextButton(onClick = onConfirm) {
                Text("确定清空", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            NarraTextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        },
    )
}

@Composable
internal fun RoleplaySettingsHero(
    backdropState: ImmersiveBackdropState,
    scenarioTitle: String,
    characterName: String,
    currentModel: String,
    contextStatus: RoleplayContextStatus,
    contextGovernance: ContextGovernanceSnapshot?,
    onOpenReadingMode: () -> Unit,
) {
    val palette = backdropState.palette

    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp)),
        shape = RoundedCornerShape(30.dp),
        overlayColor = Color.White.copy(alpha = 0.1f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = scenarioTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.onGlass,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "当前沉浸角色：$characterName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onGlassMuted,
                    )
                }
                ImmersiveGlassSurface(
                    backdropState = backdropState,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    overlayColor = Color.White.copy(alpha = 0.1f),
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onOpenReadingMode)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "阅读模式",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.onGlass,
                        )
                    }
                }
            }

            // 状态摘要文本（合并原"当前沉浸状态"卡片内容）
            Text(
                text = contextGovernance?.let { governance ->
                    buildString {
                        append(if (contextStatus.isContinuingSession) "继续旧剧情" else "新剧情")
                        if (currentModel.isNotBlank()) {
                            append(" · $currentModel")
                        }
                        append(" · ")
                        append(governance.summarySupportingText)
                        if (governance.worldBookHitCount > 0) {
                            append(" · 世界书 ${governance.worldBookHitCount}")
                        }
                        if (governance.memoryCount > 0) {
                            append(" · 记忆 ${governance.memoryCount}")
                        }
                    }
                } ?: buildString {
                    append(if (contextStatus.isContinuingSession) "继续旧剧情" else "新剧情")
                    if (currentModel.isNotBlank()) {
                        append(" · $currentModel")
                    }
                    if (contextStatus.worldBookHitCount > 0) {
                        append(" · 世界书 ${contextStatus.worldBookHitCount}")
                    }
                    if (contextStatus.memoryInjectionCount > 0) {
                        append(" · 记忆 ${contextStatus.memoryInjectionCount}")
                    }
                    if (contextStatus.hasSummary) {
                        append(" · 摘要 ${contextStatus.summaryCoveredMessageCount}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.onGlassMuted,
            )
        }
    }
}

@Composable
internal fun RoleplaySettingSwitchRow(
    palette: ImmersiveGlassPalette,
    icon: @Composable () -> Unit,
    title: String,
    supportingText: String = "",
    checked: Boolean,
    switchModifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.onGlass,
            )
            if (supportingText.isNotBlank()) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onGlassMuted,
                )
            }
        }
        Switch(
            modifier = switchModifier,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
internal fun ImmersiveSettingsCard(
    backdropState: ImmersiveBackdropState,
    content: @Composable ColumnScope.() -> Unit,
) {
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        overlayColor = Color.White.copy(alpha = 0.1f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}
