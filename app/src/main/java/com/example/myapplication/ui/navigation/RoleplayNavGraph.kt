package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.myapplication.di.AppGraph
import com.example.myapplication.ui.screen.roleplay.RoleplayReadingMode
import com.example.myapplication.ui.screen.roleplay.RoleplayScenarioEditScreen
import com.example.myapplication.ui.screen.roleplay.RoleplayScenarioListScreen
import com.example.myapplication.ui.screen.roleplay.RoleplayScreen
import com.example.myapplication.ui.screen.roleplay.RoleplaySettingsScreen
import com.example.myapplication.viewmodel.SettingsViewModel

internal fun NavGraphBuilder.registerRoleplayGraph(
    appGraph: AppGraph,
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
) {
    navigation(
        startDestination = AppRoutes.ROLEPLAY,
        route = AppRoutes.ROLEPLAY_GRAPH,
    ) {
        composable(AppRoutes.ROLEPLAY) { backStackEntry ->
            val roleplayViewModel = rememberRoleplayViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val roleplayState by roleplayViewModel.uiState.collectAsStateWithLifecycle()
            RoleplayScenarioListScreen(
                scenarios = roleplayState.scenarios,
                assistants = roleplayState.settings.resolvedAssistants(),
                continuingScenarioIds = roleplayState.scenarioSessionIds,
                onAddScenario = {
                    navController.navigate(AppRoutes.roleplayEdit("new")) {
                        launchSingleTop = true
                    }
                },
                onEditScenario = { scenarioId ->
                    navController.navigate(AppRoutes.roleplayEdit(scenarioId)) {
                        launchSingleTop = true
                    }
                },
                onPlayScenario = { scenarioId ->
                    navController.navigate(AppRoutes.roleplayPlay(scenarioId)) {
                        launchSingleTop = true
                    }
                },
                noticeMessage = roleplayState.noticeMessage,
                errorMessage = roleplayState.errorMessage,
                onClearNoticeMessage = roleplayViewModel::clearNoticeMessage,
                onClearErrorMessage = roleplayViewModel::clearErrorMessage,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.ROLEPLAY_EDIT) { backStackEntry ->
            val roleplayViewModel = rememberRoleplayViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val roleplayState by roleplayViewModel.uiState.collectAsStateWithLifecycle()
            val rawScenarioId = backStackEntry.arguments?.getString("scenarioId").orEmpty()
            val scenarioId = Uri.decode(rawScenarioId)
            val scenario = roleplayState.scenarios.firstOrNull { it.id == scenarioId }.takeIf { scenarioId != "new" }
            RoleplayScenarioEditScreen(
                scenario = scenario,
                settings = roleplayState.settings,
                assistants = roleplayState.settings.resolvedAssistants(),
                onSave = { updatedScenario ->
                    roleplayViewModel.upsertScenario(updatedScenario) {
                        navController.popBackStack()
                    }
                },
                onDelete = if (scenario != null) {
                    { targetId ->
                        roleplayViewModel.deleteScenario(targetId) {
                            navController.popBackStack()
                        }
                    }
                } else {
                    null
                },
                noticeMessage = roleplayState.noticeMessage,
                errorMessage = roleplayState.errorMessage,
                onClearNoticeMessage = roleplayViewModel::clearNoticeMessage,
                onClearErrorMessage = roleplayViewModel::clearErrorMessage,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.ROLEPLAY_PLAY) { backStackEntry ->
            val roleplayViewModel = rememberRoleplayViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val roleplayState by roleplayViewModel.uiState.collectAsStateWithLifecycle()
            val rawScenarioId = backStackEntry.arguments?.getString("scenarioId").orEmpty()
            val scenarioId = Uri.decode(rawScenarioId)
            val routeScenario = roleplayState.currentScenario?.takeIf { it.id == scenarioId }
                ?: roleplayState.scenarios.firstOrNull { it.id == scenarioId }
            val routeAssistant = routeScenario?.let { scenario ->
                roleplayState.settings.resolvedAssistants().firstOrNull { it.id == scenario.assistantId }
            } ?: roleplayState.currentAssistant
            val providerOptions = remember(roleplayState.settings) {
                roleplayState.settings.providers.filter { it.enabled }
            }
            LaunchedEffect(scenarioId) {
                roleplayViewModel.enterScenario(scenarioId)
            }
            RoleplayScreen(
                scenario = routeScenario,
                assistant = routeAssistant,
                settings = roleplayState.settings,
                contextStatus = roleplayState.contextStatus,
                messages = roleplayState.messages,
                suggestions = roleplayState.suggestions,
                input = roleplayState.input,
                inputFocusToken = roleplayState.inputFocusToken,
                isSending = roleplayState.isSending,
                isGeneratingSuggestions = roleplayState.isGeneratingSuggestions,
                isScenarioLoading = roleplayState.isScenarioLoading,
                showAssistantMismatchDialog = roleplayState.showAssistantMismatchDialog,
                previousAssistantName = roleplayState.previousAssistantName,
                currentAssistantName = roleplayState.currentAssistantName,
                noticeMessage = roleplayState.noticeMessage,
                errorMessage = roleplayState.errorMessage,
                suggestionErrorMessage = roleplayState.suggestionErrorMessage,
                onClearNoticeMessage = roleplayViewModel::clearNoticeMessage,
                onClearErrorMessage = roleplayViewModel::clearErrorMessage,
                onInputChange = roleplayViewModel::updateInput,
                onGenerateSuggestions = roleplayViewModel::generateDraftInput,
                onApplySuggestion = roleplayViewModel::applySuggestion,
                onClearSuggestions = roleplayViewModel::clearSuggestions,
                onRetryTurn = roleplayViewModel::retryTurn,
                onEditUserMessage = roleplayViewModel::editUserMessage,
                onSendTransferPlay = roleplayViewModel::sendTransferPlay,
                onConfirmTransferReceipt = roleplayViewModel::confirmTransferReceipt,
                onSend = roleplayViewModel::sendMessage,
                onCancelSending = roleplayViewModel::cancelSending,
                onRestartSession = roleplayViewModel::restartCurrentSession,
                onDismissAssistantMismatch = roleplayViewModel::dismissAssistantMismatchDialog,
                onOpenReadingMode = {
                    navController.navigate(AppRoutes.roleplayReading(scenarioId)) {
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(AppRoutes.roleplaySettings(scenarioId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateBack = {
                    roleplayViewModel.leaveScenario()
                    navController.popBackStack()
                },
            )
        }

        composable(AppRoutes.ROLEPLAY_SETTINGS) { backStackEntry ->
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val roleplayViewModel = rememberRoleplayViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val roleplayState by roleplayViewModel.uiState.collectAsStateWithLifecycle()
            val rawScenarioId = backStackEntry.arguments?.getString("scenarioId").orEmpty()
            val scenarioId = Uri.decode(rawScenarioId)
            val routeScenario = roleplayState.currentScenario?.takeIf { it.id == scenarioId }
                ?: roleplayState.scenarios.firstOrNull { it.id == scenarioId }
            val routeAssistant = routeScenario?.let { scenario ->
                roleplayState.settings.resolvedAssistants().firstOrNull { it.id == scenario.assistantId }
            } ?: roleplayState.currentAssistant
            val providerOptions = remember(roleplayState.settings) {
                roleplayState.settings.providers.filter { it.enabled }
            }
            LaunchedEffect(scenarioId, roleplayState.currentScenario?.id) {
                if (roleplayState.currentScenario?.id != scenarioId) {
                    roleplayViewModel.enterScenario(scenarioId)
                }
            }
            RoleplaySettingsScreen(
                scenario = routeScenario,
                assistant = routeAssistant,
                settings = settingsUiState.savedSettings.copy(
                    showRoleplayAiHelper = settingsUiState.showRoleplayAiHelper,
                    roleplayLongformTargetChars = settingsUiState.roleplayLongformTargetChars,
                    showRoleplayPresenceStrip = settingsUiState.showRoleplayPresenceStrip,
                    showRoleplayStatusStrip = settingsUiState.showRoleplayStatusStrip,
                ),
                contextStatus = roleplayState.contextStatus,
                currentModel = roleplayState.currentModel,
                currentProviderId = roleplayState.currentProviderId,
                providerOptions = providerOptions,
                isLoadingModels = settingsUiState.isLoadingModels,
                loadingProviderId = settingsUiState.loadingProviderId,
                isSavingModel = settingsUiState.isSaving,
                latestPromptDebugDump = roleplayState.latestPromptDebugDump,
                onOpenReadingMode = {
                    settingsViewModel.saveSettings {
                        navController.navigate(AppRoutes.roleplayReading(scenarioId)) {
                            launchSingleTop = true
                        }
                    }
                },
                onUpdateShowRoleplayPresenceStrip = settingsViewModel::updateShowRoleplayPresenceStrip,
                onUpdateShowRoleplayStatusStrip = settingsViewModel::updateShowRoleplayStatusStrip,
                onUpdateShowRoleplayAiHelper = settingsViewModel::updateShowRoleplayAiHelper,
                onUpdateRoleplayLongformTargetChars = settingsViewModel::updateRoleplayLongformTargetChars,
                onSelectProvider = settingsViewModel::saveSelectedProvider,
                onSelectModel = settingsViewModel::saveSelectedModelForProvider,
                onOpenProviderDetail = { providerId ->
                    navController.navigate(AppRoutes.settingsProviderDetail(providerId)) {
                        launchSingleTop = true
                    }
                },
                onRestartSession = roleplayViewModel::restartCurrentSession,
                onResetSession = roleplayViewModel::resetCurrentSession,
                onNavigateBack = {
                    settingsViewModel.saveSettings {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable(AppRoutes.ROLEPLAY_READING) { backStackEntry ->
            val roleplayViewModel = rememberRoleplayViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val roleplayState by roleplayViewModel.uiState.collectAsStateWithLifecycle()
            val rawScenarioId = backStackEntry.arguments?.getString("scenarioId").orEmpty()
            val scenarioId = Uri.decode(rawScenarioId)
            LaunchedEffect(scenarioId, roleplayState.currentScenario?.id) {
                if (roleplayState.currentScenario?.id != scenarioId) {
                    roleplayViewModel.enterScenario(scenarioId)
                }
            }
            RoleplayReadingMode(
                messages = roleplayState.messages,
                scenarioTitle = roleplayState.currentScenario?.title.orEmpty(),
                backgroundUri = roleplayState.currentScenario?.backgroundUri.orEmpty(),
                onDismiss = { navController.popBackStack() },
            )
        }
    }
}
