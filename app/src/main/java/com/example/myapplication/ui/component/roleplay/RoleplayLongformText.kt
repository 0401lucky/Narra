package com.example.myapplication.ui.component.roleplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

@Composable
fun RoleplayLongformCard(
    speakerName: String,
    content: String,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
    titleColor: Color = MaterialTheme.colorScheme.primary,
    bodyColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    val paragraphs = remember(content) { content.toLongformParagraphs() }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                )
            }
        }
    }
}

internal fun String.toLongformParagraphs(): List<String> {
    return replace("\r\n", "\n")
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

@Composable
private fun LongformParagraphText(
    paragraph: String,
    bodyColor: Color,
    dialogueColor: Color,
) {
    val rendered = remember(paragraph, bodyColor, dialogueColor) {
        buildQuotedDialogueAnnotatedString(
            text = paragraph.trim(),
            narrationColor = bodyColor,
            dialogueColor = dialogueColor,
        )
    }
    Text(
        text = rendered,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.6.sp,
        ),
        color = bodyColor,
    )
}

internal fun buildQuotedDialogueAnnotatedString(
    text: String,
    narrationColor: Color,
    dialogueColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        if (text.isBlank()) {
            return@buildAnnotatedString
        }
        var cursor = 0
        DialogueQuotedTextRegex.findAll(text).forEach { match ->
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
    pattern = """“[^”]*”|‘[^’]*’|"[^"\n]*"|'[^'\n]*'""",
)
