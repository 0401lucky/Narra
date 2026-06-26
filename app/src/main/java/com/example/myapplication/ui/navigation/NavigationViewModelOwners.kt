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
import com.example.myapplication.data.repository.economy.formatEconomyEventNote
import com.example.myapplication.di.AppGraph
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.viewmodel.ContextTransferViewModel
import com.example.myapplication.viewmodel.MemoryManagementViewModel
import com.example.myapplication.viewmodel.MailboxViewModel
import com.example.myapplication.viewmodel.MomentsViewModel
import com.example.myapplication.viewmodel.PhoneCheckViewModel
import com.example.myapplication.viewmodel.RoleplayViewModel
import com.example.myapplication.viewmodel.WorldBookViewModel
import kotlinx.coroutines.flow.first

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
            roleplayScriptRepository = appGraph.roleplayScriptRepository,
            roleplayScriptEngine = appGraph.roleplayScriptEngine,
            promptContextAssembler = appGraph.promptContextAssembler,
            memoryRepository = appGraph.memoryRepository,
            conversationSummaryRepository = appGraph.conversationSummaryRepository,
            pendingMemoryProposalRepository = appGraph.pendingMemoryProposalRepository,
            phoneSnapshotRepository = appGraph.phoneSnapshotRepository,
            buildEconomyPromptContext = { scenarioId ->
                val eventNote = formatEconomyEventNote(appGraph.roleplayEconomyEventBus.consume(scenarioId))
                val baseContext = appGraph.roleplayEconomyRepository.buildPromptContext(scenarioId)
                listOf(eventNote, baseContext).filter(String::isNotBlank).joinToString("\n\n")
            },
            memoryWriteService = appGraph.memoryWriteService,
            contextLogStore = appGraph.contextLogStore,
            imageSaver = { b64Data ->
                ImageFileStorage.saveBase64Image(context, b64Data)
            },
            namedImageSaver = { b64Data, fileNamePrefix ->
                ImageFileStorage.saveBase64Image(
                    context = context,
                    b64Data = b64Data,
                    fileNamePrefix = fileNamePrefix,
                )
            },
            voiceSynthesisCoordinator = appGraph.voiceSynthesisCoordinator,
            holdUserTransfer = { scenarioId, referenceId, amountCents, note ->
                val session = appGraph.roleplayRepository.getSessionByScenario(scenarioId)
                val scenario = appGraph.roleplayRepository.getScenario(scenarioId)
                val settings = appGraph.aiSettingsRepository.settingsFlow.first()
                val assistant = scenario?.assistantId?.let { assistantId ->
                    settings.resolvedAssistants().firstOrNull { it.id == assistantId }
                } ?: settings.activeAssistant()
                val userPersona = scenario?.let {
                    com.example.myapplication.roleplay.RoleplayConversationSupport.resolveUserPersona(it, settings)
                }
                val characterName = scenario?.characterDisplayNameOverride.orEmpty().trim()
                    .ifBlank { assistant?.name?.trim().orEmpty() }
                    .ifBlank { "角色" }
                appGraph.roleplayEconomyRepository.ensureDefaultAccounts(
                    scenarioId = scenarioId,
                    conversationId = session?.conversationId.orEmpty(),
                    userName = userPersona?.displayName?.ifBlank { "我" } ?: "我",
                    characterName = characterName,
                    characterInitialBalanceCents = assistant?.initialWalletBalanceCents ?: 0L,
                )
                when (
                    val result = appGraph.roleplayEconomyRepository.startTransferHold(
                        scenarioId = scenarioId,
                        fromOwnerType = com.example.myapplication.model.EconomyOwnerType.USER,
                        fromOwnerId = com.example.myapplication.data.repository.economy.DEFAULT_USER_OWNER_ID,
                        toOwnerType = com.example.myapplication.model.EconomyOwnerType.CHARACTER,
                        toOwnerId = com.example.myapplication.data.repository.economy.DEFAULT_CHARACTER_OWNER_ID,
                        amountCents = amountCents,
                        referenceId = referenceId,
                        note = note.ifBlank { "转账" },
                    )
                ) {
                    is com.example.myapplication.model.EconomyOperationResult.Success -> {
                        com.example.myapplication.model.EconomyOperationResult.Success(Unit)
                    }
                    is com.example.myapplication.model.EconomyOperationResult.Failure -> {
                        result
                    }
                }
            },
            settleTransfer = { referenceId ->
                appGraph.roleplayEconomyRepository.settleTransfer(referenceId)
            },
            releaseTransfer = { referenceId ->
                appGraph.roleplayEconomyRepository.releaseTransfer(referenceId)
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
            presetRepository = appGraph.presetRepository,
            importedAssistantAvatarSaver = { importedAvatar ->
                ImageFileStorage.saveImageBytes(
                    context = context,
                    bytes = importedAvatar.bytes,
                    fileNamePrefix = "assistant-avatar-${importedAvatar.assistantId}",
                ).path
            },
            dataImportTransaction = appGraph::runDatabaseTransaction,
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
    val conversationId = Uri.decode(rawConversationId)
    val scenarioId = Uri.decode(rawScenarioId)
    return viewModel(
        key = "moments-${scenarioId.ifBlank { "global" }}-${conversationId.ifBlank { "default" }}",
        factory = MomentsViewModel.factory(
            scenarioId = scenarioId,
            settingsRepository = appGraph.aiSettingsRepository,
            settingsStore = appGraph.settingsStore,
            momentsRepository = appGraph.momentsRepository,
            momentsGenerationCoordinator = appGraph.momentsGenerationCoordinator,
            roleplayRepository = appGraph.roleplayRepository,
        ),
    )
}

@Composable
internal fun rememberMailboxViewModel(
    appGraph: AppGraph,
    backStackEntry: NavBackStackEntry,
): MailboxViewModel {
    val rawScenarioId = backStackEntry.arguments?.getString("scenarioId").orEmpty()
    val scenarioId = Uri.decode(rawScenarioId)
    return viewModel(
        key = "mailbox-$scenarioId",
        factory = MailboxViewModel.factory(
            scenarioId = scenarioId,
            settingsRepository = appGraph.aiSettingsRepository,
            conversationRepository = appGraph.conversationRepository,
            roleplayRepository = appGraph.roleplayRepository,
            phoneSnapshotRepository = appGraph.phoneSnapshotRepository,
            mailboxRepository = appGraph.mailboxRepository,
            mailboxPromptService = appGraph.mailboxPromptService,
            pendingMemoryProposalRepository = appGraph.pendingMemoryProposalRepository,
            memoryWriteService = appGraph.memoryWriteService,
        ),
    )
}
