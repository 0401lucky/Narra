package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayGroupParticipant
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.RoleplaySuggestionAxis
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.isGroupChat
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassSurface
import com.example.myapplication.ui.component.roleplay.ImmersiveRoleplayColors
import com.example.myapplication.ui.component.roleplay.RoleplayInputQuickAction
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.RoleplayEmotionChip
import com.example.myapplication.ui.component.roleplay.RoleplayInputBar
import com.example.myapplication.ui.component.roleplay.RoleplayMentionCandidate
import com.example.myapplication.ui.component.roleplay.RoleplayMessageItem
import com.example.myapplication.ui.component.roleplay.RoleplayMessageBubbleMode
import com.example.myapplication.ui.component.roleplay.rememberRoleplayNoBackgroundSkinSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val OnlineAccent = Color(0xFF5B91D7)
private val OnlineGiftAccent = Color(0xFFE77F93)
private val OnlineTaskAccent = Color(0xFFD78B31)
private val OnlinePunishAccent = Color(0xFFD55C73)
private val OnlineVideoAccent = Color(0xFF6C84FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoleplayOnlinePhoneContent(
    scenario: RoleplayScenario,
    assistant: Assistant?,
    settings: AppSettings,
    backdropState: ImmersiveBackdropState,
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
    suggestionErrorMessage: String?,
    pendingMemoryProposal: PendingMemoryProposal?,
    snackbarHostState: SnackbarHostState,
    onInputChange: (String) -> Unit,
    onGenerateSuggestions: () -> Unit,
    onApplySuggestion: (String) -> Unit,
    onClearSuggestions: () -> Unit,
    onRetryTurn: (String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    onQuoteMessage: (String, String, String) -> Unit,
    onPokeMessageAvatar: (RoleplayMessageUiModel) -> Unit,
    onClearQuotedMessage: () -> Unit,
    onRecallMessage: (String) -> Unit,
    onScreenshotChat: () -> Unit,
    onOpenDiary: () -> Unit,
    onOpenVoiceMessage: () -> Unit,
    onOpenTransferPlay: () -> Unit,
    onOpenInvitePlay: () -> Unit,
    onOpenGiftPlay: () -> Unit,
    onOpenTaskPlay: () -> Unit,
    onOpenPunishPlay: () -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
    onSend: () -> Unit,
    onCancelSending: () -> Unit,
    onApprovePendingMemoryProposal: () -> Unit,
    onRejectPendingMemoryProposal: () -> Unit,
    onOpenPhoneCheck: () -> Unit,
    onOpenMoments: () -> Unit,
    onOpenVideoCall: () -> Unit,
    onOpenMailbox: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val immersiveMode = settings.roleplayImmersiveMode
    ApplyRoleplaySystemBars(
        backdropState = backdropState,
        immersiveMode = immersiveMode,
    )
    val colors = rememberOnlinePhoneColors(
        backdropState = backdropState,
        noBackgroundSkin = settings.roleplayNoBackgroundSkin,
    )
    val noBackgroundSkinSpec = rememberRoleplayNoBackgroundSkinSpec(settings.roleplayNoBackgroundSkin)
    val palette = backdropState.palette
    val statusBarTopPadding = if (immersiveMode == RoleplayImmersiveMode.HIDE_SYSTEM_BARS) {
        0.dp
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    }
    val chromeSurfaceColor = remember(backdropState.hasImage, colors.panelBackgroundStrong) {
        colors.panelBackgroundStrong.copy(
            alpha = if (backdropState.hasImage) 0.92f else 1f,
        )
    }
    val timestampSurfaceColor = remember(backdropState.hasImage, colors.panelBackground) {
        colors.panelBackground.copy(
            alpha = if (backdropState.hasImage) 0.72f else 0.92f,
        )
    }
    val onlineBackdropScrim = remember(backdropState.hasImage, palette) {
        Brush.verticalGradient(
            colors = listOf(
                palette.scrimTop.copy(alpha = if (backdropState.hasImage) 0.14f else 0.08f),
                palette.scrimBottom.copy(alpha = if (backdropState.hasImage) 0.28f else 0.14f),
            ),
        )
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    val quickActions = remember(
        onOpenVoiceMessage,
        onOpenDiary,
        onOpenPhoneCheck,
        onOpenTransferPlay,
        onOpenInvitePlay,
        onOpenGiftPlay,
        onOpenTaskPlay,
        onOpenPunishPlay,
        onOpenMoments,
        onOpenVideoCall,
        onOpenMailbox,
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
                    accentColor = OnlineAccent,
                    onClick = onOpenVoiceMessage,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "查手机",
                    icon = Icons.Default.Visibility,
                    accentColor = OnlineAccent,
                    onClick = onOpenPhoneCheck,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "转账",
                    icon = Icons.Default.Share,
                    accentColor = Color(0xFF07C160),
                    onClick = onOpenTransferPlay,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "邀约",
                    icon = Icons.Default.Event,
                    accentColor = OnlineAccent,
                    onClick = onOpenInvitePlay,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "礼物",
                    icon = Icons.Default.CardGiftcard,
                    accentColor = OnlineGiftAccent,
                    onClick = onOpenGiftPlay,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "委托",
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    accentColor = OnlineTaskAccent,
                    onClick = onOpenTaskPlay,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "惩罚",
                    icon = Icons.Default.Gavel,
                    accentColor = OnlinePunishAccent,
                    onClick = onOpenPunishPlay,
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
                    label = "视频",
                    icon = Icons.Default.Videocam,
                    accentColor = OnlineVideoAccent,
                    onClick = onOpenVideoCall,
                ),
            )
        }
    }
    val characterName = remember(scenario.title, scenario.characterDisplayNameOverride, assistant?.name, scenario.chatType) {
        if (scenario.isGroupChat) {
            scenario.title.ifBlank { "群聊" }
        } else {
            scenario.characterDisplayNameOverride.trim()
                .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
        }
    }
    val groupMentionCandidates = remember(groupParticipants, settings, scenario.chatType) {
        if (!scenario.isGroupChat) {
            emptyList()
        } else {
            val assistants = settings.resolvedAssistants()
            groupParticipants
                .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
                .map { participant ->
                    val memberAssistant = assistants.firstOrNull { it.id == participant.assistantId }
                    RoleplayMentionCandidate(
                        id = participant.id,
                        displayName = participant.displayNameOverride.ifBlank {
                            memberAssistant?.name?.trim().orEmpty()
                        }.ifBlank { "角色" },
                        avatarUri = participant.avatarUriOverride.ifBlank {
                            memberAssistant?.avatarUri.orEmpty()
                        },
                        iconName = memberAssistant?.iconName.orEmpty(),
                        muted = participant.isMuted,
                    )
                }
        }
    }
    val visibleMessages = messages.filter { message ->
        message.contentType != RoleplayContentType.SYSTEM &&
            (
                settings.showOnlineRoleplayNarration ||
                    message.contentType != RoleplayContentType.THOUGHT
                )
    }
    val shouldStickToBottom by remember(listState, visibleMessages.size) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (visibleMessages.isEmpty()) {
                true
            } else {
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisibleIndex >= layoutInfo.totalItemsCount - 2
            }
        }
    }

    LaunchedEffect(
        visibleMessages.firstOrNull()?.sourceMessageId,
        visibleMessages.firstOrNull()?.createdAt,
    ) {
        if (visibleMessages.isNotEmpty()) {
            listState.scrollToItem(visibleMessages.lastIndex)
        }
    }
    LaunchedEffect(
        visibleMessages.size,
        visibleMessages.lastOrNull()?.content?.length,
        visibleMessages.lastOrNull()?.isStreaming,
    ) {
        if (visibleMessages.isNotEmpty() && shouldStickToBottom) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        RoleplaySceneBackground(
            backdropState = backdropState,
            modifier = Modifier.fillMaxSize(),
            fallbackBackgroundColor = noBackgroundSkinSpec.background.takeIf { !backdropState.hasImage },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(onlineBackdropScrim),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .roleplayNavigationBarPadding(immersiveMode),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ImmersiveGlassSurface(
                    backdropState = backdropState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = statusBarTopPadding + 6.dp, bottom = 6.dp),
                    shape = RoundedCornerShape(28.dp),
                    blurRadius = 22.dp,
                    overlayColor = palette.panelTintStrong.copy(
                        alpha = if (backdropState.hasImage) 0.82f else 0.94f,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OnlinePhoneHeaderButton(
                            onClick = onNavigateBack,
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            colors = colors,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = characterName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = buildString {
                                    append(if (scenario.isGroupChat) "${groupParticipants.size} 位成员" else "线上模式")
                                    if (scenario.isGroupChat) append(" · ${scenario.groupReplyMode.displayName}")
                                    if (contextStatus.isContinuingSession) append(" · 继续聊天")
                                    if (isSending) append(" · 正在输入")
                                    contextStatus.summaryCoveredMessageCount.takeIf { it > 0 }?.let { count ->
                                        append(" · 摘要 $count")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OnlinePhoneHeaderButton(
                            onClick = onOpenMoments,
                            icon = Icons.Default.Forum,
                            contentDescription = "动态",
                            colors = colors,
                        )
                        OnlinePhoneHeaderButton(
                            onClick = onOpenPhoneCheck,
                            icon = Icons.Default.PhoneAndroid,
                            contentDescription = "查手机",
                            colors = colors,
                        )
                        Box {
                            OnlinePhoneHeaderButton(
                                onClick = { showMenu = true },
                                icon = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                colors = colors,
                            )
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("截图聊天") },
                                    onClick = {
                                        showMenu = false
                                        onScreenshotChat()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("设置") },
                                    onClick = {
                                        showMenu = false
                                        onOpenSettings()
                                    },
                                )
                            }
                        }
                    }
                }
            }

        pendingMemoryProposal?.let { proposal ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = chromeSurfaceColor,
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("建议记住一条长期设定", fontWeight = FontWeight.SemiBold, color = colors.characterAccent)
                    Text(proposal.content, color = colors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.TextButton(onClick = onApprovePendingMemoryProposal) { Text("记住") }
                        androidx.compose.material3.TextButton(onClick = onRejectPendingMemoryProposal) { Text("忽略") }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(
                    items = visibleMessages,
                    key = { index, item ->
                        "${item.sourceMessageId}-${item.contentType}-${item.createdAt}-$index"
                    },
                ) { index, message ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = androidx.compose.animation.core.tween(durationMillis = 240),
                                fadeOutSpec = androidx.compose.animation.core.tween(durationMillis = 160),
                                placementSpec = androidx.compose.animation.core.spring(
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                                ),
                            ),
                    ) {
                        val previous = visibleMessages.getOrNull(index - 1)
                        if (scenario.enableTimeAwareness && shouldShowOnlineTimestamp(previous, message)) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    color = timestampSurfaceColor,
                                    shape = RoundedCornerShape(999.dp),
                                ) {
                                    Text(
                                        text = formatOnlineTime(message.createdAt),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textMuted,
                                    )
                                }
                            }
                        }
                        RoleplayMessageItem(
                        message = message,
                        colors = colors,
                        backdropState = backdropState,
                        onRetryTurn = onRetryTurn,
                        onEditUserMessage = onEditUserMessage,
                        onQuoteMessage = onQuoteMessage,
                        onPokeMessageAvatar = onPokeMessageAvatar,
                        onRecallMessage = onRecallMessage,
                        onOpenQuotedMessage = { quotedMessageId ->
                            val targetIndex = visibleMessages.indexOfFirst { it.sourceMessageId == quotedMessageId }
                            if (targetIndex >= 0) {
                                scope.launch {
                                    listState.animateScrollToItem(targetIndex)
                                }
                            }
                        },
                        onConfirmTransferReceipt = onConfirmTransferReceipt,
                        onOpenVideoCall = onOpenVideoCall,
                        bubbleMode = RoleplayMessageBubbleMode.ONLINE_PHONE,
                        noBackgroundSkin = settings.roleplayNoBackgroundSkin,
                    )
                    }
                }
            }

            AppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
            }

        if (settings.showRoleplayAiHelper) {
            RoleplayOnlineAiHelperBar(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                backdropState = backdropState,
                colors = colors,
                suggestions = suggestions,
                isGeneratingSuggestions = isGeneratingSuggestions,
                suggestionErrorMessage = suggestionErrorMessage,
                isSending = isSending,
                onGenerateSuggestions = onGenerateSuggestions,
                onApplySuggestion = onApplySuggestion,
                onClearSuggestions = onClearSuggestions,
            )
        }

        if (replyToMessageId.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = chromeSurfaceColor,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier
                            .width(3.dp)
                            .height(36.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = colors.characterAccent.copy(alpha = 0.68f),
                    ) {}
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "回复 ${replyToSpeakerName.ifBlank { "对方" }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.characterAccent,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = replyToPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = onClearQuotedMessage) {
                        Text("取消")
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            RoleplayInputBar(
                colors = colors,
                backdropState = backdropState,
                input = input,
                inputFocusToken = inputFocusToken,
                isSending = isSending,
                onInputChange = onInputChange,
                onSend = onSend,
                onCancel = onCancelSending,
                onOpenSpecialPlay = onOpenTransferPlay,
                quickActions = quickActions,
                mentionCandidates = groupMentionCandidates,
            )
        }
    }

}
}

@Composable
private fun OnlinePhoneHeaderButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    colors: ImmersiveRoleplayColors,
) {
    NarraIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.textPrimary,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.textPrimary,
        )
    }
}

@Composable
private fun RoleplayOnlineAiHelperBar(
    modifier: Modifier = Modifier,
    backdropState: ImmersiveBackdropState,
    colors: ImmersiveRoleplayColors,
    suggestions: List<RoleplaySuggestionUiModel>,
    isGeneratingSuggestions: Boolean,
    suggestionErrorMessage: String?,
    isSending: Boolean,
    onGenerateSuggestions: () -> Unit,
    onApplySuggestion: (String) -> Unit,
    onClearSuggestions: () -> Unit,
) {
    val showPanel = suggestions.isNotEmpty() || isGeneratingSuggestions || !suggestionErrorMessage.isNullOrBlank()
    val actionEnabled = !isSending && !isGeneratingSuggestions
    val view = LocalView.current
    val shape = RoundedCornerShape(24.dp)
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        blurRadius = 20.dp,
        overlayColor = colors.panelBackgroundStrong.copy(alpha = 0.88f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "AI 帮写",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.characterAccent,
                    )
                    Text(
                        text = if (showPanel) {
                            "给你下一句、换个语气，或者直接推进剧情。"
                        } else {
                            "卡住时点一下，直接给你三条能发出去的话。"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                        color = colors.textMuted,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showPanel) {
                        androidx.compose.material3.TextButton(onClick = onClearSuggestions) {
                            Text(
                                text = "收起",
                                color = colors.textMuted,
                            )
                        }
                    }
                    OnlineAiHelperPrimaryAction(
                        text = when {
                            isGeneratingSuggestions -> "生成中"
                            showPanel -> "换一批"
                            else -> "生成"
                        },
                        enabled = actionEnabled,
                        colors = colors,
                        onClick = {
                            view.performHapticFeedback(androidx.core.view.HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK)
                            onGenerateSuggestions()
                        },
                    )
                }
            }

            if (showPanel) {
                if (isGeneratingSuggestions) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.characterAccent,
                        )
                        Text(
                            text = "正在生成建议…",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
                if (!suggestionErrorMessage.isNullOrBlank()) {
                    Text(
                        text = suggestionErrorMessage,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                        color = colors.errorText,
                    )
                }
                suggestions.forEach { suggestion ->
                    RoleplayOnlineSuggestionCard(
                        suggestion = suggestion,
                        backdropState = backdropState,
                        colors = colors,
                        enabled = !isSending,
                        onClick = {
                            view.performHapticFeedback(androidx.core.view.HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK)
                            onApplySuggestion(suggestion.text)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineAiHelperPrimaryAction(
    text: String,
    enabled: Boolean,
    colors: ImmersiveRoleplayColors,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(
                color = if (enabled) {
                    colors.characterAccent.copy(alpha = 0.92f)
                } else {
                    colors.panelBackground.copy(alpha = 0.88f)
                },
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (enabled) {
                Color.Black.copy(alpha = 0.88f)
            } else {
                colors.textMuted
            },
        )
    }
}

@Composable
private fun RoleplayOnlineSuggestionCard(
    suggestion: RoleplaySuggestionUiModel,
    backdropState: ImmersiveBackdropState,
    colors: ImmersiveRoleplayColors,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        blurRadius = 18.dp,
        overlayColor = colors.panelBackground.copy(alpha = 0.68f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoleplayEmotionChip(
                    text = suggestion.axis.toOnlineReadableLabel(),
                    textColor = colors.characterAccent,
                    containerColor = colors.panelBackground.copy(alpha = 0.58f),
                    borderColor = colors.characterAccent.copy(alpha = 0.22f),
                )
                Text(
                    text = suggestion.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
            }
            Text(
                text = suggestion.text,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = colors.textPrimary,
            )
        }
    }
}

private fun RoleplaySuggestionAxis.toOnlineReadableLabel(): String {
    return when (this) {
        RoleplaySuggestionAxis.PLOT -> "推进"
        RoleplaySuggestionAxis.INFO -> "探索"
        RoleplaySuggestionAxis.EMOTION -> "情绪"
    }
}

private fun shouldShowOnlineTimestamp(
    previous: RoleplayMessageUiModel?,
    current: RoleplayMessageUiModel,
): Boolean {
    if (current.createdAt <= 0L) {
        return previous == null
    }
    if (previous == null || previous.createdAt <= 0L) {
        return true
    }
    return current.createdAt - previous.createdAt >= 20 * 60 * 1000L
}

private fun formatOnlineTime(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "刚刚"
    }
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@Composable
private fun rememberOnlinePhoneColors(
    backdropState: ImmersiveBackdropState,
    noBackgroundSkin: com.example.myapplication.model.RoleplayNoBackgroundSkinSettings,
): ImmersiveRoleplayColors {
    val palette = backdropState.palette
    val hasImage = backdropState.hasImage
    val skinSpec = rememberRoleplayNoBackgroundSkinSpec(noBackgroundSkin)
    return remember(palette, hasImage, skinSpec) {
        ImmersiveRoleplayColors(
            textPrimary = if (hasImage) palette.onGlass else skinSpec.characterText,
            userText = if (hasImage) palette.onGlass else skinSpec.userText,
            textMuted = if (hasImage) palette.onGlassMuted else skinSpec.mutedText,
            characterAccent = if (hasImage) palette.characterAccent else skinSpec.accent,
            userAccent = if (hasImage) palette.userAccent else skinSpec.userAccent,
            thoughtText = if (hasImage) palette.thoughtText else skinSpec.mutedText,
            panelBackground = if (hasImage) {
                palette.panelTintStrong.copy(alpha = 0.22f)
            } else {
                skinSpec.panel.copy(alpha = skinSpec.panel.alpha.coerceAtLeast(0.86f))
            },
            panelBackgroundStrong = if (hasImage) {
                palette.readingSurface.copy(alpha = 0.30f)
            } else {
                skinSpec.panelStrong.copy(alpha = skinSpec.panelStrong.alpha.coerceAtLeast(0.94f))
            },
            panelBorder = if (hasImage) {
                palette.panelBorder.copy(alpha = 0.28f)
            } else {
                skinSpec.border.copy(alpha = skinSpec.border.alpha.coerceAtLeast(0.22f))
            },
            errorText = Color(0xFFB3261E),
            errorBackground = Color(0xFFFFE9E8).copy(alpha = if (hasImage) 0.74f else 1f),
            errorBackgroundStrong = Color(0xFFFFDAD6).copy(alpha = if (hasImage) 0.86f else 1f),
            userBubbleBackground = if (hasImage) palette.userAccent.copy(alpha = 0.12f) else skinSpec.userBubble,
            characterBubbleBackground = if (hasImage) palette.readingSurface.copy(alpha = 0.16f) else skinSpec.characterBubble,
            narrationBubbleBackground = if (hasImage) palette.readingSurface.copy(alpha = 0.10f) else skinSpec.narrationBubble.copy(alpha = skinSpec.narrationBubble.alpha.coerceAtLeast(0.72f)),
        )
    }
}
