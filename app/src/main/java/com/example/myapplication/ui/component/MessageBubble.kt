package com.example.myapplication.ui.component

import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.isTransferPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.toMessageAttachmentOrNull
import com.example.myapplication.model.toPlainText
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownPadding
import coil3.compose.AsyncImage
import java.io.File

private const val AssistantBubbleWidthFactor = 0.9f
private val MessageActionButtonSize = 30.dp
private val MessageActionIconSize = 16.dp
private val UserMessageMaxWidth = 300.dp
private val UserUploadedImageThumbnailSize = 148.dp
private const val MessageBubbleTag = "MessageBubble"
private const val ChatCodeBlockCollapseLines = 10
private enum class ReasoningCardDisplayState {
    Collapsed,
    Preview,
    Expanded,
}

private val ChatCodeBlockShape = RoundedCornerShape(18.dp)
private val OrderedMarkdownListHintRegex = Regex("""\d+\.\s+.+""")
private val MarkdownInlineHintRegex = Regex(
    """(```|`[^`\n]+`|\[[^\]]+]\([^)]+\)|!\[[^\]]*]\([^)]+\)|\*\*[^*\n]+\*\*|__[^_\n]+__|~~[^~\n]+~~)""",
)

@Suppress("DEPRECATION")
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    streamingContent: String? = null,
    streamingReasoningContent: String? = null,
    streamingParts: List<ChatMessagePart>? = null,
    onRetry: ((String) -> Unit)? = null,
    onToggleMemory: ((String) -> Unit)? = null,
    isRemembered: Boolean = false,
    onTranslate: ((String) -> Unit)? = null,
    onConfirmTransferReceipt: ((String) -> Unit)? = null,
    messageTextScale: Float = 1f,
    reasoningExpandedByDefault: Boolean = true,
    showThinkingContent: Boolean = true,
    autoCollapseThinking: Boolean = true,
    autoPreviewImages: Boolean = true,
    codeBlockAutoWrap: Boolean = false,
    codeBlockAutoCollapse: Boolean = false,
    reduceVisualEffects: Boolean = false,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val isUser = message.role == MessageRole.USER
    val isError = !isUser && message.status == MessageStatus.ERROR
    val isLoading = !isUser && message.status == MessageStatus.LOADING
    val isStreaming = streamingContent != null
    val resolvedContent = streamingContent ?: message.content
    val resolvedReasoningContent = streamingReasoningContent ?: message.reasoningContent
    val rawMessageParts = remember(message.parts, streamingParts) {
        normalizeChatMessageParts(streamingParts ?: message.parts)
    }
    val displayParts = remember(rawMessageParts, isUser, isStreaming) {
        if (rawMessageParts.isNotEmpty()) {
            rawMessageParts.map { part ->
                if (part.type == ChatMessagePartType.TEXT && !isUser && !isStreaming) {
                    part.copy(text = normalizeAssistantMarkdownForDisplay(part.text))
                } else {
                    part
                }
            }
        } else {
            emptyList()
        }
    }
    val hasStructuredParts = displayParts.isNotEmpty()
    val messageDisplayText = remember(hasStructuredParts, displayParts, resolvedContent) {
        if (hasStructuredParts) {
            displayParts.toPlainText().ifBlank { resolvedContent }
        } else {
            resolvedContent
        }
    }
    val displayAttachments = remember(hasStructuredParts, message.attachments) {
        if (hasStructuredParts) {
            emptyList()
        } else {
            message.attachments
        }
    }

    val isReasoningPhase = isLoading && resolvedContent.isBlank() && resolvedReasoningContent.isNotBlank()
    val hasReasoningContent = resolvedReasoningContent.isNotBlank()
    var userToggledReasoning by rememberSaveable(message.id) { mutableStateOf<Boolean?>(null) }
    var lastReasoningPhase by rememberSaveable(message.id) { mutableStateOf(isReasoningPhase) }
    LaunchedEffect(
        isReasoningPhase,
        hasReasoningContent,
        autoCollapseThinking,
    ) {
        when {
            !hasReasoningContent -> userToggledReasoning = null
            lastReasoningPhase && !isReasoningPhase && autoCollapseThinking -> {
                userToggledReasoning = false
            }
        }
        lastReasoningPhase = isReasoningPhase
    }
    val reasoningDisplayState = remember(
        userToggledReasoning,
        hasReasoningContent,
        isReasoningPhase,
        reasoningExpandedByDefault,
        showThinkingContent,
        autoCollapseThinking,
    ) {
        when {
            !hasReasoningContent -> ReasoningCardDisplayState.Collapsed
            userToggledReasoning == true -> ReasoningCardDisplayState.Expanded
            userToggledReasoning == false && isReasoningPhase && showThinkingContent -> {
                ReasoningCardDisplayState.Preview
            }
            userToggledReasoning == false -> ReasoningCardDisplayState.Collapsed
            isReasoningPhase && showThinkingContent -> ReasoningCardDisplayState.Preview
            isReasoningPhase -> ReasoningCardDisplayState.Collapsed
            autoCollapseThinking -> ReasoningCardDisplayState.Collapsed
            reasoningExpandedByDefault -> ReasoningCardDisplayState.Expanded
            else -> ReasoningCardDisplayState.Collapsed
        }
    }
    val reasoningExpanded = reasoningDisplayState == ReasoningCardDisplayState.Expanded
    val reasoningPreviewVisible = reasoningDisplayState == ReasoningCardDisplayState.Preview
    val userBubbleColor = lerp(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.surface,
        0.22f,
    )

    val backgroundColor = when {
        isUser -> userBubbleColor
        isError -> MaterialTheme.colorScheme.errorContainer
        isLoading -> MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        else -> MaterialTheme.colorScheme.surface
    }

    val contentColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val border = when {
        isUser -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
        isError -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.24f))
        isLoading -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    }

    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 24.dp)
    }

    val displayContent = remember(messageDisplayText, isLoading, resolvedReasoningContent) {
        messageDisplayText.ifBlank {
            if (isLoading && resolvedReasoningContent.isBlank()) {
                "正在生成回复…"
            } else {
                ""
            }
        }
    }
    val assistantVisualContent = remember(
        isUser,
        isStreaming,
        hasStructuredParts,
        displayContent,
    ) {
        if (!isUser && !isStreaming && !hasStructuredParts) {
            extractAssistantVisualContent(displayContent)
        } else {
            AssistantVisualContent(
                text = displayContent,
                imageSources = emptyList(),
            )
        }
    }
    val renderedDisplayContent = remember(
        isUser,
        isStreaming,
        hasStructuredParts,
        assistantVisualContent,
    ) {
        if (!isUser && !isStreaming && !hasStructuredParts) {
            normalizeAssistantMarkdownForDisplay(assistantVisualContent.text)
        } else {
            assistantVisualContent.text
        }
    }
    val renderedReasoningContent = remember(isLoading, resolvedReasoningContent) {
        if (!isLoading) {
            normalizeAssistantMarkdownForDisplay(resolvedReasoningContent)
        } else {
            resolvedReasoningContent
        }
    }
    val reasoningParts = remember(renderedReasoningContent) {
        if (renderedReasoningContent.isNotBlank()) {
            listOf(textMessagePart(renderedReasoningContent))
        } else {
            emptyList()
        }
    }
    val copyPayload = remember(message, displayContent, resolvedReasoningContent) {
        buildMessageCopyPayload(
            message = message,
            displayContent = displayContent,
            reasoningContent = resolvedReasoningContent,
        )
    }
    val reasoningPreview = remember(reasoningParts, renderedReasoningContent) {
        buildReasoningPreview(reasoningParts.toPlainText().ifBlank { renderedReasoningContent })
    }
    val shouldShowContentBubble = displayAttachments.isNotEmpty() ||
        hasStructuredParts ||
        assistantVisualContent.imageSources.isNotEmpty() ||
        displayContent.isNotBlank() ||
        isUser ||
        isError
    val shouldShowUserActions = isUser && copyPayload.isNotBlank()
    val shouldShowAssistantActions = !isUser &&
        !isLoading &&
        !isError &&
        (
            displayContent.isNotBlank() ||
                hasStructuredParts ||
                assistantVisualContent.imageSources.isNotEmpty() ||
                resolvedReasoningContent.isNotBlank()
            )
    val shouldShowErrorActions = isError && onRetry != null
    val shouldUseSplitUserLayout = isUser && (
        displayAttachments.any { it.type == AttachmentType.IMAGE } ||
            displayParts.any { it.type != ChatMessagePartType.TEXT }
        )
    val assistantWidthModifier = Modifier.fillMaxWidth(AssistantBubbleWidthFactor)
    val assistantMarkdownTypography = chatMarkdownTypography(
        paragraphStyle = MaterialTheme.typography.bodyLarge.scaledBy(messageTextScale),
        compact = false,
    )
    val plainMessageTextStyle = MaterialTheme.typography.bodyLarge.scaledBy(messageTextScale)
    val assistantMarkdownPadding = chatMarkdownPadding(compact = false)
    val reasoningMarkdownTypography = chatMarkdownTypography(
        paragraphStyle = MaterialTheme.typography.bodyMedium.scaledBy(messageTextScale),
        compact = true,
    )
    val reasoningMarkdownPadding = chatMarkdownPadding(compact = true)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (isUser) {
            Text(
                text = "你",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            val assistantLabel = message.modelName.ifBlank { "助手" }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (message.modelName.isNotBlank()) {
                    ModelIcon(modelName = message.modelName, size = 18.dp)
                }
                Text(
                    text = assistantLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (!isUser && resolvedReasoningContent.isNotBlank()) {
            AssistantReasoningCard(
                reasoningParts = reasoningParts,
                reasoningPreview = reasoningPreview,
                expanded = reasoningExpanded,
                previewVisible = reasoningPreviewVisible,
                isLoading = isLoading && resolvedContent.isBlank(),
                markdownTypography = reasoningMarkdownTypography,
                markdownPadding = reasoningMarkdownPadding,
                onToggleExpanded = {
                    userToggledReasoning = reasoningDisplayState != ReasoningCardDisplayState.Expanded
                },
                        previewTextStyle = MaterialTheme.typography.bodyMedium.scaledBy(messageTextScale),
                        autoPreviewImages = autoPreviewImages,
                        codeBlockAutoWrap = codeBlockAutoWrap,
                        codeBlockAutoCollapse = codeBlockAutoCollapse,
                        reduceVisualEffects = reduceVisualEffects,
                        modifier = assistantWidthModifier,
                    )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (shouldShowContentBubble) {
            if (shouldUseSplitUserLayout) {
                UserStructuredMessageContent(
                    displayAttachments = displayAttachments,
                    displayParts = displayParts,
                    displayContent = displayContent,
                    contentColor = contentColor,
                    messageTextScale = messageTextScale,
                    autoPreviewImages = autoPreviewImages,
                    onConfirmTransferReceipt = onConfirmTransferReceipt,
                )
            } else if (!isUser && !isError) {
                GlassMessageContainer(
                    modifier = assistantWidthModifier,
                    shape = bubbleShape,
                    tint = MaterialTheme.colorScheme.primary,
                    contentColor = contentColor,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    reduceVisualEffects = reduceVisualEffects,
                ) {
                    MessageBubbleContent(
                        message = message,
                        displayAttachments = displayAttachments,
                        isUser = isUser,
                        useMarkdown = !isUser && !isStreaming,
                        displayContent = renderedDisplayContent,
                        displayParts = displayParts,
                        assistantImageSources = assistantVisualContent.imageSources,
                        contentColor = contentColor,
                        assistantMarkdownTypography = assistantMarkdownTypography,
                        assistantMarkdownPadding = assistantMarkdownPadding,
                        plainTextStyle = plainMessageTextStyle,
                        autoPreviewImages = autoPreviewImages,
                        codeBlockAutoWrap = codeBlockAutoWrap,
                        codeBlockAutoCollapse = codeBlockAutoCollapse,
                        onConfirmTransferReceipt = onConfirmTransferReceipt,
                    )
                }
            } else {
                Surface(
                    modifier = if (isUser) Modifier else assistantWidthModifier,
                    shape = bubbleShape,
                    color = backgroundColor,
                    contentColor = contentColor,
                    border = border,
                    tonalElevation = if (isUser) 0.dp else 2.dp,
                    shadowElevation = 0.dp,
                ) {
                    MessageBubbleContent(
                        message = message,
                        displayAttachments = displayAttachments,
                        isUser = isUser,
                        useMarkdown = !isUser && !isStreaming,
                        displayContent = renderedDisplayContent,
                        displayParts = displayParts,
                        assistantImageSources = assistantVisualContent.imageSources,
                        contentColor = contentColor,
                        assistantMarkdownTypography = assistantMarkdownTypography,
                        assistantMarkdownPadding = assistantMarkdownPadding,
                        plainTextStyle = plainMessageTextStyle,
                        autoPreviewImages = autoPreviewImages,
                        codeBlockAutoWrap = codeBlockAutoWrap,
                        codeBlockAutoCollapse = codeBlockAutoCollapse,
                        onConfirmTransferReceipt = onConfirmTransferReceipt,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }

        if (shouldShowUserActions) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MessageActionIconButton(
                    icon = Icons.Outlined.ContentCopy,
                    contentDescription = "复制消息",
                    onClick = {
                        if (copyPayload.isBlank()) {
                            return@MessageActionIconButton
                        }
                        clipboardManager.setText(AnnotatedString(copyPayload))
                        Toast.makeText(context, "已复制消息", Toast.LENGTH_SHORT).show()
                    },
                )
                if (onTranslate != null) {
                    MessageActionIconButton(
                        icon = Icons.Default.Translate,
                        contentDescription = "翻译消息",
                        onClick = { onTranslate(message.id) },
                    )
                }
                if (onToggleMemory != null) {
                    MessageActionIconButton(
                        icon = Icons.Outlined.Psychology,
                        contentDescription = if (isRemembered) "取消记忆" else "记住这条",
                        onClick = { onToggleMemory(message.id) },
                        highlighted = isRemembered,
                    )
                }
            }
        }

        if (shouldShowAssistantActions) {
            Row(
                modifier = assistantWidthModifier
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (copyPayload.isNotBlank()) {
                    MessageActionIconButton(
                        icon = Icons.Outlined.ContentCopy,
                        contentDescription = "复制回复",
                        onClick = {
                            clipboardManager.setText(AnnotatedString(copyPayload))
                            Toast.makeText(context, "已复制回复", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                if (onTranslate != null && copyPayload.isNotBlank()) {
                    MessageActionIconButton(
                        icon = Icons.Default.Translate,
                        contentDescription = "翻译回复",
                        onClick = { onTranslate(message.id) },
                    )
                }
                if (onRetry != null) {
                    MessageActionIconButton(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = "重新生成回复",
                        onClick = { onRetry(message.id) },
                    )
                }
                if (onToggleMemory != null && copyPayload.isNotBlank()) {
                    MessageActionIconButton(
                        icon = Icons.Outlined.Psychology,
                        contentDescription = if (isRemembered) "取消记忆" else "记住这条",
                        onClick = { onToggleMemory(message.id) },
                        highlighted = isRemembered,
                    )
                }
            }
        }

        if (shouldShowErrorActions) {
            Row(
                modifier = assistantWidthModifier
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (copyPayload.isNotBlank()) {
                    MessageActionIconButton(
                        icon = Icons.Outlined.ContentCopy,
                        contentDescription = "复制错误消息",
                        onClick = {
                            clipboardManager.setText(AnnotatedString(copyPayload))
                            Toast.makeText(context, "已复制消息", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                MessageActionIconButton(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = "重试",
                    onClick = { onRetry?.invoke(message.id) },
                )
                if (onTranslate != null && copyPayload.isNotBlank()) {
                    MessageActionIconButton(
                        icon = Icons.Default.Translate,
                        contentDescription = "翻译错误消息",
                        onClick = { onTranslate(message.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserStructuredMessageContent(
    displayAttachments: List<MessageAttachment>,
    displayParts: List<ChatMessagePart>,
    displayContent: String,
    contentColor: androidx.compose.ui.graphics.Color,
    messageTextScale: Float,
    autoPreviewImages: Boolean,
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
                            Box(modifier = Modifier.widthIn(max = UserMessageMaxWidth)) {
                                PartAttachmentCard(
                                    attachment = attachment,
                                    contentColor = contentColor,
                                )
                            }
                        }
                    }

                    ChatMessagePartType.SPECIAL -> {
                        if (part.isTransferPart()) {
                            Box(modifier = Modifier.widthIn(max = UserMessageMaxWidth)) {
                                TransferPlayCard(
                                    part = part,
                                    isUserMessage = true,
                                    onConfirmTransferReceipt = onConfirmTransferReceipt,
                                )
                            }
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
                        Box(modifier = Modifier.widthIn(max = UserMessageMaxWidth)) {
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
private fun UserTextBubble(
    text: String,
    contentColor: androidx.compose.ui.graphics.Color,
    bubbleColor: androidx.compose.ui.graphics.Color,
    bubbleBorder: BorderStroke,
    bubbleShape: Shape,
    messageTextScale: Float,
) {
    Surface(
        modifier = Modifier.widthIn(max = UserMessageMaxWidth),
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
private fun MessageActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(MessageActionButtonSize),
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
            modifier = Modifier.size(MessageActionIconSize),
        )
    }
}

private fun buildMessageCopyPayload(
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

                part.isTransferPart() -> {
                    buildString {
                        append("转账：")
                        append(formatTransferAmount(part.specialAmount))
                        append(" · ")
                        append(part.specialCounterparty.ifBlank { "对方" })
                    }
                }

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
private fun MessageBubbleContent(
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
    onConfirmTransferReceipt: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                onConfirmTransferReceipt = onConfirmTransferReceipt,
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
                )
            }
        }
    }
}

@Composable
private fun MessagePartsRenderer(
    parts: List<ChatMessagePart>,
    isUser: Boolean,
    useMarkdown: Boolean,
    contentColor: androidx.compose.ui.graphics.Color,
    markdownTypography: MarkdownTypography,
    markdownPadding: MarkdownPadding,
    plainTextStyle: TextStyle,
    autoPreviewImages: Boolean,
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
    onConfirmTransferReceipt: ((String) -> Unit)?,
) {
    parts.forEachIndexed { index, part ->
        when (part.type) {
            ChatMessagePartType.TEXT -> {
                RenderMessageText(
                    text = part.text,
                    useMarkdown = useMarkdown,
                    contentColor = contentColor,
                    assistantMarkdownTypography = markdownTypography,
                    assistantMarkdownPadding = markdownPadding,
                    plainTextStyle = plainTextStyle,
                    codeBlockAutoWrap = codeBlockAutoWrap,
                    codeBlockAutoCollapse = codeBlockAutoCollapse,
                )
            }

            ChatMessagePartType.IMAGE -> {
                GeneratedImageAttachment(
                    uri = part.uri,
                    fileName = part.fileName.ifBlank {
                        if (isUser) "uploaded-image-${index + 1}" else "assistant-image-${index + 1}"
                    },
                    autoPreviewImages = autoPreviewImages,
                )
            }

            ChatMessagePartType.FILE -> {
                val attachment = part.toMessageAttachmentOrNull()
                if (attachment != null) {
                    PartAttachmentCard(
                        attachment = attachment,
                        contentColor = contentColor,
                    )
                }
            }

            ChatMessagePartType.SPECIAL -> {
                if (part.specialType == ChatSpecialType.TRANSFER) {
                    TransferPlayCard(
                        part = part,
                        isUserMessage = isUser,
                        onConfirmTransferReceipt = onConfirmTransferReceipt,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferPlayCard(
    part: ChatMessagePart,
    isUserMessage: Boolean,
    onConfirmTransferReceipt: ((String) -> Unit)?,
) {
    if (!part.isTransferPart()) {
        return
    }

    val isAssistantToUser = part.specialDirection == TransferDirection.ASSISTANT_TO_USER
    val canConfirmReceipt = !isUserMessage &&
        isAssistantToUser &&
        part.specialStatus == TransferStatus.PENDING &&
        onConfirmTransferReceipt != null &&
        part.specialId.isNotBlank()
    val topColor = if (isUserMessage) {
        androidx.compose.ui.graphics.Color(0xFFF4C16F)
    } else {
        androidx.compose.ui.graphics.Color(0xFFF0B35A)
    }
    val bottomColor = if (isUserMessage) {
        androidx.compose.ui.graphics.Color(0xFFEAAA47)
    } else {
        androidx.compose.ui.graphics.Color(0xFFE19A3A)
    }
    val contentColor = androidx.compose.ui.graphics.Color.White

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(topColor, bottomColor),
                    ),
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = androidx.compose.ui.graphics.Color.White,
                    )
                }
                Text(
                    text = transferDirectionLabel(part),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = formatTransferAmount(part.specialAmount),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            if (part.specialNote.isNotBlank()) {
                Text(
                    text = part.specialNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.92f),
                )
            }
            HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f))
            Text(
                text = transferStatusLabel(
                    status = part.specialStatus,
                    direction = part.specialDirection,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.92f),
            )
            if (canConfirmReceipt) {
                FilledTonalButton(
                    onClick = { onConfirmTransferReceipt?.invoke(part.specialId) },
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = androidx.compose.ui.graphics.Color.White,
                        contentColor = androidx.compose.ui.graphics.Color(0xFF8A4B08),
                    ),
                ) {
                    Text("确认收款")
                }
            }
        }
    }
}

private fun transferDirectionLabel(part: ChatMessagePart): String {
    return when (part.specialDirection) {
        TransferDirection.USER_TO_ASSISTANT -> "转账给 ${part.specialCounterparty.ifBlank { "对方" }}"
        TransferDirection.ASSISTANT_TO_USER -> "${part.specialCounterparty.ifBlank { "对方" }} 向你转账"
        null -> "转账"
    }
}

private fun transferStatusLabel(
    status: TransferStatus?,
    direction: TransferDirection?,
): String {
    return when (status) {
        TransferStatus.PENDING -> when (direction) {
            TransferDirection.USER_TO_ASSISTANT -> "待对方收款"
            TransferDirection.ASSISTANT_TO_USER -> "请确认收款"
            null -> "待收款"
        }
        TransferStatus.RECEIVED -> "已收款"
        null -> "处理中"
    }
}

private fun formatTransferAmount(amount: String): String {
    return if (amount.startsWith("¥")) amount else "¥$amount"
}

@Composable
private fun RenderMessageText(
    text: String,
    useMarkdown: Boolean,
    contentColor: androidx.compose.ui.graphics.Color,
    assistantMarkdownTypography: MarkdownTypography,
    assistantMarkdownPadding: MarkdownPadding,
    plainTextStyle: TextStyle,
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
) {
    if (text.isBlank()) {
        return
    }

    val shouldUseMarkdown = remember(text, useMarkdown) {
        useMarkdown && shouldRenderWithMarkdown(text)
    }
    val markdownComponents = rememberChatMarkdownComponents(
        markdownPadding = assistantMarkdownPadding,
        codeBlockAutoWrap = codeBlockAutoWrap,
        codeBlockAutoCollapse = codeBlockAutoCollapse,
    )

    if (shouldUseMarkdown) {
        Markdown(
            content = text,
            colors = markdownColor(text = contentColor),
            typography = assistantMarkdownTypography,
            modifier = Modifier.fillMaxWidth(),
            padding = assistantMarkdownPadding,
            components = markdownComponents,
        )
    } else {
        Text(
            text = text,
            color = contentColor,
            style = plainTextStyle,
        )
    }
}

@Composable
private fun rememberChatMarkdownComponents(
    markdownPadding: MarkdownPadding,
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
): MarkdownComponents {
    val codeBlockPadding = markdownPadding.codeBlock
    return remember(codeBlockPadding, codeBlockAutoWrap, codeBlockAutoCollapse) {
        markdownComponents(
            checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
            codeFence = {
                MarkdownCodeFence(it.content, it.node, style = it.typography.code) { code, language, style ->
                    ChatMarkdownCodeBlock(
                        code = code,
                        language = language,
                        style = style,
                        codeBlockPadding = codeBlockPadding,
                        codeBlockAutoWrap = codeBlockAutoWrap,
                        codeBlockAutoCollapse = codeBlockAutoCollapse,
                    )
                }
            },
            codeBlock = {
                MarkdownCodeBlock(it.content, it.node, style = it.typography.code) { code, language, style ->
                    ChatMarkdownCodeBlock(
                        code = code,
                        language = language,
                        style = style,
                        codeBlockPadding = codeBlockPadding,
                        codeBlockAutoWrap = codeBlockAutoWrap,
                        codeBlockAutoCollapse = codeBlockAutoCollapse,
                    )
                }
            },
        )
    }
}

@Composable
private fun ChatMarkdownCodeBlock(
    code: String,
    language: String?,
    style: TextStyle,
    codeBlockPadding: PaddingValues,
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
) {
    val normalizedCode = remember(code) {
        code.trimEnd('\n', '\r')
    }
    val normalizedLanguage = remember(language) {
        language.orEmpty().trim()
    }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val codeLines = remember(normalizedCode) {
        normalizedCode.lines()
    }
    val shouldShowCollapseAction = codeBlockAutoCollapse && codeLines.size > ChatCodeBlockCollapseLines
    var isExpanded by remember(normalizedCode, codeBlockAutoCollapse) {
        mutableStateOf(!codeBlockAutoCollapse)
    }
    val displayCode = remember(codeLines, isExpanded, shouldShowCollapseAction, normalizedCode) {
        if (shouldShowCollapseAction && !isExpanded) {
            codeLines.take(ChatCodeBlockCollapseLines).joinToString(separator = "\n")
        } else {
            normalizedCode
        }
    }
    val horizontalScrollState = rememberScrollState()
    val canScrollHorizontally by remember(horizontalScrollState) {
        derivedStateOf { !codeBlockAutoWrap && horizontalScrollState.maxValue > 0 }
    }
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = normalizedLanguage.ifBlank { "代码块" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
                if (canScrollHorizontally) {
                    Text(
                        text = "左右滑动查看完整内容",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(normalizedCode))
                    Toast.makeText(context, "已复制代码块", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "复制代码块",
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = ChatCodeBlockShape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Box(
                modifier = if (codeBlockAutoWrap) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState)
                },
            ) {
                Text(
                    text = displayCode,
                    modifier = Modifier.padding(codeBlockPadding),
                    style = style,
                    softWrap = codeBlockAutoWrap,
                )
            }
        }

        if (shouldShowCollapseAction) {
            Text(
                text = if (isExpanded) "收起代码" else "展开全部代码",
                modifier = Modifier.clickable { isExpanded = !isExpanded },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LegacyAttachmentCard(
    attachment: MessageAttachment,
    isUser: Boolean,
    contentColor: androidx.compose.ui.graphics.Color,
    autoPreviewImages: Boolean,
) {
    if (!isUser && attachment.type == AttachmentType.IMAGE) {
        GeneratedImageAttachment(
            uri = attachment.uri,
            fileName = attachment.fileName,
            autoPreviewImages = autoPreviewImages,
        )
        return
    }

    PartAttachmentCard(
        attachment = attachment,
        contentColor = contentColor,
    )
}

@Composable
private fun PartAttachmentCard(
    attachment: MessageAttachment,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = contentColor.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (attachment.type == AttachmentType.IMAGE) {
                    Icons.Default.Image
                } else {
                    Icons.Default.Description
                },
                contentDescription = null,
                modifier = Modifier.padding(top = 1.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = attachment.fileName.ifBlank {
                        if (attachment.type == AttachmentType.IMAGE) "已附加图片" else "已附加文件"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
                Text(
                    text = if (attachment.type == AttachmentType.IMAGE) "图片输入" else "文件输入",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun AssistantReasoningCard(
    reasoningParts: List<ChatMessagePart>,
    reasoningPreview: String,
    expanded: Boolean,
    previewVisible: Boolean,
    isLoading: Boolean,
    markdownTypography: MarkdownTypography,
    markdownPadding: MarkdownPadding,
    onToggleExpanded: () -> Unit,
    previewTextStyle: TextStyle,
    autoPreviewImages: Boolean,
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
    reduceVisualEffects: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentAnimationModifier = if (isLoading) {
        Modifier
    } else {
        Modifier.animateContentSize(
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            ),
        )
    }

    GlassMessageContainer(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tint = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        reduceVisualEffects = reduceVisualEffects,
    ) {
        Column(
            modifier = contentAnimationModifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                    )
                    Text(
                        text = if (isLoading) "思考中" else "思考",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起思考内容" else "展开思考内容",
                )
            }

            if (expanded) {
                if (reasoningParts.isNotEmpty()) {
                    MessagePartsRenderer(
                        parts = reasoningParts,
                        isUser = false,
                        useMarkdown = !isLoading,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        markdownTypography = markdownTypography,
                        markdownPadding = markdownPadding,
                        plainTextStyle = previewTextStyle,
                        autoPreviewImages = autoPreviewImages,
                        codeBlockAutoWrap = codeBlockAutoWrap,
                        codeBlockAutoCollapse = codeBlockAutoCollapse,
                        onConfirmTransferReceipt = null,
                    )
                }
            } else if (previewVisible && reasoningPreview.isNotBlank()) {
                Text(
                    text = reasoningPreview,
                    style = previewTextStyle,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GlassMessageContainer(
    modifier: Modifier = Modifier,
    shape: Shape,
    tint: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    contentPadding: PaddingValues,
    reduceVisualEffects: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = glassPalette(tint = tint)
    val containerModifier = if (reduceVisualEffects) {
        modifier
            .clip(shape)
            .background(brush = palette.containerBrush, shape = shape)
            .border(width = 1.dp, brush = palette.borderBrush, shape = shape)
    } else {
        modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                clip = false,
                ambientColor = palette.shadowColor,
                spotColor = palette.shadowColor,
            )
            .clip(shape)
            .background(brush = palette.containerBrush, shape = shape)
            .border(width = 1.dp, brush = palette.borderBrush, shape = shape)
    }

    Box(
        modifier = containerModifier,
    ) {
        if (!reduceVisualEffects) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(brush = palette.sheenBrush, shape = shape),
            )
        }

        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

private fun buildReasoningPreview(reasoningContent: String): String {
    if (reasoningContent.isBlank()) {
        return ""
    }

    val normalized = reasoningContent
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
        .takeLast(4)
        .joinToString(separator = "\n")

    if (normalized.length <= 220) {
        return normalized
    }

    return "…" + normalized.takeLast(220)
}

internal fun shouldRenderWithMarkdown(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.isBlank()) {
        return false
    }
    if ("```" in normalized) {
        return true
    }

    val hasStructuredLine = normalized
        .lineSequence()
        .map(String::trimStart)
        .any { line ->
            line.startsWith("#") ||
                line.startsWith("> ") ||
                line.startsWith("- ") ||
                line.startsWith("* ") ||
                line.startsWith("+ ") ||
                OrderedMarkdownListHintRegex.matches(line) ||
                line.startsWith("|")
        }
    if (hasStructuredLine) {
        return true
    }

    return MarkdownInlineHintRegex.containsMatchIn(normalized)
}

@Composable
private fun glassPalette(
    tint: androidx.compose.ui.graphics.Color,
): GlassPalette {
    val surface = MaterialTheme.colorScheme.surface
    val isDark = surface.luminance() < 0.5f

    return remember(tint, surface, isDark) {
        GlassPalette(
            containerBrush = Brush.verticalGradient(
                colors = listOf(
                    if (isDark) {
                        surface.copy(alpha = 0.80f)
                    } else {
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.76f)
                    },
                    tint.copy(alpha = if (isDark) 0.18f else 0.12f),
                    surface.copy(alpha = if (isDark) 0.58f else 0.64f),
                ),
            ),
            sheenBrush = Brush.linearGradient(
                colors = listOf(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = if (isDark) 0.12f else 0.36f),
                    androidx.compose.ui.graphics.Color.White.copy(alpha = if (isDark) 0.04f else 0.10f),
                    androidx.compose.ui.graphics.Color.Transparent,
                ),
                start = Offset.Zero,
                end = Offset(560f, 280f),
            ),
            borderBrush = Brush.verticalGradient(
                colors = listOf(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = if (isDark) 0.24f else 0.58f),
                    tint.copy(alpha = if (isDark) 0.18f else 0.16f),
                    androidx.compose.ui.graphics.Color.White.copy(alpha = if (isDark) 0.08f else 0.20f),
                ),
            ),
            shadowColor = tint.copy(alpha = if (isDark) 0.16f else 0.10f),
            flatContainerColor = if (isDark) {
                surface.copy(alpha = 0.92f)
            } else {
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.98f)
            },
            flatBorderColor = tint.copy(alpha = if (isDark) 0.14f else 0.12f),
        )
    }
}

@Composable
private fun chatMarkdownTypography(
    paragraphStyle: TextStyle,
    compact: Boolean,
): MarkdownTypography {
    val sectionTitleStyle = if (compact) {
        MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 22.sp,
        )
    } else {
        MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 30.sp,
        )
    }
    val subsectionTitleStyle = if (compact) {
        MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
        )
    } else {
        MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
        )
    }
    val supportingStyle = if (compact) {
        MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
    } else {
        MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
    }
    val emphasizedParagraphStyle = paragraphStyle.copy(fontWeight = FontWeight.SemiBold)
    val codeStyle = paragraphStyle.copy(fontFamily = FontFamily.Monospace)

    // 聊天气泡里的 Markdown 不适合沿用 display 级标题，否则会把整条回复撑成海报。
    return markdownTypography(
        h1 = sectionTitleStyle,
        h2 = subsectionTitleStyle,
        h3 = emphasizedParagraphStyle,
        h4 = emphasizedParagraphStyle,
        h5 = supportingStyle.copy(fontWeight = FontWeight.SemiBold),
        h6 = supportingStyle.copy(fontWeight = FontWeight.Medium),
        text = paragraphStyle,
        code = codeStyle,
        inlineCode = codeStyle,
        quote = supportingStyle.copy(fontStyle = FontStyle.Italic),
        paragraph = paragraphStyle,
        ordered = paragraphStyle,
        bullet = paragraphStyle,
        list = paragraphStyle,
        table = supportingStyle,
    )
}

@Composable
private fun GeneratedImageAttachment(
    uri: String,
    fileName: String,
    autoPreviewImages: Boolean,
) {
    if (!autoPreviewImages) {
        PartAttachmentPreviewCard(
            fileName = fileName.ifBlank { "图片预览已关闭" },
            supportingText = "已关闭自动图片预览，可通过复制、导出或系统分享查看原始链接。",
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    var loadFailed by remember(uri) { mutableStateOf(false) }
    val model = remember(uri) {
        resolveImageModel(uri)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 320.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = fileName.ifBlank { "生成的图片" },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 320.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.FillWidth,
            onLoading = {
                loadFailed = false
            },
            onSuccess = {
                loadFailed = false
            },
            onError = { state ->
                loadFailed = true
                Log.w(MessageBubbleTag, "图片加载失败：$uri", state.result.throwable)
            },
        )

        if (loadFailed) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 320.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Text(
                            text = fileName.ifBlank { "图片加载失败" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = imageLoadFailureHint(uri),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun resolveImageModel(uri: String): Any {
    return when {
        uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true) ||
            uri.startsWith("data:image", ignoreCase = true) ||
            uri.startsWith("file://", ignoreCase = true) -> uri
        uri.startsWith("content://", ignoreCase = true) -> uri.toUri()
        else -> File(uri)
    }
}

private fun imageLoadFailureHint(uri: String): String {
    return when {
        uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true) ->
            "远程图片不可访问，可能是链接过期、鉴权失败或网络受限。"
        uri.startsWith("data:image", ignoreCase = true) ->
            "Base64 图片数据无效、过大，或在解析过程中被截断。"
        uri.startsWith("content://", ignoreCase = true) ->
            "内容 URI 无法读取，请确认权限仍然有效。"
        else ->
            "本地图片文件不存在，或当前路径无法被图片加载器读取。"
    }
}

private fun shouldHideLegacyAttachmentSummary(
    content: String,
    attachments: List<MessageAttachment>,
): Boolean {
    if (attachments.isEmpty()) {
        return false
    }

    return content in setOf("图片已发送", "文件已附加")
}

@Composable
private fun UserUploadedImageThumbnail(
    uri: String,
    fileName: String,
    autoPreviewImages: Boolean,
) {
    if (!autoPreviewImages) {
        PartAttachmentPreviewCard(
            fileName = fileName.ifBlank { "已选择图片" },
            supportingText = "已关闭自动图片预览",
            modifier = Modifier.size(UserUploadedImageThumbnailSize),
        )
        return
    }
    var loadFailed by remember(uri) { mutableStateOf(false) }
    val model = remember(uri) {
        resolveImageModel(uri)
    }

    Surface(
        modifier = Modifier.size(UserUploadedImageThumbnailSize),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model,
                contentDescription = fileName.ifBlank { "上传的图片" },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onLoading = {
                    loadFailed = false
                },
                onSuccess = {
                    loadFailed = false
                },
                onError = { state ->
                    loadFailed = true
                    Log.w(MessageBubbleTag, "图片加载失败：$uri", state.result.throwable)
                },
            )

            if (loadFailed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "图片不可预览",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PartAttachmentPreviewCard(
    fileName: String,
    supportingText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 72.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun TextStyle.scaledBy(scale: Float): TextStyle {
    return copy(
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )
}

@Composable
private fun chatMarkdownPadding(
    compact: Boolean,
): MarkdownPadding {
    return markdownPadding(
        block = if (compact) 1.dp else 2.dp,
        list = if (compact) 1.dp else 2.dp,
        listItemTop = 0.dp,
        listItemBottom = if (compact) 0.dp else 1.dp,
        listIndent = if (compact) 6.dp else 8.dp,
        codeBlock = if (compact) {
            PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        } else {
            PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        },
        blockQuote = PaddingValues(horizontal = if (compact) 10.dp else 12.dp, vertical = 0.dp),
        blockQuoteText = PaddingValues(bottom = if (compact) 2.dp else 3.dp),
    )
}

private data class GlassPalette(
    val containerBrush: Brush,
    val sheenBrush: Brush,
    val borderBrush: Brush,
    val shadowColor: androidx.compose.ui.graphics.Color,
    val flatContainerColor: androidx.compose.ui.graphics.Color,
    val flatBorderColor: androidx.compose.ui.graphics.Color,
)
