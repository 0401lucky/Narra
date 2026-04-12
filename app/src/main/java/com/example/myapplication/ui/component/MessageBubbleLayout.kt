package com.example.myapplication.ui.component

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@Composable
internal fun UserStructuredMessageContent(
    displayAttachments: List<MessageAttachment>,
    displayParts: List<ChatMessagePart>,
    displayContent: String,
    contentColor: androidx.compose.ui.graphics.Color,
    messageTextScale: Float,
    autoPreviewImages: Boolean,
    performanceMode: ChatMessagePerformanceMode,
    onConfirmTransferReceipt: ((String) -> Unit)?,
) {
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
                        UserUploadedImageThumbnail(
                            uri = part.uri,
                            fileName = part.fileName.ifBlank { "uploaded-image-${index + 1}" },
                            autoPreviewImages = autoPreviewImages,
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
    displayAttachments: List<MessageAttachment>,
    isUser: Boolean,
    useMarkdown: Boolean,
    displayContent: String,
    displayParts: List<ChatMessagePart>,
    assistantImageSources: List<String>,
    contentColor: androidx.compose.ui.graphics.Color,
    assistantMarkdownTypography: MarkdownTypography,
    assistantMarkdownPadding: MarkdownPadding,
    plainTextStyle: TextStyle,
    autoPreviewImages: Boolean,
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
    performanceMode: ChatMessagePerformanceMode,
    onConfirmTransferReceipt: ((String) -> Unit)?,
    onOpenCitation: ((MessageCitation) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (displayAttachments.isNotEmpty()) {
            displayAttachments.forEach { attachment ->
                LegacyAttachmentCard(
                    attachment = attachment,
                    isUser = isUser,
                    contentColor = contentColor,
                    autoPreviewImages = autoPreviewImages,
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
                citations = message.citations,
                onOpenCitation = onOpenCitation,
            )
        } else {
            if (!isUser && assistantImageSources.isNotEmpty()) {
                assistantImageSources.forEachIndexed { index, imageSource ->
                    GeneratedImageAttachment(
                        uri = imageSource,
                        fileName = "assistant-image-${index + 1}",
                        autoPreviewImages = autoPreviewImages,
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
