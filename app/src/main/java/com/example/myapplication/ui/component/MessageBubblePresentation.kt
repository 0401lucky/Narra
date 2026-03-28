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
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.isTransferPart
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
private val MessageBubbleCodeBlockShape = RoundedCornerShape(18.dp)
private val MessageBubbleOrderedMarkdownListHintRegex = Regex("""\d+\.\s+.+""")
private val MessageBubbleMarkdownInlineHintRegex = Regex(
    """(```|`[^`\n]+`|\[[^\]]+]\([^)]+\)|!\[[^\]]*]\([^)]+\)|\*\*[^*\n]+\*\*|__[^_\n]+__|~~[^~\n]+~~)""",
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
internal fun RenderMessageText(
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

            NarraIconButton(
                onClick = {
                    clipboardScope.copyPlainTextToClipboard(clipboard, "code-block", normalizedCode)
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
            shape = MessageBubbleCodeBlockShape,
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

    val hasStructuredLine = normalized
        .lineSequence()
        .map(String::trimStart)
        .any { line ->
            line.startsWith("#") ||
                line.startsWith("> ") ||
                line.startsWith("- ") ||
                line.startsWith("* ") ||
                line.startsWith("+ ") ||
                MessageBubbleOrderedMarkdownListHintRegex.matches(line) ||
                line.startsWith("|")
        }
    if (hasStructuredLine) {
        return true
    }

    return MessageBubbleMarkdownInlineHintRegex.containsMatchIn(normalized)
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
internal fun chatMarkdownTypography(
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

private data class MessageBubbleGlassPalette(
    val containerBrush: Brush,
    val sheenBrush: Brush,
    val borderBrush: Brush,
    val shadowColor: androidx.compose.ui.graphics.Color,
    val flatContainerColor: androidx.compose.ui.graphics.Color,
    val flatBorderColor: androidx.compose.ui.graphics.Color,
)
