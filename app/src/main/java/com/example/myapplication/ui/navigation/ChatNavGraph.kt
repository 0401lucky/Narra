package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.myapplication.data.repository.ImageFileStorage
import com.example.myapplication.di.AppGraph
import com.example.myapplication.ui.screen.chat.ChatConversationCallbacks
import com.example.myapplication.ui.screen.chat.ChatMessageCallbacks
import com.example.myapplication.ui.screen.chat.ChatModelCallbacks
import com.example.myapplication.ui.screen.chat.ChatNavigationCallbacks
import com.example.myapplication.ui.screen.chat.ChatProfileCallbacks
import com.example.myapplication.ui.screen.chat.ChatScreenCallbacks
import com.example.myapplication.ui.screen.chat.ChatSearchCallbacks
import com.example.myapplication.ui.screen.chat.ChatScreen
import com.example.myapplication.ui.screen.chat.ChatTranslationCallbacks
import com.example.myapplication.ui.screen.chat.ChatUiCallbacks
import com.example.myapplication.ui.screen.translate.TranslationScreen
import com.example.myapplication.viewmodel.ChatViewModel
import com.example.myapplication.viewmodel.SettingsViewModel
import com.example.myapplication.viewmodel.TranslationViewModel
import com.example.myapplication.viewmodel.selectSearchSource
import com.example.myapplication.viewmodel.updateSearchResultCount

internal fun NavGraphBuilder.registerChatNavGraph(
    appGraph: AppGraph,
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
) {
    composable(AppRoutes.CHAT) {
        val context = LocalContext.current
        val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        val chatViewModel: ChatViewModel = viewModel(
            factory = ChatViewModel.factory(
                settingsRepository = appGraph.aiSettingsRepository,
                aiGateway = appGraph.aiGateway,
                aiPromptExtrasService = appGraph.aiPromptExtrasService,
                aiTranslationService = appGraph.aiTranslationService,
                conversationRepository = appGraph.conversationRepository,
                memoryRepository = appGraph.memoryRepository,
                conversationSummaryRepository = appGraph.conversationSummaryRepository,
                promptContextAssembler = appGraph.promptContextAssembler,
                contextLogStore = appGraph.contextLogStore,
                imageSaver = { b64Data ->
                    ImageFileStorage.saveBase64Image(context, b64Data)
                },
            ),
        )
        val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
        ChatScreen(
            uiState = chatState,
            isLoadingModels = settingsUiState.isLoadingModels,
            loadingProviderId = settingsUiState.loadingProviderId,
            isSavingModel = settingsUiState.isSaving,
            callbacks = ChatScreenCallbacks(
                message = ChatMessageCallbacks(
                    onInputChange = chatViewModel::updateInput,
                    onSend = chatViewModel::sendMessage,
                    onRetryMessage = chatViewModel::retryMessage,
                    onEditUserMessage = chatViewModel::editUserMessage,
                    onToggleMemoryMessage = chatViewModel::toggleMessageMemory,
                    onCancelSending = chatViewModel::cancelSending,
                    onAddPendingParts = chatViewModel::addPendingParts,
                    onRemovePendingPart = chatViewModel::removePendingPart,
                    onSendSpecialPlay = chatViewModel::sendSpecialPlay,
                    onConfirmTransferReceipt = chatViewModel::confirmTransferReceipt,
                ),
                conversation = ChatConversationCallbacks(
                    onCreateConversation = chatViewModel::createConversation,
                    onSelectConversation = chatViewModel::selectConversation,
                    onClearConversation = chatViewModel::clearConversation,
                    onDeleteConversation = chatViewModel::deleteConversation,
                    onClearCurrentConversation = chatViewModel::clearCurrentConversation,
                    onRefreshConversationSummary = chatViewModel::refreshConversationSummary,
                ),
                search = ChatSearchCallbacks(
                    onToggleSearch = chatViewModel::toggleConversationSearch,
                    onSelectSearchSource = settingsViewModel::selectSearchSource,
                    onUpdateSearchResultCount = settingsViewModel::updateSearchResultCount,
                ),
                translation = ChatTranslationCallbacks(
                    onTranslateDraft = chatViewModel::translateDraftInput,
                    onTranslateMessage = chatViewModel::translateMessage,
                    onDismissTranslationSheet = chatViewModel::dismissTranslationSheet,
                    onApplyTranslationToInput = chatViewModel::applyTranslationToInput,
                    onSendTranslationAsMessage = chatViewModel::sendTranslationAsMessage,
                ),
                model = ChatModelCallbacks(
                    onSelectProvider = settingsViewModel::saveSelectedProvider,
                    onSelectModel = settingsViewModel::saveSelectedModelForProvider,
                    onUpdateThinkingBudget = settingsViewModel::saveThinkingBudgetForProvider,
                ),
                profile = ChatProfileCallbacks(
                    onSaveUserProfile = settingsViewModel::saveUserProfile,
                    onSelectAssistant = { assistantId ->
                        settingsViewModel.selectAssistant(assistantId)
                        chatViewModel.selectAssistant(assistantId)
                    },
                    onOpenAssistantDetail = { assistantId ->
                        navController.navigate(AppRoutes.settingsAssistantDetail(assistantId)) {
                            launchSingleTop = true
                        }
                    },
                ),
                navigation = ChatNavigationCallbacks(
                    onOpenSettings = {
                        navController.navigate(AppRoutes.SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                    onOpenHome = {
                        navController.navigate(AppRoutes.HOME) {
                            launchSingleTop = true
                        }
                    },
                    onOpenTranslator = {
                        navController.navigate(AppRoutes.TRANSLATOR) {
                            launchSingleTop = true
                        }
                    },
                    onOpenRoleplay = {
                        navController.navigate(AppRoutes.ROLEPLAY) {
                            launchSingleTop = true
                        }
                    },
                    onOpenPhoneCheck = { conversationId ->
                        navController.navigate(AppRoutes.phoneCheck(conversationId)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenProviderDetail = { providerId ->
                        navController.navigate(AppRoutes.settingsProviderDetail(providerId)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenContextLog = {
                        navController.navigate(AppRoutes.SETTINGS_CONTEXT_LOG) {
                            launchSingleTop = true
                        }
                    },
                ),
                ui = ChatUiCallbacks(
                    onClearErrorMessage = chatViewModel::clearErrorMessage,
                    onClearNoticeMessage = chatViewModel::clearNoticeMessage,
                ),
            ),
        )
    }

    composable(AppRoutes.TRANSLATOR) {
        val translationViewModel: TranslationViewModel = viewModel(
            factory = TranslationViewModel.factory(
                settingsRepository = appGraph.aiSettingsRepository,
                settingsEditor = appGraph.aiSettingsEditor,
                aiTranslationService = appGraph.aiTranslationService,
            ),
        )
        val translationState by translationViewModel.uiState.collectAsStateWithLifecycle()
        TranslationScreen(
            uiState = translationState,
            supportedLanguages = TranslationViewModel.SupportedLanguages,
            onInputTextChange = translationViewModel::updateInputText,
            onTargetLanguageChange = translationViewModel::updateTargetLanguage,
            onTranslate = translationViewModel::translate,
            onCancelTranslation = translationViewModel::cancelTranslation,
            onPasteText = translationViewModel::updateInputText,
            onSelectHistoryItem = translationViewModel::useHistoryItem,
            onClearHistory = translationViewModel::clearHistory,
            onUpdateTranslationModel = translationViewModel::updateTranslationModel,
            onClearErrorMessage = translationViewModel::clearErrorMessage,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
