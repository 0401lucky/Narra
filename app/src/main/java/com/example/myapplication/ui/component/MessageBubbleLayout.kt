package com.example.myapplication.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.background
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.toActionCopyText
import com.example.myapplication.model.toMessageAttachmentOrNull
import com.example.myapplication.model.toSpecialPlayCopyText
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownPadding

private val MessageBubbleActionButtonSize = 30.dp
private val MessageBubbleActionIconSize = 16.dp
private val MessageBubbleUserMessageMaxWidth = 300.dp

@Immutable
internal data class MessageBubbleRenderedContent(
    val displayAttachments: List<MessageAttachment>,
    val displayParts: List<ChatMessagePart>,
    val assistantImageSources: List<String>,
)

@Composable
internal fun UserStructuredMessageContent(
    renderedContent: MessageBubbleRenderedContent,
    displayContent: String,
    contentColor: androidx.compose.ui.graphics.Color,
    messageTextScale: Float,
    autoPreviewImages: Boolean,
    performanceMode: ChatMessagePerformanceMode,
    onConfirmTransferReceipt: ((String) -> Unit)?,
    onOpenImagePreview: ((Int) -> Unit)? = null,
) {
    val displayAttachments = renderedContent.displayAttachments
    val displayParts = renderedContent.displayParts
    val bubbleShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 24.dp,
        bottomEnd = 6.dp,
    )
    val bubbleColor = lerp(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.surface,
        0.22f,
    )
    val bubbleBorder = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (displayParts.isNotEmpty()) {
            displayParts.forEachIndexed { index, part ->
                when (part.type) {
                    ChatMessagePartType.TEXT -> {
                        if (part.text.isNotBlank()) {
                            UserTextBubble(
                                text = part.text,
                                contentColor = contentColor,
                                bubbleColor = bubbleColor,
                                bubbleBorder = bubbleBorder,
                                bubbleShape = bubbleShape,
                                messageTextScale = messageTextScale,
                            )
                        }
                    }

                    ChatMessagePartType.IMAGE -> {
                        val imageIndex = displayParts.take(index + 1).count { it.type == ChatMessagePartType.IMAGE } - 1
                        UserUploadedImageThumbnail(
                            uri = part.uri,
                            fileName = part.fileName.ifBlank { "uploaded-image-${index + 1}" },
                            autoPreviewImages = autoPreviewImages,
                            onOpenPreview = {
                                onOpenImagePreview?.invoke(imageIndex.coerceAtLeast(0))
                            },
                        )
                    }

                    ChatMessagePartType.FILE -> {
                        val attachment = part.toMessageAttachmentOrNull()
                        if (attachment != null) {
                            Box(modifier = Modifier.widthIn(max = MessageBubbleUserMessageMaxWidth)) {
                                PartAttachmentCard(
                                    attachment = attachment,
                                    contentColor = contentColor,
                                )
                            }
                        }
                    }

                    ChatMessagePartType.ACTION -> {
                        if (part.toActionCopyText().isNotBlank()) {
                            UserTextBubble(
                                text = part.toActionCopyText(),
                                contentColor = contentColor,
                                bubbleColor = bubbleColor,
                                bubbleBorder = bubbleBorder,
                                bubbleShape = bubbleShape,
                                messageTextScale = messageTextScale,
                            )
                        }
                    }

                    ChatMessagePartType.SPECIAL -> {
                        Box(modifier = Modifier.widthIn(max = MessageBubbleUserMessageMaxWidth)) {
                            SpecialPlayCard(
                                part = part,
                                isUserMessage = true,
                                onConfirmTransferReceipt = onConfirmTransferReceipt,
                                autoPreviewImages = autoPreviewImages,
                                reduceMotion = performanceMode != ChatMessagePerformanceMode.FULL,
                            )
                        }
                    }

                    ChatMessagePartType.STATUS -> {
                        Unit
                    }
                }
            }
        } else {
            if (displayContent.isNotBlank() && !shouldHideLegacyAttachmentSummary(displayContent, displayAttachments)) {
                UserTextBubble(
                    text = displayContent,
                    contentColor = contentColor,
                    bubbleColor = bubbleColor,
                    bubbleBorder = bubbleBorder,
                    bubbleShape = bubbleShape,
                    messageTextScale = messageTextScale,
                )
            }
            displayAttachments.forEachIndexed { index, attachment ->
                when (attachment.type) {
                    AttachmentType.IMAGE -> {
                        UserUploadedImageThumbnail(
                            uri = attachment.uri,
                            fileName = attachment.fileName.ifBlank { "uploaded-image-${index + 1}" },
                            autoPreviewImages = autoPreviewImages,
                            onOpenPreview = {
                                onOpenImagePreview?.invoke(index)
                            },
                        )
                    }

                    AttachmentType.FILE -> {
                        Box(modifier = Modifier.widthIn(max = MessageBubbleUserMessageMaxWidth)) {
                            PartAttachmentCard(
                                attachment = attachment,
                                contentColor = contentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun UserTextBubble(
    text: String,
    contentColor: androidx.compose.ui.graphics.Color,
    bubbleColor: androidx.compose.ui.graphics.Color,
    bubbleBorder: BorderStroke,
    bubbleShape: Shape,
    messageTextScale: Float,
) {
    Surface(
        modifier = Modifier.widthIn(max = MessageBubbleUserMessageMaxWidth),
        shape = bubbleShape,
        color = bubbleColor,
        contentColor = contentColor,
        border = bubbleBorder,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge.scaledBy(messageTextScale),
            )
        }
    }
}

@Composable
internal fun MessageActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    NarraIconButton(
        onClick = onClick,
        modifier = Modifier.size(MessageBubbleActionButtonSize),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
            },
            contentColor = if (highlighted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(MessageBubbleActionIconSize),
        )
    }
}

internal fun buildMessageCopyPayload(
    message: ChatMessage,
    displayContent: String,
    reasoningContent: String,
): String {
    val partSummary = message.parts
        .filter { it.type != ChatMessagePartType.TEXT }
        .map { part ->
            when {
                part.type == ChatMessagePartType.IMAGE -> {
                    "图片：${part.fileName.ifBlank { "未命名图片" }}"
                }

                part.type == ChatMessagePartType.FILE -> {
                    "文件：${part.fileName.ifBlank { "未命名文件" }}"
                }

                part.type == ChatMessagePartType.ACTION -> part.toActionCopyText().lineSequence().firstOrNull().orEmpty()

                part.type == ChatMessagePartType.SPECIAL -> part.toSpecialPlayCopyText().lineSequence().firstOrNull().orEmpty()

                else -> ""
            }
        }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n")
    val attachmentSummary = if (partSummary.isNotBlank()) {
        partSummary
    } else {
        message.attachments
            .map { attachment ->
                val attachmentName = attachment.fileName.ifBlank {
                    if (attachment.type == AttachmentType.IMAGE) {
                        "未命名图片"
                    } else {
                        "未命名文件"
                    }
                }
                buildString {
                    append(if (attachment.type == AttachmentType.IMAGE) "图片" else "文件")
                    append("：")
                    append(attachmentName)
                }
            }
            .joinToString(separator = "\n")
    }

    return when (message.role) {
        MessageRole.USER -> listOfNotNull(
            displayContent.takeIf { it.isNotBlank() },
            attachmentSummary.takeIf { it.isNotBlank() },
        ).joinToString(separator = "\n\n")
        MessageRole.ASSISTANT -> listOfNotNull(
            displayContent.takeIf { it.isNotBlank() },
            attachmentSummary.takeIf { it.isNotBlank() },
            reasoningContent.takeIf { it.isNotBlank() && displayContent.isBlank() && attachmentSummary.isBlank() },
        ).joinToString(separator = "\n\n")
    }
}

@Composable
internal fun MessageBubbleContent(
    message: ChatMessage,
    renderedContent: MessageBubbleRenderedContent,
    isUser: Boolean,
    useMarkdown: Boolean,
    displayContent: String,
    contentColor: androidx.compose.ui.graphics.Color,
    assistantMarkdownTypography: MarkdownTypography,
    assistantMarkdownPadding: MarkdownPadding,
    plainTextStyle: TextStyle,
    autoPreviewImages: Boolean,
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
    performanceMode: ChatMessagePerformanceMode,
    fastPlainText: Boolean,
    onConfirmTransferReceipt: ((String) -> Unit)?,
    onOpenImagePreview: ((Int) -> Unit)? = null,
    onOpenCitation: ((MessageCitation) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val displayAttachments = renderedContent.displayAttachments
    val displayParts = renderedContent.displayParts
    val assistantImageSources = renderedContent.assistantImageSources

    val isLoading = !isUser && message.status == com.example.myapplication.model.MessageStatus.LOADING
    val isRealContentBlank = displayContent.isBlank() || displayContent == "正在生成回复…"
    val isFirstTokenLoading = isLoading &&
        isRealContentBlank &&
        message.reasoningContent.isBlank() &&
        displayParts.isEmpty() &&
        displayAttachments.isEmpty() &&
        assistantImageSources.isEmpty()

    if (isFirstTokenLoading) {
        NarraPremiumLoadingIndicator(contentColor = contentColor, modifier = modifier)
        return
    }

    Column(
        // 流式结束、纯文本切换为富文本（Markdown）落定时平滑尺寸变化；流式进行中不启用，避免持续重排动画
        modifier = modifier.then(
            if (!fastPlainText) Modifier.animateContentSize() else Modifier,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (displayAttachments.isNotEmpty()) {
            displayAttachments.forEachIndexed { index, attachment ->
                LegacyAttachmentCard(
                    attachment = attachment,
                    isUser = isUser,
                    contentColor = contentColor,
                    autoPreviewImages = autoPreviewImages,
                    onOpenPreview = if (attachment.type == AttachmentType.IMAGE) {
                        { onOpenImagePreview?.invoke(index) }
                    } else {
                        null
                    },
                )
            }
        }

        if (displayParts.isNotEmpty()) {
            MessagePartsRenderer(
                parts = displayParts,
                isUser = isUser,
                useMarkdown = useMarkdown,
                contentColor = contentColor,
                markdownTypography = assistantMarkdownTypography,
                markdownPadding = assistantMarkdownPadding,
                plainTextStyle = plainTextStyle,
                autoPreviewImages = autoPreviewImages,
                codeBlockAutoWrap = codeBlockAutoWrap,
                codeBlockAutoCollapse = codeBlockAutoCollapse,
                performanceMode = performanceMode,
                onConfirmTransferReceipt = onConfirmTransferReceipt,
                onOpenImagePreviewAtIndex = onOpenImagePreview,
                citations = message.citations,
                onOpenCitation = onOpenCitation,
                fillTextWidth = !isUser,
                fastPlainText = fastPlainText,
            )
        } else {
            if (!isUser && assistantImageSources.isNotEmpty()) {
                assistantImageSources.forEachIndexed { index, imageSource ->
                    GeneratedImageAttachment(
                        uri = imageSource,
                        fileName = "assistant-image-${index + 1}",
                        autoPreviewImages = autoPreviewImages,
                        onOpenPreview = {
                            onOpenImagePreview?.invoke(index)
                        },
                    )
                }
            }

            if (displayContent.isNotBlank()) {
                RenderMessageText(
                    text = displayContent,
                    useMarkdown = useMarkdown,
                    contentColor = contentColor,
                    assistantMarkdownTypography = assistantMarkdownTypography,
                    assistantMarkdownPadding = assistantMarkdownPadding,
                    plainTextStyle = plainTextStyle,
                    codeBlockAutoWrap = codeBlockAutoWrap,
                    codeBlockAutoCollapse = codeBlockAutoCollapse,
                    performanceMode = performanceMode,
                    citations = message.citations,
                    onOpenCitation = onOpenCitation,
                    fillWidth = !isUser,
                    fastPlainText = fastPlainText,
                )
            }
        }
    }
}

internal fun TextStyle.scaledBy(scale: Float): TextStyle {
    return copy(
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )
}

@Composable
internal fun chatMarkdownPadding(
    compact: Boolean,
): MarkdownPadding {
    return markdownPadding(
        block = if (compact) 2.dp else 4.dp,
        list = if (compact) 2.dp else 4.dp,
        listItemTop = 0.dp,
        listItemBottom = if (compact) 1.dp else 2.dp,
        listIndent = if (compact) 8.dp else 10.dp,
        codeBlock = if (compact) {
            PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        } else {
            PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        },
        blockQuote = PaddingValues(horizontal = if (compact) 12.dp else 14.dp, vertical = 2.dp),
        blockQuoteText = PaddingValues(bottom = if (compact) 3.dp else 4.dp),
    )
}

@Composable
internal fun NarraPremiumLoadingIndicator(
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "premium_loading")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.keyframes {
                durationMillis = 1800
                0.85f at 0 using androidx.compose.animation.core.FastOutSlowInEasing
                1.15f at 900 using androidx.compose.animation.core.FastOutSlowInEasing
                0.85f at 1800 using androidx.compose.animation.core.FastOutSlowInEasing
            },
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "scale"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 2400, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "rotation"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 1800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Row(
        modifier = modifier
            .padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val tertiaryColor = MaterialTheme.colorScheme.tertiary
        val primaryContainer = MaterialTheme.colorScheme.primaryContainer

        Box(
            modifier = Modifier
                .size(18.dp)
                .scale(scale)
                .rotate(rotation)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                        colors = listOf(
                            primaryColor,
                            tertiaryColor,
                            primaryContainer,
                            primaryColor
                        )
                    ),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }

        Text(
            text = "正在思考...",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                letterSpacing = 0.5.sp
            ),
            color = contentColor.copy(alpha = glowAlpha)
        )
    }
}
