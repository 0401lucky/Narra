package com.example.myapplication.ui.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.myapplication.di.AppGraph
import com.example.myapplication.ui.screen.settings.SettingsScreen
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
            SettingsScreen(
                uiState = settingsUiState,
                onSave = {
                    settingsViewModel.saveSettings {
                        navController.navigate(AppRoutes.CHAT) {
                            popUpTo(AppRoutes.CHAT) { inclusive = true }
                            launchSingleTop = true
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
                onOpenConnectionSettings = {
                    navController.navigate(AppRoutes.SETTINGS_CONNECTION) {
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

        // ── 提供商、模型、连接、搜索工具、翻译、更新 ──
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
