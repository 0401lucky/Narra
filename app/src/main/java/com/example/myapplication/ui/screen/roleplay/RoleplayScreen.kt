package com.example.myapplication.ui.screen.roleplay

import android.content.Intent
import android.net.Uri
import com.example.myapplication.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.InvitePlayDraft
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.PunishPlayDraft
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayGroupParticipant
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.TaskPlayDraft
import com.example.myapplication.model.TransferPlayDraft
import com.example.myapplication.model.VoiceMessageDraft
import com.example.myapplication.model.toChatMessagePart
import com.example.myapplication.system.performance.BenchmarkActionIds
import com.example.myapplication.system.performance.BenchmarkContract
import com.example.myapplication.system.performance.BenchmarkFixtureIds
import com.example.myapplication.system.performance.BenchmarkOfflineReplaySpec
import com.example.myapplication.system.performance.BenchmarkReadyIds
import com.example.myapplication.system.performance.BenchmarkScenarioId
import com.example.myapplication.ui.component.NarraAlertDialog
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.roleplay.RoleplayInputQuickAction
import com.example.myapplication.ui.component.rememberSystemHighTextContrastEnabled
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.component.specialplay.GiftPlayDraftSaver
import com.example.myapplication.ui.component.specialplay.InvitePlayDraftSaver
import com.example.myapplication.ui.component.specialplay.PunishPlayDraftSaver
import com.example.myapplication.ui.component.specialplay.TaskPlayDraftSaver
import com.example.myapplication.ui.component.specialplay.TransferPlayDraftSaver
import com.example.myapplication.ui.screen.chat.resolveSelectedAttachment
import com.example.myapplication.ui.performance.LocalBenchmarkRuntimePolicy
import com.example.myapplication.ui.performance.benchmarkReadyTag
import com.example.myapplication.viewmodel.RoleplayOfflineReplayPhase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleplayScreen(
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    settings: AppSettings,
    contextStatus: RoleplayContextStatus,
    groupParticipants: List<RoleplayGroupParticipant>,
    messages: List<RoleplayMessageUiModel>,
    suggestions: List<RoleplaySuggestionUiModel>,
    input: String,
    inputFocusToken: Long,
    replyToMessageId: String,
    replyToPreview: String,
    replyToSpeakerName: String,
    isSending: Boolean,
    isGeneratingSuggestions: Boolean,
    isScenarioLoading: Boolean,
    hasActiveSession: Boolean = false,
    isOfflineReplayFixtureSession: Boolean = false,
    offlineReplayPhase: RoleplayOfflineReplayPhase = RoleplayOfflineReplayPhase.DISABLED,
    showAssistantMismatchDialog: Boolean,
    previousAssistantName: String,
    currentAssistantName: String,
    noticeMessage: String?,
    errorMessage: String?,
    suggestionErrorMessage: String?,
    pendingMemoryProposal: PendingMemoryProposal?,
    callbacks: RoleplayScreenCallbacks,
) {
    // 解构 callbacks 为局部变量，保持内部代码不变
    val onClearNoticeMessage = callbacks.ui.onClearNoticeMessage
    val onClearErrorMessage = callbacks.ui.onClearErrorMessage
    val onInputChange = callbacks.message.onInputChange
    val onSend = callbacks.message.onSend
    val onRequestGroupParticipantReply = callbacks.message.onRequestGroupParticipantReply
    val onCancelSending = callbacks.message.onCancelSending
    val onRetryTurn = callbacks.message.onRetryTurn
    val onRetryAiPhoto = callbacks.message.onRetryAiPhoto
    val onEditUserMessage = callbacks.message.onEditUserMessage
    val onQuoteMessage = callbacks.message.onQuoteMessage
    val onPokeMessageAvatar = callbacks.message.onPokeMessageAvatar
    val onClearQuotedMessage = callbacks.message.onClearQuotedMessage
    val onDeleteMessage = callbacks.message.onDeleteMessage
    val onRecallMessage = callbacks.message.onRecallMessage
    val onCaptureOnlineChat = callbacks.message.onCaptureOnlineChat
    val onSendSpecialPlay = callbacks.message.onSendSpecialPlay
    val onSendVoiceMessage = callbacks.message.onSendVoiceMessage
    val onConfirmTransferReceipt = callbacks.message.onConfirmTransferReceipt
    val onGenerateSuggestions = callbacks.suggestion.onGenerateSuggestions
    val onApplySuggestion = callbacks.suggestion.onApplySuggestion
    val onClearSuggestions = callbacks.suggestion.onClearSuggestions
    val onOpenDiary = callbacks.navigation.onOpenDiary
    val onOpenPhoneCheck = callbacks.navigation.onOpenPhoneCheck
    val onOpenMoments = callbacks.navigation.onOpenMoments
    val onOpenVideoCall = callbacks.navigation.onOpenVideoCall
    val onOpenMailbox = callbacks.navigation.onOpenMailbox
    val onOpenWallet = callbacks.navigation.onOpenWallet
    val onOpenSettings = callbacks.navigation.onOpenSettings
    val onOpenVoiceSynthesisSettings = callbacks.navigation.onOpenVoiceSynthesisSettings
    val onNavigateBack = callbacks.navigation.onNavigateBack
    val onRestartSession = callbacks.session.onRestartSession
    val onDismissAssistantMismatch = callbacks.session.onDismissAssistantMismatch
    val onApprovePendingMemoryProposal = callbacks.session.onApprovePendingMemoryProposal
    val onRejectPendingMemoryProposal = callbacks.session.onRejectPendingMemoryProposal
    val onStartOfflineReplay = callbacks.session.onStartOfflineReplay

    val snackbarHostState = remember { SnackbarHostState() }
    val benchmarkRuntimePolicy = if (
        BuildConfig.APP_CHANNEL == BenchmarkContract.BASELINE_CHANNEL
    ) {
        LocalBenchmarkRuntimePolicy.current
    } else {
        null
    }
    val benchmarkScenarioId = benchmarkRuntimePolicy
        ?.takeIf { it.isBenchmarkSessionActive }
        ?.scenarioId
    val benchmarkReturningScenario = when (benchmarkScenarioId) {
        BenchmarkScenarioId.S1_RETURNING_CORE_STARTUP,
        BenchmarkScenarioId.S2_POPULATED_STARTUP,
        BenchmarkScenarioId.S3_UPGRADE_STARTUP,
        BenchmarkScenarioId.N1_HEAVY_NAVIGATION,
        BenchmarkScenarioId.P1_BASELINE_PROFILE,
        -> true

        else -> false
    }
    val benchmarkSourceMessageCount = if (benchmarkReturningScenario) {
        messages.asSequence()
            .map { message -> message.sourceMessageId }
            .filter(String::isNotBlank)
            .distinct()
            .count()
    } else {
        0
    }
    val benchmarkReturningReady = benchmarkReturningScenario &&
        scenario?.id == BenchmarkFixtureIds.ROLEPLAY_SCENARIO_ID &&
        assistant != null &&
        hasActiveSession &&
        groupParticipants.isNotEmpty() &&
        benchmarkSourceMessageCount == benchmarkScenarioId?.fixtureId?.roleplayMessageCount &&
        !isScenarioLoading
    val hasLoadedOfflineReplayFixture =
        isOfflineReplayFixtureSession &&
            scenario?.id == BenchmarkFixtureIds.ROLEPLAY_SCENARIO_ID &&
            assistant != null &&
            hasActiveSession &&
            groupParticipants.isNotEmpty() &&
            !isScenarioLoading
    val isOfflineReplayFixtureReady = remember(
        hasLoadedOfflineReplayFixture,
        isOfflineReplayFixtureSession,
        offlineReplayPhase,
        messages,
    ) {
        hasLoadedOfflineReplayFixture &&
            when (offlineReplayPhase) {
                RoleplayOfflineReplayPhase.READY,
                RoleplayOfflineReplayPhase.COMPLETED,
                -> hasExpectedOfflineReplayFixtureSources(messages)

                RoleplayOfflineReplayPhase.DISABLED,
                RoleplayOfflineReplayPhase.RUNNING,
                RoleplayOfflineReplayPhase.FAILED,
                -> false
            }
    }
    val benchmarkRoleplaySettingsVisited = if (
        benchmarkScenarioId == BenchmarkScenarioId.P1_BASELINE_PROFILE
    ) {
        rememberSaveable(
            benchmarkRuntimePolicy?.sessionId,
            scenario?.id,
        ) { mutableStateOf(false) }
    } else {
        null
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingImageParts by remember { mutableStateOf<List<ChatMessagePart>>(emptyList()) }
    var clearPendingImagesOnAcceptedSend by remember { mutableStateOf(false) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val attachment = runCatching {
            resolveSelectedAttachment(context, uri, AttachmentType.IMAGE)
        }.getOrElse {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("无法读取所选图片")
            }
            return@rememberLauncherForActivityResult
        }
        pendingImageParts = listOf(attachment.toChatMessagePart())
    }
    val pickRoleplayImage = {
        imagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
    val sendCurrentInput = {
        clearPendingImagesOnAcceptedSend = pendingImageParts.isNotEmpty()
        onSend(pendingImageParts)
    }

    LaunchedEffect(scenario?.id) {
        pendingImageParts = emptyList()
        clearPendingImagesOnAcceptedSend = false
    }

    LaunchedEffect(isSending, clearPendingImagesOnAcceptedSend) {
        if (clearPendingImagesOnAcceptedSend && isSending) {
            pendingImageParts = emptyList()
            clearPendingImagesOnAcceptedSend = false
        }
    }

    LaunchedEffect(noticeMessage) {
        noticeMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearNoticeMessage()
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearErrorMessage()
        }
    }

    if (scenario == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isScenarioLoading) stringResource(id = R.string.roleplay_scenario_loading)
                else stringResource(id = R.string.roleplay_scenario_missing)
            )
        }
        return
    }

    var pendingDeleteMessageId by remember(scenario.id) { mutableStateOf<String?>(null) }
    val onRequestDeleteMessage: ((String) -> Unit)? = if (isSending) {
        null
    } else {
        { sourceMessageId -> pendingDeleteMessageId = sourceMessageId }
    }

    val characterName = scenario.characterDisplayNameOverride.trim()
        .ifBlank { assistant?.name?.trim().orEmpty() }
        .ifBlank { stringResource(id = R.string.roleplay_character_fallback) }
    val effectiveHighContrast = settings.roleplayHighContrast || rememberSystemHighTextContrastEnabled()
    val backdropState = rememberImmersiveBackdropState(
        backgroundUri = scenario.backgroundUri,
        highContrast = effectiveHighContrast,
    )

    var showSpecialPlaySheet by rememberSaveable { mutableStateOf(false) }
    var showVoiceMessageSheet by rememberSaveable { mutableStateOf(false) }
    var showPhoneOwnerPicker by rememberSaveable { mutableStateOf(false) }
    var activeSpecialPlayTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceDraft by rememberSaveable(stateSaver = VoiceMessageDraftSaver) {
        mutableStateOf(VoiceMessageDraft())
    }
    var transferDraft by rememberSaveable(stateSaver = TransferPlayDraftSaver) {
        mutableStateOf(TransferPlayDraft(counterparty = characterName))
    }
    var inviteDraft by rememberSaveable(stateSaver = InvitePlayDraftSaver) {
        mutableStateOf(InvitePlayDraft(target = characterName))
    }
    var giftDraft by rememberSaveable(stateSaver = GiftPlayDraftSaver) {
        mutableStateOf(GiftPlayDraft(target = characterName))
    }
    var taskDraft by rememberSaveable(stateSaver = TaskPlayDraftSaver) {
        mutableStateOf(TaskPlayDraft())
    }
    var punishDraft by rememberSaveable(stateSaver = PunishPlayDraftSaver) {
        mutableStateOf(PunishPlayDraft())
    }

    val activeSpecialPlayType = activeSpecialPlayTypeName?.let(ChatSpecialType::valueOf)
    val activeSpecialPlayDraft = when (activeSpecialPlayType) {
        ChatSpecialType.TRANSFER -> transferDraft
        ChatSpecialType.INVITE -> inviteDraft
        ChatSpecialType.GIFT -> giftDraft
        ChatSpecialType.TASK -> taskDraft
        ChatSpecialType.PUNISH -> punishDraft
        null -> null
    }

    fun primeSpecialPlayDraft(type: ChatSpecialType) {
        when (type) {
            ChatSpecialType.TRANSFER -> {
                if (transferDraft.counterparty.isBlank()) {
                    transferDraft = transferDraft.copy(counterparty = characterName)
                }
            }

            ChatSpecialType.INVITE -> {
                if (inviteDraft.target.isBlank()) {
                    inviteDraft = inviteDraft.copy(target = characterName)
                }
            }

            ChatSpecialType.GIFT -> {
                if (giftDraft.target.isBlank()) {
                    giftDraft = giftDraft.copy(target = characterName)
                }
            }

            ChatSpecialType.TASK,
            ChatSpecialType.PUNISH,
            -> Unit
        }
    }

    fun resetSpecialPlayDraft(type: ChatSpecialType) {
        when (type) {
            ChatSpecialType.TRANSFER -> transferDraft = TransferPlayDraft(counterparty = characterName)
            ChatSpecialType.INVITE -> inviteDraft = InvitePlayDraft(target = characterName)
            ChatSpecialType.GIFT -> giftDraft = GiftPlayDraft(target = characterName)
            ChatSpecialType.TASK -> taskDraft = TaskPlayDraft()
            ChatSpecialType.PUNISH -> punishDraft = PunishPlayDraft()
        }
    }

    fun resetVoiceDraft() {
        voiceDraft = VoiceMessageDraft()
    }

    val offlineQuickActions = remember(
        onOpenDiary,
        onOpenMoments,
        onOpenVideoCall,
        onOpenMailbox,
        onOpenWallet,
    ) {
        buildList {
            add(
                RoleplayInputQuickAction(
                    label = "日记",
                    icon = Icons.Default.AutoStories,
                    accentColor = Color(0xFFC78A38),
                    onClick = onOpenDiary,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "语音",
                    icon = Icons.Default.KeyboardVoice,
                    accentColor = Color(0xFF5B91D7),
                    onClick = { showVoiceMessageSheet = true },
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "查手机",
                    icon = Icons.Default.Visibility,
                    accentColor = Color(0xFF5B91D7),
                    onClick = { showPhoneOwnerPicker = true },
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "转账",
                    icon = Icons.Default.Share,
                    accentColor = Color(0xFF07C160),
                    onClick = {
                        primeSpecialPlayDraft(ChatSpecialType.TRANSFER)
                        activeSpecialPlayTypeName = ChatSpecialType.TRANSFER.name
                    },
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "邀约",
                    icon = Icons.Default.Event,
                    accentColor = Color(0xFF5B91D7),
                    onClick = {
                        primeSpecialPlayDraft(ChatSpecialType.INVITE)
                        activeSpecialPlayTypeName = ChatSpecialType.INVITE.name
                    },
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "礼物",
                    icon = Icons.Default.CardGiftcard,
                    accentColor = Color(0xFFE77F93),
                    onClick = {
                        primeSpecialPlayDraft(ChatSpecialType.GIFT)
                        activeSpecialPlayTypeName = ChatSpecialType.GIFT.name
                    },
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "委托",
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    accentColor = Color(0xFFD78B31),
                    onClick = {
                        primeSpecialPlayDraft(ChatSpecialType.TASK)
                        activeSpecialPlayTypeName = ChatSpecialType.TASK.name
                    },
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "惩罚",
                    icon = Icons.Default.Gavel,
                    accentColor = Color(0xFFD55C73),
                    onClick = {
                        primeSpecialPlayDraft(ChatSpecialType.PUNISH)
                        activeSpecialPlayTypeName = ChatSpecialType.PUNISH.name
                    },
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "动态",
                    icon = Icons.Default.Forum,
                    accentColor = Color(0xFF7C93F6),
                    onClick = onOpenMoments,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "信箱",
                    icon = Icons.Default.Mail,
                    accentColor = Color(0xFF2E8068),
                    onClick = onOpenMailbox,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "钱包",
                    icon = Icons.Default.AccountBalanceWallet,
                    accentColor = Color(0xFF2E8068),
                    onClick = onOpenWallet,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "视频",
                    icon = Icons.Default.Videocam,
                    accentColor = Color(0xFF6C84FF),
                    onClick = onOpenVideoCall,
                ),
            )
        }
    }

    if (scenario.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
        RoleplayOnlinePhoneContent(
            scenario = scenario,
            assistant = assistant,
            settings = settings,
            backdropState = backdropState,
            contextStatus = contextStatus,
            groupParticipants = groupParticipants,
            messages = messages,
            suggestions = suggestions,
            input = input,
            inputFocusToken = inputFocusToken,
            replyToMessageId = replyToMessageId,
            replyToPreview = replyToPreview,
            replyToSpeakerName = replyToSpeakerName,
            isSending = isSending,
            isGeneratingSuggestions = isGeneratingSuggestions,
            suggestionErrorMessage = suggestionErrorMessage,
            pendingMemoryProposal = pendingMemoryProposal,
            snackbarHostState = snackbarHostState,
            onInputChange = onInputChange,
            onGenerateSuggestions = onGenerateSuggestions,
            onApplySuggestion = onApplySuggestion,
            onClearSuggestions = onClearSuggestions,
            onRetryTurn = onRetryTurn,
            onRetryAiPhoto = onRetryAiPhoto,
            onEditUserMessage = onEditUserMessage,
            onQuoteMessage = onQuoteMessage,
            onPokeMessageAvatar = onPokeMessageAvatar,
            onClearQuotedMessage = onClearQuotedMessage,
            onRecallMessage = onRecallMessage,
            onDeleteMessage = onRequestDeleteMessage,
            onScreenshotChat = onCaptureOnlineChat,
            pendingImageParts = pendingImageParts,
            onPickImage = pickRoleplayImage,
            onRemovePendingImagePart = { part ->
                pendingImageParts = pendingImageParts.filterNot { it == part }
                clearPendingImagesOnAcceptedSend = false
            },
            onOpenDiary = onOpenDiary,
            onOpenVoiceMessage = { showVoiceMessageSheet = true },
            onOpenTransferPlay = {
                primeSpecialPlayDraft(ChatSpecialType.TRANSFER)
                activeSpecialPlayTypeName = ChatSpecialType.TRANSFER.name
            },
            onOpenInvitePlay = {
                primeSpecialPlayDraft(ChatSpecialType.INVITE)
                activeSpecialPlayTypeName = ChatSpecialType.INVITE.name
            },
            onOpenGiftPlay = {
                primeSpecialPlayDraft(ChatSpecialType.GIFT)
                activeSpecialPlayTypeName = ChatSpecialType.GIFT.name
            },
            onOpenTaskPlay = {
                primeSpecialPlayDraft(ChatSpecialType.TASK)
                activeSpecialPlayTypeName = ChatSpecialType.TASK.name
            },
            onOpenPunishPlay = {
                primeSpecialPlayDraft(ChatSpecialType.PUNISH)
                activeSpecialPlayTypeName = ChatSpecialType.PUNISH.name
            },
            onConfirmTransferReceipt = onConfirmTransferReceipt,
            onSend = sendCurrentInput,
            onRequestGroupParticipantReply = onRequestGroupParticipantReply,
            onCancelSending = onCancelSending,
            onApprovePendingMemoryProposal = onApprovePendingMemoryProposal,
            onRejectPendingMemoryProposal = onRejectPendingMemoryProposal,
            onOpenPhoneCheck = { showPhoneOwnerPicker = true },
            onOpenMoments = onOpenMoments,
            onOpenVideoCall = onOpenVideoCall,
            onOpenMailbox = onOpenMailbox,
            onOpenWallet = onOpenWallet,
            onOpenSettings = onOpenSettings,
            onNavigateBack = onNavigateBack,
        )
    } else {
        val benchmarkBackActionId = if (
            benchmarkScenarioId == BenchmarkScenarioId.P1_BASELINE_PROFILE
        ) {
            BenchmarkActionIds.OPEN_CHAT_HISTORY
        } else {
            null
        }
        val benchmarkSettingsActionId = when {
            benchmarkScenarioId == BenchmarkScenarioId.N1_HEAVY_NAVIGATION -> {
                BenchmarkActionIds.OPEN_SETTINGS
            }

            benchmarkScenarioId == BenchmarkScenarioId.P1_BASELINE_PROFILE &&
                benchmarkRoleplaySettingsVisited?.value != true -> {
                BenchmarkActionIds.OPEN_SETTINGS
            }

            benchmarkScenarioId == BenchmarkScenarioId.P1_BASELINE_PROFILE -> {
                BenchmarkActionIds.OPEN_VOICE_SYNTHESIS
            }

            else -> null
        }
        RoleplaySceneContent(
            modifier = Modifier.benchmarkReadyTag(
                readyId = BenchmarkReadyIds.RETURNING_ROLEPLAY_READY,
                ready = benchmarkReturningReady,
            ),
            scenario = scenario,
            assistant = assistant,
            settings = settings,
            messages = messages,
            suggestions = suggestions,
            input = input,
            inputFocusToken = inputFocusToken,
            isSending = isSending,
            isGeneratingSuggestions = isGeneratingSuggestions,
            suggestionErrorMessage = suggestionErrorMessage,
            pendingMemoryProposal = pendingMemoryProposal,
            snackbarHostState = snackbarHostState,
            backdropState = backdropState,
            onInputChange = onInputChange,
            onGenerateSuggestions = onGenerateSuggestions,
            onApplySuggestion = onApplySuggestion,
            onClearSuggestions = onClearSuggestions,
            onRetryTurn = onRetryTurn,
            onRetryAiPhoto = onRetryAiPhoto,
            onEditUserMessage = onEditUserMessage,
            onDeleteMessage = onRequestDeleteMessage,
            onOpenSpecialPlay = { showSpecialPlaySheet = true },
            quickActions = offlineQuickActions,
            pendingImageParts = pendingImageParts,
            onPickImage = pickRoleplayImage,
            onRemovePendingImagePart = { part ->
                pendingImageParts = pendingImageParts.filterNot { it == part }
                clearPendingImagesOnAcceptedSend = false
            },
            onOpenPhoneCheck = { showPhoneOwnerPicker = true },
            onOpenWallet = onOpenWallet,
            onConfirmTransferReceipt = onConfirmTransferReceipt,
            onSend = sendCurrentInput,
            onCancelSending = onCancelSending,
            offlineReplayPhase = offlineReplayPhase,
            isOfflineReplayFixtureReady = isOfflineReplayFixtureReady,
            onStartOfflineReplay = onStartOfflineReplay,
            onApprovePendingMemoryProposal = onApprovePendingMemoryProposal,
            onRejectPendingMemoryProposal = onRejectPendingMemoryProposal,
            onOpenSettings = {
                if (
                    benchmarkScenarioId == BenchmarkScenarioId.P1_BASELINE_PROFILE &&
                    benchmarkRoleplaySettingsVisited?.value == true
                ) {
                    onOpenVoiceSynthesisSettings()
                } else {
                    if (benchmarkScenarioId == BenchmarkScenarioId.P1_BASELINE_PROFILE) {
                        benchmarkRoleplaySettingsVisited?.value = true
                    }
                    onOpenSettings()
                }
            },
            onNavigateBack = onNavigateBack,
            benchmarkBackActionId = benchmarkBackActionId,
            benchmarkSettingsActionId = benchmarkSettingsActionId,
            showSpecialPlaySheet = showSpecialPlaySheet,
            activeSpecialPlayDraft = activeSpecialPlayDraft,
            onDismissSpecialPlay = { showSpecialPlaySheet = false },
            onOpenSpecialPlayEditor = { type ->
                showSpecialPlaySheet = false
                primeSpecialPlayDraft(type)
                activeSpecialPlayTypeName = type.name
            },
            onDismissSpecialPlayEditor = { activeSpecialPlayTypeName = null },
            onSpecialPlayDraftChange = { draft ->
                when (draft) {
                    is TransferPlayDraft -> transferDraft = draft
                    is InvitePlayDraft -> inviteDraft = draft
                    is GiftPlayDraft -> giftDraft = draft
                    is TaskPlayDraft -> taskDraft = draft
                    is PunishPlayDraft -> punishDraft = draft
                }
            },
            onSpecialPlayConfirm = {
                val activeDraft = activeSpecialPlayDraft
                if (activeDraft != null) {
                    onSendSpecialPlay(activeDraft)
                    resetSpecialPlayDraft(activeDraft.type)
                    activeSpecialPlayTypeName = null
                }
            },
        )
    }

    RoleplayAssistantMismatchDialog(
        showAssistantMismatchDialog = showAssistantMismatchDialog,
        previousAssistantName = previousAssistantName,
        currentAssistantName = currentAssistantName,
        onRestartSession = onRestartSession,
        onDismissAssistantMismatch = onDismissAssistantMismatch,
    )

    RoleplayMessageDeleteConfirmationDialog(
        sourceMessageId = pendingDeleteMessageId,
        messages = messages,
        isSending = isSending,
        onDismiss = { pendingDeleteMessageId = null },
        onConfirm = { confirmedSourceMessageId ->
            pendingDeleteMessageId = null
            onDeleteMessage(confirmedSourceMessageId)
        },
    )

    if (showPhoneOwnerPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPhoneOwnerPicker = false },
            title = { Text(stringResource(R.string.roleplay_phone_owner_title)) },
            text = { Text(stringResource(R.string.roleplay_phone_owner_body)) },
            confirmButton = {
                NarraTextButton(onClick = {
                    showPhoneOwnerPicker = false
                    onOpenPhoneCheck(PhoneSnapshotOwnerType.CHARACTER)
                }) {
                    Text(stringResource(R.string.roleplay_phone_owner_character))
                }
            },
            dismissButton = {
                Row {
                    NarraTextButton(onClick = {
                        showPhoneOwnerPicker = false
                        onOpenPhoneCheck(PhoneSnapshotOwnerType.USER)
                    }) {
                        Text(stringResource(R.string.roleplay_phone_owner_self))
                    }
                    NarraTextButton(onClick = { showPhoneOwnerPicker = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            },
        )
    }

    if (scenario.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
        RoleplaySpecialPlayOverlays(
            showSpecialPlaySheet = showSpecialPlaySheet,
            activeSpecialPlayDraft = activeSpecialPlayDraft,
            onDismissSpecialPlay = { showSpecialPlaySheet = false },
            onOpenSpecialPlayEditor = { type ->
                showSpecialPlaySheet = false
                primeSpecialPlayDraft(type)
                activeSpecialPlayTypeName = type.name
            },
            onOpenPhoneCheck = { showPhoneOwnerPicker = true },
            onOpenVideoCall = onOpenVideoCall,
            onOpenMoments = onOpenMoments,
            onOpenWallet = onOpenWallet,
            onDismissSpecialPlayEditor = { activeSpecialPlayTypeName = null },
            onSpecialPlayDraftChange = { draft ->
                when (draft) {
                    is TransferPlayDraft -> transferDraft = draft
                    is InvitePlayDraft -> inviteDraft = draft
                    is GiftPlayDraft -> giftDraft = draft
                    is TaskPlayDraft -> taskDraft = draft
                    is PunishPlayDraft -> punishDraft = draft
                }
            },
            onSpecialPlayConfirm = {
                val activeDraft = activeSpecialPlayDraft
                if (activeDraft != null) {
                    onSendSpecialPlay(activeDraft)
                    resetSpecialPlayDraft(activeDraft.type)
                    activeSpecialPlayTypeName = null
                }
            },
        )
    }

    if (showVoiceMessageSheet) {
        VoiceMessageEditorSheet(
            draft = voiceDraft,
            isSending = isSending,
            onDraftChange = { voiceDraft = it },
            onDismissRequest = {
                showVoiceMessageSheet = false
                resetVoiceDraft()
            },
            onConfirm = {
                onSendVoiceMessage(voiceDraft)
                showVoiceMessageSheet = false
                resetVoiceDraft()
            },
        )
    }
}

private fun hasExpectedOfflineReplayFixtureSources(
    messages: List<RoleplayMessageUiModel>,
): Boolean {
    val replaySpec = BenchmarkOfflineReplaySpec.DEFAULT
    val streamingMessageCount = messages.count { message -> message.isStreaming }
    val persistedMessageCount = replaySpec.persistedMessageCount(
        totalMessageCount = messages.size,
        streamingMessageCount = streamingMessageCount,
    )
    if (!replaySpec.hasExpectedPersistedMessageCount(persistedMessageCount)) {
        return false
    }
    val persistedSourceIds = messages.asSequence()
        .filterNot { message -> message.isStreaming }
        .map { message -> message.sourceMessageId }
        .distinct()
        .toList()
    return persistedSourceIds.size == persistedMessageCount &&
        persistedSourceIds.all(BenchmarkFixtureIds::isRoleplayMessageId)
}

@Composable
internal fun RoleplayMessageDeleteConfirmationDialog(
    sourceMessageId: String?,
    messages: List<RoleplayMessageUiModel>,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val targetId = sourceMessageId?.takeIf { it.isNotBlank() } ?: return
    val targetMessages = messages.filter { it.sourceMessageId == targetId }
    if (targetMessages.isEmpty()) {
        return
    }
    NarraAlertDialog(
        title = "确认删除消息",
        message = "删除后无法恢复；同一条底层消息产生的相关内容会一并移除。",
        confirmLabel = "确认删除",
        isDestructive = true,
        confirmEnabled = !isSending && targetMessages.none {
            it.isStreaming || it.messageStatus == MessageStatus.LOADING
        },
        onDismiss = onDismiss,
        onConfirm = { onConfirm(targetId) },
    )
}
