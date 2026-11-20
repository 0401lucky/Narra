package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.myapplication.di.AppGraph
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.isGroupChat
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.screen.roleplay.ApplyRoleplaySurfaceSystemBars
import com.example.myapplication.ui.screen.roleplay.CompanionSettingsCallbacks
import com.example.myapplication.ui.screen.roleplay.CompanionSettingsScreen
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.CompanionSettingsViewModel
import kotlinx.coroutines.CancellationException

internal fun NavGraphBuilder.registerCompanionSettingsDestination(
    appGraph: AppGraph,
    navController: NavHostController
) {
    composable(AppRoutes.ROLEPLAY_COMPANION_SETTINGS) { entry ->
        val scenarioId = entry.arguments?.getString("scenarioId").orEmpty().let(Uri::decode)
        val assistantId = entry.arguments?.getString("assistantId").orEmpty().let(Uri::decode)
        var scenario by remember(scenarioId) { mutableStateOf<RoleplayScenario?>(null) }
        var loading by remember(scenarioId) { mutableStateOf(true) }
        LaunchedEffect(scenarioId) {
            try {
                scenario = appGraph.roleplayRepository.getScenario(scenarioId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                scenario = null
            } finally {
                loading = false
            }
        }
        val settings by appGraph.settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
        ApplyRoleplaySurfaceSystemBars(
            MaterialTheme.colorScheme.surface,
            settings.roleplayImmersiveMode
        )
        when {
            loading -> CircularProgressIndicator()
            scenario?.assistantId != assistantId || scenario?.isGroupChat != false -> {
                Column(Modifier.padding(NarraUi.space16)) {
                    Text("当前场景与角色不可用，请返回后重新打开。")
                    NarraTextButton(onClick = { navController.popBackStack() }) { Text("返回") }
                }
            }

            else -> {
                val viewModel: CompanionSettingsViewModel = viewModel(
                    key = "companion-settings:$assistantId",
                    factory = CompanionSettingsViewModel.factory(
                        assistantId,
                        appGraph.settingsStore,
                        entry.savedStateHandle
                    ),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.closeRequested) { if (state.closeRequested) navController.popBackStack() }
                CompanionSettingsScreen(
                    state, CompanionSettingsCallbacks(
                        onWritingChange = viewModel::updateWriting,
                        onZoneChange = viewModel::updateZone,
                        onAddCustomField = viewModel::addCustomField,
                        onAddTemplate = viewModel::addTemplate,
                        onSave = viewModel::save,
                        onBack = viewModel::requestClose,
                        onDismissDiscard = viewModel::dismissDiscard,
                        onDiscard = viewModel::discardAndClose,
                        onReload = viewModel::reloadDiscardingDraft,
                    )
                )
            }
        }
    }
}
