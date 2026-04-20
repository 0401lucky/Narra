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
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.ui.screen.roleplay.RoleplayReadingMode
import com.example.myapplication.ui.screen.roleplay.RoleplayDiaryDetailScreen
import com.example.myapplication.ui.screen.roleplay.RoleplayDiaryScreen
import com.example.myapplication.ui.screen.roleplay.RoleplayScenarioEditScreen
import com.example.myapplication.ui.screen.roleplay.RoleplayScenarioListScreen
import com.example.myapplication.ui.screen.roleplay.RoleplayScreen
import com.example.myapplication.ui.screen.roleplay.RoleplayScreenCallbacks
import com.example.myapplication.ui.screen.roleplay.RoleplayMessageCallbacks
import com.example.myapplication.ui.screen.roleplay.RoleplaySuggestionCallbacks
import com.example.myapplication.ui.screen.roleplay.RoleplayNavigationCallbacks
import com.example.myapplication.ui.screen.roleplay.RoleplaySessionCallbacks
import com.example.myapplication.ui.screen.roleplay.RoleplayUiCallbacks
import com.example.myapplication.ui.screen.roleplay.RoleplaySettingsScreen
import com.example.myapplication.ui.screen.roleplay.RoleplayVideoCallScreen
import com.example.myapplication.viewmodel.SettingsViewModel
import com.example.myapplication.viewmodel.updateRoleplayHighContrast
import com.example.myapplication.viewmodel.updateRoleplayImmersiveMode
import com.example.myapplication.viewmodel.updateRoleplayLineHeightScale
import com.example.myapplication.viewmodel.updateRoleplayLongformTargetChars
import com.example.myapplication.viewmodel.updateShowOnlineRoleplayNarration
import com.example.myapplication.viewmodel.updateShowRoleplayAiHelper
import com.example.myapplication.viewmodel.updateShowRoleplayPresenceStrip
import com.example.myapplication.viewmodel.updateShowRoleplayStatusStrip

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
                // 仅在场景尚未加载或会话缺失时才进入，避免从设置页返回时重置消息列表
                if (roleplayState.currentScenario?.id != scenarioId || roleplayState.currentSession == null) {
                    roleplayViewModel.enterScenario(scenarioId)
                }
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
                replyToMessageId = roleplayState.replyToMessageId,
                replyToPreview = roleplayState.replyToPreview,
                replyToSpeakerName = roleplayState.replyToSpeakerName,
                isSending = roleplayState.isSending,
                isGeneratingSuggestions = roleplayState.isGeneratingSuggestions,
                isScenarioLoading = roleplayState.isScenarioLoading,
                showAssistantMismatchDialog = roleplayState.showAssistantMismatchDialog,
                previousAssistantName = roleplayState.previousAssistantName,
                currentAssistantName = roleplayState.currentAssistantName,
                noticeMessage = roleplayState.noticeMessage,
                errorMessage = roleplayState.errorMessage,
                suggestionErrorMessage = roleplayState.suggestionErrorMessage,
                pendingMemoryProposal = roleplayState.pendingMemoryProposal,
                callbacks = RoleplayScreenCallbacks(
                    message = RoleplayMessageCallbacks(
                        onInputChange = roleplayViewModel::updateInput,
                        onSend = roleplayViewModel::sendMessage,
                        onCancelSending = roleplayViewModel::cancelSending,
                        onRetryTurn = roleplayViewModel::retryTurn,
                        onEditUserMessage = roleplayViewModel::editUserMessage,
                        onQuoteMessage = roleplayViewModel::quoteMessage,
                        onClearQuotedMessage = roleplayViewModel::clearQuotedMessage,
                        onRecallMessage = roleplayViewModel::recallMessage,
                        onCaptureOnlineChat = roleplayViewModel::captureOnlineChat,
                        onSendSpecialPlay = roleplayViewModel::sendSpecialPlay,
                        onSendVoiceMessage = roleplayViewModel::sendVoiceMessage,
                        onConfirmTransferReceipt = roleplayViewModel::confirmTransferReceipt,
                    ),
                    suggestion = RoleplaySuggestionCallbacks(
                        onGenerateSuggestions = roleplayViewModel::generateDraftInput,
                        onApplySuggestion = roleplayViewModel::applySuggestion,
                        onClearSuggestions = roleplayViewModel::clearSuggestions,
                    ),
                    navigation = RoleplayNavigationCallbacks(
                        onOpenDiary = {
                            navController.navigate(AppRoutes.roleplayDiary(scenarioId)) {
                                launchSingleTop = true
                            }
                        },
                        onOpenPhoneCheck = { ownerType ->
                            val conversationId = roleplayState.currentSession?.conversationId.orEmpty()
                            if (conversationId.isNotBlank()) {
                                navController.navigate(
                                    AppRoutes.phoneCheck(
                                        conversationId = conversationId,
                                        scenarioId = scenarioId,
                                        ownerType = ownerType,
                                    ),
                                ) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onOpenMoments = {
                            val conversationId = roleplayState.currentSession?.conversationId.orEmpty()
                            if (conversationId.isNotBlank()) {
                                navController.navigate(
                                    AppRoutes.moments(
                                        conversationId = conversationId,
                                        scenarioId = scenarioId,
                                        ownerType = PhoneSnapshotOwnerType.CHARACTER,
                                    ),
                                ) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onOpenVideoCall = {
                            navController.navigate(AppRoutes.roleplayVideoCall(scenarioId)) {
                                launchSingleTop = true
                            }
                        },
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
                    ),
                    session = RoleplaySessionCallbacks(
                        onRestartSession = roleplayViewModel::restartCurrentSession,
                        onDismissAssistantMismatch = roleplayViewModel::dismissAssistantMismatchDialog,
                        onApprovePendingMemoryProposal = roleplayViewModel::approvePendingMemoryProposal,
                        onRejectPendingMemoryProposal = roleplayViewModel::rejectPendingMemoryProposal,
                    ),
                    ui = RoleplayUiCallbacks(
                        onClearNoticeMessage = roleplayViewModel::clearNoticeMessage,
                        onClearErrorMessage = roleplayViewModel::clearErrorMessage,
                    ),
                ),
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
            LaunchedEffect(scenarioId) {
                // Settings 页依托已有的共享 ViewModel，不需要重新 enter 场景。
                // 如果因为深链或重建丢失了场景，仅在当前 session 也为 null 时才补 enter。
                if (roleplayState.currentScenario?.id != scenarioId && roleplayState.currentSession == null) {
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
                    showOnlineRoleplayNarration = settingsUiState.showOnlineRoleplayNarration,
                    roleplayImmersiveMode = settingsUiState.roleplayImmersiveMode,
                    roleplayHighContrast = settingsUiState.roleplayHighContrast,
                    roleplayLineHeightScale = settingsUiState.roleplayLineHeightScale,
                ),
                contextStatus = roleplayState.contextStatus,
                currentModel = roleplayState.currentModel,
                currentProviderId = roleplayState.currentProviderId,
                providerOptions = providerOptions,
                isLoadingModels = settingsUiState.isLoadingModels,
                loadingProviderId = settingsUiState.loadingProviderId,
                isSavingModel = settingsUiState.isSaving,
                latestPromptDebugDump = roleplayState.latestPromptDebugDump,
                contextGovernance = roleplayState.contextGovernance,
                recentMemoryProposalHistory = roleplayState.recentMemoryProposalHistory,
                onOpenReadingMode = {
                    settingsViewModel.saveSettings {
                        navController.navigate(AppRoutes.roleplayReading(scenarioId)) {
                            launchSingleTop = true
                        }
                    }
                },
                onUpdateShowRoleplayPresenceStrip = settingsViewModel::updateShowRoleplayPresenceStrip,
                onUpdateShowRoleplayStatusStrip = settingsViewModel::updateShowRoleplayStatusStrip,
                onUpdateShowOnlineRoleplayNarration = settingsViewModel::updateShowOnlineRoleplayNarration,
                onUpdateShowRoleplayAiHelper = settingsViewModel::updateShowRoleplayAiHelper,
                onUpdateScenarioNarrationEnabled = roleplayViewModel::updateCurrentScenarioNarrationEnabled,
                onUpdateScenarioDeepImmersionEnabled = roleplayViewModel::updateCurrentScenarioDeepImmersionEnabled,
                onUpdateScenarioTimeAwarenessEnabled = roleplayViewModel::updateCurrentScenarioTimeAwarenessEnabled,
                onUpdateScenarioNetMemeEnabled = roleplayViewModel::updateCurrentScenarioNetMemeEnabled,
                onUpdateRoleplayLongformTargetChars = settingsViewModel::updateRoleplayLongformTargetChars,
                onUpdateScenarioInteractionMode = roleplayViewModel::updateCurrentScenarioInteractionMode,
                onUpdateRoleplayImmersiveMode = settingsViewModel::updateRoleplayImmersiveMode,
                onUpdateRoleplayHighContrast = settingsViewModel::updateRoleplayHighContrast,
                onUpdateRoleplayLineHeightScale = settingsViewModel::updateRoleplayLineHeightScale,
                onSelectProvider = settingsViewModel::saveSelectedProvider,
                onSelectModel = settingsViewModel::saveSelectedModelForProvider,
                onOpenProviderDetail = { providerId ->
                    navController.navigate(AppRoutes.settingsProviderDetail(providerId)) {
                        launchSingleTop = true
                    }
                },
                onRefreshConversationSummary = roleplayViewModel::refreshCurrentConversationSummary,
                onRestartSession = { onSuccess -> roleplayViewModel.restartCurrentSession(onSuccess) },
                onResetSession = { onSuccess -> roleplayViewModel.resetCurrentSession(onSuccess) },
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
            LaunchedEffect(scenarioId) {
                if (roleplayState.currentScenario?.id != scenarioId && roleplayState.currentSession == null) {
                    roleplayViewModel.enterScenario(scenarioId)
                }
            }
            RoleplayReadingMode(
                messages = roleplayState.messages,
                scenarioTitle = roleplayState.currentScenario?.title.orEmpty(),
                backgroundUri = roleplayState.currentScenario?.backgroundUri.orEmpty(),
                lineHeightScale = roleplayState.settings.roleplayLineHeightScale.scaleFactor,
                highContrast = roleplayState.settings.roleplayHighContrast,
                onDismiss = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.ROLEPLAY_DIARY) { backStackEntry ->
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
            LaunchedEffect(scenarioId) {
                if (roleplayState.currentScenario?.id != scenarioId && roleplayState.currentSession == null) {
                    roleplayViewModel.enterScenario(scenarioId)
                }
            }
            RoleplayDiaryScreen(
                scenario = routeScenario,
                assistant = routeAssistant,
                settings = roleplayState.settings,
                diaryEntries = roleplayState.diaryEntries,
                isGeneratingDiary = roleplayState.isGeneratingDiary,
                noticeMessage = roleplayState.noticeMessage,
                errorMessage = roleplayState.errorMessage,
                onClearNoticeMessage = roleplayViewModel::clearNoticeMessage,
                onClearErrorMessage = roleplayViewModel::clearErrorMessage,
                onGenerateDiary = roleplayViewModel::generateRoleplayDiaries,
                onOpenEntry = { entryId ->
                    navController.navigate(AppRoutes.roleplayDiaryDetail(scenarioId, entryId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.ROLEPLAY_DIARY_DETAIL) { backStackEntry ->
            val roleplayViewModel = rememberRoleplayViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val roleplayState by roleplayViewModel.uiState.collectAsStateWithLifecycle()
            val rawScenarioId = backStackEntry.arguments?.getString("scenarioId").orEmpty()
            val scenarioId = Uri.decode(rawScenarioId)
            val rawEntryId = backStackEntry.arguments?.getString("entryId").orEmpty()
            val entryId = Uri.decode(rawEntryId)
            val routeScenario = roleplayState.currentScenario?.takeIf { it.id == scenarioId }
                ?: roleplayState.scenarios.firstOrNull { it.id == scenarioId }
            LaunchedEffect(scenarioId) {
                if (roleplayState.currentScenario?.id != scenarioId && roleplayState.currentSession == null) {
                    roleplayViewModel.enterScenario(scenarioId)
                }
            }
            RoleplayDiaryDetailScreen(
                scenario = routeScenario,
                settings = roleplayState.settings,
                diaryEntries = roleplayState.diaryEntries,
                entryId = entryId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.ROLEPLAY_VIDEO_CALL) { backStackEntry ->
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
            LaunchedEffect(scenarioId) {
                if (roleplayState.currentScenario?.id != scenarioId && roleplayState.currentSession == null) {
                    roleplayViewModel.enterScenario(scenarioId)
                }
                roleplayViewModel.startVideoCall()
            }
            RoleplayVideoCallScreen(
                scenario = routeScenario,
                assistant = routeAssistant,
                settings = roleplayState.settings,
                messages = roleplayState.messages,
                input = roleplayState.input,
                inputFocusToken = roleplayState.inputFocusToken,
                isSending = roleplayState.isSending,
                activeVideoCallStartedAt = roleplayState.activeVideoCallStartedAt,
                isVideoCallActive = roleplayState.isVideoCallActive,
                noticeMessage = roleplayState.noticeMessage,
                errorMessage = roleplayState.errorMessage,
                onClearNoticeMessage = roleplayViewModel::clearNoticeMessage,
                onClearErrorMessage = roleplayViewModel::clearErrorMessage,
                onInputChange = roleplayViewModel::updateInput,
                onSend = roleplayViewModel::sendMessage,
                onCancelSending = roleplayViewModel::cancelSending,
                onHangup = {
                    roleplayViewModel.hangupVideoCall()
                    navController.popBackStack()
                },
            )
        }
    }
}
