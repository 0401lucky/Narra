package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.example.myapplication.data.repository.ImageFileStorage
import com.example.myapplication.di.AppGraph
import com.example.myapplication.viewmodel.ContextTransferViewModel
import com.example.myapplication.viewmodel.MemoryManagementViewModel
import com.example.myapplication.viewmodel.RoleplayViewModel
import com.example.myapplication.viewmodel.WorldBookViewModel

@Composable
internal fun rememberRoleplayViewModel(
    navController: NavHostController,
    appGraph: AppGraph,
    backStackEntry: NavBackStackEntry,
): RoleplayViewModel {
    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry(AppRoutes.ROLEPLAY_GRAPH)
    }
    return viewModel(
        viewModelStoreOwner = parentEntry,
        factory = RoleplayViewModel.factory(
            settingsRepository = appGraph.aiSettingsRepository,
            settingsEditor = appGraph.aiSettingsEditor,
            aiGateway = appGraph.aiGateway,
            aiPromptExtrasService = appGraph.aiPromptExtrasService,
            conversationRepository = appGraph.conversationRepository,
            roleplayRepository = appGraph.roleplayRepository,
            promptContextAssembler = appGraph.promptContextAssembler,
            memoryRepository = appGraph.memoryRepository,
            conversationSummaryRepository = appGraph.conversationSummaryRepository,
            pendingMemoryProposalRepository = appGraph.pendingMemoryProposalRepository,
            memoryWriteService = appGraph.memoryWriteService,
        ),
    )
}

@Composable
internal fun rememberWorldBookViewModel(
    navController: NavHostController,
    appGraph: AppGraph,
    backStackEntry: NavBackStackEntry,
): WorldBookViewModel {
    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry(AppRoutes.SETTINGS_GRAPH)
    }
    return viewModel(
        viewModelStoreOwner = parentEntry,
        factory = WorldBookViewModel.factory(appGraph.worldBookRepository),
    )
}

@Composable
internal fun rememberMemoryManagementViewModel(
    navController: NavHostController,
    appGraph: AppGraph,
    backStackEntry: NavBackStackEntry,
): MemoryManagementViewModel {
    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry(AppRoutes.SETTINGS_GRAPH)
    }
    return viewModel(
        viewModelStoreOwner = parentEntry,
        factory = MemoryManagementViewModel.factory(
            memoryRepository = appGraph.memoryRepository,
            conversationSummaryRepository = appGraph.conversationSummaryRepository,
        ),
    )
}

@Composable
internal fun rememberContextTransferViewModel(
    navController: NavHostController,
    appGraph: AppGraph,
    backStackEntry: NavBackStackEntry,
): ContextTransferViewModel {
    val context = LocalContext.current
    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry(AppRoutes.SETTINGS_GRAPH)
    }
    return viewModel(
        viewModelStoreOwner = parentEntry,
        factory = ContextTransferViewModel.factory(
            settingsRepository = appGraph.aiSettingsRepository,
            settingsEditor = appGraph.aiSettingsEditor,
            worldBookRepository = appGraph.worldBookRepository,
            memoryRepository = appGraph.memoryRepository,
            conversationSummaryRepository = appGraph.conversationSummaryRepository,
            importedAssistantAvatarSaver = { importedAvatar ->
                ImageFileStorage.saveImageBytes(
                    context = context,
                    bytes = importedAvatar.bytes,
                    fileNamePrefix = "assistant-avatar-${importedAvatar.assistantId}",
                ).path
            },
        ),
    )
}
