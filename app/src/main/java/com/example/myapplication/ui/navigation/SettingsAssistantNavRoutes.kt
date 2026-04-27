package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.myapplication.di.AppGraph
import com.example.myapplication.ui.screen.settings.AssistantBasicScreen
import com.example.myapplication.ui.screen.settings.AssistantDetailScreen
import com.example.myapplication.ui.screen.settings.AssistantExtensionsScreen
import com.example.myapplication.ui.screen.settings.AssistantListScreen
import com.example.myapplication.ui.screen.settings.AssistantMemoryScreen
import com.example.myapplication.ui.screen.settings.AssistantPromptScreen
import com.example.myapplication.ui.screen.settings.memory.SimpleMemoryEditorScreen
import com.example.myapplication.ui.screen.settings.worldbook.buildWorldBookBooks
import com.example.myapplication.viewmodel.SettingsViewModel
import com.example.myapplication.viewmodel.SimpleMemoryEditorViewModel

// 助手列表、详情、基本信息、提示词、扩展、记忆

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

    composable(AppRoutes.SETTINGS_ASSISTANT_MEMORY_SIMPLE) { backStackEntry ->
        val storedSettings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
        val assistantId = backStackEntry.arguments?.getString("assistantId").orEmpty()
        val assistant = storedSettings.resolvedAssistants().firstOrNull { it.id == assistantId } ?: return@composable
        val conversationId = backStackEntry.arguments
            ?.getString("conversationId")
            ?.let(Uri::decode)
            ?.takeIf { it.isNotBlank() }
        val simpleViewModel: SimpleMemoryEditorViewModel = viewModel(
            key = "simple-memory-$assistantId-${conversationId.orEmpty()}",
            factory = SimpleMemoryEditorViewModel.factory(
                assistantId = assistantId,
                conversationId = conversationId,
                memoryRepository = appGraph.memoryRepository,
                assistantsProvider = { settingsViewModel.storedSettings.value.resolvedAssistants() },
            ),
        )
        SimpleMemoryEditorScreen(
            viewModel = simpleViewModel,
            assistantName = assistant.name,
            onOpenAdvancedManagement = {
                navController.navigate(AppRoutes.settingsAssistantMemory(assistantId)) {
                    launchSingleTop = true
                }
            },
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
