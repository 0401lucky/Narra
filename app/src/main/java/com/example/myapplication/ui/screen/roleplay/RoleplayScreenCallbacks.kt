package com.example.myapplication.ui.screen.roleplay

import com.example.myapplication.model.ChatSpecialPlayDraft
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.RoleplayGroupReplyMode
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.VoiceMessageDraft

/**
 * RoleplayScreen 的所有用户交互回调，按职责分组。
 * 参照 ChatScreenCallbacks 的模式，将 48 个参数中的 27 个回调参数归类。
 */
data class RoleplayScreenCallbacks(
    val message: RoleplayMessageCallbacks,
    val suggestion: RoleplaySuggestionCallbacks,
    val navigation: RoleplayNavigationCallbacks,
    val session: RoleplaySessionCallbacks,
    val group: RoleplayGroupCallbacks,
    val ui: RoleplayUiCallbacks,
)

data class RoleplayMessageCallbacks(
    val onInputChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onCancelSending: () -> Unit,
    val onRetryTurn: (String) -> Unit,
    val onEditUserMessage: (String) -> Unit,
    val onQuoteMessage: (messageId: String, speakerName: String, preview: String) -> Unit,
    val onPokeMessageAvatar: (RoleplayMessageUiModel) -> Unit,
    val onClearQuotedMessage: () -> Unit,
    val onRecallMessage: (String) -> Unit,
    val onCaptureOnlineChat: () -> Unit,
    val onSendSpecialPlay: (ChatSpecialPlayDraft) -> Unit,
    val onSendVoiceMessage: (VoiceMessageDraft) -> Unit,
    val onConfirmTransferReceipt: (String) -> Unit,
)

data class RoleplaySuggestionCallbacks(
    val onGenerateSuggestions: () -> Unit,
    val onApplySuggestion: (String) -> Unit,
    val onClearSuggestions: () -> Unit,
)

data class RoleplayNavigationCallbacks(
    val onOpenDiary: () -> Unit,
    val onOpenPhoneCheck: (PhoneSnapshotOwnerType) -> Unit,
    val onOpenMoments: () -> Unit,
    val onOpenVideoCall: () -> Unit,
    val onOpenMailbox: () -> Unit,
    val onOpenReadingMode: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onNavigateBack: () -> Unit,
)

data class RoleplaySessionCallbacks(
    val onRestartSession: () -> Unit,
    val onDismissAssistantMismatch: (keepCurrent: Boolean) -> Unit,
    val onApprovePendingMemoryProposal: () -> Unit,
    val onRejectPendingMemoryProposal: () -> Unit,
)

data class RoleplayGroupCallbacks(
    val onToggleParticipantMuted: (String) -> Unit,
    val onRemoveParticipant: (String) -> Unit,
    val onUpdateReplyMode: (RoleplayGroupReplyMode) -> Unit,
)

data class RoleplayUiCallbacks(
    val onClearNoticeMessage: () -> Unit,
    val onClearErrorMessage: () -> Unit,
)
