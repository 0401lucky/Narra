package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import android.net.Uri
import com.example.myapplication.conversation.PhoneContextBuilder
import com.example.myapplication.data.repository.ImageFileStorage
import com.example.myapplication.di.AppGraph
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.viewmodel.ContextTransferViewModel
import com.example.myapplication.viewmodel.MemoryManagementViewModel
import com.example.myapplication.viewmodel.MomentsViewModel
import com.example.myapplication.viewmodel.PhoneCheckViewModel
import com.example.myapplication.viewmodel.RoleplayViewModel
import com.example.myapplication.viewmodel.WorldBookViewModel

@Composable
internal fun rememberRoleplayViewModel(
    navController: NavHostController,
    appGraph: AppGraph,
    backStackEntry: NavBackStackEntry,
): RoleplayViewModel {
    val context = LocalContext.current
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
            phoneSnapshotRepository = appGraph.phoneSnapshotRepository,
            memoryWriteService = appGraph.memoryWriteService,
            imageSaver = { b64Data ->
                ImageFileStorage.saveBase64Image(context, b64Data)
            },
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

@Composable
internal fun rememberPhoneCheckViewModel(
    appGraph: AppGraph,
    backStackEntry: NavBackStackEntry,
): PhoneCheckViewModel {
    val rawConversationId = backStackEntry.arguments?.getString("conversationId").orEmpty()
    val rawScenarioId = backStackEntry.arguments?.getString("scenarioId").orEmpty()
    val rawOwnerType = backStackEntry.arguments?.getString("ownerType").orEmpty()
    val conversationId = Uri.decode(rawConversationId)
    val scenarioId = Uri.decode(rawScenarioId)
    val ownerType = PhoneSnapshotOwnerType.fromStorageValue(Uri.decode(rawOwnerType))
    return viewModel(
        factory = PhoneCheckViewModel.factory(
            conversationId = conversationId,
            scenarioId = scenarioId,
            ownerType = ownerType,
            settingsRepository = appGraph.aiSettingsRepository,
            conversationRepository = appGraph.conversationRepository,
            roleplayRepository = appGraph.roleplayRepository,
            phoneSnapshotRepository = appGraph.phoneSnapshotRepository,
            aiPromptExtrasService = appGraph.aiPromptExtrasService,
            phoneContextBuilder = PhoneContextBuilder(
                promptContextAssembler = appGraph.promptContextAssembler,
            ),
        ),
    )
}

@Composable
internal fun rememberMomentsViewModel(
    appGraph: AppGraph,
    backStackEntry: NavBackStackEntry,
): MomentsViewModel {
    val rawConversationId = backStackEntry.arguments?.getString("conversationId").orEmpty()
    val rawScenarioId = backStackEntry.arguments?.getString("scenarioId").orEmpty()
    val rawOwnerType = backStackEntry.arguments?.getString("ownerType").orEmpty()
    val conversationId = Uri.decode(rawConversationId)
    val scenarioId = Uri.decode(rawScenarioId)
    val ownerType = PhoneSnapshotOwnerType.fromStorageValue(Uri.decode(rawOwnerType))
    return viewModel(
        factory = MomentsViewModel.factory(
            conversationId = conversationId,
            scenarioId = scenarioId,
            ownerType = ownerType,
            settingsRepository = appGraph.aiSettingsRepository,
            conversationRepository = appGraph.conversationRepository,
            roleplayRepository = appGraph.roleplayRepository,
            phoneSnapshotRepository = appGraph.phoneSnapshotRepository,
            aiPromptExtrasService = appGraph.aiPromptExtrasService,
            phoneContextBuilder = PhoneContextBuilder(
                promptContextAssembler = appGraph.promptContextAssembler,
            ),
        ),
    )
}
