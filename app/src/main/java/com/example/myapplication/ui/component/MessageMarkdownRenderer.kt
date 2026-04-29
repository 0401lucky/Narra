package com.example.myapplication.ui.component

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.specialMetadataValue
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

private const val MessageBubbleCodeBlockCollapseLines = 10
private val MessageBubbleCodeBlockShape = RoundedCornerShape(22.dp)
internal val CitationMarkdownRegex = Regex("""\[citation,([^\]]+)]\(([^)]+)\)""")

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
    onOpenImagePreviewAtIndex: ((Int) -> Unit)? = null,
    citations: List<MessageCitation> = emptyList(),
    onOpenCitation: ((MessageCitation) -> Unit)? = null,
    fillTextWidth: Boolean = true,
) {
    var imageIndex = 0
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
                    fillWidth = fillTextWidth,
                )
            }

            ChatMessagePartType.IMAGE -> {
                val currentImageIndex = imageIndex
                imageIndex += 1
                GeneratedImageAttachment(
                    uri = part.uri,
                    fileName = part.fileName.ifBlank {
                        if (isUser) "uploaded-image-${index + 1}" else "assistant-image-${index + 1}"
                    },
                    autoPreviewImages = autoPreviewImages,
                    onOpenPreview = {
                        onOpenImagePreviewAtIndex?.invoke(currentImageIndex)
                    },
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
                    fillWidth = fillTextWidth,
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

            ChatMessagePartType.STATUS -> {
                StatusCardPart(
                    part = part,
                    contentColor = contentColor,
                )
            }
        }
    }
}

@Composable
internal fun StatusCardPart(
    part: ChatMessagePart,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val rawText = part.specialMetadataValue("raw").ifBlank { part.text }.trim()
    if (rawText.isBlank()) {
        return
    }
    val title = part.specialMetadataValue("title").ifBlank { "状态" }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val clipboardScope = rememberCoroutineScope()
    val parsedRows = remember(rawText) { parseStatusRows(rawText) }
    val preview = remember(rawText, parsedRows) {
        parsedRows.firstOrNull()?.let { "${it.first}：${it.second}" }
            ?: rawText.lineSequence().firstOrNull()?.trim().orEmpty()
    }
    var expanded by remember(rawText) { mutableStateOf(false) }
    val cardColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
    val borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.74f),
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    NarraIconButton(
                        onClick = {
                            clipboardScope.copyPlainTextToClipboard(clipboard, "status-card", rawText)
                            Toast.makeText(context, "已复制状态原文", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(30.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "复制状态",
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = if (expanded) "收起" else "展开",
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                if (parsedRows.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        parsedRows.forEach { (key, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = key,
                                    modifier = Modifier.widthIn(min = 64.dp, max = 112.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                                )
                                Text(
                                    text = value,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor,
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = rawText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                }
            }
        }
    }
}

private fun parseStatusRows(rawText: String): List<Pair<String, String>> {
    return rawText
        .lines()
        .mapNotNull { line ->
            val trimmed = line.trim().trim('-', '•', '*', ' ')
            val delimiterIndex = listOf(
                trimmed.indexOf('：'),
                trimmed.indexOf(':'),
                trimmed.indexOf('='),
            )
                .filter { it > 0 }
                .minOrNull()
                ?: return@mapNotNull null
            val key = trimmed.take(delimiterIndex).trim()
            val value = trimmed.drop(delimiterIndex + 1).trim()
            if (key.isBlank() || value.isBlank()) {
                null
            } else {
                key to value
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
    fillWidth: Boolean = true,
) {
    if (text.isBlank()) {
        return
    }

    val renderedText = remember(text) {
        normalizeCitationMarkdownForDisplay(text)
    }
    val safeHtmlBlocks = remember(renderedText) {
        parseSafeHtmlTextBlocks(renderedText)
    }
    if (safeHtmlBlocks != null) {
        SafeHtmlRichText(
            blocks = safeHtmlBlocks,
            contentColor = contentColor,
            plainTextStyle = plainTextStyle,
            fillWidth = fillWidth,
        )
        return
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
                modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
                padding = assistantMarkdownPadding,
                components = markdownComponents,
            )
        }
    } else {
        Text(
            text = text,
            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
            color = contentColor,
            style = plainTextStyle,
        )
    }
}

@Composable
private fun SafeHtmlRichText(
    blocks: List<SafeHtmlTextBlock>,
    contentColor: androidx.compose.ui.graphics.Color,
    plainTextStyle: TextStyle,
    fillWidth: Boolean,
) {
    Column(
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            if (block.divider) {
                HorizontalDivider(
                    color = contentColor.copy(alpha = 0.22f),
                    thickness = 1.dp,
                )
            } else {
                val fontSize = if (plainTextStyle.fontSize.isSpecified) {
                    plainTextStyle.fontSize * block.fontScale
                } else {
                    plainTextStyle.fontSize
                }
                Text(
                    text = block.text,
                    modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
                    color = block.color ?: contentColor,
                    style = plainTextStyle.copy(
                        fontSize = fontSize,
                        textAlign = block.textAlign ?: plainTextStyle.textAlign,
                    ),
                )
            }
        }
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
