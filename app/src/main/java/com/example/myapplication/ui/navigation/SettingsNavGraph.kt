package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.myapplication.di.AppGraph
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.roleplay.script.RoleplayScriptScope
import com.example.myapplication.ui.screen.settings.PresetEditScreen
import com.example.myapplication.ui.screen.settings.PresetListScreen
import com.example.myapplication.ui.screen.settings.RoleplayScriptBindingOption
import com.example.myapplication.ui.screen.settings.RoleplayScriptLabScreen
import com.example.myapplication.ui.screen.settings.SettingsScreen
import com.example.myapplication.ui.screen.settings.UserPersonaMasksScreen
import com.example.myapplication.ui.screen.settings.VoiceSynthesisSettingsScreen
import com.example.myapplication.ui.screen.settings.copyPresetForUser
import com.example.myapplication.viewmodel.AppUpdateViewModel
import com.example.myapplication.viewmodel.RoleplayScriptLabViewModel
import com.example.myapplication.viewmodel.SettingsViewModel
import com.example.myapplication.viewmodel.updateAutoCollapseThinking
import com.example.myapplication.viewmodel.updateAutoPreviewImages
import com.example.myapplication.viewmodel.updateCodeBlockAutoCollapse
import com.example.myapplication.viewmodel.updateCodeBlockAutoWrap
import com.example.myapplication.viewmodel.updateMessageTextScale
import com.example.myapplication.viewmodel.updateReasoningExpandedByDefault
import com.example.myapplication.viewmodel.updateShowThinkingContent
import com.example.myapplication.viewmodel.updateThemeMode
import kotlinx.coroutines.launch

internal fun NavGraphBuilder.registerSettingsNavGraph(
    appGraph: AppGraph,
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    appUpdateViewModel: AppUpdateViewModel,
) {
    navigation(
        startDestination = AppRoutes.SETTINGS,
        route = AppRoutes.SETTINGS_GRAPH,
    ) {
        // ── 设置主页 ──
        composable(AppRoutes.SETTINGS) {
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val draftHasRequiredConfig = if (settingsUiState.providers.isNotEmpty()) {
                settingsUiState.currentProvider?.hasRequiredConfig() == true
            } else {
                settingsUiState.baseUrl.isNotBlank() &&
                    settingsUiState.apiKey.isNotBlank() &&
                    settingsUiState.selectedModel.isNotBlank()
            }
            SettingsScreen(
                uiState = settingsUiState,
                onSave = {
                    settingsViewModel.saveSettings {
                        if (draftHasRequiredConfig) {
                            navController.navigate(AppRoutes.ROLEPLAY) {
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate(AppRoutes.HOME) {
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onConsumeMessage = settingsViewModel::consumeMessage,
                onOpenRoleplay = {
                    navController.navigate(AppRoutes.ROLEPLAY) {
                        launchSingleTop = true
                    }
                },
                onOpenProviderSettings = {
                    navController.navigate(AppRoutes.SETTINGS_PROVIDERS) {
                        launchSingleTop = true
                    }
                },
                onOpenPresetSettings = {
                    navController.navigate(AppRoutes.SETTINGS_PRESETS) {
                        launchSingleTop = true
                    }
                },
                onOpenVoiceSynthesisSettings = {
                    navController.navigate(AppRoutes.SETTINGS_VOICE_SYNTHESIS) {
                        launchSingleTop = true
                    }
                },
                onOpenUpdateSettings = {
                    navController.navigate(AppRoutes.SETTINGS_UPDATES) {
                        launchSingleTop = true
                    }
                },
                onOpenUserMasks = {
                    navController.navigate(AppRoutes.SETTINGS_USER_MASKS) {
                        launchSingleTop = true
                    }
                },
                onOpenModelSettings = {
                    navController.navigate(AppRoutes.SETTINGS_MODEL) {
                        launchSingleTop = true
                    }
                },
                onOpenAssistantSettings = {
                    navController.navigate(AppRoutes.SETTINGS_ASSISTANTS) {
                        launchSingleTop = true
                    }
                },
                onOpenWorldBookSettings = {
                    navController.navigate(AppRoutes.SETTINGS_WORLD_BOOKS) {
                        launchSingleTop = true
                    }
                },
                onOpenMemorySettings = {
                    navController.navigate(AppRoutes.SETTINGS_MEMORY) {
                        launchSingleTop = true
                    }
                },
                onOpenContextTransferSettings = {
                    navController.navigate(AppRoutes.SETTINGS_CONTEXT_TRANSFER) {
                        launchSingleTop = true
                    }
                },
                onOpenRoleplayScripts = {
                    navController.navigate(AppRoutes.SETTINGS_ROLEPLAY_SCRIPTS) {
                        launchSingleTop = true
                    }
                },
                onUpdateThemeMode = settingsViewModel::updateThemeMode,
                onUpdateMessageTextScale = settingsViewModel::updateMessageTextScale,
                onUpdateReasoningExpandedByDefault = settingsViewModel::updateReasoningExpandedByDefault,
                onUpdateShowThinkingContent = settingsViewModel::updateShowThinkingContent,
                onUpdateAutoCollapseThinking = settingsViewModel::updateAutoCollapseThinking,
                onUpdateAutoPreviewImages = settingsViewModel::updateAutoPreviewImages,
                onUpdateCodeBlockAutoWrap = settingsViewModel::updateCodeBlockAutoWrap,
                onUpdateCodeBlockAutoCollapse = settingsViewModel::updateCodeBlockAutoCollapse,
                onOpenHome = {
                    navController.navigate(AppRoutes.HOME) {
                        launchSingleTop = true
                    }
                },
                onNavigateBack = {
                    settingsViewModel.saveSettings {}
                    navController.popBackStack()
                },
            )
        }

        composable(AppRoutes.SETTINGS_PRESETS) {
            val storedSettings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
            val presets by appGraph.presetRepository.observePresets().collectAsStateWithLifecycle(emptyList())
            val coroutineScope = rememberCoroutineScope()
            PresetListScreen(
                presets = presets,
                defaultPresetId = storedSettings.defaultPresetId,
                onOpenPreset = { presetId ->
                    navController.navigate(AppRoutes.settingsPresetEdit(presetId)) {
                        launchSingleTop = true
                    }
                },
                onSetDefault = settingsViewModel::saveDefaultPresetId,
                onCopyPreset = { preset ->
                    coroutineScope.launch {
                        val copied = copyPresetForUser(preset)
                        appGraph.presetRepository.upsertPreset(copied)
                        navController.navigate(AppRoutes.settingsPresetEdit(copied.id)) {
                            launchSingleTop = true
                        }
                    }
                },
                onImportPreset = { preset ->
                    coroutineScope.launch {
                        appGraph.presetRepository.upsertPreset(preset)
                        navController.navigate(AppRoutes.settingsPresetEdit(preset.id)) {
                            launchSingleTop = true
                        }
                    }
                },
                onDeletePreset = { presetId ->
                    coroutineScope.launch {
                        appGraph.presetRepository.deleteCustomPreset(presetId)
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.SETTINGS_PRESET_EDIT) { backStackEntry ->
            val storedSettings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
            val presets by appGraph.presetRepository.observePresets().collectAsStateWithLifecycle(emptyList())
            val coroutineScope = rememberCoroutineScope()
            val presetId = backStackEntry.arguments
                ?.getString("presetId")
                ?.let(Uri::decode)
                .orEmpty()
            val preset = presets.firstOrNull { it.id == presetId } ?: return@composable
            PresetEditScreen(
                preset = preset,
                isGlobalDefault = storedSettings.defaultPresetId == preset.id,
                onSavePreset = { updated ->
                    coroutineScope.launch {
                        appGraph.presetRepository.upsertPreset(updated)
                        navController.popBackStack()
                    }
                },
                onCopyPreset = { source ->
                    coroutineScope.launch {
                        val copied = copyPresetForUser(source)
                        appGraph.presetRepository.upsertPreset(copied)
                        navController.navigate(AppRoutes.settingsPresetEdit(copied.id)) {
                            launchSingleTop = true
                        }
                    }
                },
                onDeletePreset = { deletingPresetId ->
                    coroutineScope.launch {
                        appGraph.presetRepository.deleteCustomPreset(deletingPresetId)
                        navController.popBackStack(AppRoutes.SETTINGS_PRESETS, false)
                    }
                },
                onSetDefault = settingsViewModel::saveDefaultPresetId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.SETTINGS_USER_MASKS) {
            val storedSettings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
            UserPersonaMasksScreen(
                settings = storedSettings,
                onUpsertMask = settingsViewModel::upsertUserPersonaMask,
                onDeleteMask = settingsViewModel::deleteUserPersonaMask,
                onSetDefaultMask = settingsViewModel::setDefaultUserPersonaMask,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.SETTINGS_VOICE_SYNTHESIS) {
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            VoiceSynthesisSettingsScreen(
                settings = settingsUiState.voiceSynthesisSettings,
                assistants = settingsUiState.savedSettings.resolvedAssistants(),
                uiMessage = settingsUiState.message,
                isTesting = settingsUiState.isTestingVoiceSynthesis,
                onUpdateSettings = settingsViewModel::updateVoiceSynthesisSettings,
                onTestSettings = settingsViewModel::testVoiceSynthesis,
                onSaveChanges = { settingsViewModel.saveSettings {} },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.SETTINGS_ROLEPLAY_SCRIPTS) {
            val scriptLabViewModel: RoleplayScriptLabViewModel = viewModel(
                factory = RoleplayScriptLabViewModel.factory(
                    scriptRepository = appGraph.roleplayScriptRepository,
                    scriptEngine = appGraph.roleplayScriptEngine,
                ),
            )
            val storedSettings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
            val scenarios by appGraph.roleplayRepository.observeScenarios().collectAsStateWithLifecycle(emptyList())
            val sessions by appGraph.roleplayRepository.observeSessions().collectAsStateWithLifecycle(emptyList())
            val bindingOptions = remember(storedSettings, scenarios, sessions) {
                buildRoleplayScriptBindingOptions(
                    settings = storedSettings,
                    scenarios = scenarios,
                    sessions = sessions,
                )
            }
            val scriptLabState by scriptLabViewModel.uiState.collectAsStateWithLifecycle()
            RoleplayScriptLabScreen(
                uiState = scriptLabState,
                bindingOptions = bindingOptions,
                onCreateScript = scriptLabViewModel::createScript,
                onSelectScript = scriptLabViewModel::selectScript,
                onApplyTemplate = scriptLabViewModel::applyTemplate,
                onUpdateName = scriptLabViewModel::updateName,
                onUpdateScope = scriptLabViewModel::updateScope,
                onUpdateOwnerId = scriptLabViewModel::updateOwnerId,
                onUpdateSource = scriptLabViewModel::updateSource,
                onUpdateEnabled = scriptLabViewModel::updateEnabled,
                onTogglePermission = scriptLabViewModel::togglePermission,
                onUpdateTestEvent = scriptLabViewModel::updateTestEvent,
                onUpdateTestUserText = scriptLabViewModel::updateTestUserText,
                onUpdateTestPromptText = scriptLabViewModel::updateTestPromptText,
                onUpdateTestAssistantText = scriptLabViewModel::updateTestAssistantText,
                onUpdateTestVariablesText = scriptLabViewModel::updateTestVariablesText,
                onRunScriptTest = scriptLabViewModel::runScriptTest,
                onSaveScript = scriptLabViewModel::saveScript,
                onDeleteSelectedScript = scriptLabViewModel::deleteSelectedScript,
                onConsumeMessage = scriptLabViewModel::consumeMessage,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── 提供商、模型、更新 ──
        registerSettingsProviderRoutes(
            navController = navController,
            settingsViewModel = settingsViewModel,
            appUpdateViewModel = appUpdateViewModel,
        )

        // ── 角色配置 ──
        registerSettingsAssistantRoutes(
            appGraph = appGraph,
            navController = navController,
            settingsViewModel = settingsViewModel,
        )

        // ── 世界书、上下文导入导出、记忆管理 ──
        registerSettingsDataRoutes(
            appGraph = appGraph,
            navController = navController,
            settingsViewModel = settingsViewModel,
        )
    }
}

private fun buildRoleplayScriptBindingOptions(
    settings: AppSettings,
    scenarios: List<RoleplayScenario>,
    sessions: List<RoleplaySession>,
): List<RoleplayScriptBindingOption> {
    val scenarioTitlesById = scenarios.associate { scenario ->
        scenario.id to scenario.title.ifBlank { "未命名场景" }
    }
    val characterOptions = settings.resolvedAssistants().map { assistant ->
        RoleplayScriptBindingOption(
            scope = RoleplayScriptScope.CHARACTER,
            id = assistant.id,
            title = assistant.name.ifBlank { "未命名角色" },
            subtitle = "角色 ID：${assistant.id}",
        )
    }
    val scenarioOptions = scenarios.map { scenario ->
        RoleplayScriptBindingOption(
            scope = RoleplayScriptScope.SCENARIO,
            id = scenario.id,
            title = scenario.title.ifBlank { "未命名场景" },
            subtitle = "场景 ID：${scenario.id}",
        )
    }
    val sessionOptions = sessions.map { session ->
        RoleplayScriptBindingOption(
            scope = RoleplayScriptScope.SESSION,
            id = session.id,
            title = scenarioTitlesById[session.scenarioId] ?: "未命名会话",
            subtitle = "会话 ID：${session.id}",
        )
    }
    return characterOptions + scenarioOptions + sessionOptions
}
