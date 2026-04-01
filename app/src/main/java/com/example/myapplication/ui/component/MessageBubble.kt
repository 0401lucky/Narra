package com.example.myapplication.ui.component

import com.example.myapplication.ui.component.*

import android.widget.Toast
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
import com.example.myapplication.model.formatTransferAmount
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

private const val AssistantBubbleWidthFactor = 0.9f
private val MessageActionButtonSize = 30.dp
private val MessageActionIconSize = 16.dp
private val UserMessageMaxWidth = 300.dp

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
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardScope = rememberCoroutineScope()
    val renderState = rememberMessageBubbleRenderState(
        message = message,
        streamingContent = streamingContent,
        streamingReasoningContent = streamingReasoningContent,
        streamingParts = streamingParts,
        isRemembered = isRemembered,
        messageTextScale = messageTextScale,
        reasoningExpandedByDefault = reasoningExpandedByDefault,
        showThinkingContent = showThinkingContent,
        autoCollapseThinking = autoCollapseThinking,
    )
    val isStreaming = streamingContent != null
    val resolvedContent = streamingContent ?: message.content
    val isUser = renderState.isUser
    val isError = renderState.isError
    val isLoading = renderState.isLoading
    val displayParts = renderState.displayParts
    val displayAttachments = renderState.displayAttachments
    val displayContent = renderState.displayContent
    val renderedDisplayContent = renderState.renderedDisplayContent
    val resolvedReasoningContent = renderState.resolvedReasoningContent
    val reasoningParts = renderState.reasoningParts
    val reasoningExpanded = renderState.reasoningExpanded
    val reasoningPreviewVisible = renderState.reasoningPreviewVisible
    val reasoningPreview = renderState.reasoningPreview
    val assistantVisualContent = renderState.assistantVisualContent
    val shouldShowContentBubble = renderState.shouldShowContentBubble
    val shouldUseSplitUserLayout = renderState.shouldUseSplitUserLayout
    val contentColor = renderState.contentColor
    val backgroundColor = renderState.backgroundColor
    val border = renderState.border
    val bubbleShape = renderState.bubbleShape
    val assistantMarkdownTypography = renderState.assistantMarkdownTypography
    val assistantMarkdownPadding = renderState.assistantMarkdownPadding
    val plainMessageTextStyle = renderState.plainMessageTextStyle
    val reasoningMarkdownTypography = renderState.reasoningMarkdownTypography
    val reasoningMarkdownPadding = renderState.reasoningMarkdownPadding
    val copyPayload = renderState.copyPayload
    val assistantWidthModifier = Modifier.fillMaxWidth(AssistantBubbleWidthFactor)

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
                onToggleExpanded = renderState.onToggleReasoning,
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

        if (!isUser && message.citations.isNotEmpty()) {
            AssistantCitationSection(
                citations = message.citations,
                onOpenUrl = uriHandler::openUri,
                modifier = assistantWidthModifier.padding(top = 8.dp),
            )
        }

        MessageBubbleActionRows(
            message = message,
            copyPayload = copyPayload,
            isUser = renderState.isUser,
            isError = renderState.isError,
            isRemembered = isRemembered,
            shouldShowUserActions = renderState.shouldShowUserActions,
            shouldShowAssistantActions = renderState.shouldShowAssistantActions,
            shouldShowErrorActions = renderState.shouldShowErrorActions,
            assistantWidthModifier = assistantWidthModifier,
            onRetry = onRetry,
            onToggleMemory = onToggleMemory,
            onTranslate = onTranslate,
            clipboard = clipboard,
            clipboardScope = clipboardScope,
        )
    }
}

@Composable
private fun AssistantCitationSection(
    citations: List<com.example.myapplication.model.MessageCitation>,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "来源",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            citations.forEach { citation ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenUrl(citation.url) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = citation.title.ifBlank { citation.url },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            if (citation.sourceLabel.isNotBlank()) {
                                append(citation.sourceLabel)
                                append(" · ")
                            }
                            append(citation.url)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.74f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

