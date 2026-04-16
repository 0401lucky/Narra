package com.example.myapplication.ui.component

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatReasoningStep
import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.toActionCopyText
import com.example.myapplication.model.toMessageAttachmentOrNull
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

private const val MessageBubbleCodeBlockCollapseLines = 10
private val MessageBubbleCodeBlockShape = RoundedCornerShape(22.dp)
private val MessageBubbleOrderedMarkdownListHintRegex = Regex("""\d+\.\s+.+""")
private val CitationMarkdownRegex = Regex("""\[citation,([^\]]+)]\(([^)]+)\)""")
private val MessageBubbleMarkdownInlineHintRegex = Regex(
    """(```|`[^`\n]+`|\[[^\]]+]\([^)]+\)|!\[[^\]]*]\([^)]+\)|\*\*[^*\n]+\*\*|__[^_\n]+__|~~[^~\n]+~~)""",
)
private val MessageBubbleScrollingMarkdownHintRegex = Regex(
    """(```|`[^`\n]+`|\[[^\]]+]\([^)]+\)|!\[[^\]]*]\([^)]+\))""",
)

@Composable
internal fun MessagePartsRenderer(
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
    performanceMode: ChatMessagePerformanceMode,
    onConfirmTransferReceipt: ((String) -> Unit)?,
    citations: List<MessageCitation> = emptyList(),
    onOpenCitation: ((MessageCitation) -> Unit)? = null,
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
                    performanceMode = performanceMode,
                    citations = citations,
                    onOpenCitation = onOpenCitation,
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

            ChatMessagePartType.ACTION -> {
                RenderMessageText(
                    text = part.toActionCopyText(),
                    useMarkdown = false,
                    contentColor = contentColor,
                    assistantMarkdownTypography = markdownTypography,
                    assistantMarkdownPadding = markdownPadding,
                    plainTextStyle = plainTextStyle,
                    codeBlockAutoWrap = codeBlockAutoWrap,
                    codeBlockAutoCollapse = codeBlockAutoCollapse,
                    performanceMode = performanceMode,
                )
            }

            ChatMessagePartType.SPECIAL -> {
                Box(modifier = Modifier.widthIn(max = 280.dp)) {
                    SpecialPlayCard(
                        part = part,
                        isUserMessage = isUser,
                        onConfirmTransferReceipt = onConfirmTransferReceipt,
                        autoPreviewImages = autoPreviewImages,
                        reduceMotion = performanceMode != ChatMessagePerformanceMode.FULL,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RenderMessageText(
    text: String,
    useMarkdown: Boolean,
    contentColor: androidx.compose.ui.graphics.Color,
    assistantMarkdownTypography: MarkdownTypography,
    assistantMarkdownPadding: MarkdownPadding,
    plainTextStyle: TextStyle,
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
    performanceMode: ChatMessagePerformanceMode,
    citations: List<MessageCitation> = emptyList(),
    onOpenCitation: ((MessageCitation) -> Unit)? = null,
) {
    if (text.isBlank()) {
        return
    }

    val renderedText = remember(text) {
        normalizeCitationMarkdownForDisplay(text)
    }
    val shouldUseMarkdown = remember(renderedText, useMarkdown) {
        useMarkdown && shouldRenderWithMarkdown(renderedText)
    }
    val markdownComponents = rememberChatMarkdownComponents(
        markdownPadding = assistantMarkdownPadding,
        codeBlockAutoWrap = codeBlockAutoWrap,
        codeBlockAutoCollapse = codeBlockAutoCollapse,
        enableContentSizeAnimation = performanceMode == ChatMessagePerformanceMode.FULL,
    )
    val defaultUriHandler = LocalUriHandler.current
    val citationMap = remember(citations) {
        citations.associateBy { citation ->
            citation.id.trim().ifBlank { citation.url.trim() }
        }
    }
    val citationAwareUriHandler = remember(defaultUriHandler, citationMap, onOpenCitation) {
        object : androidx.compose.ui.platform.UriHandler {
            override fun openUri(uri: String) {
                if (uri.startsWith("citation:")) {
                    val citationId = uri.removePrefix("citation:").trim()
                    val citation = citationMap[citationId]
                        ?: citationMap.values.firstOrNull { it.url.trim() == citationId }
                    if (citation != null && onOpenCitation != null) {
                        onOpenCitation(citation)
                    }
                    return
                }
                defaultUriHandler.openUri(uri)
            }
        }
    }

    if (shouldUseMarkdown) {
        CompositionLocalProvider(LocalUriHandler provides citationAwareUriHandler) {
            Markdown(
                content = renderedText,
                colors = markdownColor(text = contentColor),
                typography = assistantMarkdownTypography,
                modifier = Modifier.fillMaxWidth(),
                padding = assistantMarkdownPadding,
                components = markdownComponents,
            )
        }
    } else {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = contentColor,
            style = plainTextStyle,
        )
    }
}

internal fun normalizeCitationMarkdownForDisplay(
    text: String,
): String {
    return text.replace(CitationMarkdownRegex) { match ->
        val domain = match.groupValues.getOrNull(1).orEmpty().trim().ifBlank { "引用" }
        val citationId = match.groupValues.getOrNull(2).orEmpty().trim()
        "[〔$domain〕](citation:$citationId)"
    }
}

@Composable
private fun rememberChatMarkdownComponents(
    markdownPadding: MarkdownPadding,
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
    enableContentSizeAnimation: Boolean,
): MarkdownComponents {
    val codeBlockPadding = markdownPadding.codeBlock
    return remember(codeBlockPadding, codeBlockAutoWrap, codeBlockAutoCollapse, enableContentSizeAnimation) {
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
                        enableContentSizeAnimation = enableContentSizeAnimation,
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
                        enableContentSizeAnimation = enableContentSizeAnimation,
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
    enableContentSizeAnimation: Boolean,
) {
    val normalizedCode = remember(code) {
        code.trimEnd('\n', '\r')
    }
    val normalizedLanguage = remember(language) {
        language.orEmpty().trim()
    }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val clipboardScope = rememberCoroutineScope()
    val codeLines = remember(normalizedCode) {
        normalizedCode.lines()
    }
    val shouldShowCollapseAction = codeBlockAutoCollapse && codeLines.size > MessageBubbleCodeBlockCollapseLines
    var isExpanded by remember(normalizedCode, codeBlockAutoCollapse) {
        mutableStateOf(!codeBlockAutoCollapse)
    }
    val codeLineCount = remember(codeLines) { codeLines.size }
    val displayCode = remember(codeLines, isExpanded, shouldShowCollapseAction, normalizedCode) {
        if (shouldShowCollapseAction && !isExpanded) {
            codeLines.take(MessageBubbleCodeBlockCollapseLines).joinToString(separator = "\n")
        } else {
            normalizedCode
        }
    }
    val horizontalScrollState = rememberScrollState()
    val canScrollHorizontally by remember(horizontalScrollState) {
        derivedStateOf { !codeBlockAutoWrap && horizontalScrollState.maxValue > 0 }
    }
    val shellColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
    val bodyColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    val headerTint = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.58f)
    val secondaryLabel = buildString {
        append("${codeLineCount} 行")
        if (canScrollHorizontally) {
            append(" · 可横向滚动")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MessageBubbleCodeBlockShape,
            color = shellColor,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = headerTint,
                        ) {
                            Text(
                                text = normalizedLanguage.ifBlank { "代码" },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Text(
                            text = secondaryLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                        )
                    }

                    NarraIconButton(
                        onClick = {
                            clipboardScope.copyPlainTextToClipboard(clipboard, "code-block", normalizedCode)
                            Toast.makeText(context, "已复制代码块", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(30.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "复制代码块",
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bodyColor)
                        .let { baseModifier ->
                            if (enableContentSizeAnimation) {
                                baseModifier.animateContentSize()
                            } else {
                                baseModifier
                            }
                        },
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = if (isExpanded) "收起代码" else "展开全部代码",
                            modifier = Modifier.clickable { isExpanded = !isExpanded },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReasoningTimelineCard(
    reasoningSteps: List<ChatReasoningStep>,
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
    performanceMode: ChatMessagePerformanceMode,
    modifier: Modifier = Modifier,
) {
    if (reasoningSteps.isEmpty()) {
        return
    }

    val visibleSteps = remember(reasoningSteps, expanded) {
        visibleReasoningTimelineSteps(reasoningSteps, expanded)
    }
    val contentVisible = expanded || previewVisible
    val cardModifier = if (isLoading || performanceMode != ChatMessagePerformanceMode.FULL) {
        modifier
    } else {
        modifier.animateContentSize(
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            ),
        )
    }

    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (reasoningSteps.size > 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onToggleExpanded)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (expanded) {
                            "收起思考过程"
                        } else {
                            "展开 ${reasoningSteps.size - 2} 条思考步骤"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            val lineColor = MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier.drawBehind {
                    val x = 11.dp.toPx()
                    val top = 18.dp.toPx()
                    val bottom = size.height - 18.dp.toPx()
                    if (bottom > top) {
                        drawLine(
                            color = lineColor,
                            start = Offset(x, top),
                            end = Offset(x, bottom),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                },
            ) {
                Column {
                    visibleSteps.forEachIndexed { index, step ->
                        ReasoningTimelineStep(
                            step = step,
                            isFirst = index == 0,
                            isLast = index == visibleSteps.lastIndex,
                            contentVisible = contentVisible,
                            previewVisible = previewVisible && !expanded,
                            markdownTypography = markdownTypography,
                            markdownPadding = markdownPadding,
                            previewTextStyle = previewTextStyle,
                            autoPreviewImages = autoPreviewImages,
                            codeBlockAutoWrap = codeBlockAutoWrap,
                            codeBlockAutoCollapse = codeBlockAutoCollapse,
                            performanceMode = performanceMode,
                            onToggleExpanded = onToggleExpanded,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningTimelineStep(
    step: ChatReasoningStep,
    isFirst: Boolean,
    isLast: Boolean,
    contentVisible: Boolean,
    previewVisible: Boolean,
    markdownTypography: MarkdownTypography,
    markdownPadding: MarkdownPadding,
    previewTextStyle: TextStyle,
    autoPreviewImages: Boolean,
    codeBlockAutoWrap: Boolean,
    codeBlockAutoCollapse: Boolean,
    performanceMode: ChatMessagePerformanceMode,
    onToggleExpanded: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var currentTimeMillis by remember(step.id, step.finishedAt) {
        mutableStateOf(step.finishedAt ?: System.currentTimeMillis())
    }
    LaunchedEffect(step.id, step.finishedAt) {
        if (step.finishedAt != null) {
            currentTimeMillis = step.finishedAt
            return@LaunchedEffect
        }
        while (isActive) {
            currentTimeMillis = System.currentTimeMillis()
            delay(100)
        }
    }
    LaunchedEffect(previewVisible, step.id, step.text, step.finishedAt) {
        if (previewVisible && step.finishedAt == null) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    val durationSeconds = remember(step.createdAt, step.finishedAt, currentTimeMillis) {
        val endAt = step.finishedAt ?: currentTimeMillis
        val elapsed = ((endAt - step.createdAt).coerceAtLeast(0L)).toDouble() / 1000.0
        if (elapsed <= 0.0) null else elapsed
    }
    val thinkingTitle = remember(step.text) {
        extractReasoningStepTitle(step.text)
    }
    val headerLabel = remember(thinkingTitle, durationSeconds, step.finishedAt) {
        when {
            !thinkingTitle.isNullOrBlank() -> thinkingTitle
            durationSeconds != null && step.finishedAt == null -> {
                "思考了 ${String.format(Locale.getDefault(), "%.1f", durationSeconds)} 秒"
            }
            else -> "深度思考"
        }
    }
    val durationLabel = remember(thinkingTitle, durationSeconds, step.finishedAt) {
        if (thinkingTitle.isNullOrBlank() || durationSeconds == null) {
            null
        } else {
            "${String.format(Locale.getDefault(), "%.1f", durationSeconds)}s"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleExpanded)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.width(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            color = if (isFirst) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .width(1.dp)
                        .background(
                            color = if (isLast) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
            }

            Text(
                text = headerLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!durationLabel.isNullOrBlank()) {
                Text(
                    text = durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = if (contentVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (contentVisible) {
            val contentModifier = if (previewVisible) {
                Modifier
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithCache {
                        val fadeHeight = 28.dp.toPx()
                        val brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to androidx.compose.ui.graphics.Color.Transparent,
                                (fadeHeight / size.height.coerceAtLeast(1f)) to androidx.compose.ui.graphics.Color.Black,
                                (1f - fadeHeight / size.height.coerceAtLeast(1f)) to androidx.compose.ui.graphics.Color.Black,
                                1f to androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(
                                brush = brush,
                                size = Size(size.width, size.height),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    }
                    .heightIn(max = 100.dp)
                    .verticalScroll(scrollState)
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 4.dp, bottom = 8.dp)
                    .then(contentModifier),
            ) {
                RenderMessageText(
                    text = step.text,
                    useMarkdown = step.finishedAt != null,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    assistantMarkdownTypography = markdownTypography,
                    assistantMarkdownPadding = markdownPadding,
                    plainTextStyle = previewTextStyle,
                    codeBlockAutoWrap = codeBlockAutoWrap,
                    codeBlockAutoCollapse = codeBlockAutoCollapse,
                    performanceMode = performanceMode,
                )
            }
        }
    }
}

internal fun extractReasoningStepTitle(text: String): String? {
    val lines = text.lines()
    for (index in lines.indices.reversed()) {
        val match = Regex("^\\*\\*(.+?)\\*\\*$").find(lines[index].trim()) ?: continue
        val title = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (title.isNotBlank()) {
            return title
        }
    }
    return null
}

internal fun visibleReasoningTimelineSteps(
    reasoningSteps: List<ChatReasoningStep>,
    expanded: Boolean,
    collapsedVisibleCount: Int = 2,
): List<ChatReasoningStep> {
    if (expanded || reasoningSteps.size <= collapsedVisibleCount) {
        return reasoningSteps
    }
    return reasoningSteps.takeLast(collapsedVisibleCount)
}

@Composable
internal fun GlassMessageContainer(
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
                elevation = 10.dp,
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

internal fun buildReasoningPreview(reasoningContent: String): String {
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

    if (containsStructuredMarkdown(normalized)) {
        return true
    }

    return MessageBubbleMarkdownInlineHintRegex.containsMatchIn(normalized)
}

internal fun shouldRenderWithMarkdownDuringScrolling(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.isBlank()) {
        return false
    }
    if ("```" in normalized) {
        return true
    }
    if (containsStructuredMarkdown(normalized)) {
        return true
    }
    return MessageBubbleScrollingMarkdownHintRegex.containsMatchIn(normalized)
}

@Composable
private fun glassPalette(
    tint: androidx.compose.ui.graphics.Color,
): MessageBubbleGlassPalette {
    val surface = MaterialTheme.colorScheme.surface
    val isDark = surface.luminance() < 0.5f

    return remember(tint, surface, isDark) {
        MessageBubbleGlassPalette(
            containerBrush = Brush.verticalGradient(
                colors = listOf(
                    if (isDark) {
                        surface.copy(alpha = 0.94f)
                    } else {
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.96f)
                    },
                    tint.copy(alpha = if (isDark) 0.10f else 0.08f),
                    surface.copy(alpha = if (isDark) 0.86f else 0.92f),
                ),
            ),
            sheenBrush = Brush.linearGradient(
                colors = listOf(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = if (isDark) 0.08f else 0.18f),
                    tint.copy(alpha = if (isDark) 0.03f else 0.05f),
                    androidx.compose.ui.graphics.Color.Transparent,
                ),
                start = Offset.Zero,
                end = Offset(560f, 280f),
            ),
            borderBrush = Brush.verticalGradient(
                colors = listOf(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = if (isDark) 0.14f else 0.30f),
                    tint.copy(alpha = if (isDark) 0.10f else 0.08f),
                    androidx.compose.ui.graphics.Color.White.copy(alpha = if (isDark) 0.05f else 0.10f),
                ),
            ),
            shadowColor = tint.copy(alpha = if (isDark) 0.10f else 0.06f),
            flatContainerColor = if (isDark) {
                surface.copy(alpha = 0.96f)
            } else {
                androidx.compose.ui.graphics.Color.White
            },
            flatBorderColor = tint.copy(alpha = if (isDark) 0.10f else 0.08f),
        )
    }
}

@Composable
internal fun chatMarkdownTypography(
    paragraphStyle: TextStyle,
    compact: Boolean,
): MarkdownTypography {
    val sectionTitleStyle = if (compact) {
        MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
        )
    } else {
        MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 32.sp,
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
        MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp)
    } else {
        MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp)
    }
    val emphasizedParagraphStyle = paragraphStyle.copy(fontWeight = FontWeight.SemiBold)
    val codeStyle = paragraphStyle.copy(fontFamily = FontFamily.Monospace)

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
        quote = paragraphStyle.copy(
            fontStyle = FontStyle.Italic,
            lineHeight = paragraphStyle.lineHeight * 1.04f,
        ),
        paragraph = paragraphStyle,
        ordered = paragraphStyle,
        bullet = paragraphStyle,
        list = paragraphStyle,
        table = supportingStyle,
    )
}

private data class MessageBubbleGlassPalette(
    val containerBrush: Brush,
    val sheenBrush: Brush,
    val borderBrush: Brush,
    val shadowColor: androidx.compose.ui.graphics.Color,
    val flatContainerColor: androidx.compose.ui.graphics.Color,
    val flatBorderColor: androidx.compose.ui.graphics.Color,
)
