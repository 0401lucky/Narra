package com.example.myapplication.ui.component.roleplay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.RoleplayMessageUiModel

@Composable
internal fun UserDialogueBubbleContent(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
    onOpenQuotedMessage: ((String) -> Unit)?,
    fillWidth: Boolean,
    bubbleStyle: RoleplayDialogueBubbleStyle = DefaultRoleplayDialogueBubbleStyle,
) {
    val shape = bubbleStyle.shape(isUser = true)
    val bubbleModifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .widthIn(min = 96.dp)
                .padding(
                    horizontal = bubbleStyle.paddingHorizontal,
                    vertical = bubbleStyle.paddingVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.userAccent,
            )
            if (message.isStreaming) {
                StreamingLogText(
                    content = message.content,
                    textColor = colors.userText,
                    accentColor = colors.userAccent,
                    lineHeightScale = lineHeightScale,
                )
            } else {
                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    paragraphs.forEach { paragraph ->
                        Text(
                            paragraph,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 26.sp * lineHeightScale,
                                letterSpacing = 0.6.sp,
                            ),
                            color = colors.userText,
                        )
                    }
                }
            }
        }
    }
    if (!backdropState.hasImage) {
        Surface(
            modifier = bubbleModifier,
            shape = shape,
            color = colors.userBubbleBackground,
            border = BorderStroke(1.dp, colors.panelBorder.copy(alpha = 0.18f)),
            shadowElevation = 0.dp,
        ) {
            content()
        }
    } else {
        ImmersiveReadingGlassSurface(
            backdropState = backdropState,
            modifier = bubbleModifier,
            shape = shape,
            variant = ImmersiveReadingGlassVariant.DIALOGUE,
            overlayColor = colors.userBubbleBackground,
        ) {
            content()
        }
    }
}

@Composable
internal fun CharacterDialogueBubbleContent(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
    onOpenQuotedMessage: ((String) -> Unit)?,
    isError: Boolean,
    fillWidth: Boolean,
    bubbleStyle: RoleplayDialogueBubbleStyle = DefaultRoleplayDialogueBubbleStyle,
) {
    val shape = bubbleStyle.shape(isUser = false)
    val bubbleModifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    val bubbleColor = if (isError) colors.errorBackground else colors.characterBubbleBackground
    val dialogueHighlightColor = if (isError) {
        colors.errorText
    } else {
        resolveRoleplayDialogueHighlightColor(
            hasImage = backdropState.hasImage,
            colors = colors,
        )
    }
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(
                horizontal = bubbleStyle.paddingHorizontal,
                vertical = bubbleStyle.paddingVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    accentColor = dialogueHighlightColor,
                    lineHeightScale = lineHeightScale,
                )
            } else {
                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    paragraphs.forEach { paragraph ->
                        val rendered = remember(paragraph, colors, isError) {
                            buildCharacterDialogueAnnotatedString(
                                text = paragraph,
                                narrationColor = if (isError) colors.errorText.copy(alpha = 0.84f) else colors.textPrimary.copy(alpha = 0.78f),
                                dialogueColor = dialogueHighlightColor,
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
    if (!backdropState.hasImage) {
        Surface(
            modifier = bubbleModifier,
            shape = shape,
            color = bubbleColor,
            border = BorderStroke(1.dp, colors.panelBorder.copy(alpha = 0.18f)),
            shadowElevation = 0.dp,
        ) {
            content()
        }
    } else {
        ImmersiveReadingGlassSurface(
            backdropState = backdropState,
            modifier = bubbleModifier,
            shape = shape,
            variant = ImmersiveReadingGlassVariant.DIALOGUE,
            overlayColor = bubbleColor,
        ) {
            content()
        }
    }
}

@Composable
internal fun RoleplayReplyPreview(
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
        shape = RoundedCornerShape(8.dp),
        color = colors.panelBackground.copy(alpha = 0.78f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp),
                shape = RoundedCornerShape(999.dp),
                color = colors.characterAccent.copy(alpha = 0.62f),
            ) {}
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = message.replyToSpeakerName.ifBlank { "\u5F15\u7528\u6D88\u606F" },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.characterAccent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = message.replyToPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
