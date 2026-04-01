package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.screen.settings.SettingsTopBar

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
    recentMemoryProposalHistory: List<MemoryProposalHistoryItem>,
    onOpenReadingMode: () -> Unit,
    onUpdateShowRoleplayPresenceStrip: (Boolean) -> Unit,
    onUpdateShowRoleplayStatusStrip: (Boolean) -> Unit,
    onUpdateShowRoleplayAiHelper: (Boolean) -> Unit,
    onUpdateRoleplayLongformTargetChars: (Int) -> Unit,
    onSelectProvider: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onOpenProviderDetail: (String) -> Unit,
    onRestartSession: () -> Unit,
    onResetSession: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val backdropState = rememberImmersiveBackdropState(scenario?.backgroundUri.orEmpty())
    var showModelSheet by rememberSaveable { mutableStateOf(false) }
    var showPromptDebugSheet by rememberSaveable { mutableStateOf(false) }
    var showConfirmResetDialog by rememberSaveable { mutableStateOf(false) }
    var showConfirmRestartDialog by rememberSaveable { mutableStateOf(false) }
    var longformCharsText by rememberSaveable(settings.roleplayLongformTargetChars) {
        mutableStateOf(settings.roleplayLongformTargetChars.toString())
    }
    Box(modifier = Modifier.fillMaxSize()) {
        RoleplaySceneBackground(
            backdropState = backdropState,
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        Scaffold(
            topBar = {
                SettingsTopBar(
                    title = "沉浸设置",
                    subtitle = scenario?.title?.trim().orEmpty().ifBlank { "沉浸扮演" },
                    onNavigateBack = onNavigateBack,
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                RoleplaySettingsContent(
                    scenario = scenario,
                    assistant = assistant,
                    settings = settings,
                    contextStatus = contextStatus,
                    currentModel = currentModel,
                    backdropState = backdropState,
                    latestPromptDebugDump = latestPromptDebugDump,
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
                    onUpdateShowRoleplayAiHelper = onUpdateShowRoleplayAiHelper,
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

    RoleplayPromptDebugSheet(
        showPromptDebugSheet = showPromptDebugSheet,
        latestPromptDebugDump = latestPromptDebugDump,
        onDismissRequest = { showPromptDebugSheet = false },
    )

    RoleplayRestartConfirmDialog(
        showConfirmRestartDialog = showConfirmRestartDialog,
        onDismissRequest = { showConfirmRestartDialog = false },
        onConfirm = {
            showConfirmRestartDialog = false
            onRestartSession()
            onNavigateBack()
        },
    )

    RoleplayResetConfirmDialog(
        showConfirmResetDialog = showConfirmResetDialog,
        onDismissRequest = { showConfirmResetDialog = false },
        onConfirm = {
            showConfirmResetDialog = false
            onResetSession()
            onNavigateBack()
        },
    )
}

