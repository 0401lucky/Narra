package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassPalette
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassSurface
import com.example.myapplication.ui.screen.chat.ModelPickerSheet
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding

internal const val TAG_ROLEPLAY_SETTINGS_LIST = "roleplay_settings_list"

@Composable
internal fun RoleplaySettingsContent(
    activePage: RoleplaySettingsPanelPage,
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    settings: AppSettings,
    contextStatus: RoleplayContextStatus,
    currentModel: String,
    currentProviderId: String,
    providerOptions: List<ProviderSettings>,
    backdropState: ImmersiveBackdropState,
    latestPromptDebugDump: String,
    contextGovernance: ContextGovernanceSnapshot?,
    recentMemoryProposalHistory: List<MemoryProposalHistoryItem>,
    longMemoryCount: Int,
    sceneMemoryCount: Int,
    isRefreshingConversationSummary: Boolean,
    longformCharsText: String,
    onLongformCharsTextChange: (String) -> Unit,
    onNavigateToPage: (RoleplaySettingsPanelPage) -> Unit,
    onOpenReadingMode: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenContextLog: () -> Unit,
    onUpdateShowRoleplayPresenceStrip: (Boolean) -> Unit,
    onUpdateShowRoleplayStatusStrip: (Boolean) -> Unit,
    onUpdateShowOnlineRoleplayNarration: (Boolean) -> Unit,
    onUpdateShowRoleplayAiHelper: (Boolean) -> Unit,
    onUpdateScenarioNarrationEnabled: (Boolean) -> Unit,
    onUpdateScenarioDeepImmersionEnabled: (Boolean) -> Unit,
    onUpdateScenarioTimeAwarenessEnabled: (Boolean) -> Unit,
    onUpdateScenarioNetMemeEnabled: (Boolean) -> Unit,
    onUpdateRoleplayLongformTargetChars: (Int) -> Unit,
    onUpdateScenarioInteractionMode: (RoleplayInteractionMode) -> Unit,
    systemHighContrastEnabled: Boolean,
    onUpdateRoleplayImmersiveMode: (RoleplayImmersiveMode) -> Unit,
    onUpdateRoleplayHighContrast: (Boolean) -> Unit,
    onUpdateRoleplayLineHeightScale: (RoleplayLineHeightScale) -> Unit,
    onUpdateRoleplayNoBackgroundSkin: (RoleplayNoBackgroundSkinSettings) -> Unit,
    onOpenProviderDetail: (String) -> Unit,
    onOpenProviderSettings: () -> Unit,
    onOpenAssistantPrompt: () -> Unit,
    onOpenUserMasks: () -> Unit,
    onOpenWorldBookSettings: () -> Unit,
    onOpenLongMemorySettings: () -> Unit,
    onUpdateAssistantMemoryEnabled: (Boolean) -> Unit,
    onRefreshConversationSummary: () -> Unit,
    onShowRestartDialog: () -> Unit,
    onShowResetDialog: () -> Unit,
) {
    RoleplaySettingsSidebarContent(
        activePage = activePage,
        scenario = scenario,
        assistant = assistant,
        settings = settings,
        contextStatus = contextStatus,
        currentModel = currentModel,
        currentProviderId = currentProviderId,
        providerOptions = providerOptions,
        backdropState = backdropState,
        latestPromptDebugDump = latestPromptDebugDump,
        contextGovernance = contextGovernance,
        recentMemoryProposalHistory = recentMemoryProposalHistory,
        longMemoryCount = longMemoryCount,
        sceneMemoryCount = sceneMemoryCount,
        isRefreshingConversationSummary = isRefreshingConversationSummary,
        longformCharsText = longformCharsText,
        onLongformCharsTextChange = onLongformCharsTextChange,
        onNavigateToPage = onNavigateToPage,
        onOpenReadingMode = onOpenReadingMode,
        onOpenModelPicker = onOpenModelPicker,
        onOpenContextLog = onOpenContextLog,
        onUpdateShowRoleplayPresenceStrip = onUpdateShowRoleplayPresenceStrip,
        onUpdateShowRoleplayStatusStrip = onUpdateShowRoleplayStatusStrip,
        onUpdateShowOnlineRoleplayNarration = onUpdateShowOnlineRoleplayNarration,
        onUpdateShowRoleplayAiHelper = onUpdateShowRoleplayAiHelper,
        onUpdateScenarioNarrationEnabled = onUpdateScenarioNarrationEnabled,
        onUpdateScenarioDeepImmersionEnabled = onUpdateScenarioDeepImmersionEnabled,
        onUpdateScenarioTimeAwarenessEnabled = onUpdateScenarioTimeAwarenessEnabled,
        onUpdateScenarioNetMemeEnabled = onUpdateScenarioNetMemeEnabled,
        onUpdateRoleplayLongformTargetChars = onUpdateRoleplayLongformTargetChars,
        onUpdateScenarioInteractionMode = onUpdateScenarioInteractionMode,
        systemHighContrastEnabled = systemHighContrastEnabled,
        onUpdateRoleplayImmersiveMode = onUpdateRoleplayImmersiveMode,
        onUpdateRoleplayHighContrast = onUpdateRoleplayHighContrast,
        onUpdateRoleplayLineHeightScale = onUpdateRoleplayLineHeightScale,
        onUpdateRoleplayNoBackgroundSkin = onUpdateRoleplayNoBackgroundSkin,
        onOpenProviderDetail = onOpenProviderDetail,
        onOpenProviderSettings = onOpenProviderSettings,
        onOpenAssistantPrompt = onOpenAssistantPrompt,
        onOpenUserMasks = onOpenUserMasks,
        onOpenWorldBookSettings = onOpenWorldBookSettings,
        onOpenLongMemorySettings = onOpenLongMemorySettings,
        onUpdateAssistantMemoryEnabled = onUpdateAssistantMemoryEnabled,
        onRefreshConversationSummary = onRefreshConversationSummary,
        onShowRestartDialog = onShowRestartDialog,
        onShowResetDialog = onShowResetDialog,
    )
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
internal fun RoleplayRestartConfirmDialog(
    showConfirmRestartDialog: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!showConfirmRestartDialog) return

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(id = R.string.roleplay_restart_dialog_title)) },
        text = { Text(stringResource(id = R.string.roleplay_restart_dialog_body)) },
        confirmButton = {
            NarraTextButton(onClick = onConfirm) {
                Text(stringResource(id = R.string.roleplay_restart_dialog_confirm))
            }
        },
        dismissButton = {
            NarraTextButton(onClick = onDismissRequest) {
                Text(stringResource(id = R.string.common_cancel))
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
        title = { Text(stringResource(id = R.string.roleplay_reset_dialog_title)) },
        text = { Text(stringResource(id = R.string.roleplay_reset_dialog_body)) },
        confirmButton = {
            NarraTextButton(onClick = onConfirm) {
                Text(
                    stringResource(id = R.string.roleplay_reset_dialog_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            NarraTextButton(onClick = onDismissRequest) {
                Text(stringResource(id = R.string.common_cancel))
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
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Cursive,
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onGlass,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(id = R.string.roleplay_current_character_prefix, characterName),
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
                            text = stringResource(id = R.string.roleplay_settings_action_reading_mode),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.onGlass,
                        )
                    }
                }
            }

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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = RoleplaySettingsPanelTitleColor,
            )
            if (supportingText.isNotBlank()) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = RoleplaySettingsPanelBodyColor,
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
        shape = RoundedCornerShape(24.dp),
        overlayColor = Color(0xFFF2EEE8).copy(alpha = 0.82f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}
