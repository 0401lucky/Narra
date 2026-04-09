package com.example.myapplication.ui.screen.roleplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.ui.component.AppSnackbarHost
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.roleplay.ImmersiveRoleplayColors
import com.example.myapplication.ui.component.roleplay.RoleplayInputBar
import com.example.myapplication.ui.component.roleplay.RoleplayMessageItem
import com.example.myapplication.ui.component.roleplay.RoleplaySuggestionSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val OnlineSurface = Color(0xFFF5F7FB)
private val OnlineCard = Color(0xFFFFFFFF)
private val OnlineMuted = Color(0xFF7F8A9A)
private val OnlineAccent = Color(0xFF5B91D7)
private val OnlineUserAccent = Color(0xFF8BC0FF)

@Composable
internal fun RoleplayOnlinePhoneContent(
    scenario: RoleplayScenario,
    assistant: Assistant?,
    settings: AppSettings,
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
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val colors = remember {
        ImmersiveRoleplayColors(
            textPrimary = Color(0xFF1F2430),
            textMuted = OnlineMuted,
            characterAccent = OnlineAccent,
            userAccent = OnlineUserAccent,
            thoughtText = OnlineMuted,
            panelBackground = Color(0xFFF0F3F9),
            panelBackgroundStrong = Color(0xFFE8EDF7),
            errorText = Color(0xFFB3261E),
            errorBackground = Color(0xFFFFE9E8),
            errorBackgroundStrong = Color(0xFFFFDAD6),
        )
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showMenu by remember { androidx.compose.runtime.mutableStateOf(false) }
    val visibleMessages = messages.filter { it.contentType != RoleplayContentType.SYSTEM }

    LaunchedEffect(visibleMessages.size, visibleMessages.lastOrNull()?.content) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnlineSurface)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = OnlineCard,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NarraIconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = scenario.characterDisplayNameOverride.trim()
                            .ifBlank { assistant?.name?.trim().orEmpty() }
                            .ifBlank { "角色" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append("线上模式")
                            if (contextStatus.isContinuingSession) append(" · 继续聊天")
                            contextStatus.summaryCoveredMessageCount.takeIf { it > 0 }?.let {
                                append(" · 摘要 $it")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OnlineMuted,
                    )
                }
                NarraIconButton(onClick = onOpenPhoneCheck) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "查手机",
                    )
                }
                Box {
                    NarraIconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多",
                        )
                    }
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

        pendingMemoryProposal?.let { proposal ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = OnlineCard,
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
                    key = { _, item -> "${item.sourceMessageId}-${item.createdAt}-${item.contentType}-${item.copyText.hashCode()}" },
                ) { index, message ->
                    val previous = visibleMessages.getOrNull(index - 1)
                    if (shouldShowOnlineTimestamp(previous, message)) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(
                                    text = formatOnlineTime(message.createdAt),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnlineMuted,
                                )
                            }
                        }
                    }
                    RoleplayMessageItem(
                        message = message,
                        colors = colors,
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
                    )
                }
            }

            AppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )

            if (isSending) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 12.dp),
                    color = OnlineCard,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = "对方正在输入…",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnlineMuted,
                    )
                }
            }
        }

        if (settings.showRoleplayAiHelper) {
            RoleplaySuggestionSection(
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
                color = OnlineCard,
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
                            color = OnlineMuted,
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

        RoleplayInputBar(
            colors = colors,
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
