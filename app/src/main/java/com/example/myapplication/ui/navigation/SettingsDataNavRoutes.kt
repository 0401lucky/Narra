package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.myapplication.di.AppGraph
import com.example.myapplication.ui.screen.settings.ContextTransferScreen
import com.example.myapplication.ui.screen.settings.memory.MemoryManagementScreen
import com.example.myapplication.ui.screen.settings.worldbook.WorldBookBookDetailScreen
import com.example.myapplication.ui.screen.settings.worldbook.WorldBookEditScreen
import com.example.myapplication.ui.screen.settings.worldbook.WorldBookListScreen
import com.example.myapplication.viewmodel.SettingsViewModel

// 世界书列表、书目详情、词条编辑 + 上下文导入导出 + 记忆管理

internal fun NavGraphBuilder.registerSettingsDataRoutes(
    appGraph: AppGraph,
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
) {
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
            uiMessage = worldBookState.message,
            onConsumeMessage = worldBookViewModel::consumeMessage,
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

        // 整本书被删干净后自动返回列表页（snackbar 由列表页显示）
        LaunchedEffect(bookEntries.isEmpty()) {
            if (bookEntries.isEmpty() && bookId.isNotBlank()) {
                navController.popBackStack()
            }
        }

        WorldBookBookDetailScreen(
            bookId = bookId,
            bookName = bookName,
            entries = bookEntries,
            isSaving = worldBookState.isSaving,
            onRenameBook = { targetBookId, newName ->
                worldBookViewModel.renameBook(targetBookId, newName)
                // 重命名保持在当前页，snackbar 由本页显示
            },
            onDeleteBook = { targetBookId ->
                worldBookViewModel.deleteBook(targetBookId)
                // pop 由 LaunchedEffect(bookEntries.isEmpty()) 触发
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
            uiMessage = worldBookState.message,
            onConsumeMessage = worldBookViewModel::consumeMessage,
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
        val conversations by appGraph.conversationRepository
            .observeConversations()
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val entryId = backStackEntry.arguments?.getString("entryId").orEmpty()
        val presetBookName = Uri.decode(backStackEntry.arguments?.getString("bookName").orEmpty())
        val isNew = entryId == "new"
        val entry = if (isNew) null else worldBookState.entries.firstOrNull { it.id == entryId }

        WorldBookEditScreen(
            entry = entry,
            isNew = isNew,
            assistants = storedSettings.resolvedAssistants(),
            conversations = conversations,
            presetBookName = if (isNew) presetBookName else "",
            existingBookNames = worldBookState.entries
                .mapNotNull { it.sourceBookName.trim().takeIf { name -> name.isNotBlank() } }
                .distinct()
                .sorted(),
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
        val storedSettings by settingsViewModel.storedSettings.collectAsStateWithLifecycle()
        val memoryManagementViewModel = rememberMemoryManagementViewModel(
            navController = navController,
            appGraph = appGraph,
            backStackEntry = backStackEntry,
        )
        val memoryManagementState by memoryManagementViewModel.uiState.collectAsStateWithLifecycle()
        MemoryManagementScreen(
            uiState = memoryManagementState,
            assistants = storedSettings.resolvedAssistants(),
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
