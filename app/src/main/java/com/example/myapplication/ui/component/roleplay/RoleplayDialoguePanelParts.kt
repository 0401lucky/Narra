package com.example.myapplication.ui.component.roleplay

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.ui.component.ExpandedDraftEditorDialog
import com.example.myapplication.ui.component.NarraIconButton
import com.example.myapplication.ui.component.NarraTextButton
import com.example.myapplication.ui.component.TransferPlayCard
import com.example.myapplication.ui.component.copyPlainTextToClipboard

internal data class ImmersiveRoleplayColors(
    val textPrimary: Color,
    val textMuted: Color,
    val characterAccent: Color,
    val userAccent: Color,
    val thoughtText: Color,
    val panelBackground: Color,
    val panelBackgroundStrong: Color,
    val errorText: Color,
    val errorBackground: Color,
    val errorBackgroundStrong: Color,
)

@Composable
internal fun rememberImmersiveRoleplayColors(
    backdropState: ImmersiveBackdropState,
): ImmersiveRoleplayColors {
    val palette = backdropState.palette
    val errorText = Color(0xFFFFB4AB)
    return remember(palette, errorText) {
        ImmersiveRoleplayColors(
            textPrimary = palette.onGlass,
            textMuted = palette.onGlassMuted,
            characterAccent = palette.characterAccent,
            userAccent = palette.userAccent,
            thoughtText = palette.thoughtText,
            panelBackground = palette.panelTint.copy(alpha = 0.24f),
            panelBackgroundStrong = palette.panelTintStrong.copy(alpha = 0.34f),
            errorText = errorText,
            errorBackground = errorText.copy(alpha = 0.12f),
            errorBackgroundStrong = errorText.copy(alpha = 0.18f),
        )
    }
}

@Composable
internal fun RoleplaySuggestionSection(
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
    if (!showPanel) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = !isSending,
                    onClick = onGenerateSuggestions,
                    onLongClick = {},
                )
                .background(
                    color = colors.panelBackground,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "AI帮写",
                style = MaterialTheme.typography.labelMedium,
                color = colors.characterAccent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isSending) "发送中" else "生成",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSending) colors.textMuted else colors.characterAccent,
            )
        }
        return
    }

    val suggestionListState = rememberLazyListState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colors.panelBackgroundStrong,
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
                color = colors.characterAccent,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                NarraTextButton(onClick = onGenerateSuggestions, enabled = !isSending && !isGeneratingSuggestions) {
                    Text(if (suggestions.isEmpty()) "重试" else "换一批", color = colors.characterAccent)
                }
                NarraTextButton(onClick = onClearSuggestions) {
                    Text("收起", color = colors.textMuted)
                }
            }
        }
        if (isGeneratingSuggestions) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.characterAccent)
                Text("正在生成建议…", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        if (!suggestionErrorMessage.isNullOrBlank()) {
            Text(suggestionErrorMessage, style = MaterialTheme.typography.bodySmall, color = colors.errorText)
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
                                color = colors.panelBackground,
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
                                color = colors.characterAccent.copy(alpha = 0.82f),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                suggestion.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.characterAccent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            suggestion.text,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyDialogueState(
    colors: ImmersiveRoleplayColors,
    modifier: Modifier = Modifier,
) {
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
                color = colors.textPrimary,
            )
            Text(
                "你的故事将从这里开始……",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
internal fun FailedTurnHint(
    colors: ImmersiveRoleplayColors,
    modifier: Modifier = Modifier,
) {
    RoleplayEmotionChip(
        text = "本回合生成失败，可长按重回",
        textColor = colors.errorText,
        containerColor = colors.errorBackground,
        modifier = modifier,
        borderColor = colors.errorText.copy(alpha = 0.24f),
    )
}

@Composable
internal fun StreamingLogText(
    content: String,
    textColor: Color,
    accentColor: Color,
) {
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
            withStyle(SpanStyle(color = accentColor.copy(alpha = cursorAlpha), fontWeight = FontWeight.Bold)) {
                append(" ▌")
            }
        },
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.6.sp,
        ),
        color = textColor,
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
internal fun RoleplayInputBar(
    colors: ImmersiveRoleplayColors,
    input: String,
    inputFocusToken: Long,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: (() -> Unit)?,
    onOpenSpecialPlay: () -> Unit,
) {
    val canSend = input.isNotBlank() && !isSending
    var showActionMenu by remember { mutableStateOf(false) }
    var showExpandedEditor by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(inputFocusToken) {
        if (inputFocusToken > 0L && !isSending) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colors.panelBackgroundStrong,
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
                        color = colors.panelBackground,
                        shape = CircleShape,
                    ),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = colors.textPrimary,
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
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .padding(vertical = 8.dp),
            enabled = !isSending,
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.characterAccent),
            decorationBox = { innerTextField ->
                if (input.isEmpty()) {
                    Text(
                        "随便聊聊…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textMuted.copy(alpha = 0.56f),
                    )
                }
                innerTextField()
            },
        )
        NarraIconButton(
            onClick = { showExpandedEditor = true },
            enabled = !isSending,
            modifier = Modifier.size(38.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = colors.panelBackground,
                contentColor = colors.textPrimary,
                disabledContainerColor = colors.panelBackground.copy(alpha = 0.45f),
                disabledContentColor = colors.textMuted.copy(alpha = 0.55f),
            ),
        ) {
            Icon(
                Icons.Default.OpenInFull,
                contentDescription = "展开输入编辑",
                modifier = Modifier.size(16.dp),
            )
        }
        if (isSending && onCancel != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.characterAccent)
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
                    containerColor = if (canSend) colors.characterAccent.copy(alpha = 0.88f) else colors.panelBackground,
                    contentColor = if (canSend) Color.Black.copy(alpha = 0.88f) else colors.textMuted.copy(alpha = 0.55f),
                ),
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", modifier = Modifier.size(16.dp)) }
        }
    }

    ExpandedDraftEditorDialog(
        visible = showExpandedEditor,
        value = input,
        placeholder = "随便聊聊…",
        onSave = onInputChange,
        onDismissRequest = { showExpandedEditor = false },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        accentColor = colors.characterAccent,
    )
}

@Composable
internal fun RoleplayMessageItem(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    onRetryTurn: (String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
) {
    when (message.contentType) {
        RoleplayContentType.NARRATION -> RoleplayMessageMenuWrapper(message, onRetryTurn, onEditUserMessage) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = colors.panelBackground,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                paragraphs.forEach { paragraph ->
                    Text(
                        text = buildQuotedDialogueAnnotatedString(
                            text = paragraph,
                            narrationColor = colors.textMuted,
                            dialogueColor = RoleplayQuotedDialogueHighlightColor,
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 25.sp,
                            letterSpacing = 0.6.sp,
                        ),
                        color = colors.textMuted,
                    )
                }
            }
        }

        RoleplayContentType.DIALOGUE -> {
            if (message.speaker == RoleplaySpeaker.USER) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    RoleplayMessageMenuWrapper(message, onRetryTurn, onEditUserMessage, Modifier.fillMaxWidth(0.82f)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = colors.panelBackgroundStrong,
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
                                color = colors.userAccent,
                            )
                            if (message.isStreaming) {
                                StreamingLogText(
                                    content = message.content,
                                    textColor = colors.textPrimary,
                                    accentColor = colors.userAccent,
                                )
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
                                            color = colors.textPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val isError = message.messageStatus == MessageStatus.ERROR
                RoleplayMessageMenuWrapper(message, onRetryTurn, onEditUserMessage) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isError) colors.errorBackground else colors.panelBackgroundStrong,
                                shape = RoundedCornerShape(22.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                message.speakerName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isError) colors.errorText else colors.characterAccent,
                            )
                            if (message.emotion.isNotBlank()) {
                                RoleplayEmotionChip(
                                    text = message.emotion,
                                    textColor = if (isError) colors.errorText else colors.characterAccent,
                                    containerColor = if (isError) colors.errorBackgroundStrong else colors.panelBackground,
                                )
                            }
                        }
                        if (isError) {
                            FailedTurnHint(colors = colors)
                        }
                        if (message.isStreaming) {
                            StreamingLogText(
                                content = message.content,
                                textColor = colors.textPrimary,
                                accentColor = colors.characterAccent,
                            )
                        } else {
                            val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                paragraphs.forEach { paragraph ->
                                    val rendered = remember(paragraph, colors, isError) {
                                        buildCharacterDialogueAnnotatedString(
                                            text = paragraph,
                                            narrationColor = if (isError) colors.errorText.copy(alpha = 0.84f) else colors.textPrimary.copy(alpha = 0.78f),
                                            dialogueColor = if (isError) colors.errorText else RoleplayQuotedDialogueHighlightColor,
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
            val isError = message.messageStatus == MessageStatus.ERROR
            RoleplayMessageMenuWrapper(message, onRetryTurn, onEditUserMessage) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isError) {
                        FailedTurnHint(colors = colors)
                    }
                    RoleplayLongformCard(
                        speakerName = message.speakerName,
                        content = message.content,
                        richTextSource = message.richTextSource,
                        containerColor = if (isError) colors.errorBackground else Color.Transparent,
                        titleColor = if (isError) colors.errorText else colors.characterAccent,
                        bodyColor = if (isError) colors.errorText.copy(alpha = 0.88f) else colors.textPrimary.copy(alpha = 0.82f),
                        accentColor = if (isError) colors.errorText else RoleplayQuotedDialogueHighlightColor,
                        thoughtColor = if (isError) colors.errorText.copy(alpha = 0.76f) else colors.thoughtText,
                    )
                }
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
                    onEditUserMessage = onEditUserMessage,
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
internal fun RoleplayMessageMenuWrapper(
    message: RoleplayMessageUiModel,
    onRetryTurn: (String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val clipboardScope = rememberCoroutineScope()
    val canCopy = message.copyText.isNotBlank()
    val canEditUserMessage = message.sourceMessageId.isNotBlank() &&
        message.speaker == RoleplaySpeaker.USER &&
        message.contentType == RoleplayContentType.DIALOGUE &&
        message.content.isNotBlank()
    var showMenu by remember(message.sourceMessageId, message.copyText, message.canRetry, canEditUserMessage) {
        mutableStateOf(false)
    }
    Box(
        modifier = modifier.combinedClickable(
            enabled = canCopy || message.canRetry || canEditUserMessage,
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
                        clipboardScope.copyPlainTextToClipboard(clipboard, "roleplay-message", message.copyText)
                        Toast.makeText(context, "已复制内容", Toast.LENGTH_SHORT).show()
                        showMenu = false
                    },
                )
            }
            if (canEditUserMessage) {
                DropdownMenuItem(
                    text = { Text("重回并编辑") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        onEditUserMessage(message.sourceMessageId)
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
