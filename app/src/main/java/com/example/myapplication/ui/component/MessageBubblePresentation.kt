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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ModelAbility
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

private const val MessageBubbleCodeBlockCollapseLines = 10
private val MessageBubbleCodeBlockShape = RoundedCornerShape(22.dp)
private val MessageBubbleOrderedMarkdownListHintRegex = Regex("""\d+\.\s+.+""")
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
        enableContentSizeAnimation = performanceMode == ChatMessagePerformanceMode.FULL,
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
            modifier = Modifier.fillMaxWidth(),
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
internal fun AssistantReasoningCard(
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
    performanceMode: ChatMessagePerformanceMode,
    reduceVisualEffects: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentAnimationModifier = if (isLoading || performanceMode != ChatMessagePerformanceMode.FULL) {
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
        shape = RoundedCornerShape(22.dp),
        tint = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        reduceVisualEffects = reduceVisualEffects,
    ) {
        Column(
            modifier = contentAnimationModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.76f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = if (isLoading) "思考中" else "思考",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
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
                        performanceMode = performanceMode,
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
