package com.example.myapplication.ui.screen.roleplay

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatSpecialPlayDraft
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.InvitePlayDraft
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.PunishPlayDraft
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.TaskPlayDraft
import com.example.myapplication.model.TransferPlayDraft
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.rememberSystemHighTextContrastEnabled
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.screen.chat.GiftPlayDraftSaver
import com.example.myapplication.ui.screen.chat.InvitePlayDraftSaver
import com.example.myapplication.ui.screen.chat.PunishPlayDraftSaver
import com.example.myapplication.ui.screen.chat.TaskPlayDraftSaver
import com.example.myapplication.ui.screen.chat.TransferPlayDraftSaver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleplayScreen(
    scenario: RoleplayScenario?,
    assistant: Assistant?,
    settings: AppSettings,
    contextStatus: RoleplayContextStatus,
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
    showAssistantMismatchDialog: Boolean,
    previousAssistantName: String,
    currentAssistantName: String,
    noticeMessage: String?,
    errorMessage: String?,
    suggestionErrorMessage: String?,
    pendingMemoryProposal: PendingMemoryProposal?,
    onClearNoticeMessage: () -> Unit,
    onClearErrorMessage: () -> Unit,
    onInputChange: (String) -> Unit,
    onGenerateSuggestions: () -> Unit,
    onApplySuggestion: (String) -> Unit,
    onClearSuggestions: () -> Unit,
    onRetryTurn: (String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    onQuoteMessage: (String, String, String) -> Unit,
    onClearQuotedMessage: () -> Unit,
    onRecallMessage: (String) -> Unit,
    onCaptureOnlineChat: () -> Unit,
    onSendSpecialPlay: (ChatSpecialPlayDraft) -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
    onSend: () -> Unit,
    onCancelSending: () -> Unit,
    onApprovePendingMemoryProposal: () -> Unit,
    onRejectPendingMemoryProposal: () -> Unit,
    onRestartSession: () -> Unit,
    onDismissAssistantMismatch: (Boolean) -> Unit,
    onOpenPhoneCheck: (PhoneSnapshotOwnerType) -> Unit,
    onOpenMoments: () -> Unit,
    onOpenVideoCall: () -> Unit,
    onOpenReadingMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

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
            Text(if (isScenarioLoading) "正在载入场景…" else "场景不存在或已被删除")
        }
        return
    }

    val characterName = scenario.characterDisplayNameOverride.trim()
        .ifBlank { assistant?.name?.trim().orEmpty() }
        .ifBlank { "角色" }
    val effectiveHighContrast = settings.roleplayHighContrast || rememberSystemHighTextContrastEnabled()
    val backdropState = rememberImmersiveBackdropState(
        backgroundUri = scenario.backgroundUri,
        highContrast = effectiveHighContrast,
    )

    var showSpecialPlaySheet by rememberSaveable { mutableStateOf(false) }
    var showPhoneOwnerPicker by rememberSaveable { mutableStateOf(false) }
    var activeSpecialPlayTypeName by rememberSaveable { mutableStateOf<String?>(null) }
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

    if (scenario.interactionMode == RoleplayInteractionMode.ONLINE_PHONE) {
        RoleplayOnlinePhoneContent(
            scenario = scenario,
            assistant = assistant,
            settings = settings,
            backdropState = backdropState,
            contextStatus = contextStatus,
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
            onEditUserMessage = onEditUserMessage,
            onQuoteMessage = onQuoteMessage,
            onClearQuotedMessage = onClearQuotedMessage,
            onRecallMessage = onRecallMessage,
            onScreenshotChat = onCaptureOnlineChat,
            onOpenSpecialPlay = { showSpecialPlaySheet = true },
            onConfirmTransferReceipt = onConfirmTransferReceipt,
            onSend = onSend,
            onCancelSending = onCancelSending,
            onApprovePendingMemoryProposal = onApprovePendingMemoryProposal,
            onRejectPendingMemoryProposal = onRejectPendingMemoryProposal,
            onOpenPhoneCheck = { showPhoneOwnerPicker = true },
            onOpenMoments = onOpenMoments,
            onOpenVideoCall = onOpenVideoCall,
            onOpenSettings = onOpenSettings,
            onNavigateBack = onNavigateBack,
        )
    } else {
        RoleplaySceneContent(
            scenario = scenario,
            assistant = assistant,
            settings = settings,
            contextStatus = contextStatus,
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
            onEditUserMessage = onEditUserMessage,
            onOpenSpecialPlay = { showSpecialPlaySheet = true },
            onOpenPhoneCheck = { showPhoneOwnerPicker = true },
            onConfirmTransferReceipt = onConfirmTransferReceipt,
            onSend = onSend,
            onCancelSending = onCancelSending,
            onApprovePendingMemoryProposal = onApprovePendingMemoryProposal,
            onRejectPendingMemoryProposal = onRejectPendingMemoryProposal,
            onOpenSettings = onOpenSettings,
            onNavigateBack = onNavigateBack,
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

    if (showPhoneOwnerPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPhoneOwnerPicker = false },
            title = { Text("选择查看对象") },
            text = { Text("这次你想看谁的手机？") },
            confirmButton = {
                NarraTextButton(onClick = {
                    showPhoneOwnerPicker = false
                    onOpenPhoneCheck(PhoneSnapshotOwnerType.CHARACTER)
                }) {
                    Text("TA的手机")
                }
            },
            dismissButton = {
                Row {
                    NarraTextButton(onClick = {
                        showPhoneOwnerPicker = false
                        onOpenPhoneCheck(PhoneSnapshotOwnerType.USER)
                    }) {
                        Text("我的手机")
                    }
                    NarraTextButton(onClick = { showPhoneOwnerPicker = false }) {
                        Text("取消")
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
}
