package com.example.myapplication.ui.component.roleplay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
) {
    val shape = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 8.dp,
        bottomStart = 22.dp,
        bottomEnd = 22.dp,
    )
    ImmersiveReadingGlassSurface(
        backdropState = backdropState,
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        shape = shape,
        variant = ImmersiveReadingGlassVariant.DIALOGUE,
        overlayColor = colors.userBubbleBackground,
    ) {
    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
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
            style = MaterialTheme.typography.labelMedium.copy(
                shadow = GlassTextShadowStrong,
            ),
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                paragraphs.forEach { paragraph ->
                    Text(
                        paragraph,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 26.sp * lineHeightScale,
                            letterSpacing = 0.6.sp,
                            shadow = GlassTextShadow,
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
internal fun CharacterDialogueBubbleContent(
    message: RoleplayMessageUiModel,
    colors: ImmersiveRoleplayColors,
    backdropState: ImmersiveBackdropState,
    lineHeightScale: Float,
    onOpenQuotedMessage: ((String) -> Unit)?,
    isError: Boolean,
    fillWidth: Boolean,
) {
    val shape = RoundedCornerShape(topStart = 8.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    ImmersiveReadingGlassSurface(
        backdropState = backdropState,
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        shape = shape,
        variant = ImmersiveReadingGlassVariant.DIALOGUE,
        overlayColor = if (isError) colors.errorBackground else colors.characterBubbleBackground,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
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
                    style = MaterialTheme.typography.labelLarge.copy(
                        shadow = GlassTextShadowStrong,
                    ),
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                shadow = GlassTextShadow,
                            ),
                        )
                    }
                }
            }
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
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
