package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.myapplication.di.AppGraph
import com.example.myapplication.model.*
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.screen.roleplay.*
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.viewmodel.CompanionDisplayViewModel
import com.example.myapplication.viewmodel.CompanionReadingViewModel
import com.example.myapplication.viewmodel.CompanionVisualSettingsViewModel
import com.example.myapplication.viewmodel.CompanionVoiceViewModel
import com.example.myapplication.viewmodel.RoleplayCompanionUiState
import kotlinx.coroutines.CancellationException

@Composable
internal fun rememberCompanionDisplayBinding(
    scenario: RoleplayScenario?, state: RoleplayCompanionUiState?,
    appGraph: AppGraph, navController: NavHostController,
    messages: List<RoleplayMessageUiModel> = emptyList(),
): CompanionDisplayBinding? {
    if (scenario == null || state == null) return null
    val model: CompanionDisplayViewModel = viewModel(
        key = "companion-display:${scenario.id}:${scenario.assistantId}",
        factory = CompanionDisplayViewModel.factory(
            scenario,
            appGraph.settingsStore,
            appGraph.companionResourcesRepository
        )
    )
    val display by model.uiState.collectAsStateWithLifecycle()
    val voiceOwner = CompanionVoiceOwner(scenario.id, scenario.assistantId, display.mode.name)
    val voiceModel: CompanionVoiceViewModel = viewModel(
        key = "companion-voice:${scenario.id}:${scenario.assistantId}",
        factory = CompanionVoiceViewModel.factory(
            voiceOwner, appGraph.settingsStore, appGraph.companionVoiceRepository,
            appGraph::createCompanionVoiceSession, remember(scenario.id, scenario.assistantId) { SavedStateHandle() },
        ),
    )
    val units = remember(messages, scenario.assistantId) { companionVoiceUnits(messages, scenario.assistantId) }
    val automaticCandidates = remember(units, state.turn?.replyMessageId) {
        units.filter { it.source.messageId == state.turn?.replyMessageId }
    }
    val voice = rememberCompanionVoiceBinding(voiceModel, voiceOwner, units, automaticCandidates,
        onOpenLibrary = { voiceModel.stop(); navController.navigate(AppRoutes.roleplayCompanionVoice(scenario.assistantId)) { launchSingleTop = true } })
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentScenario by rememberUpdatedState(scenario)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(scenario) { model.refresh(scenario) }
    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refresh(currentScenario)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun navigate(route: String) {
        voiceModel.stop()
        keyboard?.hide(); focus.clearFocus(); navController.navigate(route) {
            launchSingleTop = true
        }
    }
    return CompanionDisplayBinding(
        display, { mode -> voiceModel.stop(); model.chooseMode(mode) },
        onOpenReading = {
            navigate(
                AppRoutes.roleplayCompanionReading(
                    scenario.id,
                    scenario.assistantId
                )
            )
        },
        onOpenAppearance = {
            navigate(
                AppRoutes.roleplayCompanionVisuals(
                    scenario.id,
                    scenario.assistantId
                )
            )
        },
        replyMessageId = state.turn?.replyMessageId,
        onSpeechSelection = voiceModel::select,
        voice = voice,
    )
}

internal fun roleplayReadingPath(
    scenarioId: String,
    scenario: RoleplayScenario?,
    messages: List<RoleplayMessageUiModel>
): String =
    if (scenario?.chatType == RoleplayChatType.SINGLE && !scenario.longformModeEnabled &&
        scenario.interactionMode != RoleplayInteractionMode.OFFLINE_LONGFORM &&
        messages.any { it.companionReply?.source?.attemptId != null && it.messageStatus == MessageStatus.COMPLETED && !it.isStreaming }
    )
        AppRoutes.roleplayCompanionReading(
            scenarioId,
            scenario.assistantId
        ) else AppRoutes.roleplayReading(scenarioId)

internal fun NavGraphBuilder.registerCompanionPresentationDestinations(
    appGraph: AppGraph,
    navController: NavHostController
) {
    composable(
        AppRoutes.ROLEPLAY_COMPANION_READING,
        arguments = listOf("messageId", "archiveSpaceId", "archiveRunId").map { name ->
            navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
        },
    ) { entry ->
        val scenarioId = entry.arguments?.getString("scenarioId").orEmpty().let(Uri::decode)
        val assistantId = entry.arguments?.getString("assistantId").orEmpty().let(Uri::decode)
        val focusMessageId = entry.arguments?.getString("messageId")?.let(Uri::decode)
        val archiveSpaceId = entry.arguments?.getString("archiveSpaceId")?.let(Uri::decode)
        val archiveRunId = entry.arguments?.getString("archiveRunId")?.let(Uri::decode)
        val readingRoute = AppRoutes.roleplayCompanionReading(scenarioId, assistantId, focusMessageId, archiveSpaceId, archiveRunId)
        val model: CompanionReadingViewModel = viewModel(
            key = "companion-reader:$readingRoute",
            factory = CompanionReadingViewModel.factory(
                scenarioId,
                assistantId,
                appGraph.companionReadingRepository,
                appGraph.settingsStore,
                focusMessageId = focusMessageId,
                archiveSpaceId = archiveSpaceId,
                archiveRunId = archiveRunId,
            )
        )
        val state by model.uiState.collectAsStateWithLifecycle()
        val settings by appGraph.settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
        val voiceOwner = remember(scenarioId, assistantId) { CompanionVoiceOwner(scenarioId, assistantId, "READER") }
        val voiceModel: CompanionVoiceViewModel = viewModel(
            key = "companion-reader-voice:$readingRoute",
            factory = CompanionVoiceViewModel.factory(
                voiceOwner, appGraph.settingsStore, appGraph.companionVoiceRepository,
                appGraph::createCompanionVoiceSession, entry.savedStateHandle,
            ),
        )
        val units = remember(state.items) { com.example.myapplication.roleplay.companion.CompanionSpeechSupport.units(state.items) }
        val voice = rememberCompanionVoiceBinding(
            voiceModel, voiceOwner, units, selection = state.selection, synchronizeSelection = true,
            onOpenLibrary = { voiceModel.stop(); navController.navigate(AppRoutes.roleplayCompanionVoice(assistantId)) { launchSingleTop = true } },
        )
        key(readingRoute) {
            CompanionReadingScreen(
                state,
                settings,
                CompanionReadingCallbacks(
                    model::loadOlder, model::refresh, model::updateAnchor,
                    { selected -> model.select(selected); voiceModel.select(selected) }, { voiceModel.stop(); navController.popBackStack() }, model::open,
                    onOpenHistory = {
                        voiceModel.stop()
                        val previous = navController.previousBackStackEntry
                        if (previous?.destination?.route == AppRoutes.ROLEPLAY_COMPANION_HISTORY &&
                            previous.arguments?.getString("scenarioId")?.let(Uri::decode) == scenarioId &&
                            previous.arguments?.getString("assistantId")?.let(Uri::decode) == assistantId) navController.popBackStack()
                        else navController.navigate(AppRoutes.roleplayCompanionHistory(scenarioId, assistantId)) { launchSingleTop = true }
                    },
                ),
                voiceBinding = voice,
            )
        }
    }
    composable(AppRoutes.ROLEPLAY_COMPANION_VISUALS) { entry ->
        val scenarioId = entry.arguments?.getString("scenarioId").orEmpty().let(Uri::decode)
        val assistantId = entry.arguments?.getString("assistantId").orEmpty().let(Uri::decode)
        var scene by remember(scenarioId) { mutableStateOf<RoleplayScenario?>(null) }
        var loading by remember(scenarioId) { mutableStateOf(true) }
        LaunchedEffect(scenarioId) {
            try {
                scene = appGraph.roleplayRepository.getScenario(scenarioId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                scene = null
            } finally {
                loading = false
            }
        }
        val settings by appGraph.settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
        ApplyRoleplaySurfaceSystemBars(
            MaterialTheme.colorScheme.surface,
            settings.roleplayImmersiveMode
        )
        if (loading) CircularProgressIndicator()
        else if (scene?.assistantId != assistantId || scene?.chatType != RoleplayChatType.SINGLE) {
            Column(Modifier.fillMaxSize().padding(NarraUi.space16)) {
                Text("当前场景与角色不可用，请返回后重新打开。")
                NarraTextButton(onClick = { navController.popBackStack() }) { Text("返回") }
            }
        } else {
            val model: CompanionVisualSettingsViewModel = viewModel(
                key = "companion-visuals:$scenarioId:$assistantId",
                factory = CompanionVisualSettingsViewModel.factory(
                    assistantId, scenarioId, appGraph.settingsStore,
                    appGraph.companionResourcesRepository, entry.savedStateHandle
                )
            )
            val state by model.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.closeRequested) { if (state.closeRequested) navController.popBackStack() }
            CompanionVisualSettingsScreen(
                state, CompanionVisualSettingsCallbacks(
                    model::updateDraft,
                    model::importImage,
                    model::addOutfit,
                    model::removeResource,
                    model::save,
                    model::requestClose,
                    model::dismissDiscard,
                    model::discardAndClose,
                    model::reloadDiscardingDraft
                )
            )
        }
    }
    registerCompanionHistoryDestinations(appGraph, navController)
    registerCompanionVoiceDestination(appGraph, navController)
}
