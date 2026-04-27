package com.example.myapplication.ui.screen.roleplay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import com.example.myapplication.ui.component.roleplay.LocalImmersiveHazeState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatSpecialPlayDraft
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassSurface
import com.example.myapplication.ui.component.roleplay.RoleplayDialoguePanel
import com.example.myapplication.ui.component.roleplay.RoleplayInputQuickAction
import com.example.myapplication.ui.component.roleplay.RoleplayPortraitLayer
import com.example.myapplication.ui.component.roleplay.RoleplayPortraitSpec
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.roleplay.RoleplaySceneMood
import com.example.myapplication.roleplay.RoleplaySceneMoodState
import com.example.myapplication.roleplay.resolveRoleplaySceneMood
import com.example.myapplication.ui.screen.chat.SpecialPlayEditorSheet
import com.example.myapplication.ui.screen.chat.SpecialPlaySheet

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
    pendingMemoryProposal: PendingMemoryProposal?,
    snackbarHostState: SnackbarHostState,
    backdropState: ImmersiveBackdropState,
    onInputChange: (String) -> Unit,
    onGenerateSuggestions: () -> Unit,
    onApplySuggestion: (String) -> Unit,
    onClearSuggestions: () -> Unit,
    onRetryTurn: (String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    onOpenSpecialPlay: () -> Unit,
    quickActions: List<RoleplayInputQuickAction>,
    onOpenPhoneCheck: () -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
    onSend: () -> Unit,
    onCancelSending: () -> Unit,
    onApprovePendingMemoryProposal: () -> Unit,
    onRejectPendingMemoryProposal: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    showSpecialPlaySheet: Boolean,
    activeSpecialPlayDraft: ChatSpecialPlayDraft?,
    onDismissSpecialPlay: () -> Unit,
    onOpenSpecialPlayEditor: (ChatSpecialType) -> Unit,
    onDismissSpecialPlayEditor: () -> Unit,
    onSpecialPlayDraftChange: (ChatSpecialPlayDraft) -> Unit,
    onSpecialPlayConfirm: () -> Unit,
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
    val sceneMoodState = remember(messages) {
        resolveRoleplaySceneMood(messages)
    }
    val sceneMoodColor by animateColorAsState(
        targetValue = resolveMoodAccentColor(sceneMoodState.mood),
        label = "roleplay_scene_mood_color",
    )
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
    var chromeVisible by rememberSaveable(scenario.id) { mutableStateOf(true) }
    val immersiveMode = settings.roleplayImmersiveMode
    val hazeState = remember { HazeState() }

    CompositionLocalProvider(LocalImmersiveHazeState provides hazeState) {
        ApplyRoleplaySystemBars(
            backdropState = backdropState,
            immersiveMode = immersiveMode,
        )

        Box(modifier = Modifier.fillMaxSize()) {
        RoleplaySceneBackground(
            backdropState = backdropState,
            modifier = Modifier.fillMaxSize().haze(state = hazeState),
        )

        val scrimBase = lerp(backdropState.palette.panelTintStrong, sceneMoodColor, 0.18f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.30f to scrimBase.copy(alpha = 0.02f),
                            0.50f to scrimBase.copy(alpha = 0.06f),
                            0.65f to scrimBase.copy(alpha = 0.14f),
                            0.80f to scrimBase.copy(alpha = 0.24f),
                            1.0f to scrimBase.copy(alpha = 0.35f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .roleplayImmersivePadding(immersiveMode)
                .imePadding(),
        ) {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
            ) {
                Column {
                    RoleplaySceneTopBar(
                        characterName = characterName,
                        sceneMoodState = sceneMoodState,
                        sceneMoodColor = sceneMoodColor,
                        backdropState = backdropState,
                        scenario = scenario,
                        contextStatus = contextStatus,
                        showStatusStrip = settings.showRoleplayStatusStrip,
                        onOpenSettings = onOpenSettings,
                        onNavigateBack = onNavigateBack,
                        onToggleChrome = { chromeVisible = false },
                    )

                    if (settings.showRoleplayPresenceStrip) {
                        RoleplayPresenceStrip(
                            userPortrait = userPortrait,
                            characterPortrait = characterPortrait,
                            highlightedSpeaker = highlightedSpeaker,
                            autoHighlightSpeaker = scenario.autoHighlightSpeaker,
                            sceneMoodColor = sceneMoodColor,
                            isSending = isSending,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }

                    if (pendingMemoryProposal != null) {
                        PendingMemoryProposalCard(
                            proposal = pendingMemoryProposal,
                            onApprove = onApprovePendingMemoryProposal,
                            onReject = onRejectPendingMemoryProposal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (!chromeVisible) {
                RoleplayChromeHandle(
                    backdropState = backdropState,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp, bottom = 4.dp),
                    onClick = { chromeVisible = true },
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
                quickActions = quickActions,
                onConfirmTransferReceipt = onConfirmTransferReceipt,
                onSend = onSend,
                onCancel = onCancelSending,
                lineHeightScale = settings.roleplayLineHeightScale.scaleFactor,
                onToggleTopBar = { chromeVisible = !chromeVisible },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }

        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .roleplayNavigationBarPadding(immersiveMode),
        )
    }

    RoleplaySpecialPlayOverlays(
        showSpecialPlaySheet = showSpecialPlaySheet,
        activeSpecialPlayDraft = activeSpecialPlayDraft,
        onDismissSpecialPlay = onDismissSpecialPlay,
        onOpenSpecialPlayEditor = onOpenSpecialPlayEditor,
        onOpenPhoneCheck = onOpenPhoneCheck,
        onDismissSpecialPlayEditor = onDismissSpecialPlayEditor,
        onSpecialPlayDraftChange = onSpecialPlayDraftChange,
        onSpecialPlayConfirm = onSpecialPlayConfirm,
    )
    }
}

@Composable
private fun PendingMemoryProposalCard(
    proposal: PendingMemoryProposal,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "建议记住一条长期设定",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = proposal.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = buildString {
                    append(
                        if (proposal.scopeType == com.example.myapplication.model.MemoryScopeType.GLOBAL) {
                            "将写入全局长期记忆"
                        } else {
                            "将写入助手长期记忆"
                        },
                    )
                    proposal.reason.takeIf { it.isNotBlank() }?.let { reason ->
                        append(" · ")
                        append(reason)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NarraTextButton(onClick = onApprove) {
                    Text("记住")
                }
                NarraTextButton(onClick = onReject) {
                    Text("忽略")
                }
            }
        }
    }
}

@Composable
private fun RoleplaySceneTopBar(
    characterName: String,
    sceneMoodState: RoleplaySceneMoodState,
    sceneMoodColor: Color,
    backdropState: ImmersiveBackdropState,
    scenario: RoleplayScenario,
    contextStatus: RoleplayContextStatus,
    showStatusStrip: Boolean,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    onToggleChrome: () -> Unit,
) {
    val palette = backdropState.palette
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        blurRadius = 20.dp,
        overlayColor = palette.panelTintStrong.copy(alpha = 0.72f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("roleplay_scene_title")
                .clickable(onClick = onToggleChrome)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NarraIconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.common_back),
                    tint = palette.onGlass,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = characterName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onGlass,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 氛围圆点
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color = sceneMoodColor, shape = RoundedCornerShape(4.dp)),
                    )
                    val subtitle = buildString {
                        append(sceneMoodState.label)
                        if (showStatusStrip) {
                            append(" · ")
                            append(if (scenario.longformModeEnabled) "长文" else "对白")
                            append(" · ")
                            append(if (contextStatus.isContinuingSession) "继续" else "新剧情")
                            if (contextStatus.worldBookHitCount > 0) {
                                append(" · 世界书 ${contextStatus.worldBookHitCount}")
                            }
                            if (contextStatus.memoryInjectionCount > 0) {
                                append(" · 记忆 ${contextStatus.memoryInjectionCount}")
                            }
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onGlassMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            NarraIconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(id = R.string.roleplay_settings_button),
                    tint = palette.onGlass,
                )
            }
        }
    }
}

@Composable
private fun RoleplayChromeHandle(
    backdropState: ImmersiveBackdropState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        blurRadius = 18.dp,
        overlayColor = backdropState.palette.panelTint.copy(alpha = 0.26f),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .testTag("roleplay_chrome_handle")
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 4.dp)
                    .background(
                        color = backdropState.palette.onGlassMuted.copy(alpha = 0.72f),
                        shape = MaterialTheme.shapes.extraSmall,
                    ),
            )
            Text(
                text = stringResource(id = R.string.roleplay_show_title_bar),
                style = MaterialTheme.typography.labelMedium,
                color = backdropState.palette.onGlassMuted,
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
    sceneMoodColor: Color,
    isSending: Boolean,
    modifier: Modifier = Modifier,
) {
    RoleplayPortraitLayer(
        user = userPortrait,
        character = characterPortrait,
        highlightedSpeaker = highlightedSpeaker,
        autoHighlightSpeaker = autoHighlightSpeaker,
        userAccentColor = Color(0xFFD8E6FF),
        characterAccentColor = sceneMoodColor,
        isSpeaking = isSending,
        modifier = modifier,
    )
}

@Composable
internal fun RoleplaySpecialPlayOverlays(
    showSpecialPlaySheet: Boolean,
    activeSpecialPlayDraft: ChatSpecialPlayDraft?,
    onDismissSpecialPlay: () -> Unit,
    onOpenSpecialPlayEditor: (ChatSpecialType) -> Unit,
    onOpenPhoneCheck: () -> Unit,
    onOpenVideoCall: (() -> Unit)? = null,
    onOpenMoments: (() -> Unit)? = null,
    onDismissSpecialPlayEditor: () -> Unit,
    onSpecialPlayDraftChange: (ChatSpecialPlayDraft) -> Unit,
    onSpecialPlayConfirm: () -> Unit,
) {
    if (showSpecialPlaySheet) {
        SpecialPlaySheet(
            onDismissRequest = onDismissSpecialPlay,
            onOpenPlay = onOpenSpecialPlayEditor,
            onOpenPhoneCheck = onOpenPhoneCheck,
            onOpenVideoCall = onOpenVideoCall,
            onOpenMoments = onOpenMoments,
        )
    }

    activeSpecialPlayDraft?.let { draft ->
        SpecialPlayEditorSheet(
            draft = draft,
            onDraftChange = onSpecialPlayDraftChange,
            onDismissRequest = onDismissSpecialPlayEditor,
            onConfirm = onSpecialPlayConfirm,
        )
    }
}

private fun resolveMoodAccentColor(mood: RoleplaySceneMood): Color {
    return when (mood) {
        RoleplaySceneMood.NEUTRAL -> Color(0xFFD7E7FF)
        RoleplaySceneMood.WARM -> Color(0xFFF8C97E)
        RoleplaySceneMood.TENSE -> Color(0xFF86B2FF)
        RoleplaySceneMood.SHARP -> Color(0xFFFF8A8A)
        RoleplaySceneMood.MUTED -> Color(0xFFC7BEEB)
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
