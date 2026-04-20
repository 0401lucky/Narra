package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.myapplication.ui.component.ContextGovernanceSheet
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
    onRefreshConversationSummary: () -> Unit,
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
    var showPromptDebugSheet by rememberSaveable { mutableStateOf(false) }
    var showConfirmResetDialog by rememberSaveable { mutableStateOf(false) }
    var showConfirmRestartDialog by rememberSaveable { mutableStateOf(false) }
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
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        Column(modifier = Modifier.fillMaxSize()) {
            // 自定义沉浸式顶栏
            ImmersiveGlassSurface(
                backdropState = backdropState,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = SettingsScreenPadding, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                blurRadius = 20.dp,
                overlayColor = palette.panelTintStrong.copy(alpha = 0.72f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NarraIconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = palette.onGlass,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "沉浸设置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.onGlass,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = scenarioTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onGlassMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 设置内容
            Box(modifier = Modifier.fillMaxSize()) {
                RoleplaySettingsContent(
                    scenario = scenario,
                    assistant = assistant,
                    settings = settings,
                    contextStatus = contextStatus,
                    currentModel = currentModel,
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
                    onOpenReadingMode = onOpenReadingMode,
                    onOpenModelPicker = { showModelSheet = true },
                    onOpenPromptDebugSheet = { showPromptDebugSheet = true },
                    onUpdateShowRoleplayPresenceStrip = onUpdateShowRoleplayPresenceStrip,
                    onUpdateShowRoleplayStatusStrip = onUpdateShowRoleplayStatusStrip,
                    onUpdateShowOnlineRoleplayNarration = onUpdateShowOnlineRoleplayNarration,
                    onUpdateShowRoleplayAiHelper = onUpdateShowRoleplayAiHelper,
                    onUpdateScenarioNarrationEnabled = onUpdateScenarioNarrationEnabled,
                    onUpdateScenarioDeepImmersionEnabled = onUpdateScenarioDeepImmersionEnabled,
                    onUpdateScenarioTimeAwarenessEnabled = onUpdateScenarioTimeAwarenessEnabled,
                    onUpdateScenarioNetMemeEnabled = onUpdateScenarioNetMemeEnabled,
                    onUpdateScenarioInteractionMode = onUpdateScenarioInteractionMode,
                    systemHighContrastEnabled = systemHighContrastEnabled,
                    onUpdateRoleplayImmersiveMode = onUpdateRoleplayImmersiveMode,
                    onUpdateRoleplayHighContrast = onUpdateRoleplayHighContrast,
                    onUpdateRoleplayLineHeightScale = onUpdateRoleplayLineHeightScale,
                    onShowRestartDialog = { showConfirmRestartDialog = true },
                    onShowResetDialog = { showConfirmResetDialog = true },
                )
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

    if (showPromptDebugSheet) {
        ContextGovernanceSheet(
            snapshot = contextGovernance,
            rawDebugDump = latestPromptDebugDump,
            onRefreshSummary = onRefreshConversationSummary,
            onDismissRequest = { showPromptDebugSheet = false },
        )
    }

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
