package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myapplication.system.translation.ScreenTranslatorService
import com.example.myapplication.ui.screen.chat.ChatScreen
import com.example.myapplication.ui.screen.home.HomeScreen
import com.example.myapplication.ui.screen.roleplay.RoleplayScenarioEditScreen
import com.example.myapplication.ui.screen.roleplay.RoleplayScenarioListScreen
import com.example.myapplication.ui.screen.roleplay.RoleplayReadingMode
import com.example.myapplication.ui.screen.roleplay.RoleplaySettingsScreen
import com.example.myapplication.ui.screen.roleplay.RoleplayScreen
import com.example.myapplication.ui.screen.translate.TranslationScreen
import com.example.myapplication.ui.screen.settings.AssistantBasicScreen
import com.example.myapplication.ui.screen.settings.AssistantDetailScreen
import com.example.myapplication.ui.screen.settings.AssistantExtensionsScreen
import com.example.myapplication.ui.screen.settings.AssistantMemoryScreen
import com.example.myapplication.ui.screen.settings.AssistantPromptScreen
import com.example.myapplication.ui.screen.settings.AppUpdateScreen
import com.example.myapplication.ui.screen.settings.AssistantListScreen
import com.example.myapplication.ui.screen.settings.ContextTransferScreen
import com.example.myapplication.ui.screen.settings.memory.MemoryManagementScreen
import com.example.myapplication.ui.screen.settings.ProviderDetailScreen
import com.example.myapplication.ui.screen.settings.ProviderSettingsScreen
import com.example.myapplication.ui.screen.settings.ScreenTranslationSettingsScreen
import com.example.myapplication.ui.screen.settings.SettingsConnectionScreen
import com.example.myapplication.ui.screen.settings.SettingsModelScreen
import com.example.myapplication.ui.screen.settings.SettingsScreen
import com.example.myapplication.ui.screen.settings.worldbook.WorldBookEditScreen
import com.example.myapplication.ui.screen.settings.worldbook.WorldBookBookDetailScreen
import com.example.myapplication.ui.screen.settings.worldbook.WorldBookListScreen
import com.example.myapplication.viewmodel.ChatViewModel
import com.example.myapplication.viewmodel.ContextTransferViewModel
import com.example.myapplication.viewmodel.MemoryManagementViewModel
import com.example.myapplication.viewmodel.RoleplayViewModel
import com.example.myapplication.viewmodel.AppUpdateViewModel
import com.example.myapplication.viewmodel.SettingsViewModel
import com.example.myapplication.viewmodel.TranslationViewModel
import com.example.myapplication.viewmodel.WorldBookViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    chatViewModel: ChatViewModel,
    translationViewModel: TranslationViewModel,
    worldBookViewModel: WorldBookViewModel,
    contextTransferViewModel: ContextTransferViewModel,
    memoryManagementViewModel: MemoryManagementViewModel,
    roleplayViewModel: RoleplayViewModel,
    appUpdateViewModel: AppUpdateViewModel,
    startDestination: String,
) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val storedSettings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
    val worldBookState by worldBookViewModel.uiState.collectAsStateWithLifecycle()
    val contextTransferState by contextTransferViewModel.uiState.collectAsStateWithLifecycle()
    val memoryManagementState by memoryManagementViewModel.uiState.collectAsStateWithLifecycle()
    val roleplayState by roleplayViewModel.uiState.collectAsStateWithLifecycle()
    val appUpdateState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(AppRoutes.HOME) {
            HomeScreen(
                storedSettings = settingsState.savedSettings,
                onOpenChat = {
                    if (settingsState.savedSettings.hasRequiredConfig()) {
                        navController.navigate(AppRoutes.CHAT) {
                            launchSingleTop = true
                        }
                    }
                },
                onOpenSettings = {
                    navController.navigate(AppRoutes.SETTINGS) {
                        launchSingleTop = true
                    }
                },
                onOpenRoleplay = {
                    navController.navigate(AppRoutes.ROLEPLAY) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(AppRoutes.ROLEPLAY) {
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
                settings = settingsState.savedSettings.copy(
                    showRoleplayAiHelper = settingsState.showRoleplayAiHelper,
                    roleplayLongformTargetChars = settingsState.roleplayLongformTargetChars,
                    showRoleplayPresenceStrip = settingsState.showRoleplayPresenceStrip,
                    showRoleplayStatusStrip = settingsState.showRoleplayStatusStrip,
                ),
                contextStatus = roleplayState.contextStatus,
                currentModel = roleplayState.currentModel,
                currentProviderId = roleplayState.currentProviderId,
                providerOptions = providerOptions,
                isLoadingModels = settingsState.isLoadingModels,
                loadingProviderId = settingsState.loadingProviderId,
                isSavingModel = settingsState.isSaving,
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

        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                uiState = settingsState,
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
            ScreenTranslationSettingsScreen(
                uiState = settingsState,
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
            ProviderSettingsScreen(
                uiState = settingsState,
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
            val providerId = backStackEntry.arguments?.getString("providerId").orEmpty()
            ProviderDetailScreen(
                providerId = providerId,
                uiState = settingsState,
                onUpdateProviderName = settingsViewModel::updateProviderName,
                onUpdateProviderBaseUrl = settingsViewModel::updateProviderBaseUrl,
                onUpdateProviderApiKey = settingsViewModel::updateProviderApiKey,
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
            SettingsConnectionScreen(
                uiState = settingsState,
                onBaseUrlChange = settingsViewModel::updateBaseUrl,
                onApiKeyChange = settingsViewModel::updateApiKey,
                onConsumeMessage = settingsViewModel::consumeMessage,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(AppRoutes.SETTINGS_MODEL) {
            SettingsModelScreen(
                uiState = settingsState,
                onLoadModels = settingsViewModel::loadModels,
                onSelectProvider = settingsViewModel::selectProvider,
                onSelectedModelChange = settingsViewModel::updateProviderSelectedModel,
                onUpdateTitleSummaryModel = settingsViewModel::updateProviderTitleSummaryModel,
                onUpdateChatSuggestionModel = settingsViewModel::updateProviderChatSuggestionModel,
                onUpdateMemoryModel = settingsViewModel::updateProviderMemoryModel,
                onUpdateTranslationModel = settingsViewModel::updateProviderTranslationModel,
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
            val assistantId = backStackEntry.arguments?.getString("assistantId").orEmpty()
            val resolvedAssistants = storedSettings.resolvedAssistants()
            val assistant = resolvedAssistants.firstOrNull { it.id == assistantId } ?: return@composable

            AssistantDetailScreen(
                assistant = assistant,
                linkedWorldBookCount = worldBookState.entries.count { entry ->
                    entry.id in assistant.linkedWorldBookIds ||
                        (
                            entry.scopeType == com.example.myapplication.model.WorldBookScopeType.ASSISTANT &&
                                entry.scopeId == assistant.id
                            )
                },
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
            val assistantId = backStackEntry.arguments?.getString("assistantId").orEmpty()
            val assistant = storedSettings.resolvedAssistants().firstOrNull { it.id == assistantId } ?: return@composable
            AssistantPromptScreen(
                assistant = assistant,
                onSave = settingsViewModel::updateAssistant,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.SETTINGS_ASSISTANT_EXTENSIONS) { backStackEntry ->
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

        composable(AppRoutes.SETTINGS_WORLD_BOOKS) {
            WorldBookListScreen(
                entries = worldBookState.entries,
                onOpenBook = { bookName ->
                    navController.navigate(AppRoutes.settingsWorldBookBook(bookName)) {
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
            val rawBookName = backStackEntry.arguments?.getString("bookName").orEmpty()
            val bookName = Uri.decode(rawBookName)
            val bookEntries = worldBookState.entries
                .filter { it.sourceBookName == bookName }
                .sortedWith(
                    compareBy<com.example.myapplication.model.WorldBookEntry>(
                        { it.insertionOrder },
                        { it.createdAt },
                    ).thenByDescending { it.updatedAt },
                )
            WorldBookBookDetailScreen(
                bookName = bookName,
                entries = bookEntries,
                isSaving = worldBookState.isSaving,
                onRenameBook = { originalName, newName ->
                    worldBookViewModel.renameBook(originalName, newName)
                    navController.popBackStack()
                },
                onDeleteBook = { targetBookName ->
                    worldBookViewModel.deleteBook(targetBookName)
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

        composable(AppRoutes.SETTINGS_CONTEXT_TRANSFER) {
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

        composable(AppRoutes.SETTINGS_MEMORY) {
            MemoryManagementScreen(
                uiState = memoryManagementState,
                onTogglePinned = memoryManagementViewModel::togglePinned,
                onDeleteMemory = memoryManagementViewModel::deleteMemory,
                onDeleteSummary = memoryManagementViewModel::deleteSummary,
                onRefreshSummaries = memoryManagementViewModel::refreshSummaries,
                onConsumeMessage = memoryManagementViewModel::consumeMessage,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(AppRoutes.CHAT) {
            val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
            ChatScreen(
                uiState = chatState,
                isLoadingModels = settingsState.isLoadingModels,
                loadingProviderId = settingsState.loadingProviderId,
                isSavingModel = settingsState.isSaving,
                onInputChange = chatViewModel::updateInput,
                onSend = chatViewModel::sendMessage,
                onCreateConversation = chatViewModel::createConversation,
                onSelectConversation = chatViewModel::selectConversation,
                onClearConversation = chatViewModel::clearConversation,
                onDeleteConversation = chatViewModel::deleteConversation,
                onDeleteCurrentConversation = chatViewModel::deleteCurrentConversation,
                onClearCurrentConversation = chatViewModel::clearCurrentConversation,
                onRetryMessage = chatViewModel::retryMessage,
                onToggleMemoryMessage = chatViewModel::toggleMessageMemory,
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
                onOpenProviderDetail = { providerId ->
                    navController.navigate(AppRoutes.settingsProviderDetail(providerId)) {
                        launchSingleTop = true
                    }
                },
                onClearErrorMessage = chatViewModel::clearErrorMessage,
                onClearNoticeMessage = chatViewModel::clearNoticeMessage,
                onCancelSending = chatViewModel::cancelSending,
                onAddPendingParts = chatViewModel::addPendingParts,
                onRemovePendingPart = chatViewModel::removePendingPart,
                onSendTransferPlay = chatViewModel::sendTransferPlay,
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
}
