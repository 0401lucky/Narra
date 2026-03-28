package com.example.myapplication.ui.screen.roleplay

import com.example.myapplication.ui.component.*

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassChip
import com.example.myapplication.ui.component.roleplay.RoleplayDialoguePanel
import com.example.myapplication.ui.component.roleplay.RoleplayPortraitLayer
import com.example.myapplication.ui.component.roleplay.RoleplayPortraitSpec
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.rememberImmersiveBackdropState
import com.example.myapplication.ui.screen.chat.SpecialPlaySheet
import com.example.myapplication.ui.screen.chat.TransferPlayDraft
import com.example.myapplication.ui.screen.chat.TransferPlaySheet

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
    isSending: Boolean,
    isGeneratingSuggestions: Boolean,
    isScenarioLoading: Boolean,
    showAssistantMismatchDialog: Boolean,
    previousAssistantName: String,
    currentAssistantName: String,
    noticeMessage: String?,
    errorMessage: String?,
    suggestionErrorMessage: String?,
    onClearNoticeMessage: () -> Unit,
    onClearErrorMessage: () -> Unit,
    onInputChange: (String) -> Unit,
    onGenerateSuggestions: () -> Unit,
    onApplySuggestion: (String) -> Unit,
    onClearSuggestions: () -> Unit,
    onRetryTurn: (String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    onSendTransferPlay: (String, String, String) -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
    onSend: () -> Unit,
    onCancelSending: () -> Unit,
    onRestartSession: () -> Unit,
    onDismissAssistantMismatch: (Boolean) -> Unit,
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

    var showSpecialPlaySheet by rememberSaveable { mutableStateOf(false) }
    var showTransferSheet by rememberSaveable { mutableStateOf(false) }
    var transferCounterparty by rememberSaveable { mutableStateOf("") }
    var transferAmount by rememberSaveable { mutableStateOf("") }
    var transferNote by rememberSaveable { mutableStateOf("") }
    val transferDraft = TransferPlayDraft(
        counterparty = transferCounterparty,
        amount = transferAmount,
        note = transferNote,
    )
    val backdropState = rememberImmersiveBackdropState(scenario.backgroundUri)

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
        snackbarHostState = snackbarHostState,
        backdropState = backdropState,
        onInputChange = onInputChange,
        onGenerateSuggestions = onGenerateSuggestions,
        onApplySuggestion = onApplySuggestion,
        onClearSuggestions = onClearSuggestions,
        onRetryTurn = onRetryTurn,
        onEditUserMessage = onEditUserMessage,
        onOpenSpecialPlay = {
            if (transferCounterparty.isBlank()) {
                transferCounterparty = characterName
            }
            showSpecialPlaySheet = true
        },
        onConfirmTransferReceipt = onConfirmTransferReceipt,
        onSend = onSend,
        onCancelSending = onCancelSending,
        onOpenSettings = onOpenSettings,
        onNavigateBack = onNavigateBack,
        transferCounterparty = transferCounterparty,
        transferDraft = transferDraft,
        showSpecialPlaySheet = showSpecialPlaySheet,
        showTransferSheet = showTransferSheet,
        onDismissSpecialPlay = { showSpecialPlaySheet = false },
        onOpenTransferSheet = {
            showSpecialPlaySheet = false
            if (transferCounterparty.isBlank()) {
                transferCounterparty = characterName
            }
            showTransferSheet = true
        },
        onDismissTransferSheet = { showTransferSheet = false },
        onTransferDraftChange = {
            transferCounterparty = it.counterparty
            transferAmount = it.amount
            transferNote = it.note
        },
        onTransferConfirm = {
            onSendTransferPlay(
                transferDraft.counterparty.ifBlank { characterName },
                transferDraft.amount,
                transferDraft.note,
            )
            transferCounterparty = characterName
            transferAmount = ""
            transferNote = ""
            showTransferSheet = false
        },
    )

    RoleplayAssistantMismatchDialog(
        showAssistantMismatchDialog = showAssistantMismatchDialog,
        previousAssistantName = previousAssistantName,
        currentAssistantName = currentAssistantName,
        onRestartSession = onRestartSession,
        onDismissAssistantMismatch = onDismissAssistantMismatch,
    )

}

