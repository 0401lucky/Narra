package com.example.myapplication.ui.component.roleplay

import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraUi
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
import androidx.compose.ui.platform.testTag
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
    useGlassBubble: Boolean = backdropState.hasImage,
) {
    val shape = bubbleStyle.shape(isUser = true)
    val bubbleModifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .testTag("roleplay_dialogue_bubble_${message.uiItemId}")
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .widthIn(min = 96.dp)
                .padding(
                    horizontal = bubbleStyle.paddingHorizontal,
                    vertical = bubbleStyle.paddingVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
        ) {
            if (message.replyToPreview.isNotBlank()) {
                RoleplayReplyPreview(
                    message = message,
                    colors = colors,
                    onOpenQuotedMessage = onOpenQuotedMessage,
                )
            }
            if (message.isStreaming) {
                StreamingLogText(
                    content = message.content,
                    textColor = colors.userText,
                    accentColor = colors.userAccent,
                    lineHeightScale = lineHeightScale,
                )
            } else {
                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                Column(verticalArrangement = Arrangement.spacedBy(NarraUi.space12)) {
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
    if (!useGlassBubble) {
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
            minimumOverlayAlpha = RoleplayDialogueReadingMinimumAlpha,
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
    useGlassBubble: Boolean = backdropState.hasImage,
) {
    val shape = bubbleStyle.shape(isUser = false)
    val bubbleModifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .testTag("roleplay_dialogue_bubble_${message.uiItemId}")
    val bubbleColor = if (isError) colors.errorBackground else colors.characterBubbleBackground
    val dialogueHighlightColor = if (isError) {
        colors.errorText
    } else {
        resolveRoleplayDialogueHighlightColor(
            hasImage = useGlassBubble,
            colors = colors,
        )
    }
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(
                horizontal = bubbleStyle.paddingHorizontal,
                vertical = bubbleStyle.paddingVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
        ) {
            if (message.replyToPreview.isNotBlank()) {
                RoleplayReplyPreview(
                    message = message,
                    colors = colors,
                    onOpenQuotedMessage = onOpenQuotedMessage,
                )
            }
            if (message.emotion.isNotBlank()) {
                RoleplayEmotionChip(
                    text = message.emotion,
                    textColor = if (isError) colors.errorText else colors.characterAccent,
                    containerColor = if (isError) colors.errorBackgroundStrong else colors.panelBackground,
                )
            }
            if (isError) {
                FailedTurnHint(colors = colors)
            }
            if (message.isStreaming) {
                StreamingLogText(
                    content = message.content,
                    textColor = if (isError) colors.errorText else colors.textPrimary,
                    accentColor = dialogueHighlightColor,
                    lineHeightScale = lineHeightScale,
                )
            } else {
                val paragraphs = remember(message.content) { message.content.toLongformParagraphs() }
                Column(verticalArrangement = Arrangement.spacedBy(NarraUi.space12)) {
                    paragraphs.forEach { paragraph ->
                        val rendered = remember(paragraph, colors, isError, dialogueHighlightColor) {
                            buildCharacterDialogueAnnotatedString(
                                text = paragraph,
                                narrationColor = if (isError) colors.errorText else colors.textPrimary,
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
    if (!useGlassBubble || isError) {
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
            minimumOverlayAlpha = RoleplayDialogueReadingMinimumAlpha,
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
        shape = NarraShapes.small,
        color = colors.systemBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NarraUi.space8, vertical = NarraUi.space8),
            horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp),
                shape = NarraShapes.pill,
                color = colors.characterAccent.copy(alpha = 0.62f),
            ) {}
            Column(verticalArrangement = Arrangement.spacedBy(NarraUi.space4)) {
                Text(
                    text = message.replyToSpeakerName.ifBlank { "\u5F15\u7528\u6D88\u606F" },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.systemText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = message.replyToPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.systemText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
