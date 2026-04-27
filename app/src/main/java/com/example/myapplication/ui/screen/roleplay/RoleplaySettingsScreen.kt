package com.example.myapplication.ui.screen.roleplay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.rememberSystemHighTextContrastEnabled
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassSurface
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.screen.settings.SettingsScreenPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleplaySettingsScreen(
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    settings: AppSettings,
    contextStatus: RoleplayContextStatus,
    currentModel: String,
    currentProviderId: String,
    providerOptions: List<ProviderSettings>,
    isLoadingModels: Boolean,
    loadingProviderId: String,
    isSavingModel: Boolean,
    latestPromptDebugDump: String,
    contextGovernance: ContextGovernanceSnapshot?,
    recentMemoryProposalHistory: List<MemoryProposalHistoryItem>,
    onOpenReadingMode: () -> Unit,
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
    onUpdateRoleplayImmersiveMode: (com.example.myapplication.model.RoleplayImmersiveMode) -> Unit,
    onUpdateRoleplayHighContrast: (Boolean) -> Unit,
    onUpdateRoleplayLineHeightScale: (com.example.myapplication.model.RoleplayLineHeightScale) -> Unit,
    onSelectProvider: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onOpenProviderDetail: (String) -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onOpenAssistantPrompt: () -> Unit,
    onOpenWorldBookSettings: () -> Unit,
    onOpenLongMemorySettings: () -> Unit,
    onUpdateAssistantMemoryEnabled: (Boolean) -> Unit,
    onRefreshConversationSummary: () -> Unit,
    onOpenContextLog: () -> Unit,
    onRestartSession: (() -> Unit) -> Unit,
    onResetSession: (() -> Unit) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val systemHighContrastEnabled = rememberSystemHighTextContrastEnabled()
    val effectiveHighContrast = settings.roleplayHighContrast || systemHighContrastEnabled
    val backdropState = rememberImmersiveBackdropState(
        backgroundUri = scenario?.backgroundUri.orEmpty(),
        highContrast = effectiveHighContrast,
    )
    var showModelSheet by rememberSaveable { mutableStateOf(false) }
    var showConfirmResetDialog by rememberSaveable { mutableStateOf(false) }
    var showConfirmRestartDialog by rememberSaveable { mutableStateOf(false) }
    var activePage by rememberSaveable { mutableStateOf(RoleplaySettingsPanelPage.MAIN) }
    var longformCharsText by rememberSaveable(settings.roleplayLongformTargetChars) {
        mutableStateOf(settings.roleplayLongformTargetChars.toString())
    }
    val scenarioTitle = scenario?.title?.trim().orEmpty().ifBlank { "沉浸扮演" }
    val palette = backdropState.palette
    Box(modifier = Modifier.fillMaxSize()) {
        RoleplaySceneBackground(
            backdropState = backdropState,
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val panelWidthFraction = if (maxWidth > 560.dp) 0.66f else 0.84f
            ImmersiveGlassSurface(
                backdropState = backdropState,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(panelWidthFraction),
                shape = RoundedCornerShape(34.dp),
                blurRadius = 22.dp,
                overlayColor = Color(0xFFF6F2EC).copy(alpha = 0.92f),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ImmersiveGlassSurface(
                        backdropState = backdropState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(26.dp),
                        blurRadius = 16.dp,
                        overlayColor = Color(0xFFF0ECE6).copy(alpha = 0.85f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NarraIconButton(
                                onClick = {
                                    if (activePage == RoleplaySettingsPanelPage.MAIN) {
                                        onNavigateBack()
                                    } else {
                                        activePage = RoleplaySettingsPanelPage.MAIN
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = RoleplaySettingsPanelTitleColor,
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                AnimatedContent(
                                    targetState = activePage,
                                    transitionSpec = {
                                        fadeIn(androidx.compose.animation.core.tween(220))
                                            .togetherWith(fadeOut(androidx.compose.animation.core.tween(180)))
                                    },
                                    label = "settings_title",
                                ) { page ->
                                    Text(
                                        text = when (page) {
                                            RoleplaySettingsPanelPage.MAIN -> "聊天设定"
                                            RoleplaySettingsPanelPage.SCENE -> "情景设定"
                                            RoleplaySettingsPanelPage.IDENTITY -> "用户身份"
                                            RoleplaySettingsPanelPage.THEME -> "主题"
                                            RoleplaySettingsPanelPage.QUICK -> "快捷切换"
                                            RoleplaySettingsPanelPage.REGEX -> "正则"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RoleplaySettingsPanelTitleColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = scenarioTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoleplaySettingsPanelBodyColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        RoleplaySettingsContent(
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
                            longformCharsText = longformCharsText,
                            onLongformCharsTextChange = { raw ->
                                val digits = raw.filter(Char::isDigit).take(4)
                                longformCharsText = digits
                                digits.toIntOrNull()?.let { value ->
                                    onUpdateRoleplayLongformTargetChars(value)
                                }
                            },
                            onNavigateToPage = { activePage = it },
                            onOpenReadingMode = onOpenReadingMode,
                            onOpenModelPicker = { showModelSheet = true },
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
                            onOpenProviderDetail = onOpenProviderDetail,
                            onOpenConnectionSettings = onOpenConnectionSettings,
                            onOpenAssistantPrompt = onOpenAssistantPrompt,
                            onOpenWorldBookSettings = onOpenWorldBookSettings,
                            onOpenLongMemorySettings = onOpenLongMemorySettings,
                            onUpdateAssistantMemoryEnabled = onUpdateAssistantMemoryEnabled,
                            onRefreshConversationSummary = onRefreshConversationSummary,
                            onShowRestartDialog = { showConfirmRestartDialog = true },
                            onShowResetDialog = { showConfirmResetDialog = true },
                        )
                    }
                }
            }
        }
    }

    RoleplaySettingsModelSheet(
        showModelSheet = showModelSheet,
        providerOptions = providerOptions,
        currentProviderId = currentProviderId,
        currentModel = currentModel,
        isLoadingModels = isLoadingModels,
        loadingProviderId = loadingProviderId,
        isSavingModel = isSavingModel,
        onDismissRequest = { showModelSheet = false },
        onSelectProvider = onSelectProvider,
        onOpenProviderDetail = onOpenProviderDetail,
        onSelectModel = onSelectModel,
    )

    RoleplayRestartConfirmDialog(
        showConfirmRestartDialog = showConfirmRestartDialog,
        onDismissRequest = { showConfirmRestartDialog = false },
        onConfirm = {
            showConfirmRestartDialog = false
            onRestartSession(onNavigateBack)
        },
    )

    RoleplayResetConfirmDialog(
        showConfirmResetDialog = showConfirmResetDialog,
        onDismissRequest = { showConfirmResetDialog = false },
        onConfirm = {
            showConfirmResetDialog = false
            onResetSession(onNavigateBack)
        },
    )
}
