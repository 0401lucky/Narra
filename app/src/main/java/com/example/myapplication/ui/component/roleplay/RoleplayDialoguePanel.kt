package com.example.myapplication.ui.component.roleplay

import com.example.myapplication.ui.component.*

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.ui.component.TransferPlayCard

// 沉浸风文字颜色
private val ImmersiveTextWhite = Color.White.copy(alpha = 0.92f)
private val ImmersiveTextMuted = Color.White.copy(alpha = 0.62f)
private val ImmersiveAccentBlue = Color(0xFF7EB8E8)
private val ImmersiveUserAccent = Color(0xFFA8D8EA)

@Composable
fun RoleplayDialoguePanel(
    backdropState: ImmersiveBackdropState,
    messages: List<RoleplayMessageUiModel>,
    suggestions: List<RoleplaySuggestionUiModel>,
    isGeneratingSuggestions: Boolean,
    suggestionErrorMessage: String?,
    showAiHelper: Boolean,
    input: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onGenerateSuggestions: () -> Unit,
    onApplySuggestion: (String) -> Unit,
    onClearSuggestions: () -> Unit,
    onRetryTurn: (String) -> Unit,
    onOpenSpecialPlay: () -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val storyMessages = messages.filter { it.contentType != RoleplayContentType.SYSTEM }
    val listState = rememberLazyListState()
    val shouldStickToBottom by remember(listState, storyMessages.size) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (storyMessages.isEmpty()) {
                true
            } else {
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisibleIndex >= layoutInfo.totalItemsCount - 2
            }
        }
    }
    LaunchedEffect(storyMessages.size, storyMessages.lastOrNull()?.content?.length, storyMessages.lastOrNull()?.isStreaming) {
        if (storyMessages.isNotEmpty() && shouldStickToBottom) {
            listState.animateScrollToItem(storyMessages.lastIndex)
        }
    }
    Column(
        modifier = modifier.fillMaxWidth()
            .padding(start = 14.dp, top = 0.dp, end = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (storyMessages.isEmpty()) {
            EmptyDialogueState(modifier = Modifier.weight(1f, fill = true))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(storyMessages, key = { "${it.sourceMessageId}-${it.createdAt}-${it.contentType}-${it.copyText.hashCode()}" }) { message ->
                    RoleplayMessageItem(message, onRetryTurn, onConfirmTransferReceipt)
                }
            }
        }
        if (showAiHelper) {
            RoleplaySuggestionSection(
                suggestions = suggestions,
                isGeneratingSuggestions = isGeneratingSuggestions,
                suggestionErrorMessage = suggestionErrorMessage,
                isSending = isSending,
                onGenerateSuggestions = onGenerateSuggestions,
                onApplySuggestion = onApplySuggestion,
                onClearSuggestions = onClearSuggestions,
            )
        }
        RoleplayInputBar(input, isSending, onInputChange, onSend, onCancel, onOpenSpecialPlay)
    }
}

@Composable
private fun RoleplaySuggestionSection(
    suggestions: List<RoleplaySuggestionUiModel>,
    isGeneratingSuggestions: Boolean,
    suggestionErrorMessage: String?,
    isSending: Boolean,
    onGenerateSuggestions: () -> Unit,
    onApplySuggestion: (String) -> Unit,
    onClearSuggestions: () -> Unit,
) {
    val showPanel = suggestions.isNotEmpty() || isGeneratingSuggestions || !suggestionErrorMessage.isNullOrBlank()
    if (!showPanel) {
        // 收起态：半透明条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = !isSending,
                    onClick = onGenerateSuggestions,
                    onLongClick = {},
                )
                .background(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "AI帮写",
                style = MaterialTheme.typography.labelMedium,
                color = ImmersiveAccentBlue,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isSending) "发送中" else "生成",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSending) ImmersiveTextMuted else ImmersiveAccentBlue,
            )
        }
        return
    }

    val suggestionListState = rememberLazyListState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "AI帮写",
                style = MaterialTheme.typography.labelMedium,
                color = ImmersiveAccentBlue,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                NarraTextButton(onClick = onGenerateSuggestions, enabled = !isSending && !isGeneratingSuggestions) {
                    Text(if (suggestions.isEmpty()) "重试" else "换一批", color = ImmersiveAccentBlue)
                }
                NarraTextButton(onClick = onClearSuggestions) {
                    Text("收起", color = ImmersiveTextMuted)
                }
            }
        }
        if (isGeneratingSuggestions) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ImmersiveAccentBlue)
                Text("正在生成建议…", style = MaterialTheme.typography.bodySmall, color = ImmersiveTextMuted)
            }
        }
        if (!suggestionErrorMessage.isNullOrBlank()) {
            Text(suggestionErrorMessage, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF8A80))
        }
        if (suggestions.isNotEmpty()) {
            LazyColumn(
                state = suggestionListState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(suggestions, key = { it.id }) { suggestion ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                enabled = !isSending,
                                onClick = { onApplySuggestion(suggestion.text) },
                                onLongClick = {},
                            )
                            .background(
                                color = Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = suggestion.axis.toReadableLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = ImmersiveAccentBlue.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                suggestion.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = ImmersiveAccentBlue,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            suggestion.text,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                            color = ImmersiveTextWhite,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDialogueState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "输入第一句对白",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = ImmersiveTextWhite,
            )
            Text(
                "你的故事将从这里开始……",
                style = MaterialTheme.typography.bodyMedium,
                color = ImmersiveTextMuted,
            )
        }
    }
}

@Composable
private fun StreamingLogText(content: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "roleplay_log_cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 520), repeatMode = RepeatMode.Reverse),
        label = "roleplay_log_cursor_alpha",
    )
    Text(
        text = buildAnnotatedString {
            append(content)
            withStyle(SpanStyle(color = ImmersiveAccentBlue.copy(alpha = cursorAlpha), fontWeight = FontWeight.Bold)) {
                append(" ▌")
            }
        },
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.6.sp,
        ),
        color = ImmersiveTextWhite,
    )
}

private fun com.example.myapplication.model.RoleplaySuggestionAxis.toReadableLabel(): String {
    return when (this) {
        com.example.myapplication.model.RoleplaySuggestionAxis.PLOT -> "推进"
        com.example.myapplication.model.RoleplaySuggestionAxis.INFO -> "探索"
        com.example.myapplication.model.RoleplaySuggestionAxis.EMOTION -> "情绪"
    }
}

@Composable
private fun RoleplayInputBar(
    input: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: (() -> Unit)?,
    onOpenSpecialPlay: () -> Unit,
) {
    val canSend = input.isNotBlank() && !isSending
    var showActionMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(start = 8.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            NarraIconButton(
                onClick = { showActionMenu = true },
                enabled = !isSending,
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = CircleShape,
                    ),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = ImmersiveTextWhite,
                ),
            ) { Icon(Icons.Default.Add, contentDescription = "更多玩法", modifier = Modifier.size(18.dp)) }
            DropdownMenu(expanded = showActionMenu, onDismissRequest = { showActionMenu = false }) {
                DropdownMenuItem(
                    text = { Text("特殊玩法") },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = { showActionMenu = false; onOpenSpecialPlay() },
                )
            }
        }
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            enabled = !isSending,
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = ImmersiveTextWhite),
            cursorBrush = SolidColor(ImmersiveAccentBlue),
            decorationBox = { innerTextField ->
                if (input.isEmpty()) {
                    Text(
                        "随便聊聊…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.32f),
                    )
                }
                innerTextField()
            },
        )
        if (isSending && onCancel != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ImmersiveAccentBlue)
                NarraIconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(38.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFFF5252).copy(alpha = 0.7f),
                        contentColor = Color.White,
                    ),
                ) { Icon(Icons.Default.Close, contentDescription = "取消", modifier = Modifier.size(16.dp)) }
            }
        } else {
            NarraIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (canSend) ImmersiveAccentBlue.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f),
                    contentColor = if (canSend) Color.Black.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.25f),
                ),
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun RoleplayMessageItem(
    message: RoleplayMessageUiModel,
    onRetryTurn: (String) -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
) {
    when (message.contentType) {
        RoleplayContentType.NARRATION -> RoleplayMessageMenuWrapper(message, onRetryTurn) {
            // 旁白：居中斜体，无卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                paragraphs.forEach { paragraph ->
                    Text(
                        paragraph,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 25.sp,
                            letterSpacing = 0.6.sp,
                        ),
                        color = ImmersiveTextMuted,
                    )
                }
            }
        }

        RoleplayContentType.DIALOGUE -> {
            if (message.speaker == RoleplaySpeaker.USER) {
                // 用户消息：右对齐，半透明背景
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    RoleplayMessageMenuWrapper(message, onRetryTurn, Modifier.fillMaxWidth(0.82f)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color.White.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(
                                        topStart = 20.dp,
                                        topEnd = 6.dp,
                                        bottomStart = 20.dp,
                                        bottomEnd = 20.dp,
                                    ),
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                message.speakerName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ImmersiveUserAccent,
                            )
                            if (message.isStreaming) {
                                StreamingLogText(message.content)
                            } else {
                                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    paragraphs.forEach { paragraph ->
                                        Text(
                                            paragraph,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 16.sp,
                                                lineHeight = 26.sp,
                                                letterSpacing = 0.6.sp,
                                            ),
                                            color = ImmersiveTextWhite,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 角色消息：左对齐，无背景，直接内联
                RoleplayMessageMenuWrapper(message, onRetryTurn) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                message.speakerName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = ImmersiveAccentBlue,
                            )
                            if (message.emotion.isNotBlank()) {
                                Text(
                                    message.emotion,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ImmersiveTextMuted,
                                )
                            }
                        }
                        if (message.isStreaming) {
                            StreamingLogText(message.content)
                        } else {
                            val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                paragraphs.forEach { paragraph ->
                                    // 引号对话加粗突出，叙述略淡
                                    val rendered = remember(paragraph) {
                                        buildQuotedDialogueAnnotatedString(
                                            text = paragraph,
                                            narrationColor = ImmersiveTextWhite.copy(alpha = 0.78f),
                                            dialogueColor = ImmersiveTextWhite,
                                        )
                                    }
                                    Text(
                                        text = rendered,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 16.sp,
                                            lineHeight = 26.sp,
                                            letterSpacing = 0.6.sp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        RoleplayContentType.LONGFORM -> {
            RoleplayMessageMenuWrapper(message, onRetryTurn) {
                RoleplayLongformCard(
                    speakerName = message.speakerName,
                    content = message.content,
                    containerColor = Color.Transparent,
                    titleColor = ImmersiveAccentBlue,
                    bodyColor = ImmersiveTextWhite.copy(alpha = 0.78f),
                    accentColor = ImmersiveTextWhite,
                )
            }
        }

        RoleplayContentType.SPECIAL_TRANSFER -> {
            val specialPart = message.specialPart ?: return
            val isUserMessage = message.speaker == RoleplaySpeaker.USER
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                RoleplayMessageMenuWrapper(
                    message = message,
                    onRetryTurn = onRetryTurn,
                    modifier = if (isUserMessage) Modifier.fillMaxWidth(0.82f) else Modifier.fillMaxWidth(),
                ) {
                    TransferPlayCard(
                        part = specialPart,
                        isUserMessage = isUserMessage,
                        onConfirmTransferReceipt = onConfirmTransferReceipt,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        RoleplayContentType.SYSTEM -> Unit
    }
}

@Composable
private fun RoleplayMessageMenuWrapper(
    message: RoleplayMessageUiModel,
    onRetryTurn: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val canCopy = message.copyText.isNotBlank()
    var showMenu by remember(message.sourceMessageId, message.copyText, message.canRetry) { mutableStateOf(false) }
    Box(
        modifier = modifier.combinedClickable(
            enabled = canCopy || message.canRetry,
            onClick = {},
            onLongClick = { showMenu = true },
        ),
    ) {
        content()
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (canCopy) {
                DropdownMenuItem(
                    text = { Text("复制内容") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.copyText))
                        Toast.makeText(context, "已复制内容", Toast.LENGTH_SHORT).show()
                        showMenu = false
                    },
                )
            }
            if (message.canRetry && message.sourceMessageId.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("重回此回合") },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    onClick = {
                        onRetryTurn(message.sourceMessageId)
                        showMenu = false
                    },
                )
            }
        }
    }
}
