package com.example.myapplication.ui.component.roleplay

import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.ui.component.AssistantAvatar
import com.example.myapplication.ui.component.SpecialPlayCard
import com.example.myapplication.ui.component.StatusCardPart
import com.example.myapplication.ui.component.copyPlainTextToClipboard

@Composable
internal fun RoleplayMessageItem(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    onRetryTurn: (String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    onQuoteMessage: ((String, String, String) -> Unit)? = null,
    onPokeMessageAvatar: ((RoleplayMessageUiModel) -> Unit)? = null,
    onRecallMessage: ((String) -> Unit)? = null,
    onOpenQuotedMessage: ((String) -> Unit)? = null,
    onConfirmTransferReceipt: (String) -> Unit,
    onOpenVideoCall: (() -> Unit)? = null,
    lineHeightScale: Float = 1.0f,
    bubbleMode: RoleplayMessageBubbleMode = RoleplayMessageBubbleMode.DEFAULT,
    noBackgroundSkin: RoleplayNoBackgroundSkinSettings = RoleplayNoBackgroundSkinSettings(),
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        RoleplayMessageItemContent(
            message = message,
            colors = colors,
            backdropState = backdropState,
            onRetryTurn = onRetryTurn,
            onEditUserMessage = onEditUserMessage,
            onQuoteMessage = onQuoteMessage,
            onPokeMessageAvatar = onPokeMessageAvatar,
            onRecallMessage = onRecallMessage,
            onOpenQuotedMessage = onOpenQuotedMessage,
            onConfirmTransferReceipt = onConfirmTransferReceipt,
            onOpenVideoCall = onOpenVideoCall,
            lineHeightScale = lineHeightScale,
            bubbleMode = bubbleMode,
            noBackgroundSkin = noBackgroundSkin,
        )
    }
}

@Composable
private fun RoleplayMessageItemContent(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    onRetryTurn: (String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    onQuoteMessage: ((String, String, String) -> Unit)? = null,
    onPokeMessageAvatar: ((RoleplayMessageUiModel) -> Unit)? = null,
    onRecallMessage: ((String) -> Unit)? = null,
    onOpenQuotedMessage: ((String) -> Unit)? = null,
    onConfirmTransferReceipt: (String) -> Unit,
    onOpenVideoCall: (() -> Unit)? = null,
    lineHeightScale: Float = 1.0f,
    bubbleMode: RoleplayMessageBubbleMode = RoleplayMessageBubbleMode.DEFAULT,
    noBackgroundSkin: RoleplayNoBackgroundSkinSettings = RoleplayNoBackgroundSkinSettings(),
) {
    val bubbleStyle = rememberRoleplayDialogueBubbleStyle(
        backdropState = backdropState,
        settings = noBackgroundSkin,
        bubbleMode = bubbleMode,
    )
    when (message.contentType) {
        RoleplayContentType.NARRATION -> RoleplayMessageMenuWrapper(message, onRetryTurn, onEditUserMessage, onQuoteMessage = onQuoteMessage, onRecallMessage = onRecallMessage) {
            if (bubbleMode == RoleplayMessageBubbleMode.ONLINE_PHONE) {
                OnlinePhoneNarrationBubble(
                    message = message,
                    colors = colors,
                    backdropState = backdropState,
                    lineHeightScale = lineHeightScale,
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    ImmersiveGlassSurface(
                        backdropState = backdropState,
                        modifier = Modifier.fillMaxWidth(0.92f),
                        shape = RoundedCornerShape(22.dp),
                        blurRadius = 18.dp,
                        overlayColor = colors.narrationBubbleBackground,
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                            paragraphs.forEach { paragraph ->
                                Text(
                                    text = buildQuotedDialogueAnnotatedString(
                                        text = paragraph,
                                        narrationColor = colors.textMuted,
                                        dialogueColor = resolveRoleplayDialogueHighlightColor(
                                            hasImage = backdropState.hasImage,
                                            colors = colors,
                                        ),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 15.sp,
                                        fontStyle = FontStyle.Italic,
                                        lineHeight = 25.sp * lineHeightScale,
                                        letterSpacing = 0.6.sp,
                                    ),
                                    color = colors.textMuted,
                                )
                            }
                        }
                    }
                }
            }
        }

        RoleplayContentType.THOUGHT -> RoleplayMessageMenuWrapper(message, onRetryTurn, onEditUserMessage, onQuoteMessage = onQuoteMessage, onRecallMessage = onRecallMessage) {
            if (bubbleMode == RoleplayMessageBubbleMode.ONLINE_PHONE) {
                OnlinePhoneThoughtBubble(
                    message = message,
                    colors = colors,
                    backdropState = backdropState,
                    lineHeightScale = lineHeightScale,
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    ImmersiveGlassSurface(
                        backdropState = backdropState,
                        modifier = Modifier.fillMaxWidth(0.82f),
                        shape = RoundedCornerShape(22.dp),
                        blurRadius = 18.dp,
                        overlayColor = colors.narrationBubbleBackground,
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                            paragraphs.forEach { paragraph ->
                                Text(
                                    text = buildQuotedDialogueAnnotatedString(
                                        text = paragraph,
                                        narrationColor = colors.thoughtText,
                                        dialogueColor = resolveRoleplayDialogueHighlightColor(
                                            hasImage = backdropState.hasImage,
                                            colors = colors,
                                        ),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 15.sp,
                                        fontStyle = FontStyle.Italic,
                                        lineHeight = 24.sp * lineHeightScale,
                                        letterSpacing = 0.5.sp,
                                    ),
                                    color = colors.thoughtText,
                                )
                            }
                        }
                    }
                }
            }
        }

        RoleplayContentType.DIALOGUE -> {
            if (message.speaker == RoleplaySpeaker.USER) {
                val maxWidthFraction = resolveDialogueMaxWidthFraction(
                    hasImage = backdropState.hasImage,
                    bubbleMode = bubbleMode,
                    isUser = true,
                    noBackgroundFraction = bubbleStyle.maxWidthFraction,
                )
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    val messageMaxWidth = maxWidth
                    if (bubbleMode == RoleplayMessageBubbleMode.ONLINE_PHONE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.Top,
                        ) {
                            RoleplayMessageMenuWrapper(
                                message = message,
                                onRetryTurn = onRetryTurn,
                                onEditUserMessage = onEditUserMessage,
                                modifier = Modifier.widthIn(max = messageMaxWidth * maxWidthFraction),
                                onQuoteMessage = onQuoteMessage,
                                onRecallMessage = onRecallMessage,
                            ) {
                                UserDialogueBubbleContent(
                                    message = message,
                                    colors = colors,
                                    backdropState = backdropState,
                                    lineHeightScale = lineHeightScale,
                                    onOpenQuotedMessage = onOpenQuotedMessage,
                                    fillWidth = false,
                                    bubbleStyle = bubbleStyle,
                                )
                            }
                            RoleplayMessageAvatar(
                                message = message,
                                colors = colors,
                                onPokeMessageAvatar = onPokeMessageAvatar,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    } else {
                        RoleplayMessageMenuWrapper(
                            message = message,
                            onRetryTurn = onRetryTurn,
                            onEditUserMessage = onEditUserMessage,
                            modifier = Modifier.widthIn(max = messageMaxWidth * maxWidthFraction),
                            onQuoteMessage = onQuoteMessage,
                            onRecallMessage = onRecallMessage,
                        ) {
                            UserDialogueBubbleContent(
                                message = message,
                                colors = colors,
                                backdropState = backdropState,
                                lineHeightScale = lineHeightScale,
                                onOpenQuotedMessage = onOpenQuotedMessage,
                                fillWidth = false,
                                bubbleStyle = bubbleStyle,
                            )
                        }
                    }
                }
            } else {
                val isError = message.messageStatus == MessageStatus.ERROR
                val maxWidthFraction = resolveDialogueMaxWidthFraction(
                    hasImage = backdropState.hasImage,
                    bubbleMode = bubbleMode,
                    isUser = false,
                    noBackgroundFraction = bubbleStyle.maxWidthFraction,
                )
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val messageMaxWidth = maxWidth
                    if (bubbleMode == RoleplayMessageBubbleMode.ONLINE_PHONE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Top,
                        ) {
                            RoleplayMessageAvatar(
                                message = message,
                                colors = colors,
                                onPokeMessageAvatar = onPokeMessageAvatar,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            RoleplayMessageMenuWrapper(
                                message = message,
                                onRetryTurn = onRetryTurn,
                                onEditUserMessage = onEditUserMessage,
                                modifier = Modifier.widthIn(max = messageMaxWidth * maxWidthFraction),
                                onQuoteMessage = onQuoteMessage,
                                onRecallMessage = onRecallMessage,
                            ) {
                                CharacterDialogueBubbleContent(
                                    message = message,
                                    colors = colors,
                                    backdropState = backdropState,
                                    lineHeightScale = lineHeightScale,
                                    onOpenQuotedMessage = onOpenQuotedMessage,
                                    isError = isError,
                                    fillWidth = false,
                                    bubbleStyle = bubbleStyle,
                                )
                            }
                        }
                    } else {
                        RoleplayMessageMenuWrapper(
                            message = message,
                            onRetryTurn = onRetryTurn,
                            onEditUserMessage = onEditUserMessage,
                            modifier = Modifier.widthIn(max = messageMaxWidth * maxWidthFraction),
                            onQuoteMessage = onQuoteMessage,
                            onRecallMessage = onRecallMessage,
                        ) {
                            CharacterDialogueBubbleContent(
                                message = message,
                                colors = colors,
                                backdropState = backdropState,
                                lineHeightScale = lineHeightScale,
                                onOpenQuotedMessage = onOpenQuotedMessage,
                                isError = isError,
                                fillWidth = false,
                                bubbleStyle = bubbleStyle,
                            )
                        }
                    }
                }
            }
        }

        RoleplayContentType.LONGFORM -> {
            val isError = message.messageStatus == MessageStatus.ERROR
            RoleplayMessageMenuWrapper(message, onRetryTurn, onEditUserMessage, onQuoteMessage = onQuoteMessage, onRecallMessage = onRecallMessage) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isError) {
                        FailedTurnHint(colors = colors)
                    }
                    RoleplayLongformCard(
                        speakerName = message.speakerName,
                        content = message.content,
                        richTextSource = message.richTextSource,
                        backdropState = backdropState,
                        containerColor = if (isError) colors.errorBackground else colors.panelBackground,
                        titleColor = if (isError) colors.errorText else colors.characterAccent,
                        bodyColor = if (isError) colors.errorText.copy(alpha = 0.88f) else colors.textPrimary.copy(alpha = 0.94f),
                        accentColor = if (isError) {
                            colors.errorText
                        } else {
                            resolveRoleplayDialogueHighlightColor(
                                hasImage = backdropState.hasImage,
                                colors = colors,
                            )
                        },
                        thoughtColor = if (isError) colors.errorText.copy(alpha = 0.76f) else colors.thoughtText,
                        lineHeightScale = lineHeightScale,
                    )
                }
            }
        }

        RoleplayContentType.ACTION -> {
            val actionPart = message.actionPart ?: return
            val isUserMessage = message.speaker == RoleplaySpeaker.USER
            if (actionPart.actionType == ChatActionType.POKE) {
                RoleplayPokeSystemMessage(
                    message = message,
                    colors = colors,
                )
            } else if (bubbleMode == RoleplayMessageBubbleMode.ONLINE_PHONE) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val messageMaxWidth = maxWidth
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (!isUserMessage) {
                            RoleplayMessageAvatar(
                                message = message,
                                colors = colors,
                                onPokeMessageAvatar = onPokeMessageAvatar,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        RoleplayMessageMenuWrapper(
                            message = message,
                            onRetryTurn = onRetryTurn,
                            onEditUserMessage = onEditUserMessage,
                            modifier = Modifier.widthIn(max = messageMaxWidth * if (isUserMessage) 0.82f else 0.84f),
                            onQuoteMessage = onQuoteMessage,
                            onRecallMessage = onRecallMessage,
                        ) {
                            RoleplayActionCard(
                                message = message,
                                actionPart = actionPart,
                                colors = colors,
                                backdropState = backdropState,
                                onOpenVideoCall = if (actionPart.actionType == ChatActionType.VIDEO_CALL && !isUserMessage) {
                                    onOpenVideoCall
                                } else {
                                    null
                                },
                            )
                        }
                        if (isUserMessage) {
                            RoleplayMessageAvatar(
                                message = message,
                                colors = colors,
                                onPokeMessageAvatar = onPokeMessageAvatar,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    RoleplayMessageMenuWrapper(
                        message = message,
                        onRetryTurn = onRetryTurn,
                        onEditUserMessage = onEditUserMessage,
                        modifier = if (isUserMessage) Modifier.fillMaxWidth(0.82f) else Modifier.fillMaxWidth(0.92f),
                        onQuoteMessage = onQuoteMessage,
                        onRecallMessage = onRecallMessage,
                    ) {
                        RoleplayActionCard(
                            message = message,
                            actionPart = actionPart,
                            colors = colors,
                            backdropState = backdropState,
                            onOpenVideoCall = if (actionPart.actionType == ChatActionType.VIDEO_CALL && !isUserMessage) {
                                onOpenVideoCall
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        RoleplayContentType.SPECIAL_PLAY -> {
            val specialPart = message.specialPart ?: return
            val isUserMessage = message.speaker == RoleplaySpeaker.USER
            if (bubbleMode == RoleplayMessageBubbleMode.ONLINE_PHONE) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val messageMaxWidth = maxWidth
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (!isUserMessage) {
                            RoleplayMessageAvatar(
                                message = message,
                                colors = colors,
                                onPokeMessageAvatar = onPokeMessageAvatar,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        RoleplayMessageMenuWrapper(
                            message = message,
                            onRetryTurn = onRetryTurn,
                            onEditUserMessage = onEditUserMessage,
                            modifier = Modifier.widthIn(max = messageMaxWidth * 0.82f),
                            onQuoteMessage = onQuoteMessage,
                            onRecallMessage = onRecallMessage,
                        ) {
                            SpecialPlayCard(
                                part = specialPart,
                                isUserMessage = isUserMessage,
                                onConfirmTransferReceipt = onConfirmTransferReceipt,
                            )
                        }
                        if (isUserMessage) {
                            RoleplayMessageAvatar(
                                message = message,
                                colors = colors,
                                onPokeMessageAvatar = onPokeMessageAvatar,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    RoleplayMessageMenuWrapper(
                        message = message,
                        onRetryTurn = onRetryTurn,
                        onEditUserMessage = onEditUserMessage,
                        modifier = if (isUserMessage) Modifier.fillMaxWidth(0.82f) else Modifier.fillMaxWidth(0.82f),
                        onQuoteMessage = onQuoteMessage,
                        onRecallMessage = onRecallMessage,
                    ) {
                        SpecialPlayCard(
                            part = specialPart,
                            isUserMessage = isUserMessage,
                            onConfirmTransferReceipt = onConfirmTransferReceipt,
                        )
                    }
                }
            }
        }

        RoleplayContentType.STATUS -> {
            val statusPart = message.specialPart ?: return
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                RoleplayMessageMenuWrapper(
                    message = message,
                    onRetryTurn = onRetryTurn,
                    onEditUserMessage = onEditUserMessage,
                    modifier = Modifier.fillMaxWidth(0.92f),
                    onQuoteMessage = onQuoteMessage,
                    onRecallMessage = onRecallMessage,
                ) {
                    StatusCardPart(
                        part = statusPart,
                        contentColor = colors.textPrimary,
                    )
                }
            }
        }

        RoleplayContentType.SYSTEM -> Unit
    }
}

private fun resolveDialogueMaxWidthFraction(
    hasImage: Boolean,
    bubbleMode: RoleplayMessageBubbleMode,
    isUser: Boolean,
    noBackgroundFraction: Float,
): Float {
    if (!hasImage) {
        return noBackgroundFraction
    }
    return when (bubbleMode) {
        RoleplayMessageBubbleMode.ONLINE_PHONE -> if (isUser) 0.82f else 0.84f
        RoleplayMessageBubbleMode.DEFAULT -> if (isUser) 0.82f else 0.88f
    }
}

@Composable
private fun RoleplayMessageAvatar(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    onPokeMessageAvatar: ((RoleplayMessageUiModel) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isUser = message.speaker == RoleplaySpeaker.USER
    val avatarSource = message.speakerAvatarUrl.ifBlank { message.speakerAvatarUri }
    val avatarModifier = if (onPokeMessageAvatar != null) {
        modifier
            .size(36.dp)
            .combinedClickable(
                onClick = {},
                onDoubleClick = { onPokeMessageAvatar(message) },
                onLongClick = { onPokeMessageAvatar(message) },
            )
    } else {
        modifier.size(36.dp)
    }
    AssistantAvatar(
        name = message.speakerName.ifBlank { if (isUser) "你" else "角色" },
        iconName = if (isUser) "" else message.speakerIconName.ifBlank { "auto_stories" },
        avatarUri = avatarSource,
        modifier = avatarModifier,
        size = 36.dp,
        containerColor = if (isUser) {
            colors.userAccent.copy(alpha = 0.16f)
        } else {
            colors.characterAccent.copy(alpha = 0.16f)
        },
        contentColor = if (isUser) colors.userAccent else colors.characterAccent,
        cornerRadius = 18.dp,
    )
}

@Composable
internal fun RoleplayMessageMenuWrapper(
    message: RoleplayMessageUiModel,
    onRetryTurn: (String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    onQuoteMessage: ((String, String, String) -> Unit)? = null,
    onRecallMessage: ((String) -> Unit)? = null,
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
    val canQuoteMessage = onQuoteMessage != null &&
        message.sourceMessageId.isNotBlank() &&
        message.contentType in setOf(
            RoleplayContentType.DIALOGUE,
            RoleplayContentType.NARRATION,
            RoleplayContentType.THOUGHT,
            RoleplayContentType.LONGFORM,
        )
    val canRecallMessage = onRecallMessage != null &&
        message.sourceMessageId.isNotBlank() &&
        message.speaker == RoleplaySpeaker.USER &&
        message.contentType == RoleplayContentType.DIALOGUE &&
        !message.isRecalled
    var showMenu by remember(message.sourceMessageId, message.copyText, message.canRetry, canEditUserMessage) {
        mutableStateOf(false)
    }
    Box(
        modifier = modifier.combinedClickable(
            enabled = canCopy || message.canRetry || canEditUserMessage || canQuoteMessage || canRecallMessage,
            onClick = {},
            onLongClick = { showMenu = true },
        ),
    ) {
        content()
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (canCopy) {
                DropdownMenuItem(
                    text = { Text("\u590D\u5236\u5185\u5BB9") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        clipboardScope.copyPlainTextToClipboard(clipboard, "roleplay-message", message.copyText)
                        Toast.makeText(context, "\u5DF2\u590D\u5236\u5185\u5BB9", Toast.LENGTH_SHORT).show()
                        showMenu = false
                    },
                )
            }
            if (canEditUserMessage) {
                DropdownMenuItem(
                    text = { Text("\u91CD\u56DE\u5E76\u7F16\u8F91") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        onEditUserMessage(message.sourceMessageId)
                        showMenu = false
                    },
                )
            }
            if (canQuoteMessage) {
                DropdownMenuItem(
                    text = { Text("\u5F15\u7528") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        onQuoteMessage?.invoke(
                            message.sourceMessageId,
                            message.speakerName,
                            message.copyText.lineSequence().firstOrNull().orEmpty().take(60),
                        )
                        showMenu = false
                    },
                )
            }
            if (canRecallMessage) {
                DropdownMenuItem(
                    text = { Text("\u64A4\u56DE") },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    onClick = {
                        onRecallMessage?.invoke(message.sourceMessageId)
                        showMenu = false
                    },
                )
            }
            if (message.canRetry && message.sourceMessageId.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("\u91CD\u56DE\u6B64\u56DE\u5408") },
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
