package com.example.myapplication.ui.component.roleplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.roleplay.RoleplayLongformMarkupParser
import com.example.myapplication.roleplay.RoleplayLongformParagraph
import com.example.myapplication.roleplay.RoleplayLongformSpanType

@Composable
fun RoleplayLongformCard(
    speakerName: String,
    content: String,
    modifier: Modifier = Modifier,
    richTextSource: String = content,
    backdropState: ImmersiveBackdropState? = null,
    useReadingGlassStyle: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
    titleColor: Color = MaterialTheme.colorScheme.primary,
    bodyColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    thoughtColor: Color = bodyColor.copy(alpha = 0.72f),
    lineHeightScale: Float = 1.0f,
) {
    val paragraphs = remember(content, richTextSource) {
        RoleplayLongformMarkupParser.parseParagraphs(richTextSource)
            .ifEmpty { RoleplayLongformMarkupParser.parseParagraphs(content) }
    }
    val shape = RoundedCornerShape(22.dp)
    val border = BorderStroke(0.dp, Color.Transparent)

    val innerContent = @Composable { contentModifier: Modifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = speakerName,
                style = MaterialTheme.typography.labelLarge,
                color = titleColor,
                fontWeight = FontWeight.Bold,
            )
            paragraphs.forEach { paragraph ->
                LongformParagraphText(
                    paragraph = paragraph,
                    bodyColor = bodyColor,
                    dialogueColor = accentColor,
                    thoughtColor = thoughtColor,
                    lineHeightScale = lineHeightScale,
                )
            }
        }
    }

    if (backdropState != null && useReadingGlassStyle) {
        ImmersiveReadingGlassSurface(
            backdropState = backdropState,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            variant = ImmersiveReadingGlassVariant.CARD,
        ) {
            innerContent(Modifier.padding(horizontal = 20.dp, vertical = 18.dp))
        }
    } else if (backdropState != null && backdropState.hasImage) {
        innerContent(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    } else if (backdropState != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, backdropState.palette.readingBorder.copy(alpha = 0.18f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            innerContent(Modifier.padding(horizontal = 20.dp, vertical = 18.dp))
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            innerContent(Modifier.padding(horizontal = 20.dp, vertical = 18.dp))
        }
    }
}

internal fun String.toLongformParagraphs(): List<String> {
    return RoleplayLongformMarkupParser.splitDisplayParagraphs(this)
}

internal val RoleplayQuotedDialogueHighlightColor = Color(0xFF90CAF9)

@Composable
private fun LongformParagraphText(
    paragraph: RoleplayLongformParagraph,
    bodyColor: Color,
    dialogueColor: Color,
    thoughtColor: Color,
    lineHeightScale: Float = 1.0f,
) {
    val rendered = remember(paragraph, bodyColor, dialogueColor, thoughtColor) {
        buildLongformAnnotatedString(
            paragraph = paragraph,
            narrationColor = bodyColor,
            dialogueColor = dialogueColor,
            thoughtColor = thoughtColor,
        )
    }
    val baseLineHeight = 32.sp
    Text(
        text = rendered,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 17.sp,
            lineHeight = baseLineHeight * lineHeightScale,
            letterSpacing = 0.sp,
        ),
        color = bodyColor,
    )
}

private fun buildLongformAnnotatedString(
    paragraph: RoleplayLongformParagraph,
    narrationColor: Color,
    dialogueColor: Color,
    thoughtColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        paragraph.spans.forEach { span ->
            when (span.type) {
                RoleplayLongformSpanType.NARRATION -> {
                    append(
                        buildNarrationWithThoughtFallback(
                            text = span.text,
                            narrationColor = narrationColor,
                            dialogueColor = dialogueColor,
                            thoughtColor = thoughtColor,
                        ),
                    )
                }

                RoleplayLongformSpanType.CHARACTER_SPEECH -> {
                    withStyle(
                        SpanStyle(
                            color = dialogueColor,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append(span.text)
                    }
                }

                RoleplayLongformSpanType.THOUGHT -> {
                    val trimmedText = span.text.trim()
                    val alreadyWrapped = trimmedText.startsWith("（") && trimmedText.endsWith("）")
                    withStyle(
                        SpanStyle(
                            color = thoughtColor,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                            letterSpacing = 0.sp,
                        ),
                    ) {
                        if (!alreadyWrapped) append("（")
                        append(
                            buildQuotedDialogueAnnotatedString(
                                text = span.text,
                                narrationColor = thoughtColor,
                                dialogueColor = dialogueColor,
                            ),
                        )
                        if (!alreadyWrapped) append("）")
                    }
                }
            }
        }
    }
}

internal fun buildQuotedDialogueAnnotatedString(
    text: String,
    narrationColor: Color,
    dialogueColor: Color,
): AnnotatedString {
    return buildDialogueAnnotatedString(
        text = text,
        narrationColor = narrationColor,
        dialogueColor = dialogueColor,
        highlightWholeTextWhenNoQuotes = false,
    )
}

internal fun buildCharacterDialogueAnnotatedString(
    text: String,
    narrationColor: Color,
    dialogueColor: Color,
): AnnotatedString {
    return buildDialogueAnnotatedString(
        text = text,
        narrationColor = narrationColor,
        dialogueColor = dialogueColor,
        highlightWholeTextWhenNoQuotes = true,
    )
}

private fun buildDialogueAnnotatedString(
    text: String,
    narrationColor: Color,
    dialogueColor: Color,
    highlightWholeTextWhenNoQuotes: Boolean,
): AnnotatedString {
    return buildAnnotatedString {
        if (text.isBlank()) {
            return@buildAnnotatedString
        }
        val matches = DialogueQuotedTextRegex.findAll(text).toList()
        if (matches.isEmpty() && highlightWholeTextWhenNoQuotes) {
            withStyle(
                SpanStyle(
                    color = dialogueColor,
                    fontWeight = FontWeight.SemiBold,
                ),
            ) {
                append(text)
            }
            return@buildAnnotatedString
        }

        var cursor = 0
        matches.forEach { match ->
            if (match.range.first > cursor) {
                withStyle(SpanStyle(color = narrationColor)) {
                    append(text.substring(cursor, match.range.first))
                }
            }
            withStyle(
                SpanStyle(
                    color = dialogueColor,
                    fontWeight = FontWeight.Bold,
                ),
            ) {
                append(match.value)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            withStyle(SpanStyle(color = narrationColor)) {
                append(text.substring(cursor))
            }
        }
    }
}

private val DialogueQuotedTextRegex = Regex(
    pattern = "“[^”\\n]*”|\"[^\"\\n]*\"",
)

// 模型有时遗漏 <thought> 标签，但思想内容仍用全角括号（……）包裹。
// 在叙述段中探测这种模式，自动应用思想样式。
private val NarrationInlineThoughtRegex = Regex("（[^）\\n]*）")

private fun buildNarrationWithThoughtFallback(
    text: String,
    narrationColor: Color,
    dialogueColor: Color,
    thoughtColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        if (text.isBlank()) {
            return@buildAnnotatedString
        }
        val thoughtMatches = NarrationInlineThoughtRegex.findAll(text).toList()
        if (thoughtMatches.isEmpty()) {
            append(
                buildQuotedDialogueAnnotatedString(
                    text = text,
                    narrationColor = narrationColor,
                    dialogueColor = dialogueColor,
                ),
            )
            return@buildAnnotatedString
        }
        var cursor = 0
        thoughtMatches.forEach { match ->
            if (match.range.first > cursor) {
                append(
                    buildQuotedDialogueAnnotatedString(
                        text = text.substring(cursor, match.range.first),
                        narrationColor = narrationColor,
                        dialogueColor = dialogueColor,
                    ),
                )
            }
            withStyle(
                SpanStyle(
                    color = thoughtColor,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp,
                ),
            ) {
                append(match.value)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(
                buildQuotedDialogueAnnotatedString(
                    text = text.substring(cursor),
                    narrationColor = narrationColor,
                    dialogueColor = dialogueColor,
                ),
            )
        }
    }
}
