package com.example.myapplication.ui.component.roleplay

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.actionMetadataValue
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.voiceMessageContent
import com.example.myapplication.model.voiceMessageDurationLabel
import com.example.myapplication.model.voiceMessageDurationSeconds
import com.example.myapplication.ui.component.SpecialPlayCard
import com.example.myapplication.ui.component.copyPlainTextToClipboard
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
internal fun RoleplayMessageItem(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    onRetryTurn: (String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    onQuoteMessage: ((String, String, String) -> Unit)? = null,
    onRecallMessage: ((String) -> Unit)? = null,
    onOpenQuotedMessage: ((String) -> Unit)? = null,
    onConfirmTransferReceipt: (String) -> Unit,
    onOpenVideoCall: (() -> Unit)? = null,
    lineHeightScale: Float = 1.0f,
    bubbleMode: RoleplayMessageBubbleMode = RoleplayMessageBubbleMode.DEFAULT,
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
            onRecallMessage = onRecallMessage,
            onOpenQuotedMessage = onOpenQuotedMessage,
            onConfirmTransferReceipt = onConfirmTransferReceipt,
            onOpenVideoCall = onOpenVideoCall,
            lineHeightScale = lineHeightScale,
            bubbleMode = bubbleMode,
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
    onRecallMessage: ((String) -> Unit)? = null,
    onOpenQuotedMessage: ((String) -> Unit)? = null,
    onConfirmTransferReceipt: (String) -> Unit,
    onOpenVideoCall: (() -> Unit)? = null,
    lineHeightScale: Float = 1.0f,
    bubbleMode: RoleplayMessageBubbleMode = RoleplayMessageBubbleMode.DEFAULT,
) {
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
                        shape = RoundedCornerShape(18.dp),
                        overlayColor = colors.panelBackground,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                        shape = RoundedCornerShape(18.dp),
                        overlayColor = colors.panelBackground.copy(alpha = 0.18f),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                            paragraphs.forEach { paragraph ->
                                Text(
                                    text = buildQuotedDialogueAnnotatedString(
                                        text = paragraph,
                                        narrationColor = colors.thoughtText,
                                        dialogueColor = RoleplayQuotedDialogueHighlightColor,
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
                val isOnlinePhoneBubble = bubbleMode == RoleplayMessageBubbleMode.ONLINE_PHONE
                if (isOnlinePhoneBubble) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        RoleplayMessageMenuWrapper(
                            message = message,
                            onRetryTurn = onRetryTurn,
                            onEditUserMessage = onEditUserMessage,
                            modifier = Modifier.widthIn(max = maxWidth * 0.82f),
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
                            )
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        RoleplayMessageMenuWrapper(
                            message,
                            onRetryTurn,
                            onEditUserMessage,
                            Modifier.fillMaxWidth(0.82f),
                            onQuoteMessage = onQuoteMessage,
                            onRecallMessage = onRecallMessage,
                        ) {
                            UserDialogueBubbleContent(
                                message = message,
                                colors = colors,
                                backdropState = backdropState,
                                lineHeightScale = lineHeightScale,
                                onOpenQuotedMessage = onOpenQuotedMessage,
                                fillWidth = true,
                            )
                        }
                    }
                }
            } else {
                val isError = message.messageStatus == MessageStatus.ERROR
                if (bubbleMode == RoleplayMessageBubbleMode.ONLINE_PHONE) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        RoleplayMessageMenuWrapper(
                            message = message,
                            onRetryTurn = onRetryTurn,
                            onEditUserMessage = onEditUserMessage,
                            modifier = Modifier.widthIn(max = maxWidth * 0.84f),
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
                            )
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        RoleplayMessageMenuWrapper(
                            message = message,
                            onRetryTurn = onRetryTurn,
                            onEditUserMessage = onEditUserMessage,
                            modifier = Modifier.fillMaxWidth(0.88f),
                            onQuoteMessage = onQuoteMessage,
                            onRecallMessage = onRecallMessage
                        ) {
                            CharacterDialogueBubbleContent(
                                message = message,
                                colors = colors,
                                backdropState = backdropState,
                                lineHeightScale = lineHeightScale,
                                onOpenQuotedMessage = onOpenQuotedMessage,
                                isError = isError,
                                fillWidth = true,
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
                        containerColor = if (isError) colors.errorBackground else Color.Transparent,
                        titleColor = if (isError) colors.errorText else colors.characterAccent,
                        bodyColor = if (isError) colors.errorText.copy(alpha = 0.88f) else colors.textPrimary.copy(alpha = 0.82f),
                        accentColor = if (isError) colors.errorText else RoleplayQuotedDialogueHighlightColor,
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

        RoleplayContentType.SYSTEM -> Unit
    }
}

@Composable
private fun RoleplayActionCard(
    message: RoleplayMessageUiModel,
    actionPart: com.example.myapplication.model.ChatMessagePart,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    onOpenVideoCall: (() -> Unit)?,
) {
    val isUserMessage = message.speaker == RoleplaySpeaker.USER
    if (actionPart.actionType == ChatActionType.VOICE_MESSAGE) {
        RoleplayVoiceMessageCard(
            message = message,
            actionPart = actionPart,
            colors = colors,
            backdropState = backdropState,
            isUserMessage = isUserMessage,
        )
        return
    }
    val title = when (actionPart.actionType) {
        ChatActionType.EMOJI -> "\u8868\u60C5"
        ChatActionType.VOICE_MESSAGE -> "\u8BED\u97F3\u6D88\u606F"
        ChatActionType.AI_PHOTO -> "\u7167\u7247"
        ChatActionType.LOCATION -> "\u4F4D\u7F6E"
        ChatActionType.POKE -> "\u4E92\u52A8"
        ChatActionType.VIDEO_CALL -> "\u89C6\u9891\u901A\u8BDD"
        null -> "\u6D88\u606F"
    }
    val body = when (actionPart.actionType) {
        ChatActionType.EMOJI -> actionPart.actionMetadataValue("description")
        ChatActionType.VOICE_MESSAGE -> actionPart.actionMetadataValue("content")
        ChatActionType.AI_PHOTO -> actionPart.actionMetadataValue("description")
        ChatActionType.LOCATION -> buildString {
            append(actionPart.actionMetadataValue("location_name"))
            actionPart.actionMetadataValue("address").takeIf { it.isNotBlank() }?.let { address ->
                append("\n")
                append(address)
            }
            actionPart.actionMetadataValue("coordinates").takeIf { it.isNotBlank() }?.let { coordinates ->
                append("\n")
                append(coordinates)
            }
        }
        ChatActionType.POKE -> if (isUserMessage) "\u4F60\u6233\u4E86\u6233\u5BF9\u65B9" else "\u6233\u4E86\u6233\u4F60"
        ChatActionType.VIDEO_CALL -> actionPart.actionMetadataValue("reason")
        null -> message.copyText
    }.trim().ifBlank { message.copyText }
    val cardModifier = if (actionPart.actionType == ChatActionType.VIDEO_CALL && onOpenVideoCall != null) {
        Modifier.clickable(onClick = onOpenVideoCall)
    } else {
        Modifier
    }
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier
            .fillMaxWidth()
            .then(cardModifier),
        shape = RoundedCornerShape(22.dp),
        overlayColor = if (isUserMessage) colors.panelBackgroundStrong else colors.panelBackground,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isUserMessage) colors.userAccent else colors.characterAccent,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            if (actionPart.actionType == ChatActionType.VIDEO_CALL && onOpenVideoCall != null) {
                Text(
                    text = "\u70B9\u8FD9\u91CC\u63A5\u901A",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun RoleplayVoiceMessageCard(
    message: RoleplayMessageUiModel,
    actionPart: com.example.myapplication.model.ChatMessagePart,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    isUserMessage: Boolean,
) {
    var isPlaying by rememberSaveable(message.sourceMessageId, actionPart.actionId) { mutableStateOf(false) }
    var isExpanded by rememberSaveable(message.sourceMessageId, actionPart.actionId) { mutableStateOf(false) }
    val voiceContent = actionPart.voiceMessageContent()
    val durationSeconds = actionPart.voiceMessageDurationSeconds()
    val waveform = remember(actionPart.actionId, voiceContent) {
        buildVoiceWaveformSeed(
            seed = "${actionPart.actionId}:$voiceContent",
            count = 12,
        )
    }
    val infiniteTransition = rememberInfiniteTransition(label = "roleplay_voice_wave")
    val animatedWavePulses = List(waveform.size) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 420 + index * 45,
                    delayMillis = index * 50,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "roleplay_voice_wave_$index",
        ).value
    }

    LaunchedEffect(isPlaying, actionPart.actionId) {
        if (isPlaying) {
            val fakePlaybackMillis = durationSeconds
                .coerceIn(1, 8) * 420L
            delay(fakePlaybackMillis)
            isPlaying = false
        }
    }

    // \u8BED\u97F3\u65F6\u957F\u52A8\u6001\u5BBD\u5EA6
    val dynamicWidth = (120 + (durationSeconds.coerceIn(1, 12) - 1) * 12).coerceAtMost(260).dp

    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.widthIn(min = 120.dp, max = dynamicWidth),
        shape = RoundedCornerShape(22.dp),
        overlayColor = if (isUserMessage) colors.panelBackgroundStrong else colors.panelBackground,
    ) {
        Column(
            modifier = Modifier.animateContentSize(),
        ) {
            Row(
                modifier = Modifier
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            color = if (isUserMessage) colors.userAccent else colors.characterAccent,
                            shape = CircleShape,
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        ) { isPlaying = !isPlaying },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "\u6682\u505C\u8BED\u97F3" else "\u64AD\u653E\u8BED\u97F3",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    waveform.forEachIndexed { index, baseHeight ->
                        val animatedHeight = if (isPlaying) {
                            baseHeight * animatedWavePulses[index]
                        } else {
                            baseHeight
                        }
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height((8f + animatedHeight * 14f).dp)
                                .background(
                                    color = if (isUserMessage) {
                                        colors.userAccent.copy(alpha = 0.95f)
                                    } else {
                                        colors.characterAccent.copy(alpha = 0.92f)
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                ),
                        )
                    }
                }
                Text(
                    text = actionPart.voiceMessageDurationLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textMuted,
                )
            }
            if (isExpanded && voiceContent.isNotBlank()) {
                Text(
                    text = voiceContent,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        letterSpacing = 0.4.sp,
                    ),
                    color = colors.textPrimary.copy(alpha = 0.88f),
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = 14.dp,
                        bottom = 12.dp,
                    ),
                )
            }
        }
    }
}

private fun buildVoiceWaveformSeed(
    seed: String,
    count: Int,
): List<Float> {
    var state = seed.hashCode().let { if (it == Int.MIN_VALUE) 7 else abs(it) + 7 }
    return List(count) {
        state = (state * 1103515245 + 12345)
        val normalized = abs(state % 1000) / 1000f
        0.35f + normalized * 0.75f
    }
}

@Composable
private fun UserDialogueBubbleContent(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
    onOpenQuotedMessage: ((String) -> Unit)?,
    fillWidth: Boolean,
) {
    val shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 6.dp,
        bottomStart = 20.dp,
        bottomEnd = 20.dp,
    )
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        shape = shape,
        overlayColor = colors.panelBackgroundStrong,
    ) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (message.replyToPreview.isNotBlank()) {
            RoleplayReplyPreview(
                message = message,
                colors = colors,
                onOpenQuotedMessage = onOpenQuotedMessage,
            )
        }
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
                lineHeightScale = lineHeightScale,
            )
        } else {
            val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                paragraphs.forEach { paragraph ->
                    Text(
                        paragraph,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 26.sp * lineHeightScale,
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

@Composable
private fun CharacterDialogueBubbleContent(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
    onOpenQuotedMessage: ((String) -> Unit)?,
    isError: Boolean,
    fillWidth: Boolean,
) {
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        shape = shape,
        overlayColor = if (isError) colors.errorBackground else colors.panelBackgroundStrong,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (message.replyToPreview.isNotBlank()) {
                RoleplayReplyPreview(
                    message = message,
                    colors = colors,
                    onOpenQuotedMessage = onOpenQuotedMessage,
                )
            }
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
                    lineHeightScale = lineHeightScale,
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
                                lineHeight = 26.sp * lineHeightScale,
                                letterSpacing = 0.6.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlinePhoneNarrationBubble(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
    ImmersiveGlassSurface(
        backdropState = backdropState,
        modifier = Modifier.fillMaxWidth(0.96f),
        shape = shape,
        blurRadius = 18.dp,
        overlayColor = colors.panelBackground.copy(alpha = 0.42f),
    ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                paragraphs.forEach { paragraph ->
                    Text(
                        text = buildQuotedDialogueAnnotatedString(
                            text = paragraph,
                            narrationColor = colors.textMuted.copy(alpha = 0.92f),
                            dialogueColor = RoleplayQuotedDialogueHighlightColor,
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 24.sp * lineHeightScale,
                            letterSpacing = 0.5.sp,
                        ),
                        color = colors.textMuted.copy(alpha = 0.92f),
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlinePhoneThoughtBubble(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
) {
    var expanded by rememberSaveable(
        message.sourceMessageId,
        message.createdAt,
        message.content,
    ) {
        mutableStateOf(false)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ImmersiveGlassSurface(
            backdropState = backdropState,
            modifier = Modifier
                .wrapContentWidth()
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(999.dp),
            blurRadius = 14.dp,
            overlayColor = colors.panelBackground.copy(alpha = if (expanded) 0.34f else 0.24f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = if (expanded) "\u6536\u8D77\u5FC3\u58F0" else "\u5C55\u5F00\u5FC3\u58F0",
                    tint = colors.characterAccent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (expanded) "\u6536\u8D77\u5FC3\u58F0" else "\u67E5\u770B\u5FC3\u58F0",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.thoughtText,
                )
            }
        }
        if (expanded) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                ImmersiveGlassSurface(
                    backdropState = backdropState,
                    modifier = Modifier.widthIn(max = maxWidth * 0.76f),
                    shape = RoundedCornerShape(18.dp),
                    blurRadius = 16.dp,
                    overlayColor = colors.panelBackground.copy(alpha = 0.24f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                        paragraphs.forEach { paragraph ->
                            Text(
                                text = buildQuotedDialogueAnnotatedString(
                                    text = paragraph,
                                    narrationColor = colors.thoughtText,
                                    dialogueColor = RoleplayQuotedDialogueHighlightColor,
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontStyle = FontStyle.Italic,
                                    lineHeight = 22.sp * lineHeightScale,
                                    letterSpacing = 0.4.sp,
                                ),
                                color = colors.thoughtText,
                            )
                        }
                    }
                }
            }
        }
    }
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

@Composable
private fun RoleplayReplyPreview(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    onOpenQuotedMessage: ((String) -> Unit)?,
) {
    Surface(
        modifier = if (message.replyToMessageId.isNotBlank() && onOpenQuotedMessage != null) {
            Modifier.clickable { onOpenQuotedMessage(message.replyToMessageId) }
        } else {
            Modifier
        },
        shape = RoundedCornerShape(14.dp),
        color = colors.panelBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = message.replyToSpeakerName.ifBlank { "\u5F15\u7528\u6D88\u606F" },
                style = MaterialTheme.typography.labelSmall,
                color = colors.characterAccent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message.replyToPreview,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RoleplayPokeSystemMessage(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
) {
    val pokeText = buildString {
        val speakerName = message.speakerName.ifBlank { "\u5BF9\u65B9" }
        val content = message.content.trim()
        if (content.isNotBlank() && content != "\u6233\u4E86\u6233\u4F60" && content != "\u4F60\u6233\u4E86\u6233\u5BF9\u65B9") {
            append(speakerName)
            append(" ")
            append(content)
        } else if (message.speaker == RoleplaySpeaker.USER) {
            append("\u4F60\u6233\u4E86\u6233 ")
            append(speakerName)
        } else {
            append(speakerName)
            append(" \u6233\u4E86\u6233\u4F60")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = pokeText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                letterSpacing = 0.3.sp,
            ),
            color = colors.textMuted.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
    }
}
