package com.example.myapplication.ui.screen.roleplay

import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessagePart
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
import com.example.myapplication.ui.component.roleplay.RoleplayStreamingAutoScrollEffect
import com.example.myapplication.ui.component.roleplay.RoleplayStreamingScrollSignal
import com.example.myapplication.ui.component.roleplay.rememberRoleplayNoBackgroundSkinSpec
import com.example.myapplication.ui.component.roleplay.rememberImmersiveRoleplayColors
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget
import com.example.myapplication.ui.component.roleplay.rememberRoleplayAutoFollowScrollConnection
import com.example.myapplication.ui.component.roleplay.rememberRoleplayStreamingAutoScrollState
import com.example.myapplication.ui.component.roleplay.roleplayLazyContentType
import com.example.myapplication.ui.component.roleplay.shouldUseRoleplayGlassBubble
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val OnlinePhoneJumpButtonSize = NarraMinimumTouchTarget
private const val OnlinePhoneJumpButtonMessageThreshold = 4

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
    onRetryAiPhoto: (String, String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    onQuoteMessage: (String, String, String) -> Unit,
    onPokeMessageAvatar: (RoleplayMessageUiModel) -> Unit,
    onClearQuotedMessage: () -> Unit,
    onRecallMessage: (String) -> Unit,
    onDeleteMessage: ((String) -> Unit)? = null,
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
    pendingImageParts: List<ChatMessagePart> = emptyList(),
    onPickImage: (() -> Unit)? = null,
    onRemovePendingImagePart: (ChatMessagePart) -> Unit = {},
    onRequestGroupParticipantReply: (String) -> Unit,
    onCancelSending: () -> Unit,
    onApprovePendingMemoryProposal: () -> Unit,
    onRejectPendingMemoryProposal: () -> Unit,
    onOpenPhoneCheck: () -> Unit,
    onOpenMoments: () -> Unit,
    onOpenVideoCall: () -> Unit,
    onOpenMailbox: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val immersiveMode = settings.roleplayImmersiveMode
    ApplyRoleplaySystemBars(
        backdropState = backdropState,
        immersiveMode = immersiveMode,
    )
    val colors = rememberOnlinePhoneColors(
        backdropState = backdropState,
        noBackgroundSkin = settings.roleplayNoBackgroundSkin,
    )
    val useGlassBubble = shouldUseRoleplayGlassBubble(
        hasBackgroundImage = backdropState.hasImage,
        bubbleMode = RoleplayMessageBubbleMode.ONLINE_PHONE,
        onlineBackgroundGlassEnabled = settings.roleplayNoBackgroundSkin.onlineBackgroundGlassEnabled,
    )
    val messageColors = rememberOnlinePhoneColors(
        backdropState = backdropState,
        noBackgroundSkin = settings.roleplayNoBackgroundSkin,
        useGlassColors = useGlassBubble,
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
    val onlineBackdropScrim = remember(backdropState) { roleplayOnlineBackdropScrim(backdropState) }
    val listState = rememberLazyListState()
    val autoScrollState = rememberRoleplayStreamingAutoScrollState(scenario.id)
    val autoFollowScrollConnection = rememberRoleplayAutoFollowScrollConnection(autoScrollState)
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showAiHelperPanel by remember { mutableStateOf(false) }
    val quickActions = remember(
        colorScheme,
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
        onOpenWallet,
    ) {
        buildList {
            add(
                RoleplayInputQuickAction(
                    label = "日记",
                    icon = Icons.Default.AutoStories,
                    accentColor = colorScheme.secondary,
                    onClick = onOpenDiary,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "语音",
                    icon = Icons.Default.KeyboardVoice,
                    accentColor = colorScheme.primary,
                    onClick = onOpenVoiceMessage,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "查手机",
                    icon = Icons.Default.Visibility,
                    accentColor = colorScheme.primary,
                    onClick = onOpenPhoneCheck,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "转账",
                    icon = Icons.Default.Share,
                    accentColor = colorScheme.primary,
                    onClick = onOpenTransferPlay,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "邀约",
                    icon = Icons.Default.Event,
                    accentColor = colorScheme.primary,
                    onClick = onOpenInvitePlay,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "礼物",
                    icon = Icons.Default.CardGiftcard,
                    accentColor = colorScheme.tertiary,
                    onClick = onOpenGiftPlay,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "委托",
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    accentColor = colorScheme.secondary,
                    onClick = onOpenTaskPlay,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "惩罚",
                    icon = Icons.Default.Gavel,
                    accentColor = colorScheme.error,
                    onClick = onOpenPunishPlay,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "动态",
                    icon = Icons.Default.Forum,
                    accentColor = colorScheme.tertiary,
                    onClick = onOpenMoments,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "信箱",
                    icon = Icons.Default.Mail,
                    accentColor = colorScheme.primary,
                    onClick = onOpenMailbox,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "钱包",
                    icon = Icons.Default.AccountBalanceWallet,
                    accentColor = colorScheme.primary,
                    onClick = onOpenWallet,
                ),
            )
            add(
                RoleplayInputQuickAction(
                    label = "视频",
                    icon = Icons.Default.Videocam,
                    accentColor = colorScheme.primary,
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
    val lastVisibleMessage = visibleMessages.lastOrNull()
    RoleplayStreamingAutoScrollEffect(
        state = autoScrollState,
        listState = listState,
        conversationIdentity = scenario.id,
        signal = RoleplayStreamingScrollSignal(
            lastUiItemId = lastVisibleMessage?.uiItemId,
            itemCount = visibleMessages.size,
            isStreaming = lastVisibleMessage?.isStreaming == true,
            contentVersion = lastVisibleMessage?.content?.length ?: 0,
        ),
    )
    val isAtTop by remember(listState, visibleMessages.size) {
        derivedStateOf {
            visibleMessages.isEmpty() ||
                (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 8)
        }
    }
    val isAtBottomExact by remember(listState, visibleMessages.size) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            visibleMessages.isEmpty() || (
                lastVisibleItem != null &&
                    lastVisibleItem.index >= layoutInfo.totalItemsCount - 1 &&
                    !listState.canScrollForward &&
                    (lastVisibleItem.offset + lastVisibleItem.size) <= layoutInfo.viewportEndOffset + 8
                )
        }
    }
    val showJumpButtons = visibleMessages.size > OnlinePhoneJumpButtonMessageThreshold
    val hasAiHelperActivity = suggestions.isNotEmpty() ||
        isGeneratingSuggestions ||
        !suggestionErrorMessage.isNullOrBlank()
    val showAiHelperEntry = settings.showRoleplayAiHelper
    val showAiHelperContent = showAiHelperEntry && showAiHelperPanel

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val expandedPanelMaxHeight = maxHeight / 3
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
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().testTag("roleplay_online_header")) {
                val compactHeader = maxWidth < NarraMinimumTouchTarget * 7 || LocalDensity.current.fontScale >= 1.5f
                ImmersiveGlassSurface(
                    backdropState = backdropState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NarraUi.space12)
                        .padding(top = statusBarTopPadding + NarraUi.space8, bottom = NarraUi.space8),
                    shape = NarraShapes.large,
                    blurRadius = 22.dp,
                    overlayColor = chromeSurfaceColor,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NarraUi.space8, vertical = NarraUi.space8),
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
                                .padding(horizontal = NarraUi.space8),
                            verticalArrangement = Arrangement.spacedBy(NarraUi.space4),
                        ) {
                            Text(
                                text = characterName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.panelText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = buildString {
                                    append(if (scenario.isGroupChat) "${groupParticipants.size} 位成员" else "线上模式")
                                    if (scenario.isGroupChat) append(" · ${scenario.groupReplyMode.displayName}")
                                    if (contextStatus.isContinuingSession) append(" · 继续会话")
                                    if (isSending) append(" · 正在输入")
                                    contextStatus.summaryCoveredMessageCount.takeIf { it > 0 }?.let { count ->
                                        append(" · 摘要 $count")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.panelMutedText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!compactHeader) {
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
                        }
                        if (isSending) {
                            OnlinePhoneHeaderButton(
                                onClick = onCancelSending,
                                icon = Icons.Default.Close,
                                contentDescription = "停止生成",
                                colors = colors,
                                containerColor = colors.errorBackgroundStrong.copy(alpha = 0.72f),
                                contentColor = colors.errorText,
                            )
                        }
                        Box {
                            OnlinePhoneHeaderButton(
                                onClick = { showMenu = true },
                                icon = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                colors = colors,
                            )
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                if (compactHeader) {
                                    DropdownMenuItem(text = { Text("动态") }, onClick = {
                                        showMenu = false
                                        onOpenMoments()
                                    })
                                    DropdownMenuItem(text = { Text("查手机") }, onClick = {
                                        showMenu = false
                                        onOpenPhoneCheck()
                                    })
                                }
                                DropdownMenuItem(
                                    text = { Text("截取会话") },
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
                    .padding(horizontal = NarraUi.space12, vertical = NarraUi.space8),
                color = chromeSurfaceColor,
                shape = NarraShapes.medium,
            ) {
                Column(
                    modifier = Modifier.padding(NarraUi.space16),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
                ) {
                    Text("建议记住一条长期设定", fontWeight = FontWeight.SemiBold, color = colors.characterAccent)
                    Text(proposal.content, color = colors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(NarraUi.space8)) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("roleplay_online_message_list")
                    .nestedScroll(autoFollowScrollConnection),
                contentPadding = PaddingValues(horizontal = NarraUi.space12, vertical = NarraUi.space16),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
            ) {
                itemsIndexed(
                    items = visibleMessages,
                    key = { _, message -> message.uiItemId },
                    contentType = { _, message -> roleplayLazyContentType(message) },
                ) { index, message ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        val previous = visibleMessages.getOrNull(index - 1)
                        if (scenario.enableTimeAwareness && shouldShowOnlineTimestamp(previous, message)) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    color = colors.systemBackground,
                                    shape = NarraShapes.pill,
                                ) {
                                    Text(
                                        text = formatOnlineTime(message.createdAt),
                                        modifier = Modifier.padding(horizontal = NarraUi.space8, vertical = NarraUi.space4),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.systemText,
                                    )
                                }
                            }
                        }
                        RoleplayMessageItem(
                            message = message,
                            colors = messageColors,
                            backdropState = backdropState,
                            onRetryTurn = onRetryTurn,
                            onRetryAiPhoto = onRetryAiPhoto,
                            onEditUserMessage = onEditUserMessage,
                            onQuoteMessage = onQuoteMessage,
                            onPokeMessageAvatar = onPokeMessageAvatar,
                            onRecallMessage = onRecallMessage,
                            onDeleteMessage = onDeleteMessage,
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

            if (showJumpButtons && !isAtTop) {
                NarraIconButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = NarraUi.space8, end = NarraUi.space8)
                        .size(OnlinePhoneJumpButtonSize),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = colors.panelBackgroundStrong.copy(alpha = 0.92f),
                        contentColor = colors.textPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "跳到顶部",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (showJumpButtons && !isAtBottomExact) {
                NarraIconButton(
                    onClick = {
                        scope.launch {
                            autoScrollState.scrollToEnd(
                                listState = listState,
                                lastIndex = visibleMessages.lastIndex,
                                animated = true,
                                resumeAutoFollow = true,
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = NarraUi.space8, bottom = NarraUi.space12)
                        .size(OnlinePhoneJumpButtonSize),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "跳到底部",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            AppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = NarraUi.space8),
            )
            }

        if (showAiHelperContent) {
            RoleplayOnlineAiHelperBar(
                modifier = Modifier
                    .padding(horizontal = NarraUi.space12, vertical = NarraUi.space8)
                    .heightIn(max = expandedPanelMaxHeight)
                    .verticalScroll(rememberScrollState()),
                backdropState = backdropState,
                colors = colors,
                suggestions = suggestions,
                isGeneratingSuggestions = isGeneratingSuggestions,
                suggestionErrorMessage = suggestionErrorMessage,
                isSending = isSending,
                onGenerateSuggestions = onGenerateSuggestions,
                onApplySuggestion = onApplySuggestion,
                onClearSuggestions = {
                    onClearSuggestions()
                    showAiHelperPanel = false
                },
            )
        }

        if (replyToMessageId.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NarraUi.space12, vertical = NarraUi.space8),
                color = chromeSurfaceColor,
                shape = NarraShapes.medium,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NarraUi.space12, vertical = NarraUi.space8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier
                            .width(3.dp)
                            .height(36.dp),
                        shape = NarraShapes.pill,
                        color = colors.characterAccent.copy(alpha = 0.68f),
                    ) {}
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = NarraUi.space8),
                        verticalArrangement = Arrangement.spacedBy(NarraUi.space4),
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
                .testTag("roleplay_online_input")
                .padding(horizontal = NarraUi.space12, vertical = NarraUi.space8),
        ) {
            RoleplayInputBar(
                colors = colors,
                backdropState = backdropState,
                input = input,
                inputFocusToken = inputFocusToken,
                isSending = isSending,
                onInputChange = onInputChange,
                onSend = onSend,
                onRequestMentionCandidateReply = if (scenario.isGroupChat) {
                    { candidate -> onRequestGroupParticipantReply(candidate.id) }
                } else {
                    null
                },
                showAiHelperButton = showAiHelperEntry,
                aiHelperActive = showAiHelperContent || hasAiHelperActivity,
                onToggleAiHelper = if (showAiHelperEntry) {
                    {
                        if (showAiHelperPanel) {
                            showAiHelperPanel = false
                        } else {
                            showAiHelperPanel = true
                            if (!hasAiHelperActivity) {
                                onGenerateSuggestions()
                            }
                        }
                    }
                } else {
                    null
                },
                onCancel = onCancelSending,
                onOpenSpecialPlay = onOpenTransferPlay,
                quickActions = quickActions,
                mentionCandidates = groupMentionCandidates,
                pendingImageParts = pendingImageParts,
                onPickImage = onPickImage,
                onRemovePendingImagePart = onRemovePendingImagePart,
                expandedPanelMaxHeight = expandedPanelMaxHeight,
            )
        }
    }

}

}

internal fun roleplayOnlineBackdropScrim(backdropState: ImmersiveBackdropState): Brush = Brush.verticalGradient(
    colors = listOf(
        backdropState.palette.scrimTop.copy(alpha = if (backdropState.hasImage) 0.14f else 0f),
        backdropState.palette.scrimBottom.copy(alpha = if (backdropState.hasImage) 0.28f else 0f),
    ),
)

@Composable
private fun OnlinePhoneHeaderButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    colors: ImmersiveRoleplayColors,
    containerColor: Color = Color.Transparent,
    contentColor: Color = colors.panelText,
) {
    NarraIconButton(
        onClick = onClick,
        modifier = Modifier.size(NarraMinimumTouchTarget),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
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
    val shape = NarraShapes.large
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
                .padding(horizontal = NarraUi.space16, vertical = NarraUi.space12),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = NarraUi.space12),
                    verticalArrangement = Arrangement.spacedBy(NarraUi.space4),
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
                    horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showPanel) {
                        androidx.compose.material3.TextButton(onClick = onClearSuggestions) {
                            Text(
                                text = if (isGeneratingSuggestions) "停止" else "收起",
                                color = if (isGeneratingSuggestions) colors.errorText else colors.textMuted,
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
                        horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
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
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .heightIn(min = NarraMinimumTouchTarget)
            .testTag("roleplay_ai_helper_primary_action")
            .background(
                color = if (enabled) {
                    scheme.primary
                } else {
                    colors.panelBackground.copy(alpha = 0.88f)
                },
                shape = NarraShapes.pill,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = NarraUi.space16, vertical = NarraUi.space8),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (enabled) {
                scheme.onPrimary
            } else {
                colors.panelMutedText
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
    val shape = NarraShapes.medium
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
                .padding(horizontal = NarraUi.space12, vertical = NarraUi.space12),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
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
    useGlassColors: Boolean = backdropState.hasImage,
): ImmersiveRoleplayColors = rememberImmersiveRoleplayColors(
    backdropState = backdropState,
    noBackgroundSkin = noBackgroundSkin,
    useGlassColors = useGlassColors,
)
