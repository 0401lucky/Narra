package com.example.myapplication.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.system.translation.ScreenTranslatorService
import com.example.myapplication.ui.screen.settings.AppUpdateScreen
import com.example.myapplication.ui.screen.settings.ProviderDetailScreen
import com.example.myapplication.ui.screen.settings.ProviderSettingsScreen
import com.example.myapplication.ui.screen.settings.ScreenTranslationSettingsScreen
import com.example.myapplication.ui.screen.settings.SearchToolSettingsScreen
import com.example.myapplication.ui.screen.settings.SettingsConnectionScreen
import com.example.myapplication.ui.screen.settings.SettingsModelScreen
import com.example.myapplication.viewmodel.AppUpdateViewModel
import com.example.myapplication.viewmodel.SettingsViewModel
import com.example.myapplication.viewmodel.selectSearchSource
import com.example.myapplication.viewmodel.updateProviderChatSuggestionModel
import com.example.myapplication.viewmodel.updateProviderChatSuggestionModelMode
import com.example.myapplication.viewmodel.updateProviderGiftImageModel
import com.example.myapplication.viewmodel.updateProviderGiftImageModelMode
import com.example.myapplication.viewmodel.updateProviderMemoryModel
import com.example.myapplication.viewmodel.updateProviderMemoryModelMode
import com.example.myapplication.viewmodel.updateProviderPhoneSnapshotModel
import com.example.myapplication.viewmodel.updateProviderPhoneSnapshotModelMode
import com.example.myapplication.viewmodel.updateProviderSearchModel
import com.example.myapplication.viewmodel.updateProviderSearchModelMode
import com.example.myapplication.viewmodel.updateProviderTitleSummaryModel
import com.example.myapplication.viewmodel.updateProviderTitleSummaryModelMode
import com.example.myapplication.viewmodel.updateProviderTranslationModel
import com.example.myapplication.viewmodel.updateProviderTranslationModelMode
import com.example.myapplication.viewmodel.updateScreenTranslationOverlayEnabled
import com.example.myapplication.viewmodel.updateScreenTranslationSelectedTextEnabled
import com.example.myapplication.viewmodel.updateScreenTranslationServiceEnabled
import com.example.myapplication.viewmodel.updateScreenTranslationShowSourceText
import com.example.myapplication.viewmodel.updateScreenTranslationTargetLanguage
import com.example.myapplication.viewmodel.updateScreenTranslationVendorGuideDismissed
import com.example.myapplication.viewmodel.updateSearchResultCount
import com.example.myapplication.viewmodel.updateSearchSourceApiKey
import com.example.myapplication.viewmodel.updateSearchSourceEnabled
import com.example.myapplication.viewmodel.updateSearchSourceEngineId
import com.example.myapplication.viewmodel.updateSearchSourceProviderId

// 提供商、模型、连接、搜索工具、屏幕翻译、应用更新

internal fun NavGraphBuilder.registerSettingsProviderRoutes(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    appUpdateViewModel: AppUpdateViewModel,
) {
    composable(AppRoutes.SETTINGS_UPDATES) {
        val appUpdateState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
        AppUpdateScreen(
            uiState = appUpdateState,
            onNavigateBack = { navController.popBackStack() },
            onCheckForUpdates = appUpdateViewModel::checkForUpdates,
            onStartDownload = appUpdateViewModel::startUpdateDownload,
            onInstallUpdate = appUpdateViewModel::installDownloadedUpdate,
            onConsumeMessage = appUpdateViewModel::consumeMessage,
        )
    }

    composable(AppRoutes.SETTINGS_SCREEN_TRANSLATION) {
        val context = LocalContext.current
        val screenTranslationState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        ScreenTranslationSettingsScreen(
            uiState = screenTranslationState,
            onNavigateBack = {
                settingsViewModel.saveSettings {}
                navController.popBackStack()
            },
            onUpdateServiceEnabled = settingsViewModel::updateScreenTranslationServiceEnabled,
            onUpdateOverlayEnabled = settingsViewModel::updateScreenTranslationOverlayEnabled,
            onUpdateSelectedTextEnabled = settingsViewModel::updateScreenTranslationSelectedTextEnabled,
            onUpdateShowSourceText = settingsViewModel::updateScreenTranslationShowSourceText,
            onUpdateTargetLanguage = settingsViewModel::updateScreenTranslationTargetLanguage,
            onUpdateVendorGuideDismissed = settingsViewModel::updateScreenTranslationVendorGuideDismissed,
            onSaveChanges = { settingsViewModel.saveSettings {} },
            onSaveAndStartService = {
                settingsViewModel.updateScreenTranslationServiceEnabled(true)
                settingsViewModel.saveSettings {
                    ScreenTranslatorService.startService(context)
                }
            },
            onSaveAndStopService = {
                settingsViewModel.updateScreenTranslationServiceEnabled(false)
                settingsViewModel.saveSettings {
                    ScreenTranslatorService.stopService(context)
                }
            },
        )
    }

    composable(AppRoutes.SETTINGS_PROVIDERS) {
        val providerSettingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        ProviderSettingsScreen(
            uiState = providerSettingsState,
            onEnsureProviderDrafts = settingsViewModel::ensureProviderDrafts,
            onShowAddDialog = settingsViewModel::showAddProviderDialog,
            onDismissAddDialog = settingsViewModel::dismissAddProviderDialog,
            onAddProviderFromTemplate = settingsViewModel::addProviderFromTemplate,
            onOpenProviderDetail = { providerId ->
                navController.navigate(AppRoutes.settingsProviderDetail(providerId)) {
                    launchSingleTop = true
                }
            },
            onCheckProviderHealth = settingsViewModel::checkProviderHealth,
            onConsumeMessage = settingsViewModel::consumeMessage,
            onNavigateBack = {
                settingsViewModel.saveSettings {}
                navController.popBackStack()
            },
        )
    }

    composable(AppRoutes.SETTINGS_PROVIDER_DETAIL) { backStackEntry ->
        val providerSettingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        val providerId = backStackEntry.arguments?.getString("providerId").orEmpty()
        ProviderDetailScreen(
            providerId = providerId,
            uiState = providerSettingsState,
            onUpdateProviderName = settingsViewModel::updateProviderName,
            onUpdateProviderBaseUrl = settingsViewModel::updateProviderBaseUrl,
            onUpdateProviderApiKey = settingsViewModel::updateProviderApiKey,
            onUpdateProviderApiProtocol = settingsViewModel::updateProviderApiProtocol,
            onUpdateProviderOpenAiTextApiMode = settingsViewModel::updateProviderOpenAiTextApiMode,
            onUpdateProviderChatCompletionsPath = settingsViewModel::updateProviderChatCompletionsPath,
            onUpdateProviderSelectedModel = settingsViewModel::updateProviderSelectedModel,
            onUpdateProviderModelAbilities = settingsViewModel::updateProviderModelAbilities,
            onLoadModels = settingsViewModel::loadModels,
            onDeleteProvider = settingsViewModel::deleteProvider,
            onToggleProviderEnabled = settingsViewModel::toggleProviderEnabled,
            onSave = {
                settingsViewModel.saveSettings {}
            },
            onConsumeMessage = settingsViewModel::consumeMessage,
            onConfirmFetchedModels = settingsViewModel::confirmFetchedModels,
            onDismissFetchedModels = settingsViewModel::dismissFetchedModels,
            onRemoveModel = settingsViewModel::removeModelFromProvider,
            onNavigateBack = {
                settingsViewModel.saveSettings {}
                navController.popBackStack()
            },
        )
    }

    composable(AppRoutes.SETTINGS_CONNECTION) {
        val providerSettingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        SettingsConnectionScreen(
            uiState = providerSettingsState,
            onBaseUrlChange = settingsViewModel::updateBaseUrl,
            onApiKeyChange = settingsViewModel::updateApiKey,
            onConsumeMessage = settingsViewModel::consumeMessage,
            onNavigateBack = {
                navController.popBackStack()
            },
        )
    }

    composable(AppRoutes.SETTINGS_SEARCH_TOOLS) {
        val providerSettingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        SearchToolSettingsScreen(
            uiState = providerSettingsState,
            onSelectSource = settingsViewModel::selectSearchSource,
            onUpdateResultCount = settingsViewModel::updateSearchResultCount,
            onUpdateSourceEnabled = settingsViewModel::updateSearchSourceEnabled,
            onUpdateSourceApiKey = settingsViewModel::updateSearchSourceApiKey,
            onUpdateSourceEngineId = settingsViewModel::updateSearchSourceEngineId,
            onUpdateSourceProviderId = settingsViewModel::updateSearchSourceProviderId,
            onNavigateBack = {
                settingsViewModel.saveSettings {}
                navController.popBackStack()
            },
        )
    }

    composable(AppRoutes.SETTINGS_MODEL) {
        val providerSettingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
        SettingsModelScreen(
            uiState = providerSettingsState,
            onLoadModels = settingsViewModel::loadModels,
            onSelectProvider = settingsViewModel::selectProvider,
            onSelectedModelChange = settingsViewModel::updateProviderSelectedModel,
            onUpdateFunctionModel = { function, providerId, model ->
                when (function) {
                    ProviderFunction.TITLE_SUMMARY -> settingsViewModel.updateProviderTitleSummaryModel(providerId, model)
                    ProviderFunction.CHAT_SUGGESTION -> settingsViewModel.updateProviderChatSuggestionModel(providerId, model)
                    ProviderFunction.MEMORY -> settingsViewModel.updateProviderMemoryModel(providerId, model)
                    ProviderFunction.TRANSLATION -> settingsViewModel.updateProviderTranslationModel(providerId, model)
                    ProviderFunction.PHONE_SNAPSHOT -> settingsViewModel.updateProviderPhoneSnapshotModel(providerId, model)
                    ProviderFunction.SEARCH -> settingsViewModel.updateProviderSearchModel(providerId, model)
                    ProviderFunction.GIFT_IMAGE -> settingsViewModel.updateProviderGiftImageModel(providerId, model)
                    else -> {}
                }
            },
            onUpdateFunctionModelMode = { function, providerId, mode ->
                when (function) {
                    ProviderFunction.TITLE_SUMMARY -> settingsViewModel.updateProviderTitleSummaryModelMode(providerId, mode)
                    ProviderFunction.CHAT_SUGGESTION -> settingsViewModel.updateProviderChatSuggestionModelMode(providerId, mode)
                    ProviderFunction.MEMORY -> settingsViewModel.updateProviderMemoryModelMode(providerId, mode)
                    ProviderFunction.TRANSLATION -> settingsViewModel.updateProviderTranslationModelMode(providerId, mode)
                    ProviderFunction.PHONE_SNAPSHOT -> settingsViewModel.updateProviderPhoneSnapshotModelMode(providerId, mode)
                    ProviderFunction.SEARCH -> settingsViewModel.updateProviderSearchModelMode(providerId, mode)
                    ProviderFunction.GIFT_IMAGE -> settingsViewModel.updateProviderGiftImageModelMode(providerId, mode)
                    else -> {}
                }
            },
            onConsumeMessage = settingsViewModel::consumeMessage,
            onNavigateBack = {
                settingsViewModel.saveSettings {}
                navController.popBackStack()
            },
        )
    }
}
