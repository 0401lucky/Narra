package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassChip
import com.example.myapplication.ui.component.roleplay.RoleplayDialoguePanel
import com.example.myapplication.ui.component.roleplay.RoleplayPortraitLayer
import com.example.myapplication.ui.component.roleplay.RoleplayPortraitSpec
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.screen.chat.SpecialPlaySheet
import com.example.myapplication.ui.screen.chat.TransferPlayDraft
import com.example.myapplication.ui.screen.chat.TransferPlaySheet

@Composable
internal fun RoleplaySceneContent(
    scenario: RoleplayScenario,
    assistant: Assistant?,
    settings: AppSettings,
    contextStatus: RoleplayContextStatus,
    messages: List<RoleplayMessageUiModel>,
    suggestions: List<RoleplaySuggestionUiModel>,
    input: String,
    inputFocusToken: Long,
    isSending: Boolean,
    isGeneratingSuggestions: Boolean,
    suggestionErrorMessage: String?,
    snackbarHostState: SnackbarHostState,
    backdropState: ImmersiveBackdropState,
    onInputChange: (String) -> Unit,
    onGenerateSuggestions: () -> Unit,
    onApplySuggestion: (String) -> Unit,
    onClearSuggestions: () -> Unit,
    onRetryTurn: (String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    onOpenSpecialPlay: () -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
    onSend: () -> Unit,
    onCancelSending: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    transferCounterparty: String,
    transferDraft: TransferPlayDraft,
    showSpecialPlaySheet: Boolean,
    showTransferSheet: Boolean,
    onDismissSpecialPlay: () -> Unit,
    onOpenTransferSheet: () -> Unit,
    onDismissTransferSheet: () -> Unit,
    onTransferDraftChange: (TransferPlayDraft) -> Unit,
    onTransferConfirm: () -> Unit,
) {
    val characterName = scenario.characterDisplayNameOverride.trim()
        .ifBlank { assistant?.name?.trim().orEmpty() }
        .ifBlank { "角色" }
    val userName = scenario.userDisplayNameOverride.trim()
        .ifBlank { settings.resolvedUserDisplayName() }
    val highlightedSpeaker = remember(messages, isSending) {
        when {
            isSending -> RoleplaySpeaker.CHARACTER
            else -> messages.lastOrNull { it.contentType != com.example.myapplication.model.RoleplayContentType.SYSTEM }
                ?.speaker
        }
    }
    val userPortrait = remember(scenario, settings, userName) {
        RoleplayPortraitSpec(
            name = userName,
            avatarUri = scenario.userPortraitUri.trim().ifBlank { settings.userAvatarUri.trim() },
            avatarUrl = scenario.userPortraitUrl.trim().ifBlank { settings.userAvatarUrl.trim() },
            fallbackLabel = "玩家",
        )
    }
    val characterPortrait = remember(scenario, assistant, characterName) {
        RoleplayPortraitSpec(
            name = characterName,
            avatarUri = scenario.characterPortraitUri.trim().ifBlank { assistant?.avatarUri.orEmpty() },
            avatarUrl = scenario.characterPortraitUrl.trim(),
            fallbackLabel = "角色",
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RoleplaySceneBackground(
            backdropState = backdropState,
            modifier = Modifier.fillMaxSize(),
        )

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
            RoleplaySceneTopBar(
                characterName = characterName,
                onOpenSettings = onOpenSettings,
                onNavigateBack = onNavigateBack,
            )

            if (settings.showRoleplayPresenceStrip) {
                RoleplayPresenceStrip(
                    userPortrait = userPortrait,
                    characterPortrait = characterPortrait,
                    highlightedSpeaker = highlightedSpeaker,
                    autoHighlightSpeaker = scenario.autoHighlightSpeaker,
                    isSending = isSending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            if (settings.showRoleplayStatusStrip) {
                RoleplayStatusStrip(
                    backdropState = backdropState,
                    scenario = scenario,
                    contextStatus = contextStatus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            RoleplayDialoguePanel(
                backdropState = backdropState,
                messages = messages,
                suggestions = suggestions,
                isGeneratingSuggestions = isGeneratingSuggestions,
                suggestionErrorMessage = suggestionErrorMessage,
                showAiHelper = settings.showRoleplayAiHelper,
                input = input,
                inputFocusToken = inputFocusToken,
                isSending = isSending,
                onInputChange = onInputChange,
                onGenerateSuggestions = onGenerateSuggestions,
                onApplySuggestion = onApplySuggestion,
                onClearSuggestions = onClearSuggestions,
                onRetryTurn = onRetryTurn,
                onEditUserMessage = onEditUserMessage,
                onOpenSpecialPlay = onOpenSpecialPlay,
                onConfirmTransferReceipt = onConfirmTransferReceipt,
                onSend = onSend,
                onCancel = onCancelSending,
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

    RoleplaySpecialPlayOverlays(
        showSpecialPlaySheet = showSpecialPlaySheet,
        showTransferSheet = showTransferSheet,
        transferDraft = transferDraft,
        onDismissSpecialPlay = onDismissSpecialPlay,
        onOpenTransferSheet = onOpenTransferSheet,
        onDismissTransferSheet = onDismissTransferSheet,
        onTransferDraftChange = onTransferDraftChange,
        onTransferConfirm = onTransferConfirm,
    )
}

@Composable
private fun RoleplaySceneTopBar(
    characterName: String,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit,
) {
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
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
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
}

@Composable
internal fun RoleplayPresenceStrip(
    userPortrait: RoleplayPortraitSpec,
    characterPortrait: RoleplayPortraitSpec,
    highlightedSpeaker: RoleplaySpeaker?,
    autoHighlightSpeaker: Boolean,
    isSending: Boolean,
    modifier: Modifier = Modifier,
) {
    RoleplayPortraitLayer(
        user = userPortrait,
        character = characterPortrait,
        highlightedSpeaker = highlightedSpeaker,
        autoHighlightSpeaker = autoHighlightSpeaker,
        isSpeaking = isSending,
        modifier = modifier,
    )
}

@Composable
internal fun RoleplayStatusStrip(
    backdropState: ImmersiveBackdropState,
    scenario: RoleplayScenario,
    contextStatus: RoleplayContextStatus,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ImmersiveGlassChip(
            text = if (scenario.longformModeEnabled) "长文模式" else "对白模式",
            backdropState = backdropState,
        )
        ImmersiveGlassChip(
            text = if (contextStatus.isContinuingSession) "继续剧情" else "新剧情",
            backdropState = backdropState,
        )
        ImmersiveGlassChip(
            text = "世界书 ${contextStatus.worldBookHitCount}",
            backdropState = backdropState,
        )
        ImmersiveGlassChip(
            text = "记忆 ${contextStatus.memoryInjectionCount}",
            backdropState = backdropState,
        )
        if (contextStatus.hasSummary) {
            ImmersiveGlassChip(
                text = "摘要 ${contextStatus.summaryCoveredMessageCount}",
                backdropState = backdropState,
            )
        }
    }
}

@Composable
internal fun RoleplaySpecialPlayOverlays(
    showSpecialPlaySheet: Boolean,
    showTransferSheet: Boolean,
    transferDraft: TransferPlayDraft,
    onDismissSpecialPlay: () -> Unit,
    onOpenTransferSheet: () -> Unit,
    onDismissTransferSheet: () -> Unit,
    onTransferDraftChange: (TransferPlayDraft) -> Unit,
    onTransferConfirm: () -> Unit,
) {
    if (showSpecialPlaySheet) {
        SpecialPlaySheet(
            onDismissRequest = onDismissSpecialPlay,
            onOpenTransfer = onOpenTransferSheet,
        )
    }

    if (showTransferSheet) {
        TransferPlaySheet(
            draft = transferDraft,
            onDraftChange = onTransferDraftChange,
            onDismissRequest = onDismissTransferSheet,
            onConfirm = onTransferConfirm,
        )
    }
}

@Composable
internal fun RoleplayAssistantMismatchDialog(
    showAssistantMismatchDialog: Boolean,
    previousAssistantName: String,
    currentAssistantName: String,
    onRestartSession: () -> Unit,
    onDismissAssistantMismatch: (Boolean) -> Unit,
) {
    if (!showAssistantMismatchDialog) return

    var suppressFuturePrompt by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismissAssistantMismatch(suppressFuturePrompt) },
        title = { Text("当前角色已改绑") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "旧剧情绑定的是“$previousAssistantName”，当前场景改成了“$currentAssistantName”。继续沿用旧剧情可能出现人格和历史错位，建议重建剧情会话。",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = suppressFuturePrompt,
                        onCheckedChange = { suppressFuturePrompt = it },
                    )
                    Text("不再显示")
                }
            }
        },
        confirmButton = {
            NarraTextButton(onClick = onRestartSession) {
                Text("重建剧情")
            }
        },
        dismissButton = {
            NarraTextButton(onClick = { onDismissAssistantMismatch(suppressFuturePrompt) }) {
                Text("继续旧剧情")
            }
        },
    )
}
