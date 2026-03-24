package com.example.myapplication.ui.screen.roleplay

import com.example.myapplication.ui.component.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.myapplication.ui.component.roleplay.RoleplayDialoguePanel
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
    onSendTransferPlay: (String, String, String) -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
    onSend: () -> Unit,
    onCancelSending: () -> Unit,
    onRestartSession: () -> Unit,
    onDismissAssistantMismatch: () -> Unit,
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

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景图直接展示，不做全屏模糊
        RoleplaySceneBackground(
            backdropState = backdropState,
            modifier = Modifier.fillMaxSize(),
        )

        // 底部渐变 scrim：用背景图自身色调，自然融入不压抑
        val scrimBase = backdropState.palette.panelTintStrong
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.25f to scrimBase.copy(alpha = 0.05f),
                            0.45f to scrimBase.copy(alpha = 0.28f),
                            0.62f to scrimBase.copy(alpha = 0.50f),
                            0.80f to scrimBase.copy(alpha = 0.68f),
                            1.0f to scrimBase.copy(alpha = 0.80f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // 精简顶栏：返回 + 角色名 + 阅读/设置
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NarraIconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White,
                    )
                }

                Text(
                    text = characterName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center,
                )

                NarraIconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "沉浸设置",
                        tint = Color.White,
                    )
                }
            }

            RoleplayDialoguePanel(
                backdropState = backdropState,
                messages = messages,
                suggestions = suggestions,
                isGeneratingSuggestions = isGeneratingSuggestions,
                suggestionErrorMessage = suggestionErrorMessage,
                showAiHelper = settings.showRoleplayAiHelper,
                input = input,
                isSending = isSending,
                onInputChange = onInputChange,
                onGenerateSuggestions = onGenerateSuggestions,
                onApplySuggestion = onApplySuggestion,
                onClearSuggestions = onClearSuggestions,
                onRetryTurn = onRetryTurn,
                onOpenSpecialPlay = {
                    if (transferCounterparty.isBlank()) {
                        transferCounterparty = characterName
                    }
                    showSpecialPlaySheet = true
                },
                onConfirmTransferReceipt = onConfirmTransferReceipt,
                onSend = onSend,
                onCancel = { onCancelSending() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }

        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    if (showSpecialPlaySheet) {
        SpecialPlaySheet(
            onDismissRequest = { showSpecialPlaySheet = false },
            onOpenTransfer = {
                showSpecialPlaySheet = false
                if (transferCounterparty.isBlank()) {
                    transferCounterparty = characterName
                }
                showTransferSheet = true
            },
        )
    }

    if (showTransferSheet) {
        TransferPlaySheet(
            draft = transferDraft,
            onDraftChange = {
                transferCounterparty = it.counterparty
                transferAmount = it.amount
                transferNote = it.note
            },
            onDismissRequest = { showTransferSheet = false },
            onConfirm = {
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
    }

    if (showAssistantMismatchDialog) {
        AlertDialog(
            onDismissRequest = onDismissAssistantMismatch,
            title = { Text("当前角色已改绑") },
            text = {
                Text(
                    "旧剧情绑定的是“$previousAssistantName”，当前场景改成了“$currentAssistantName”。继续沿用旧剧情可能出现人格和历史错位，建议重建剧情会话。",
                )
            },
            confirmButton = {
                NarraTextButton(onClick = onRestartSession) {
                    Text("重建剧情")
                }
            },
            dismissButton = {
                NarraTextButton(onClick = onDismissAssistantMismatch) {
                    Text("继续旧剧情")
                }
            },
        )
    }

}
