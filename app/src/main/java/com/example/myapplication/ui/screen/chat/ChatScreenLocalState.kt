package com.example.myapplication.ui.screen.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal data class ChatScreenLocalState(
    val showModelSheet: Boolean,
    val setShowModelSheet: (Boolean) -> Unit,
    val showReasoningSheet: Boolean,
    val setShowReasoningSheet: (Boolean) -> Unit,
    val showProfileSheet: Boolean,
    val setShowProfileSheet: (Boolean) -> Unit,
    val showExportSheet: Boolean,
    val setShowExportSheet: (Boolean) -> Unit,
    val showPromptDebugSheet: Boolean,
    val setShowPromptDebugSheet: (Boolean) -> Unit,
    val showSpecialPlaySheet: Boolean,
    val setShowSpecialPlaySheet: (Boolean) -> Unit,
    val showTransferSheet: Boolean,
    val setShowTransferSheet: (Boolean) -> Unit,
    val exportOptions: ConversationExportOptions,
    val setExportOptions: (ConversationExportOptions) -> Unit,
    val draftUserDisplayName: String,
    val setDraftUserDisplayName: (String) -> Unit,
    val draftUserAvatarUri: String,
    val setDraftUserAvatarUri: (String) -> Unit,
    val draftUserAvatarUrl: String,
    val setDraftUserAvatarUrl: (String) -> Unit,
    val transferCounterparty: String,
    val setTransferCounterparty: (String) -> Unit,
    val transferAmount: String,
    val setTransferAmount: (String) -> Unit,
    val transferNote: String,
    val setTransferNote: (String) -> Unit,
    val openProfileSheet: () -> Unit,
) {
    val transferDraft: TransferPlayDraft
        get() = TransferPlayDraft(
            counterparty = transferCounterparty,
            amount = transferAmount,
            note = transferNote,
        )
}

@Composable
internal fun rememberChatScreenLocalState(
    userDisplayName: String,
    userAvatarUri: String,
    userAvatarUrl: String,
): ChatScreenLocalState {
    var showModelSheet by rememberSaveable { mutableStateOf(false) }
    var showReasoningSheet by rememberSaveable { mutableStateOf(false) }
    var showProfileSheet by rememberSaveable { mutableStateOf(false) }
    var showExportSheet by rememberSaveable { mutableStateOf(false) }
    var showPromptDebugSheet by rememberSaveable { mutableStateOf(false) }
    var showSpecialPlaySheet by rememberSaveable { mutableStateOf(false) }
    var showTransferSheet by rememberSaveable { mutableStateOf(false) }
    var exportOptions by remember { mutableStateOf(ConversationExportOptions()) }
    var transferCounterparty by rememberSaveable { mutableStateOf("") }
    var transferAmount by rememberSaveable { mutableStateOf("") }
    var transferNote by rememberSaveable { mutableStateOf("") }
    var draftUserDisplayName by rememberSaveable { mutableStateOf("") }
    var draftUserAvatarUri by rememberSaveable { mutableStateOf("") }
    var draftUserAvatarUrl by rememberSaveable { mutableStateOf("") }

    return ChatScreenLocalState(
        showModelSheet = showModelSheet,
        setShowModelSheet = { showModelSheet = it },
        showReasoningSheet = showReasoningSheet,
        setShowReasoningSheet = { showReasoningSheet = it },
        showProfileSheet = showProfileSheet,
        setShowProfileSheet = { showProfileSheet = it },
        showExportSheet = showExportSheet,
        setShowExportSheet = { showExportSheet = it },
        showPromptDebugSheet = showPromptDebugSheet,
        setShowPromptDebugSheet = { showPromptDebugSheet = it },
        showSpecialPlaySheet = showSpecialPlaySheet,
        setShowSpecialPlaySheet = { showSpecialPlaySheet = it },
        showTransferSheet = showTransferSheet,
        setShowTransferSheet = { showTransferSheet = it },
        exportOptions = exportOptions,
        setExportOptions = { exportOptions = it },
        draftUserDisplayName = draftUserDisplayName,
        setDraftUserDisplayName = { draftUserDisplayName = it },
        draftUserAvatarUri = draftUserAvatarUri,
        setDraftUserAvatarUri = { draftUserAvatarUri = it },
        draftUserAvatarUrl = draftUserAvatarUrl,
        setDraftUserAvatarUrl = { draftUserAvatarUrl = it },
        transferCounterparty = transferCounterparty,
        setTransferCounterparty = { transferCounterparty = it },
        transferAmount = transferAmount,
        setTransferAmount = { transferAmount = it },
        transferNote = transferNote,
        setTransferNote = { transferNote = it },
        openProfileSheet = {
            draftUserDisplayName = userDisplayName
            draftUserAvatarUri = userAvatarUri
            draftUserAvatarUrl = userAvatarUrl
            showProfileSheet = true
        },
    )
}
