package com.example.myapplication.ui.screen.roleplay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.KeyboardVoice
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
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
import com.example.myapplication.model.VoiceMessageDraft
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.roleplay.RoleplayInputQuickAction
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
    callbacks: RoleplayScreenCallbacks,
) {
    // 解构 callbacks 为局部变量，保持内部代码不变
    val onClearNoticeMessage = callbacks.ui.onClearNoticeMessage
    val onClearErrorMessage = callbacks.ui.onClearErrorMessage
    val onInputChange = callbacks.message.onInputChange
    val onSend = callbacks.message.onSend
    val onCancelSending = callbacks.message.onCancelSending
    val onRetryTurn = callbacks.message.onRetryTurn
    val onEditUserMessage = callbacks.message.onEditUserMessage
    val onQuoteMessage = callbacks.message.onQuoteMessage
    val onClearQuotedMessage = callbacks.message.onClearQuotedMessage
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
    val onOpenSettings = callbacks.navigation.onOpenSettings
    val onNavigateBack = callbacks.navigation.onNavigateBack
    val onRestartSession = callbacks.session.onRestartSession
    val onDismissAssistantMismatch = callbacks.session.onDismissAssistantMismatch
    val onApprovePendingMemoryProposal = callbacks.session.onApprovePendingMemoryProposal
    val onRejectPendingMemoryProposal = callbacks.session.onRejectPendingMemoryProposal

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
            Text(
                if (isScenarioLoading) stringResource(id = R.string.roleplay_scenario_loading)
                else stringResource(id = R.string.roleplay_scenario_missing)
            )
        }
        return
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
            quickActions = offlineQuickActions,
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
