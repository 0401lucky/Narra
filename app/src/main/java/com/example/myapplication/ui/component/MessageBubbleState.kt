package com.example.myapplication.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.repository.ai.ChatStatusBlockParser
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatReasoningStep
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.toPlainText
import com.example.myapplication.model.reasoningStepsToContent
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import java.util.LinkedHashMap

internal enum class ReasoningCardDisplayState {
    Collapsed,
    Preview,
    Expanded,
}

internal fun resolveReasoningCardDisplayState(
    hasReasoningContent: Boolean,
    userToggledReasoning: Boolean?,
    isReasoningPhase: Boolean,
    reasoningExpandedByDefault: Boolean,
    showThinkingContent: Boolean,
    autoCollapseThinking: Boolean,
): ReasoningCardDisplayState {
    return when {
        !hasReasoningContent -> ReasoningCardDisplayState.Collapsed
        userToggledReasoning == true -> ReasoningCardDisplayState.Expanded
        userToggledReasoning == false && isReasoningPhase && showThinkingContent -> ReasoningCardDisplayState.Preview
        userToggledReasoning == false -> ReasoningCardDisplayState.Collapsed
        isReasoningPhase && showThinkingContent -> ReasoningCardDisplayState.Preview
        isReasoningPhase -> ReasoningCardDisplayState.Collapsed
        autoCollapseThinking -> ReasoningCardDisplayState.Collapsed
        reasoningExpandedByDefault -> ReasoningCardDisplayState.Expanded
        else -> ReasoningCardDisplayState.Collapsed
    }
}

@Immutable
internal data class MessageBubbleRenderState(
    val isUser: Boolean,
    val isError: Boolean,
    val isLoading: Boolean,
    val hasStructuredParts: Boolean,
    val displayParts: List<ChatMessagePart>,
    val displayAttachments: List<MessageAttachment>,
    val displayContent: String,
    val renderedDisplayContent: String,
    val resolvedReasoningContent: String,
    val reasoningSteps: List<ChatReasoningStep>,
    val reasoningDisplayState: ReasoningCardDisplayState,
    val reasoningExpanded: Boolean,
    val reasoningPreviewVisible: Boolean,
    val assistantVisualContent: AssistantVisualContent,
    val shouldShowContentBubble: Boolean,
    val shouldShowUserActions: Boolean,
    val shouldShowAssistantActions: Boolean,
    val shouldShowErrorActions: Boolean,
    val shouldUseSplitUserLayout: Boolean,
    val userBubbleColor: Color,
    val backgroundColor: Color,
    val contentColor: Color,
    val border: androidx.compose.foundation.BorderStroke,
    val bubbleShape: androidx.compose.ui.graphics.Shape,
    val assistantMarkdownTypography: MarkdownTypography,
    val assistantMarkdownPadding: MarkdownPadding,
    val plainMessageTextStyle: TextStyle,
    val reasoningMarkdownTypography: MarkdownTypography,
    val reasoningMarkdownPadding: MarkdownPadding,
    val copyPayload: String,
    val onToggleReasoning: () -> Unit,
)

@Composable
internal fun rememberMessageBubbleRenderState(
    message: ChatMessage,
    streamingContent: String?,
    streamingReasoningContent: String?,
    streamingReasoningSteps: List<ChatReasoningStep>?,
    streamingParts: List<ChatMessagePart>?,
    isRemembered: Boolean,
    messageTextScale: Float,
    reasoningExpandedByDefault: Boolean,
    showThinkingContent: Boolean,
    autoCollapseThinking: Boolean,
    deferRichRendering: Boolean = false,
): MessageBubbleRenderState {
    val isUser = message.role == MessageRole.USER
    val isError = !isUser && message.status == MessageStatus.ERROR
    val isLoading = !isUser && message.status == MessageStatus.LOADING
    val isStreaming = streamingContent != null
    val useFastAssistantText = !isUser && (isStreaming || deferRichRendering)
    val resolvedContent = streamingContent ?: message.content
    val rawMessageParts = remember(message.parts, streamingParts) {
        normalizeChatMessageParts(streamingParts ?: message.parts)
    }
    val statusAwareMessageParts = remember(rawMessageParts, resolvedContent, isUser, useFastAssistantText) {
        when {
            isUser -> rawMessageParts
            useFastAssistantText -> emptyList()
            rawMessageParts.isNotEmpty() -> {
                ChatStatusBlockParser.extractFromParts(
                    parts = rawMessageParts,
                    hideStatusBlocksInBubble = true,
                ).takeIf { parts -> parts.any { it.type == ChatMessagePartType.STATUS } }
                    ?: rawMessageParts
            }

            resolvedContent.isNotBlank() -> {
                ChatStatusBlockParser.extract(
                    text = resolvedContent,
                    hideStatusBlocksInBubble = true,
                ).takeIf { parts -> parts.any { it.type == ChatMessagePartType.STATUS } }
                    ?: emptyList()
            }

            else -> emptyList()
        }
    }
    val displayParts = remember(statusAwareMessageParts, isUser, useFastAssistantText) {
        if (statusAwareMessageParts.isNotEmpty()) {
            statusAwareMessageParts.map { part ->
                if (part.type == ChatMessagePartType.TEXT && !isUser && !useFastAssistantText) {
                    part.copy(text = cachedNormalizeAssistantMarkdown(part.text))
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
        if (hasStructuredParts) emptyList() else message.attachments
    }

    val resolvedReasoningSteps = remember(message.reasoningSteps, streamingReasoningSteps, isLoading, useFastAssistantText) {
        (streamingReasoningSteps ?: message.reasoningSteps).map { step ->
            if (isLoading || useFastAssistantText) {
                step
            } else {
                step.copy(text = cachedNormalizeAssistantMarkdown(step.text))
            }
        }
    }
    val resolvedReasoningContent = remember(
        resolvedReasoningSteps,
        streamingReasoningContent,
        message.reasoningContent,
    ) {
        reasoningStepsToContent(resolvedReasoningSteps)
            .ifBlank { streamingReasoningContent ?: message.reasoningContent }
    }

    val isReasoningPhase = isLoading && resolvedContent.isBlank() && resolvedReasoningSteps.isNotEmpty()
    val hasReasoningContent = resolvedReasoningSteps.isNotEmpty() || resolvedReasoningContent.isNotBlank()
    var userToggledReasoning by rememberSaveable(message.id) { mutableStateOf<Boolean?>(null) }
    var lastReasoningPhase by rememberSaveable(message.id) { mutableStateOf(isReasoningPhase) }
    LaunchedEffect(isReasoningPhase, hasReasoningContent, autoCollapseThinking) {
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
        resolveReasoningCardDisplayState(
            hasReasoningContent = hasReasoningContent,
            userToggledReasoning = userToggledReasoning,
            isReasoningPhase = isReasoningPhase,
            reasoningExpandedByDefault = reasoningExpandedByDefault,
            showThinkingContent = showThinkingContent,
            autoCollapseThinking = autoCollapseThinking,
        )
    }
    val reasoningExpanded = reasoningDisplayState == ReasoningCardDisplayState.Expanded
    val reasoningPreviewVisible = reasoningDisplayState == ReasoningCardDisplayState.Preview
    val userBubbleColor = lerp(
        androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
        androidx.compose.material3.MaterialTheme.colorScheme.surface,
        0.22f,
    )
    val backgroundColor = when {
        isUser -> userBubbleColor
        isError -> androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
        isLoading -> androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        else -> androidx.compose.material3.MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isUser -> androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
        isError -> androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
        else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    }
    val border = when {
        isUser -> androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
        isError -> androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.error.copy(alpha = 0.24f))
        isLoading -> androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        else -> androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    }
    val bubbleShape = if (isUser) {
        androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 6.dp)
    } else {
        androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 24.dp)
    }
    val displayContent = remember(messageDisplayText, isLoading, resolvedReasoningContent) {
        messageDisplayText.ifBlank {
            if (isLoading && resolvedReasoningContent.isBlank()) "正在生成回复…" else ""
        }
    }
    val assistantVisualContent = remember(isUser, useFastAssistantText, hasStructuredParts, displayContent) {
        if (!isUser && !useFastAssistantText && !hasStructuredParts) {
            cachedExtractAssistantVisualContent(displayContent)
        } else {
            AssistantVisualContent(text = displayContent, imageSources = emptyList())
        }
    }
    val renderedDisplayContent = remember(isUser, useFastAssistantText, hasStructuredParts, assistantVisualContent) {
        if (!isUser && !useFastAssistantText && !hasStructuredParts) {
            cachedNormalizeAssistantMarkdown(assistantVisualContent.text)
        } else {
            assistantVisualContent.text
        }
    }
    val copyPayload = remember(message, displayContent, resolvedReasoningContent) {
        buildMessageCopyPayload(
            message = message,
            displayContent = displayContent,
            reasoningContent = resolvedReasoningContent,
        )
    }
    val shouldShowContentBubble = displayAttachments.isNotEmpty() ||
        hasStructuredParts ||
        assistantVisualContent.imageSources.isNotEmpty() ||
        displayContent.isNotBlank() ||
        isUser ||
        isError
    val shouldShowUserActions = isUser && copyPayload.isNotBlank()
    val shouldShowAssistantActions = !isUser && !isLoading && !isError &&
        (displayContent.isNotBlank() || hasStructuredParts || assistantVisualContent.imageSources.isNotEmpty() || resolvedReasoningSteps.isNotEmpty())
    val shouldShowErrorActions = isError
    val shouldUseSplitUserLayout = isUser && (
        displayAttachments.any { it.type == AttachmentType.IMAGE } ||
            displayParts.any { it.type != ChatMessagePartType.TEXT }
        )
    val assistantParagraphStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge.scaledBy(
        messageTextScale,
    ).copy(
        lineHeight = 29.sp * messageTextScale,
        letterSpacing = 0.12.sp,
    )
    val assistantMarkdownTypography = chatMarkdownTypography(
        paragraphStyle = assistantParagraphStyle,
        compact = false,
    )
    val plainMessageTextStyle = assistantParagraphStyle
    val assistantMarkdownPadding = chatMarkdownPadding(compact = false)
    val reasoningParagraphStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.scaledBy(
        messageTextScale,
    ).copy(
        lineHeight = 24.sp * messageTextScale,
        letterSpacing = 0.08.sp,
    )
    val reasoningMarkdownTypography = chatMarkdownTypography(
        paragraphStyle = reasoningParagraphStyle,
        compact = true,
    )
    val reasoningMarkdownPadding = chatMarkdownPadding(compact = true)

    return MessageBubbleRenderState(
        isUser = isUser,
        isError = isError,
        isLoading = isLoading,
        hasStructuredParts = hasStructuredParts,
        displayParts = displayParts,
        displayAttachments = displayAttachments,
        displayContent = displayContent,
        renderedDisplayContent = renderedDisplayContent,
        resolvedReasoningContent = resolvedReasoningContent,
        reasoningSteps = resolvedReasoningSteps,
        reasoningDisplayState = reasoningDisplayState,
        reasoningExpanded = reasoningExpanded,
        reasoningPreviewVisible = reasoningPreviewVisible,
        assistantVisualContent = assistantVisualContent,
        shouldShowContentBubble = shouldShowContentBubble,
        shouldShowUserActions = shouldShowUserActions,
        shouldShowAssistantActions = shouldShowAssistantActions,
        shouldShowErrorActions = shouldShowErrorActions,
        shouldUseSplitUserLayout = shouldUseSplitUserLayout,
        userBubbleColor = userBubbleColor,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        border = border,
        bubbleShape = bubbleShape,
        assistantMarkdownTypography = assistantMarkdownTypography,
        assistantMarkdownPadding = assistantMarkdownPadding,
        plainMessageTextStyle = plainMessageTextStyle,
        reasoningMarkdownTypography = reasoningMarkdownTypography,
        reasoningMarkdownPadding = reasoningMarkdownPadding,
        copyPayload = copyPayload,
        onToggleReasoning = {
            userToggledReasoning = reasoningDisplayState != ReasoningCardDisplayState.Expanded
        },
    )
}

private fun cachedNormalizeAssistantMarkdown(content: String): String {
    if (content.isBlank()) {
        return content
    }
    return MessageBubbleTextRenderCache.normalizedMarkdown(content) {
        normalizeAssistantMarkdownForDisplay(content)
    }
}

private fun cachedExtractAssistantVisualContent(content: String): AssistantVisualContent {
    if (content.isBlank()) {
        return AssistantVisualContent(text = content, imageSources = emptyList())
    }
    return MessageBubbleTextRenderCache.visualContent(content) {
        extractAssistantVisualContent(content)
    }
}

private object MessageBubbleTextRenderCache {
    private const val MaxEntries = 200

    private val normalizedMarkdownCache = object : LinkedHashMap<String, String>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MaxEntries
        }
    }
    private val visualContentCache = object : LinkedHashMap<String, AssistantVisualContent>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AssistantVisualContent>?): Boolean {
            return size > MaxEntries
        }
    }

    fun normalizedMarkdown(
        key: String,
        producer: () -> String,
    ): String = synchronized(normalizedMarkdownCache) {
        normalizedMarkdownCache[key] ?: producer().also { normalizedMarkdownCache[key] = it }
    }

    fun visualContent(
        key: String,
        producer: () -> AssistantVisualContent,
    ): AssistantVisualContent = synchronized(visualContentCache) {
        visualContentCache[key] ?: producer().also { visualContentCache[key] = it }
    }
}
