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
import com.example.myapplication.ui.screen.chat.ChatScreen
import com.example.myapplication.ui.screen.translate.TranslationScreen
import com.example.myapplication.viewmodel.ChatViewModel
import com.example.myapplication.viewmodel.SettingsViewModel
import com.example.myapplication.viewmodel.TranslationViewModel

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
            onInputChange = chatViewModel::updateInput,
            onSend = chatViewModel::sendMessage,
            onCreateConversation = chatViewModel::createConversation,
            onSelectConversation = chatViewModel::selectConversation,
            onClearConversation = chatViewModel::clearConversation,
            onDeleteConversation = chatViewModel::deleteConversation,
            onDeleteCurrentConversation = chatViewModel::deleteCurrentConversation,
            onClearCurrentConversation = chatViewModel::clearCurrentConversation,
            onRetryMessage = chatViewModel::retryMessage,
            onEditUserMessage = chatViewModel::editUserMessage,
            onToggleMemoryMessage = chatViewModel::toggleMessageMemory,
            onToggleSearch = chatViewModel::toggleConversationSearch,
            onSelectSearchSource = settingsViewModel::selectSearchSource,
            onUpdateSearchResultCount = settingsViewModel::updateSearchResultCount,
            onTranslateDraft = chatViewModel::translateDraftInput,
            onTranslateMessage = chatViewModel::translateMessage,
            onDismissTranslationSheet = chatViewModel::dismissTranslationSheet,
            onApplyTranslationToInput = chatViewModel::applyTranslationToInput,
            onSendTranslationAsMessage = chatViewModel::sendTranslationAsMessage,
            onSelectProvider = settingsViewModel::saveSelectedProvider,
            onSelectModel = settingsViewModel::saveSelectedModelForProvider,
            onUpdateThinkingBudget = settingsViewModel::saveThinkingBudgetForProvider,
            onSaveUserProfile = settingsViewModel::saveUserProfile,
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
            onClearErrorMessage = chatViewModel::clearErrorMessage,
            onClearNoticeMessage = chatViewModel::clearNoticeMessage,
            onRefreshConversationSummary = chatViewModel::refreshConversationSummary,
            onCancelSending = chatViewModel::cancelSending,
            onAddPendingParts = chatViewModel::addPendingParts,
            onRemovePendingPart = chatViewModel::removePendingPart,
            onSendSpecialPlay = chatViewModel::sendSpecialPlay,
            onConfirmTransferReceipt = chatViewModel::confirmTransferReceipt,
            onSelectAssistant = { assistantId ->
                settingsViewModel.selectAssistant(assistantId)
                chatViewModel.selectAssistant(assistantId)
            },
            onOpenAssistantDetail = { assistantId ->
                navController.navigate(AppRoutes.settingsAssistantDetail(assistantId)) {
                    launchSingleTop = true
                }
            },
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
