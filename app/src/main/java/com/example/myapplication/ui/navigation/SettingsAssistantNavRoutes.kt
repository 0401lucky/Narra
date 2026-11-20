package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.myapplication.data.repository.ImageFileStorage
import com.example.myapplication.di.AppGraph
import com.example.myapplication.ui.screen.settings.AssistantBasicScreen
import com.example.myapplication.ui.screen.settings.CharacterArtStudioScreen
import com.example.myapplication.ui.screen.settings.AssistantDetailScreen
import com.example.myapplication.ui.screen.settings.AssistantExtensionsScreen
import com.example.myapplication.ui.screen.settings.AssistantListScreen
import com.example.myapplication.ui.screen.settings.AssistantPromptScreen
import com.example.myapplication.ui.screen.settings.worldbook.buildWorldBookBooks
import com.example.myapplication.viewmodel.SettingsViewModel
import com.example.myapplication.viewmodel.CharacterArtStudioViewModel
import com.example.myapplication.viewmodel.MemoryArchiveViewModel
import com.example.myapplication.viewmodel.MemoryArchiveReadExtras
import com.example.myapplication.viewmodel.MemoryOriginalViewModel

// 角色列表、详情、基本信息、提示词、扩展、记忆

internal fun NavGraphBuilder.registerSettingsAssistantRoutes(
    appGraph: AppGraph,
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
) {
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
        val presets by appGraph.presetRepository.observePresets()
            .collectAsStateWithLifecycle(emptyList())
        val worldBookViewModel = rememberWorldBookViewModel(
            navController = navController,
            appGraph = appGraph,
            backStackEntry = backStackEntry,
        )
        val worldBookState by worldBookViewModel.uiState.collectAsStateWithLifecycle()
        val archiveOverview = rememberMemoryArchiveOverviewViewModel(
            navController = navController,
            appGraph = appGraph,
            backStackEntry = backStackEntry,
        )
        val archiveOverviewState by archiveOverview.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(backStackEntry) { archiveOverview.refresh() }
        val assistantId = Uri.decode(backStackEntry.arguments?.getString("assistantId").orEmpty())
        val resolvedAssistants = storedSettings.resolvedAssistants()
        val assistant = resolvedAssistants.firstOrNull { it.id == assistantId } ?: return@composable

        AssistantDetailScreen(
            assistant = assistant,
            presets = presets,
            globalDefaultPresetId = storedSettings.defaultPresetId,
            linkedWorldBookCount = buildWorldBookBooks(
                worldBookState.entries.filter { entry ->
                    entry.scopeType == com.example.myapplication.model.WorldBookScopeType.ATTACHABLE &&
                            entry.resolvedBookId() in assistant.linkedWorldBookBookIds
                },
            ).size,
            assistantMemoryCount = archiveOverviewState.counts[assistant.id] ?: 0,
            onOpenBasic = {
                navController.navigate(AppRoutes.settingsAssistantBasic(assistant.id)) {
                    launchSingleTop = true
                }
            },
            onOpenArtStudio = {
                navController.navigate(AppRoutes.settingsAssistantArtStudio(assistant.id)) {
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
            onOpenCompanionVoice = {
                navController.navigate(AppRoutes.roleplayCompanionVoice(assistant.id)) { launchSingleTop = true }
            },
            onUpdateAssistant = settingsViewModel::updateAssistant,
            onSelectPreset = { presetId ->
                settingsViewModel.updateAssistant(assistant.copy(defaultPresetId = presetId))
            },
            onOpenPresetManager = {
                navController.navigate(AppRoutes.SETTINGS_PRESETS) {
                    launchSingleTop = true
                }
            },
            onNavigateBack = {
                navController.popBackStack()
            },
        )
    }

    composable(AppRoutes.SETTINGS_ASSISTANT_ART_STUDIO) { backStackEntry ->
        val context = LocalContext.current
        val assistantId = backStackEntry.arguments
            ?.getString("assistantId")
            ?.let(Uri::decode)
            .orEmpty()
        val viewModel: CharacterArtStudioViewModel = viewModel(
            key = "character-art-studio-$assistantId",
            factory = CharacterArtStudioViewModel.factory(
                initialAssistantId = assistantId,
                settingsRepository = appGraph.aiSettingsRepository,
                settingsEditor = appGraph.aiSettingsEditor,
                aiPromptExtrasService = appGraph.aiPromptExtrasService,
                aiGateway = appGraph.aiGateway,
                imageSaver = { b64Data ->
                    ImageFileStorage.saveBase64Image(
                        context = context,
                        b64Data = b64Data,
                    )
                },
            ),
        )
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        CharacterArtStudioScreen(
            uiState = uiState,
            onSelectAssistant = viewModel::selectAssistant,
            onSelectStyle = viewModel::selectStyle,
            onPromptChange = viewModel::updateEditablePrompt,
            onNegativePromptChange = viewModel::updateEditableNegativePrompt,
            onRevisionInstructionChange = viewModel::updateRevisionInstruction,
            onExtractPrompt = viewModel::extractPrompt,
            onGenerateImage = viewModel::generateImage,
            onApplyAvatar = viewModel::applyGeneratedImageAsAvatar,
            onClearMessages = viewModel::clearMessages,
            onNavigateBack = { navController.popBackStack() },
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
        val assistant = storedSettings.resolvedAssistants().firstOrNull { it.id == assistantId }
            ?: return@composable
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
        val assistant = storedSettings.resolvedAssistants().firstOrNull { it.id == assistantId }
            ?: return@composable
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

    registerMemoryArchiveRoutes(
        navController = navController,
        settingsProvider = {
            val settings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
            settings
        },
        archiveFactory = { characterId, conversationId, savedState ->
            MemoryArchiveViewModel.factory(
                appGraph.memoryArchiveRepository, characterId, conversationId,
                memoryArchiveTaskActions(appGraph), savedState,
                MemoryArchiveReadExtras(
                    appGraph.memoryVectorRepository::archiveProgress,
                    appGraph.memoryArchiveTransferRepository::getOrigins,
                    { runtime, retryUncertain ->
                        appGraph.memoryVectorRepository.rebuild(
                            runtime,
                            forceRebuild = true,
                            retryUncertain = retryUncertain
                        )
                    })
            )
        },
        originalFactory = { characterId, spaceId, personaKey, conversationId ->
            MemoryOriginalViewModel.factory(
                appGraph.memoryArchiveRepository,
                characterId,
                spaceId,
                personaKey,
                conversationId
            )
        },
        onUpdateAssistant = settingsViewModel::updateAssistant,
    )
}
