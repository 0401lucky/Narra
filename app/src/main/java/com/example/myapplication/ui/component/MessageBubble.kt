package com.example.myapplication.ui.component

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
import com.example.myapplication.model.ChatReasoningStep
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

private const val AssistantBubbleWidthFactor = 0.94f
private val MessageActionButtonSize = 30.dp
private val MessageActionIconSize = 16.dp
private val UserMessageMaxWidth = 300.dp

enum class ChatMessagePerformanceMode {
    FULL,
    SCROLLING_LIGHT,
}

@Suppress("DEPRECATION")
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    streamingContent: String? = null,
    streamingReasoningContent: String? = null,
    streamingReasoningSteps: List<ChatReasoningStep>? = null,
    streamingParts: List<ChatMessagePart>? = null,
    onRetry: ((String) -> Unit)? = null,
    onOpenMessageActions: ((String) -> Unit)? = null,
    onToggleMemory: ((String) -> Unit)? = null,
    isRemembered: Boolean = false,
    onTranslate: ((String) -> Unit)? = null,
    onOpenUrlPreview: ((String, String) -> Unit)? = null,
    onConfirmTransferReceipt: ((String) -> Unit)? = null,
    messageTextScale: Float = 1f,
    reasoningExpandedByDefault: Boolean = true,
    showThinkingContent: Boolean = true,
    autoCollapseThinking: Boolean = true,
    autoPreviewImages: Boolean = true,
    codeBlockAutoWrap: Boolean = false,
    codeBlockAutoCollapse: Boolean = false,
    performanceMode: ChatMessagePerformanceMode = ChatMessagePerformanceMode.FULL,
    reduceVisualEffects: Boolean = false,
) {
    val uriHandler = LocalUriHandler.current
    val renderState = rememberMessageBubbleRenderState(
        message = message,
        streamingContent = streamingContent,
        streamingReasoningContent = streamingReasoningContent,
        streamingReasoningSteps = streamingReasoningSteps,
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
    val reasoningSteps = renderState.reasoningSteps
    val reasoningExpanded = renderState.reasoningExpanded
    val reasoningPreviewVisible = renderState.reasoningPreviewVisible
    val assistantVisualContent = renderState.assistantVisualContent
    val renderedContent = remember(displayAttachments, displayParts, assistantVisualContent) {
        MessageBubbleRenderedContent(
            displayAttachments = displayAttachments,
            displayParts = displayParts,
            assistantImageSources = assistantVisualContent.imageSources,
        )
    }
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
    val isScrollingLight = performanceMode == ChatMessagePerformanceMode.SCROLLING_LIGHT
    val effectiveReduceVisualEffects = reduceVisualEffects || isScrollingLight
    val effectiveReasoningExpanded = reasoningExpanded
    val effectiveReasoningPreviewVisible = reasoningPreviewVisible
    val effectiveUseMarkdown = remember(isUser, isStreaming, renderedDisplayContent, performanceMode) {
        if (isUser || isStreaming) {
            false
        } else {
            when (performanceMode) {
                ChatMessagePerformanceMode.FULL -> true
                ChatMessagePerformanceMode.SCROLLING_LIGHT -> {
                    shouldRenderWithMarkdownDuringScrolling(renderedDisplayContent)
                }
            }
        }
    }

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

        if (!isUser && reasoningSteps.isNotEmpty()) {
            ReasoningTimelineCard(
                reasoningSteps = reasoningSteps,
                expanded = effectiveReasoningExpanded,
                previewVisible = effectiveReasoningPreviewVisible,
                isLoading = isLoading && resolvedContent.isBlank(),
                markdownTypography = reasoningMarkdownTypography,
                markdownPadding = reasoningMarkdownPadding,
                onToggleExpanded = renderState.onToggleReasoning,
                previewTextStyle = MaterialTheme.typography.bodyMedium.scaledBy(messageTextScale),
                autoPreviewImages = autoPreviewImages,
                codeBlockAutoWrap = codeBlockAutoWrap,
                codeBlockAutoCollapse = codeBlockAutoCollapse,
                performanceMode = performanceMode,
                modifier = assistantWidthModifier,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (shouldShowContentBubble) {
            if (shouldUseSplitUserLayout) {
                UserStructuredMessageContent(
                    renderedContent = renderedContent,
                    displayContent = displayContent,
                    contentColor = contentColor,
                    messageTextScale = messageTextScale,
                    autoPreviewImages = autoPreviewImages,
                    performanceMode = performanceMode,
                    onConfirmTransferReceipt = onConfirmTransferReceipt,
                )
            } else if (!isUser && !isError) {
                GlassMessageContainer(
                    modifier = assistantWidthModifier,
                    shape = bubbleShape,
                    tint = MaterialTheme.colorScheme.primary,
                    contentColor = contentColor,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    reduceVisualEffects = effectiveReduceVisualEffects,
                ) {
                    MessageBubbleContent(
                        message = message,
                        renderedContent = renderedContent,
                        isUser = isUser,
                        useMarkdown = effectiveUseMarkdown,
                        displayContent = renderedDisplayContent,
                        contentColor = contentColor,
                        assistantMarkdownTypography = assistantMarkdownTypography,
                        assistantMarkdownPadding = assistantMarkdownPadding,
                        plainTextStyle = plainMessageTextStyle,
                        autoPreviewImages = autoPreviewImages,
                        codeBlockAutoWrap = codeBlockAutoWrap,
                        codeBlockAutoCollapse = codeBlockAutoCollapse,
                        performanceMode = performanceMode,
                        onConfirmTransferReceipt = onConfirmTransferReceipt,
                        onOpenCitation = { citation ->
                            if (onOpenUrlPreview != null) {
                                onOpenUrlPreview(
                                    citation.url,
                                    citation.title.ifBlank { citation.url },
                                )
                            } else {
                                uriHandler.openUri(citation.url)
                            }
                        },
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
                        renderedContent = renderedContent,
                        isUser = isUser,
                        useMarkdown = effectiveUseMarkdown,
                        displayContent = renderedDisplayContent,
                        contentColor = contentColor,
                        assistantMarkdownTypography = assistantMarkdownTypography,
                        assistantMarkdownPadding = assistantMarkdownPadding,
                        plainTextStyle = plainMessageTextStyle,
                        autoPreviewImages = autoPreviewImages,
                        codeBlockAutoWrap = codeBlockAutoWrap,
                        codeBlockAutoCollapse = codeBlockAutoCollapse,
                        performanceMode = performanceMode,
                        onConfirmTransferReceipt = onConfirmTransferReceipt,
                        onOpenCitation = { citation ->
                            if (onOpenUrlPreview != null) {
                                onOpenUrlPreview(
                                    citation.url,
                                    citation.title.ifBlank { citation.url },
                                )
                            } else {
                                uriHandler.openUri(citation.url)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }

        if (!isUser && message.citations.isNotEmpty()) {
            AssistantCitationSection(
                citations = message.citations,
                onOpenUrl = uriHandler::openUri,
                onOpenUrlPreview = onOpenUrlPreview,
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
            onOpenActionSheet = onOpenMessageActions,
            onToggleMemory = onToggleMemory,
            onTranslate = onTranslate,
        )
    }
}

@Composable
private fun AssistantCitationSection(
    citations: List<com.example.myapplication.model.MessageCitation>,
    onOpenUrl: (String) -> Unit,
    onOpenUrlPreview: ((String, String) -> Unit)?,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "参考来源",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "${citations.size} 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                )
            }
            citations.forEach { citation ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (onOpenUrlPreview != null) {
                                    onOpenUrlPreview(
                                        citation.url,
                                        citation.title.ifBlank { citation.url },
                                    )
                                } else {
                                    onOpenUrl(citation.url)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = citation.title.ifBlank { citation.url },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildCitationMeta(citation),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.74f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = citation.url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.58f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun buildCitationMeta(
    citation: com.example.myapplication.model.MessageCitation,
): String {
    val host = runCatching {
        java.net.URI(citation.url).host.orEmpty().removePrefix("www.")
    }.getOrDefault("")
    return buildString {
        if (citation.sourceLabel.isNotBlank()) {
            append(citation.sourceLabel)
        }
        if (host.isNotBlank()) {
            if (isNotBlank()) {
                append(" · ")
            }
            append(host)
        }
        if (isBlank()) {
            append("链接")
        }
    }
}

