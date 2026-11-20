package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.myapplication.di.AppGraph
import com.example.myapplication.model.AppSettings
import com.example.myapplication.ui.screen.roleplay.ApplyRoleplaySurfaceSystemBars
import com.example.myapplication.ui.screen.roleplay.CompanionAppearanceTransferCallbacks
import com.example.myapplication.ui.screen.roleplay.CompanionAppearanceTransferScreen
import com.example.myapplication.ui.screen.roleplay.CompanionHistoryCallbacks
import com.example.myapplication.ui.screen.roleplay.CompanionHistoryScreen
import com.example.myapplication.viewmodel.CompanionAppearanceTransferViewModel
import com.example.myapplication.viewmodel.CompanionHistoryViewModel
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun NavGraphBuilder.registerCompanionHistoryDestinations(
    appGraph: AppGraph,
    navController: NavHostController,
) {
    composable(AppRoutes.ROLEPLAY_COMPANION_HISTORY) { entry ->
        val scenarioId = entry.arguments?.getString("scenarioId").orEmpty().let(Uri::decode)
        val assistantId = entry.arguments?.getString("assistantId").orEmpty().let(Uri::decode)
        val model: CompanionHistoryViewModel = viewModel(
            factory = CompanionHistoryViewModel.factory(
                scenarioId, assistantId, appGraph.companionHistoryManagementRepository,
                appGraph.companionTextExporter, appGraph.settingsStore,
            ),
        )
        val state by model.uiState.collectAsStateWithLifecycle()
        val settings by appGraph.settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        var pendingExportRequestId by rememberSaveable { mutableStateOf<String?>(null) }
        val exportLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                val requestId = pendingExportRequestId
                pendingExportRequestId = null
                if (requestId != null) model.completeExport(uri?.toString(), requestId)
            }
        LaunchedEffect(state.exportRequest?.id, pendingExportRequestId) {
            val request = state.exportRequest ?: return@LaunchedEffect
            if (pendingExportRequestId == null) {
                pendingExportRequestId = request.id
                exportLauncher.launch(request.fileName)
            }
        }
        ApplyRoleplaySurfaceSystemBars(
            androidx.compose.material3.MaterialTheme.colorScheme.surface,
            settings.roleplayImmersiveMode
        )
        fun navigate(route: String) {
            keyboard?.hide(); focus.clearFocus()
            navController.navigate(route) { launchSingleTop = true }
        }

        fun read(messageId: String? = null) {
            val archive = state.selectedArchive
            navigate(
                AppRoutes.roleplayCompanionReading(
                    scenarioId,
                    assistantId,
                    messageId,
                    archive?.spaceId,
                    archive?.runId
                )
            )
        }
        CompanionHistoryScreen(
            state = state,
            characterName = settings.resolvedAssistants()
                .firstOrNull { it.id == assistantId }?.name.orEmpty(),
            callbacks = CompanionHistoryCallbacks(
                model::setQuery,
                model::selectArchive,
                model::loadOlder,
                model::toggleSelection,
                model::clearSelection,
                model::beginEdit,
                model::updateDraft,
                model::cancelEdit,
                model::saveEdits,
                model::deleteSelected,
                model::requestExport,
                model::refresh,
                { keyboard?.hide(); focus.clearFocus(); navController.popBackStack() },
                { read(it) },
                { read() },
                { navigate(AppRoutes.roleplayCompanionVoice(assistantId)) },
                {
                    navigate(
                        AppRoutes.roleplayCompanionAppearanceTransfer(
                            scenarioId,
                            assistantId
                        )
                    )
                },
            ),
        )
    }
    composable(AppRoutes.ROLEPLAY_COMPANION_APPEARANCE_TRANSFER) { entry ->
        val assistantId = entry.arguments?.getString("assistantId").orEmpty().let(Uri::decode)
        val model: CompanionAppearanceTransferViewModel = viewModel(
            factory = CompanionAppearanceTransferViewModel.factory(
                assistantId,
                appGraph.contextTransferRecoveryRepository
            ),
        )
        val state by model.uiState.collectAsStateWithLifecycle()
        val settings by appGraph.settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
        val scope = rememberCoroutineScope()
        var documentBusy by remember { mutableStateOf(false) }
        var documentMessage by remember { mutableStateOf<String?>(null) }
        fun clearDocumentMessages() {
            documentMessage = null
            model.consumeMessage()
        }

        val exportLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) model.exportPackage { json, _ ->
                    scope.launch {
                        documentBusy = true
                        try {
                            appGraph.companionAppearanceDocumentStore.write(uri.toString(), json)
                            documentMessage = "装扮包已导出"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            documentMessage = SensitiveTextRedactor.throwableMessageForUi(
                                failure,
                                "装扮导出失败，请重试"
                            )
                        } finally {
                            documentBusy = false
                        }
                    }
                }
            }
        val importLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) scope.launch {
                    documentBusy = true
                    try {
                        val raw = appGraph.companionAppearanceDocumentStore.read(uri.toString())
                        model.previewImport(raw)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        documentMessage = SensitiveTextRedactor.throwableMessageForUi(
                            failure,
                            "装扮导入失败，请重试"
                        )
                    } finally {
                        documentBusy = false
                    }
                }
            }
        ApplyRoleplaySurfaceSystemBars(
            androidx.compose.material3.MaterialTheme.colorScheme.surface,
            settings.roleplayImmersiveMode
        )
        CompanionAppearanceTransferScreen(
            state.copy(
                isBusy = state.isBusy || documentBusy,
                message = documentMessage ?: state.message
            ),
            CompanionAppearanceTransferCallbacks(
                onExport = { clearDocumentMessages(); exportLauncher.launch("narra-appearance-pack.json") },
                onImport = {
                    clearDocumentMessages(); importLauncher.launch(
                    arrayOf(
                        "application/json",
                        "text/plain"
                    )
                )
                },
                onConfirm = { clearDocumentMessages(); model.confirmImport() },
                onDismissPreview = { clearDocumentMessages(); model.dismissPreview() },
                onResume = { id -> clearDocumentMessages(); model.resumeOperation(id) },
                onConsumeMessage = { documentMessage = null; model.consumeMessage() },
                onDismiss = { navController.popBackStack() },
            ),
        )
    }
}
