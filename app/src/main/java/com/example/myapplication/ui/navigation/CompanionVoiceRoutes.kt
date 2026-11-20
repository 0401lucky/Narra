package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.myapplication.di.AppGraph
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionReplyParser
import com.example.myapplication.roleplay.companion.CompanionSpeechSupport
import com.example.myapplication.ui.screen.roleplay.*
import com.example.myapplication.viewmodel.CompanionVoiceViewModel

/** 与生产 Stage/Reader 共用的宿主接线，可注入 ViewModel 验证真实回调和生命周期。 */
@Composable
internal fun rememberCompanionVoiceBinding(
    model: CompanionVoiceViewModel,
    owner: CompanionVoiceOwner,
    units: List<CompanionSpeechUnit>,
    automaticCandidates: List<CompanionSpeechUnit> = emptyList(),
    selection: CompanionSpeechSelection? = null,
    synchronizeSelection: Boolean = false,
    onOpenLibrary: () -> Unit = {},
): CompanionVoiceBinding {
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(model, owner, units, automaticCandidates, selection, synchronizeSelection) {
        model.bind(owner, units, automaticCandidates)
        if (synchronizeSelection) model.select(selection)
    }
    DisposableEffect(model, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> model.setHostActive(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> model.setHostActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        model.setHostActive(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            model.setHostActive(false)
        }
    }
    return CompanionVoiceBinding(state, companionVoiceCallbacks(model, onOpenLibrary))
}

internal fun companionVoiceCallbacks(
    model: CompanionVoiceViewModel,
    onOpenLibrary: () -> Unit = {}
) = CompanionVoiceCallbacks(
    model::select, model::playSelection, model::generateAndFavorite, model::favoriteCurrent,
    model::playFavorite, model::unfavorite, model::stop, model::retry, model::readOriginal,
    model::regenerateExplicitly, model::updateDraft, model::savePreferences,
    model::reloadDiscardingDraft, model::clearCache, onOpenLibrary,
)

internal fun companionVoiceUnits(
    messages: List<RoleplayMessageUiModel>,
    characterId: String
): List<CompanionSpeechUnit> =
    messages.filter { !it.isStreaming && it.messageStatus == MessageStatus.COMPLETED }
        .mapNotNull { it.companionReply }.distinctBy { it.source }
        .filter {
            it.source.characterId == characterId && it.source.attemptId != null &&
                    it.source.protocolVersion == 1 && it.source.parserVersion == CompanionReplyParser.PARSER_VERSION &&
                    CompanionReplyDiagnostic.MISSING_SOURCE_METADATA !in it.diagnostics
        }.flatMap(CompanionSpeechSupport::units)

internal fun NavGraphBuilder.registerCompanionVoiceDestination(
    appGraph: AppGraph,
    navController: NavHostController
) {
    composable(AppRoutes.ROLEPLAY_COMPANION_VOICE) { entry ->
        val characterId = entry.arguments?.getString("assistantId").orEmpty().let(Uri::decode)
        val owner = remember(characterId) {
            CompanionVoiceOwner(
                "voice-library:$characterId",
                characterId,
                "LIBRARY"
            )
        }
        val model: CompanionVoiceViewModel = viewModel(
            key = "companion-voice-library:$characterId",
            factory = CompanionVoiceViewModel.factory(
                owner, appGraph.settingsStore, appGraph.companionVoiceRepository,
                appGraph::createCompanionVoiceSession, entry.savedStateHandle,
            ),
        )
        val binding = rememberCompanionVoiceBinding(model, owner, emptyList())
        val settings by appGraph.settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
        ApplyRoleplaySurfaceSystemBars(
            MaterialTheme.colorScheme.surface,
            settings.roleplayImmersiveMode
        )
        CompanionVoiceScreen(
            binding.state, binding.callbacks,
            onBack = { model.stop(); navController.popBackStack() },
            onOpenVoiceConfiguration = {
                model.stop(); navController.navigate(AppRoutes.SETTINGS_VOICE_SYNTHESIS) {
                launchSingleTop = true
            }
            },
        )
    }
}
