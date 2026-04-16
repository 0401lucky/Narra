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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = BorderStroke(1.dp, titleColor.copy(alpha = 0.08f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
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
            letterSpacing = 0.3.sp,
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
                        buildQuotedDialogueAnnotatedString(
                            text = span.text,
                            narrationColor = narrationColor,
                            dialogueColor = dialogueColor,
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
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                            letterSpacing = 0.5.sp,
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
