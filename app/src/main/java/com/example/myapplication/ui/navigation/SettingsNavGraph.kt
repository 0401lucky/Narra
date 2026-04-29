package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.myapplication.di.AppGraph
import com.example.myapplication.ui.screen.settings.PresetEditScreen
import com.example.myapplication.ui.screen.settings.PresetListScreen
import com.example.myapplication.ui.screen.settings.SettingsScreen
import com.example.myapplication.ui.screen.settings.UserPersonaMasksScreen
import com.example.myapplication.ui.screen.settings.copyPresetForUser
import com.example.myapplication.viewmodel.AppUpdateViewModel
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
                            navController.navigate(AppRoutes.CHAT) {
                                popUpTo(AppRoutes.CHAT) { inclusive = true }
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
                onOpenChat = {
                    navController.navigate(AppRoutes.CHAT) {
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
                onOpenSearchToolSettings = {
                    navController.navigate(AppRoutes.SETTINGS_SEARCH_TOOLS) {
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
                onOpenScreenTranslationSettings = {
                    navController.navigate(AppRoutes.SETTINGS_SCREEN_TRANSLATION) {
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

        // ── 提供商、模型、搜索工具、翻译、更新 ──
        registerSettingsProviderRoutes(
            navController = navController,
            settingsViewModel = settingsViewModel,
            appUpdateViewModel = appUpdateViewModel,
        )

        // ── 助手配置 ──
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
