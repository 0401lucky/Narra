package com.example.myapplication.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.myapplication.ui.screen.roleplay.CompanionEntryCallbacks
import com.example.myapplication.ui.screen.roleplay.CompanionEntryScreen
import com.example.myapplication.ui.screen.roleplay.CompanionPendingResultsBar
import com.example.myapplication.viewmodel.CompanionEntryPhase
import com.example.myapplication.viewmodel.RoleplayCompanionUiState

@Composable
internal fun CompanionRouteContent(
    scenarioId: String,
    routeEntryId: String?,
    state: RoleplayCompanionUiState,
    characterName: String,
    callbacks: CompanionEntryCallbacks,
    content: @Composable () -> Unit,
) {
    val scopedState = state.takeIf {
        it.scenarioId == scenarioId && (routeEntryId == null || it.entryId == routeEntryId)
    }
    BackHandler(
        enabled = routeEntryId != null || scopedState?.isPreparing == true ||
                scopedState?.phase == CompanionEntryPhase.ACTIVE,
        onBack = callbacks.onBack,
    )
    Column(Modifier.fillMaxSize()) {
        if (!scopedState?.pendingResults.isNullOrEmpty()) {
            CompanionPendingResultsBar(
                requireNotNull(scopedState),
                characterName,
                callbacks.onSavePendingResult
            )
        }
        Box(Modifier.weight(1f)) {
            if ((routeEntryId != null || scopedState?.isPreparing == true) &&
                scopedState?.phase != CompanionEntryPhase.ACTIVE
            ) {
                CompanionEntryScreen(
                    scopedState ?: RoleplayCompanionUiState(
                        scenarioId = scenarioId,
                        phase = CompanionEntryPhase.LOADING
                    ),
                    characterName,
                    callbacks,
                )
            } else content()
        }
    }
}
