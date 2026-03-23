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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RoleplayLongformCard(
    speakerName: String,
    content: String,
    modifier: Modifier = Modifier,
) {
    val paragraphs = remember(content) { content.toLongformParagraphs() }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = speakerName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            paragraphs.forEach { paragraph ->
                LongformParagraphText(paragraph = paragraph)
            }
        }
    }
}

internal fun String.toLongformParagraphs(): List<String> {
    return replace("\r\n", "\n")
        .split(Regex("""\n\s*\n+"""))
        .map { paragraph ->
            paragraph.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(separator = "\n")
        }
        .filter { it.isNotEmpty() }
}

@Composable
private fun LongformParagraphText(paragraph: String) {
    val text = paragraph.trim()
    val rendered = buildAnnotatedString {
        if (text.isBlank()) {
            return@buildAnnotatedString
        }
        var cursor = 0
        LongformStyledTokenRegex.findAll(text).forEach { match ->
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            val token = match.value
            val isInnerThought = token.startsWith("（") || token.startsWith("(")
            withStyle(
                if (isInnerThought) {
                    SpanStyle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                    )
                } else {
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            ) {
                append(token)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
    Text(
        text = rendered,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private val LongformStyledTokenRegex = Regex(
    pattern = """（[^）]+）|\([^)\n]+\)|“[^”]+”|"[^"\n]+"|'[^'\n]+'""",
)
