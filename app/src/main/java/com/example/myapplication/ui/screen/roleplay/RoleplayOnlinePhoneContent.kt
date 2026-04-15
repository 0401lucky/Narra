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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.RoleplaySuggestionAxis
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.roleplay.ImmersiveBackdropState
import com.example.myapplication.ui.component.roleplay.ImmersiveGlassSurface
import com.example.myapplication.ui.component.roleplay.ImmersiveRoleplayColors
import com.example.myapplication.ui.component.roleplay.RoleplaySceneBackground
import com.example.myapplication.ui.component.roleplay.RoleplayEmotionChip
import com.example.myapplication.ui.component.roleplay.RoleplayInputBar
import com.example.myapplication.ui.component.roleplay.RoleplayMessageItem
import com.example.myapplication.ui.component.roleplay.RoleplayMessageBubbleMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val OnlineFallbackCard = Color(0xFFFFFFFF)
private val OnlineTextPrimary = Color(0xFF1F2430)
private val OnlineMuted = Color(0xFF7F8A9A)
private val OnlineAccent = Color(0xFF5B91D7)
private val OnlineUserAccent = Color(0xFF8BC0FF)

@Composable
internal fun RoleplayOnlinePhoneContent(
    scenario: RoleplayScenario,
    assistant: Assistant?,
    settings: AppSettings,
    backdropState: ImmersiveBackdropState,
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
    onClearQuotedMessage: () -> Unit,
    onRecallMessage: (String) -> Unit,
    onScreenshotChat: () -> Unit,
    onOpenSpecialPlay: () -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
    onSend: () -> Unit,
    onCancelSending: () -> Unit,
    onApprovePendingMemoryProposal: () -> Unit,
    onRejectPendingMemoryProposal: () -> Unit,
    onOpenPhoneCheck: () -> Unit,
    onOpenMoments: () -> Unit,
    onOpenVideoCall: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val immersiveMode = settings.roleplayImmersiveMode
    ApplyRoleplaySystemBars(
        backdropState = backdropState,
        immersiveMode = immersiveMode,
    )
    val colors = rememberOnlinePhoneColors(backdropState)
    val palette = backdropState.palette
    val statusBarTopPadding = if (immersiveMode == RoleplayImmersiveMode.NONE) {
        0.dp
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    }
    val chromeSurfaceColor = remember(backdropState.hasImage, colors.panelBackgroundStrong) {
        colors.panelBackgroundStrong.copy(
            alpha = if (backdropState.hasImage) 0.92f else 1f,
        )
    }
    val headerOverlayColor = remember(backdropState.hasImage, colors.panelBackgroundStrong) {
        colors.panelBackgroundStrong.copy(
            alpha = if (backdropState.hasImage) 0.76f else 0.94f,
        )
    }
    val timestampSurfaceColor = remember(backdropState.hasImage, colors.panelBackground) {
        colors.panelBackground.copy(
            alpha = if (backdropState.hasImage) 0.72f else 0.92f,
        )
    }
    val topChromeScrim = remember(backdropState.hasImage, palette, headerOverlayColor) {
        Brush.verticalGradient(
            colors = listOf(
                palette.scrimTop.copy(alpha = if (backdropState.hasImage) 0.74f else 0.24f),
                headerOverlayColor.copy(alpha = if (backdropState.hasImage) 0.96f else 1f),
                Color.Transparent,
            ),
        )
    }
    val onlineBackdropScrim = remember(backdropState.hasImage, palette) {
        Brush.verticalGradient(
            colors = listOf(
                palette.scrimTop.copy(alpha = if (backdropState.hasImage) 0.30f else 0.14f),
                palette.scrimBottom.copy(alpha = if (backdropState.hasImage) 0.54f else 0.24f),
            ),
        )
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    val characterName = remember(scenario.characterDisplayNameOverride, assistant?.name) {
        scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
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
                .then(
                    if (immersiveMode == RoleplayImmersiveMode.NONE) {
                        Modifier
                    } else {
                        Modifier.navigationBarsPadding()
                    },
                ),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusBarTopPadding + 44.dp)
                        .background(topChromeScrim),
                )
                ImmersiveGlassSurface(
                    backdropState = backdropState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = statusBarTopPadding + 6.dp, bottom = 6.dp),
                    shape = RoundedCornerShape(22.dp),
                    blurRadius = 22.dp,
                    overlayColor = headerOverlayColor,
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
                                    append("线上模式")
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
                        "${item.sourceMessageId}-${item.createdAt}-${item.contentType}-${item.copyText.hashCode()}-$index"
                    },
                ) { index, message ->
                    val previous = visibleMessages.getOrNull(index - 1)
                    if (shouldShowOnlineTimestamp(previous, message)) {
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
                    )
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "引用 ${replyToSpeakerName.ifBlank { "对方" }}",
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
                onOpenSpecialPlay = onOpenSpecialPlay,
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
        modifier = Modifier
            .size(36.dp)
            .background(
                color = colors.panelBackground.copy(alpha = 0.56f),
                shape = CircleShape,
            ),
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
): ImmersiveRoleplayColors {
    val palette = backdropState.palette
    val hasImage = backdropState.hasImage
    return remember(palette, hasImage) {
        ImmersiveRoleplayColors(
            textPrimary = if (hasImage) palette.onGlass else OnlineTextPrimary,
            textMuted = if (hasImage) palette.onGlassMuted else OnlineMuted,
            characterAccent = if (hasImage) palette.characterAccent else OnlineAccent,
            userAccent = if (hasImage) palette.userAccent else OnlineUserAccent,
            thoughtText = if (hasImage) palette.thoughtText else OnlineMuted,
            panelBackground = if (hasImage) {
                palette.panelTintStrong.copy(alpha = 0.56f)
            } else {
                OnlineFallbackCard.copy(alpha = 0.86f)
            },
            panelBackgroundStrong = if (hasImage) {
                palette.readingSurface.copy(alpha = 0.74f)
            } else {
                OnlineFallbackCard.copy(alpha = 0.94f)
            },
            panelBorder = if (hasImage) {
                palette.panelBorder.copy(alpha = 0.28f)
            } else {
                OnlineMuted.copy(alpha = 0.22f)
            },
            errorText = Color(0xFFB3261E),
            errorBackground = Color(0xFFFFE9E8).copy(alpha = if (hasImage) 0.74f else 1f),
            errorBackgroundStrong = Color(0xFFFFDAD6).copy(alpha = if (hasImage) 0.86f else 1f),
        )
    }
}
