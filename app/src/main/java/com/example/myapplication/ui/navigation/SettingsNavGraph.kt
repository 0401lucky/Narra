package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.myapplication.di.AppGraph
import com.example.myapplication.system.translation.ScreenTranslatorService
import com.example.myapplication.ui.screen.settings.AppUpdateScreen
import com.example.myapplication.ui.screen.settings.AssistantBasicScreen
import com.example.myapplication.ui.screen.settings.AssistantDetailScreen
import com.example.myapplication.ui.screen.settings.AssistantExtensionsScreen
import com.example.myapplication.ui.screen.settings.AssistantListScreen
import com.example.myapplication.ui.screen.settings.AssistantMemoryScreen
import com.example.myapplication.ui.screen.settings.AssistantPromptScreen
import com.example.myapplication.ui.screen.settings.ContextTransferScreen
import com.example.myapplication.ui.screen.settings.ProviderDetailScreen
import com.example.myapplication.ui.screen.settings.ProviderSettingsScreen
import com.example.myapplication.ui.screen.settings.ScreenTranslationSettingsScreen
import com.example.myapplication.ui.screen.settings.SearchToolSettingsScreen
import com.example.myapplication.ui.screen.settings.SettingsConnectionScreen
import com.example.myapplication.ui.screen.settings.SettingsModelScreen
import com.example.myapplication.ui.screen.settings.SettingsScreen
import com.example.myapplication.ui.screen.settings.memory.MemoryManagementScreen
import com.example.myapplication.ui.screen.settings.worldbook.WorldBookBookDetailScreen
import com.example.myapplication.ui.screen.settings.worldbook.WorldBookEditScreen
import com.example.myapplication.ui.screen.settings.worldbook.WorldBookListScreen
import com.example.myapplication.ui.screen.settings.worldbook.buildWorldBookBooks
import com.example.myapplication.viewmodel.AppUpdateViewModel
import com.example.myapplication.viewmodel.SettingsViewModel

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
                onUpdateTitleSummaryModel = settingsViewModel::updateProviderTitleSummaryModel,
                onUpdateChatSuggestionModel = settingsViewModel::updateProviderChatSuggestionModel,
                onUpdateMemoryModel = settingsViewModel::updateProviderMemoryModel,
                onUpdateTranslationModel = settingsViewModel::updateProviderTranslationModel,
                onUpdatePhoneSnapshotModel = settingsViewModel::updateProviderPhoneSnapshotModel,
                onUpdateSearchModel = settingsViewModel::updateProviderSearchModel,
                onUpdateGiftImageModel = settingsViewModel::updateProviderGiftImageModel,
                onUpdateTitleSummaryModelMode = settingsViewModel::updateProviderTitleSummaryModelMode,
                onUpdateChatSuggestionModelMode = settingsViewModel::updateProviderChatSuggestionModelMode,
                onUpdateMemoryModelMode = settingsViewModel::updateProviderMemoryModelMode,
                onUpdateTranslationModelMode = settingsViewModel::updateProviderTranslationModelMode,
                onUpdatePhoneSnapshotModelMode = settingsViewModel::updateProviderPhoneSnapshotModelMode,
                onUpdateSearchModelMode = settingsViewModel::updateProviderSearchModelMode,
                onUpdateGiftImageModelMode = settingsViewModel::updateProviderGiftImageModelMode,
                onConsumeMessage = settingsViewModel::consumeMessage,
                onNavigateBack = {
                    settingsViewModel.saveSettings {}
                    navController.popBackStack()
                },
            )
        }

        composable(AppRoutes.SETTINGS_ASSISTANTS) {
            AssistantListScreen(
                viewModel = settingsViewModel,
                onNavigateToAssistantConfig = { assistantId ->
                    if (assistantId == null) {
                        navController.navigate(AppRoutes.settingsAssistantBasic("new")) {
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate(AppRoutes.settingsAssistantDetail(assistantId)) {
                            launchSingleTop = true
                        }
                    }
                },
                onDeleteAssistant = settingsViewModel::removeAssistant,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(AppRoutes.SETTINGS_ASSISTANT_DETAIL) { backStackEntry ->
            val storedSettings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
            val worldBookViewModel = rememberWorldBookViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val worldBookState by worldBookViewModel.uiState.collectAsStateWithLifecycle()
            val memoryManagementViewModel = rememberMemoryManagementViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val memoryManagementState by memoryManagementViewModel.uiState.collectAsStateWithLifecycle()
            val assistantId = backStackEntry.arguments?.getString("assistantId").orEmpty()
            val resolvedAssistants = storedSettings.resolvedAssistants()
            val assistant = resolvedAssistants.firstOrNull { it.id == assistantId } ?: return@composable

            AssistantDetailScreen(
                assistant = assistant,
                linkedWorldBookCount = buildWorldBookBooks(
                    worldBookState.entries.filter { entry ->
                        entry.scopeType == com.example.myapplication.model.WorldBookScopeType.ATTACHABLE &&
                            entry.resolvedBookId() in assistant.linkedWorldBookBookIds
                    },
                ).size,
                assistantMemoryCount = memoryManagementState.memories.count { memory ->
                    memory.scopeType == com.example.myapplication.model.MemoryScopeType.ASSISTANT &&
                        memory.scopeId == assistant.id
                },
                onOpenBasic = {
                    navController.navigate(AppRoutes.settingsAssistantBasic(assistant.id)) {
                        launchSingleTop = true
                    }
                },
                onOpenPrompt = {
                    navController.navigate(AppRoutes.settingsAssistantPrompt(assistant.id)) {
                        launchSingleTop = true
                    }
                },
                onOpenExtensions = {
                    navController.navigate(AppRoutes.settingsAssistantExtensions(assistant.id)) {
                        launchSingleTop = true
                    }
                },
                onOpenMemory = {
                    navController.navigate(AppRoutes.settingsAssistantMemory(assistant.id)) {
                        launchSingleTop = true
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(AppRoutes.SETTINGS_ASSISTANT_BASIC) { backStackEntry ->
            val storedSettings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
            val assistantId = backStackEntry.arguments?.getString("assistantId").orEmpty()
            val isNew = assistantId == "new"
            val resolvedAssistants = storedSettings.resolvedAssistants()
            val assistant = if (isNew) null else resolvedAssistants.firstOrNull { it.id == assistantId }

            AssistantBasicScreen(
                assistant = assistant,
                isNew = isNew,
                onSave = { updated ->
                    if (isNew) {
                        settingsViewModel.addAssistant(updated)
                    } else {
                        settingsViewModel.updateAssistant(updated)
                    }
                },
                onDelete = { id ->
                    settingsViewModel.removeAssistant(id)
                    navController.popBackStack(AppRoutes.SETTINGS_ASSISTANTS, false)
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(AppRoutes.SETTINGS_ASSISTANT_PROMPT) { backStackEntry ->
            val storedSettings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
            val assistantId = backStackEntry.arguments?.getString("assistantId").orEmpty()
            val assistant = storedSettings.resolvedAssistants().firstOrNull { it.id == assistantId } ?: return@composable
            AssistantPromptScreen(
                assistant = assistant,
                onSave = settingsViewModel::updateAssistant,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.SETTINGS_ASSISTANT_EXTENSIONS) { backStackEntry ->
            val storedSettings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
            val worldBookViewModel = rememberWorldBookViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val worldBookState by worldBookViewModel.uiState.collectAsStateWithLifecycle()
            val assistantId = backStackEntry.arguments?.getString("assistantId").orEmpty()
            val assistant = storedSettings.resolvedAssistants().firstOrNull { it.id == assistantId } ?: return@composable
            AssistantExtensionsScreen(
                assistant = assistant,
                worldBookEntries = worldBookState.entries,
                onSave = settingsViewModel::updateAssistant,
                onOpenWorldBookSettings = {
                    navController.navigate(AppRoutes.SETTINGS_WORLD_BOOKS) {
                        launchSingleTop = true
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.SETTINGS_ASSISTANT_MEMORY) { backStackEntry ->
            val storedSettings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
            val memoryManagementViewModel = rememberMemoryManagementViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val memoryManagementState by memoryManagementViewModel.uiState.collectAsStateWithLifecycle()
            val assistantId = backStackEntry.arguments?.getString("assistantId").orEmpty()
            val assistant = storedSettings.resolvedAssistants().firstOrNull { it.id == assistantId } ?: return@composable
            val assistantMemories = memoryManagementState.memories.filter { entry ->
                when (entry.scopeType) {
                    com.example.myapplication.model.MemoryScopeType.GLOBAL -> assistant.useGlobalMemory
                    com.example.myapplication.model.MemoryScopeType.ASSISTANT -> {
                        !assistant.useGlobalMemory && entry.scopeId == assistant.id
                    }
                    com.example.myapplication.model.MemoryScopeType.CONVERSATION -> false
                }
            }
            AssistantMemoryScreen(
                assistant = assistant,
                memories = assistantMemories,
                onSaveAssistant = settingsViewModel::updateAssistant,
                onUpsertMemory = memoryManagementViewModel::upsertMemory,
                onDeleteMemory = memoryManagementViewModel::deleteMemory,
                onTogglePinned = memoryManagementViewModel::togglePinned,
                onOpenGlobalMemorySettings = {
                    navController.navigate(AppRoutes.SETTINGS_MEMORY) {
                        launchSingleTop = true
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.SETTINGS_WORLD_BOOKS) { backStackEntry ->
            val worldBookViewModel = rememberWorldBookViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val worldBookState by worldBookViewModel.uiState.collectAsStateWithLifecycle()
            WorldBookListScreen(
                entries = worldBookState.entries,
                onOpenBook = { bookId ->
                    navController.navigate(AppRoutes.settingsWorldBookBook(bookId)) {
                        launchSingleTop = true
                    }
                },
                onOpenEntryEdit = { entryId ->
                    navController.navigate(AppRoutes.settingsWorldBookEdit(entryId)) {
                        launchSingleTop = true
                    }
                },
                onAddEntry = {
                    navController.navigate(AppRoutes.settingsWorldBookEdit("new")) {
                        launchSingleTop = true
                    }
                },
                onOpenImport = {
                    navController.navigate(AppRoutes.SETTINGS_CONTEXT_TRANSFER) {
                        launchSingleTop = true
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(AppRoutes.SETTINGS_WORLD_BOOK_BOOK) { backStackEntry ->
            val worldBookViewModel = rememberWorldBookViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val worldBookState by worldBookViewModel.uiState.collectAsStateWithLifecycle()
            val rawBookId = backStackEntry.arguments?.getString("bookId").orEmpty()
            val bookId = Uri.decode(rawBookId)
            val bookEntries = worldBookState.entries
                .filter { it.resolvedBookId() == bookId }
                .sortedWith(
                    compareBy<com.example.myapplication.model.WorldBookEntry>(
                        { it.insertionOrder },
                        { it.createdAt },
                    ).thenByDescending { it.updatedAt },
                )
            val bookName = bookEntries.firstNotNullOfOrNull { entry ->
                entry.sourceBookName.trim().takeIf { it.isNotBlank() }
            }.orEmpty()
            WorldBookBookDetailScreen(
                bookId = bookId,
                bookName = bookName,
                entries = bookEntries,
                isSaving = worldBookState.isSaving,
                onRenameBook = { targetBookId, newName ->
                    worldBookViewModel.renameBook(targetBookId, newName)
                    navController.popBackStack()
                },
                onDeleteBook = { targetBookId ->
                    worldBookViewModel.deleteBook(targetBookId)
                    navController.popBackStack()
                },
                onAddEntry = {
                    navController.navigate(AppRoutes.settingsWorldBookEdit("new", bookName)) {
                        launchSingleTop = true
                    }
                },
                onOpenEntryEdit = { entryId ->
                    navController.navigate(AppRoutes.settingsWorldBookEdit(entryId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(AppRoutes.SETTINGS_WORLD_BOOK_EDIT) { backStackEntry ->
            val storedSettings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
            val worldBookViewModel = rememberWorldBookViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val worldBookState by worldBookViewModel.uiState.collectAsStateWithLifecycle()
            val entryId = backStackEntry.arguments?.getString("entryId").orEmpty()
            val presetBookName = Uri.decode(backStackEntry.arguments?.getString("bookName").orEmpty())
            val isNew = entryId == "new"
            val entry = if (isNew) null else worldBookState.entries.firstOrNull { it.id == entryId }

            WorldBookEditScreen(
                entry = entry,
                isNew = isNew,
                assistants = storedSettings.resolvedAssistants(),
                presetBookName = if (isNew) presetBookName else "",
                onSave = worldBookViewModel::saveEntry,
                onDelete = worldBookViewModel::deleteEntry,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(AppRoutes.SETTINGS_CONTEXT_TRANSFER) { backStackEntry ->
            val contextTransferViewModel = rememberContextTransferViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val contextTransferState by contextTransferViewModel.uiState.collectAsStateWithLifecycle()
            ContextTransferScreen(
                uiState = contextTransferState,
                onExportJson = contextTransferViewModel::exportBundleJson,
                onPreviewImportPayload = contextTransferViewModel::previewImportPayload,
                onConfirmImport = contextTransferViewModel::confirmImport,
                onDismissImportPreview = contextTransferViewModel::dismissImportPreview,
                onConsumeMessage = contextTransferViewModel::consumeMessage,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(AppRoutes.SETTINGS_MEMORY) { backStackEntry ->
            val memoryManagementViewModel = rememberMemoryManagementViewModel(
                navController = navController,
                appGraph = appGraph,
                backStackEntry = backStackEntry,
            )
            val memoryManagementState by memoryManagementViewModel.uiState.collectAsStateWithLifecycle()
            MemoryManagementScreen(
                uiState = memoryManagementState,
                onTogglePinned = memoryManagementViewModel::togglePinned,
                onDeleteMemory = memoryManagementViewModel::deleteMemory,
                onDeleteSummary = memoryManagementViewModel::deleteSummary,
                onConsumeMessage = memoryManagementViewModel::consumeMessage,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}
