package com.example.myapplication.ui.screen.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.myapplication.model.ChatSpecialPlayDraft
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.InvitePlayDraft
import com.example.myapplication.model.PunishPlayDraft
import com.example.myapplication.model.TaskPlayDraft
import com.example.myapplication.model.TransferPlayDraft

@Immutable
internal data class ChatScreenLocalState(
    val showModelSheet: Boolean,
    val setShowModelSheet: (Boolean) -> Unit,
    val showReasoningSheet: Boolean,
    val setShowReasoningSheet: (Boolean) -> Unit,
    val showSearchPickerSheet: Boolean,
    val setShowSearchPickerSheet: (Boolean) -> Unit,
    val showProfileSheet: Boolean,
    val setShowProfileSheet: (Boolean) -> Unit,
    val showExportSheet: Boolean,
    val setShowExportSheet: (Boolean) -> Unit,
    val showSpecialPlaySheet: Boolean,
    val setShowSpecialPlaySheet: (Boolean) -> Unit,
    val activeSpecialPlayType: ChatSpecialType?,
    val setActiveSpecialPlayType: (ChatSpecialType?) -> Unit,
    val transferDraft: TransferPlayDraft,
    val setTransferDraft: (TransferPlayDraft) -> Unit,
    val inviteDraft: InvitePlayDraft,
    val setInviteDraft: (InvitePlayDraft) -> Unit,
    val giftDraft: GiftPlayDraft,
    val setGiftDraft: (GiftPlayDraft) -> Unit,
    val taskDraft: TaskPlayDraft,
    val setTaskDraft: (TaskPlayDraft) -> Unit,
    val punishDraft: PunishPlayDraft,
    val setPunishDraft: (PunishPlayDraft) -> Unit,
    val exportOptions: ConversationExportOptions,
    val setExportOptions: (ConversationExportOptions) -> Unit,
    val draftUserDisplayName: String,
    val setDraftUserDisplayName: (String) -> Unit,
    val draftUserPersonaPrompt: String,
    val setDraftUserPersonaPrompt: (String) -> Unit,
    val draftUserAvatarUri: String,
    val setDraftUserAvatarUri: (String) -> Unit,
    val draftUserAvatarUrl: String,
    val setDraftUserAvatarUrl: (String) -> Unit,
    val activeMessageActionId: String?,
    val setActiveMessageActionId: (String?) -> Unit,
    val messageSelectionPayload: ChatMessageSelectionPayload?,
    val setMessageSelectionPayload: (ChatMessageSelectionPayload?) -> Unit,
    val messagePreviewPayload: ChatMessagePreviewPayload?,
    val setMessagePreviewPayload: (ChatMessagePreviewPayload?) -> Unit,
    val imagePreviewPayload: ChatImagePreviewPayload?,
    val setImagePreviewPayload: (ChatImagePreviewPayload?) -> Unit,
    val searchResultPreviewPayload: ChatSearchResultPreviewPayload?,
    val setSearchResultPreviewPayload: (ChatSearchResultPreviewPayload?) -> Unit,
    val pendingMessageExport: ChatMessageExportPayload?,
    val setPendingMessageExport: (ChatMessageExportPayload?) -> Unit,
    val openProfileSheet: () -> Unit,
) {
    val activeSpecialPlayDraft: ChatSpecialPlayDraft?
        get() = when (activeSpecialPlayType) {
            ChatSpecialType.TRANSFER -> transferDraft
            ChatSpecialType.INVITE -> inviteDraft
            ChatSpecialType.GIFT -> giftDraft
            ChatSpecialType.TASK -> taskDraft
            ChatSpecialType.PUNISH -> punishDraft
            null -> null
        }

    fun updateActiveSpecialPlayDraft(draft: ChatSpecialPlayDraft) {
        when (draft) {
            is TransferPlayDraft -> setTransferDraft(draft)
            is InvitePlayDraft -> setInviteDraft(draft)
            is GiftPlayDraft -> setGiftDraft(draft)
            is TaskPlayDraft -> setTaskDraft(draft)
            is PunishPlayDraft -> setPunishDraft(draft)
        }
    }
}

@Composable
internal fun rememberChatScreenLocalState(
    userDisplayName: String,
    userPersonaPrompt: String,
    userAvatarUri: String,
    userAvatarUrl: String,
): ChatScreenLocalState {
    var showModelSheet by rememberSaveable { mutableStateOf(false) }
    var showReasoningSheet by rememberSaveable { mutableStateOf(false) }
    var showSearchPickerSheet by rememberSaveable { mutableStateOf(false) }
    var showProfileSheet by rememberSaveable { mutableStateOf(false) }
    var showExportSheet by rememberSaveable { mutableStateOf(false) }
    var showSpecialPlaySheet by rememberSaveable { mutableStateOf(false) }
    var activeSpecialPlayTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    var transferDraft by rememberSaveable(stateSaver = TransferPlayDraftSaver) {
        mutableStateOf(TransferPlayDraft())
    }
    var inviteDraft by rememberSaveable(stateSaver = InvitePlayDraftSaver) {
        mutableStateOf(InvitePlayDraft())
    }
    var giftDraft by rememberSaveable(stateSaver = GiftPlayDraftSaver) {
        mutableStateOf(GiftPlayDraft())
    }
    var taskDraft by rememberSaveable(stateSaver = TaskPlayDraftSaver) {
        mutableStateOf(TaskPlayDraft())
    }
    var punishDraft by rememberSaveable(stateSaver = PunishPlayDraftSaver) {
        mutableStateOf(PunishPlayDraft())
    }
    var exportOptions by remember { mutableStateOf(ConversationExportOptions()) }
    var draftUserDisplayName by rememberSaveable { mutableStateOf("") }
    var draftUserPersonaPrompt by rememberSaveable { mutableStateOf("") }
    var draftUserAvatarUri by rememberSaveable { mutableStateOf("") }
    var draftUserAvatarUrl by rememberSaveable { mutableStateOf("") }
    var activeMessageActionId by remember { mutableStateOf<String?>(null) }
    var messageSelectionPayload by remember { mutableStateOf<ChatMessageSelectionPayload?>(null) }
    var messagePreviewPayload by remember { mutableStateOf<ChatMessagePreviewPayload?>(null) }
    var imagePreviewPayload by remember { mutableStateOf<ChatImagePreviewPayload?>(null) }
    var searchResultPreviewPayload by remember { mutableStateOf<ChatSearchResultPreviewPayload?>(null) }
    var pendingMessageExport by remember { mutableStateOf<ChatMessageExportPayload?>(null) }

    return ChatScreenLocalState(
        showModelSheet = showModelSheet,
        setShowModelSheet = { showModelSheet = it },
        showReasoningSheet = showReasoningSheet,
        setShowReasoningSheet = { showReasoningSheet = it },
        showSearchPickerSheet = showSearchPickerSheet,
        setShowSearchPickerSheet = { showSearchPickerSheet = it },
        showProfileSheet = showProfileSheet,
        setShowProfileSheet = { showProfileSheet = it },
        showExportSheet = showExportSheet,
        setShowExportSheet = { showExportSheet = it },
        showSpecialPlaySheet = showSpecialPlaySheet,
        setShowSpecialPlaySheet = { showSpecialPlaySheet = it },
        activeSpecialPlayType = activeSpecialPlayTypeName?.let(ChatSpecialType::valueOf),
        setActiveSpecialPlayType = { activeSpecialPlayTypeName = it?.name },
        transferDraft = transferDraft,
        setTransferDraft = { transferDraft = it },
        inviteDraft = inviteDraft,
        setInviteDraft = { inviteDraft = it },
        giftDraft = giftDraft,
        setGiftDraft = { giftDraft = it },
        taskDraft = taskDraft,
        setTaskDraft = { taskDraft = it },
        punishDraft = punishDraft,
        setPunishDraft = { punishDraft = it },
        exportOptions = exportOptions,
        setExportOptions = { exportOptions = it },
        draftUserDisplayName = draftUserDisplayName,
        setDraftUserDisplayName = { draftUserDisplayName = it },
        draftUserPersonaPrompt = draftUserPersonaPrompt,
        setDraftUserPersonaPrompt = { draftUserPersonaPrompt = it },
        draftUserAvatarUri = draftUserAvatarUri,
        setDraftUserAvatarUri = { draftUserAvatarUri = it },
        draftUserAvatarUrl = draftUserAvatarUrl,
        setDraftUserAvatarUrl = { draftUserAvatarUrl = it },
        activeMessageActionId = activeMessageActionId,
        setActiveMessageActionId = { activeMessageActionId = it },
        messageSelectionPayload = messageSelectionPayload,
        setMessageSelectionPayload = { messageSelectionPayload = it },
        messagePreviewPayload = messagePreviewPayload,
        setMessagePreviewPayload = { messagePreviewPayload = it },
        imagePreviewPayload = imagePreviewPayload,
        setImagePreviewPayload = { imagePreviewPayload = it },
        searchResultPreviewPayload = searchResultPreviewPayload,
        setSearchResultPreviewPayload = { searchResultPreviewPayload = it },
        pendingMessageExport = pendingMessageExport,
        setPendingMessageExport = { pendingMessageExport = it },
        openProfileSheet = {
            draftUserDisplayName = userDisplayName
            draftUserPersonaPrompt = userPersonaPrompt
            draftUserAvatarUri = userAvatarUri
            draftUserAvatarUrl = userAvatarUrl
            showProfileSheet = true
        },
    )
}
