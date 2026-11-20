package com.example.myapplication.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.myapplication.model.*
import com.example.myapplication.ui.screen.settings.memory.*
import com.example.myapplication.viewmodel.*

/** 生产与导航测试共用同一注册函数，测试只替换依赖工厂。 */
internal fun NavGraphBuilder.registerMemoryArchiveRoutes(
    navController: NavHostController,
    settingsProvider: @Composable () -> AppSettings,
    archiveFactory: (String, String?, SavedStateHandle) -> ViewModelProvider.Factory,
    originalFactory: (String, String, String, String) -> ViewModelProvider.Factory,
    onUpdateAssistant: (Assistant) -> Unit,
) {
    listOf(
        AppRoutes.SETTINGS_ASSISTANT_MEMORY to false,
        AppRoutes.SETTINGS_ASSISTANT_MEMORY_SIMPLE to true
    ).forEach { (route, simple) ->
        composable(
            route,
            arguments = listOf(navArgument("conversationId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) { entry ->
            val assistantId = Uri.decode(entry.arguments?.getString("assistantId").orEmpty())
            val conversationId = entry.arguments?.getString("conversationId")?.let(Uri::decode)
                ?.takeIf { it.isNotBlank() }
            val owner =
                remember(entry) { navController.getBackStackEntry(AppRoutes.SETTINGS_GRAPH) }
            val model: MemoryArchiveViewModel = viewModel(
                viewModelStoreOwner = owner,
                key = memoryArchiveViewModelKey(assistantId, conversationId),
                factory = archiveFactory(assistantId, conversationId, owner.savedStateHandle),
            )
            val state by model.uiState.collectAsStateWithLifecycle()
            val settings = settingsProvider()
            LaunchedEffect(entry) { model.onRouteEntered() }
            LaunchedEffect(simple) {
                if (simple && model.uiState.value.query.tab != MemoryArchiveTab.RECORDS) {
                    model.setQuery(
                        model.uiState.value.query.copy(
                            tab = MemoryArchiveTab.RECORDS,
                            category = null,
                            residentCategory = null,
                            stateKey = null
                        )
                    )
                }
            }
            val callbacks = remember(model, navController) {
                memoryArchiveCallbacks(model) { selectedConversation, persona ->
                    model.uiState.value.context?.let { context ->
                        navController.navigate(
                            AppRoutes.settingsMemoryOriginal(
                                assistantId,
                                context.spaceId,
                                selectedConversation,
                                persona
                            )
                        ) { launchSingleTop = true }
                    }
                }
            }
            MemoryArchiveScreen(
                state,
                settings.resolvedAssistants().find { it.id == assistantId },
                settings,
                callbacks,
                onUpdateAssistant = onUpdateAssistant,
                onOpenSettings = {
                    navController.navigate(AppRoutes.SETTINGS_MEMORY) {
                        launchSingleTop = true
                    }
                },
                onOpenTransfer = {
                    navController.navigate(AppRoutes.SETTINGS_CONTEXT_TRANSFER) {
                        launchSingleTop = true
                    }
                },
                onOpenOtherMode = {
                    val target = if (simple) AppRoutes.settingsAssistantMemory(
                        assistantId,
                        conversationId
                    ) else AppRoutes.settingsAssistantMemorySimple(assistantId, conversationId)
                    navController.navigate(target) { launchSingleTop = true }
                },
                onNavigateBack = { navController.popBackStack() },
                simple = simple
            )
        }
    }
    composable(AppRoutes.SETTINGS_MEMORY_ORIGINAL) { entry ->
        fun argument(name: String) = Uri.decode(entry.arguments?.getString(name).orEmpty())
        val characterId = argument("assistantId")
        val spaceId = argument("spaceId")
        val conversationId = argument("conversationId")
        val personaKey = argument("personaKey")
        val model: MemoryOriginalViewModel = viewModel(
            key = "memory-original:${Uri.encode(characterId)}:${Uri.encode(spaceId)}:${
                Uri.encode(
                    conversationId
                )
            }:${Uri.encode(personaKey)}",
            factory = originalFactory(characterId, spaceId, personaKey, conversationId),
        )
        val state by model.uiState.collectAsStateWithLifecycle()
        MemoryOriginalScreen(state, model::loadMore, model::reload) { navController.popBackStack() }
    }
}

internal fun memoryArchiveViewModelKey(characterId: String, conversationId: String?) =
    "memory-archive:${Uri.encode(characterId)}:${Uri.encode(conversationId.orEmpty())}"

internal fun memoryArchiveCallbacks(
    model: MemoryArchiveViewModel,
    onReadOriginal: (String, String) -> Unit
) = MemoryArchiveCallbacks(
    onSelectSpace = model::selectSpace,
    onSelectPersona = model::selectPersona,
    onSetPanel = model::setPanel,
    onSetQuery = model::setQuery,
    onReload = model::onRouteEntered,
    onLoadMore = model::loadMore,
    onOpenItem = model::openItem,
    onCloseDetail = model::closeDetail,
    onSelectRevision = model::selectRevision,
    onAdd = model::beginAdd,
    onCreateDailyArchive = model::createDailyArchive,
    onEdit = model::beginEdit,
    onUpdateEditor = model::updateEditor,
    onCloseEditor = model::closeEditor,
    onSaveEditor = model::saveEditor,
    onTogglePinned = model::togglePinned,
    onSetArchived = model::setArchived,
    onDelete = model::deleteSelected,
    onEndState = model::endState,
    onLinkRecords = model::linkRecords,
    onLoadLinkCandidates = model::loadLinkCandidates,
    onUnlinkMember = model::unlinkMember,
    onRebuildEvent = model::rebuildEvent,
    onRebuildIndex = model::rebuildIndex,
    onSetSpaceArchived = model::setSpaceArchived,
    onDeleteSpace = model::deleteSpace,
    onSelectConversation = model::selectConversation,
    onSelectMessage = model::selectMessage,
    onLoadOlderMessages = model::loadOlderMessages,
    onToggleSelection = model::toggleSelection,
    onInspectSelection = model::inspectSelection,
    onProcessSelection = model::processSelection,
    onResumeJob = model::resumeJob,
    onSetPaused = model::setPaused,
    onLoadMoreJobs = model::loadMoreJobs,
    onCancelWork = model::cancelWork,
    onConsumeMessage = model::consumeMessage,
    onReadOriginal = onReadOriginal,
)
