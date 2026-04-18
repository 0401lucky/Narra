package com.example.myapplication.ui.component.roleplay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

@Composable
internal fun rememberRoleplayDiaryAnnotatedString(
    text: String,
    revealMasked: Boolean,
    primaryText: Color,
): AnnotatedString {
    val highlightColor = Color(0xFFFFE9A8)
    val underlineColor = Color(0xFFE88BAA)
    val emphasisColor = Color(0xFFE7729A)
    val secretRevealBackground = Color(0x332F3640)
    return remember(text, revealMasked, primaryText) {
        buildRoleplayDiaryAnnotatedString(
            text = text,
            revealMasked = revealMasked,
            primaryText = primaryText,
            highlightColor = highlightColor,
            underlineColor = underlineColor,
            emphasisColor = emphasisColor,
            secretRevealBackground = secretRevealBackground,
        )
    }
}

private fun buildRoleplayDiaryAnnotatedString(
    text: String,
    revealMasked: Boolean,
    primaryText: Color,
    highlightColor: Color,
    underlineColor: Color,
    emphasisColor: Color,
    secretRevealBackground: Color,
): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("**", index) -> {
                    val next = text.indexOf("**", startIndex = index + 2)
                    if (next > index + 1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(index + 2, next))
                        }
                        index = next + 2
                    } else {
                        append(text[index])
                        index += 1
                    }
                }

                text.startsWith("~~", index) -> {
                    val next = text.indexOf("~~", startIndex = index + 2)
                    if (next > index + 1) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(index + 2, next))
                        }
                        index = next + 2
                    } else {
                        append(text[index])
                        index += 1
                    }
                }

                text.startsWith("!h{", index) -> {
                    index = appendBraceToken(
                        text = text,
                        start = index,
                        prefix = "!h{",
                        style = SpanStyle(
                            background = highlightColor,
                            color = primaryText,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }

                text.startsWith("!u{", index) -> {
                    index = appendBraceToken(
                        text = text,
                        start = index,
                        prefix = "!u{",
                        style = SpanStyle(
                            color = underlineColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }

                text.startsWith("!e{", index) -> {
                    index = appendBraceToken(
                        text = text,
                        start = index,
                        prefix = "!e{",
                        style = SpanStyle(
                            color = emphasisColor,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }

                text.startsWith("!w{", index) -> {
                    index = appendBraceToken(
                        text = text,
                        start = index,
                        prefix = "!w{",
                        style = SpanStyle(
                            fontFamily = FontFamily.Cursive,
                            fontStyle = FontStyle.Italic,
                        ),
                    )
                }

                text.startsWith("!m{", index) -> {
                    index = appendBraceToken(
                        text = text,
                        start = index,
                        prefix = "!m{",
                        style = SpanStyle(
                            fontFamily = FontFamily.Cursive,
                            fontStyle = FontStyle.Italic,
                            color = primaryText.copy(alpha = 0.78f),
                            letterSpacing = 0.4.sp,
                        ),
                    )
                }

                text.startsWith("||", index) -> {
                    val next = text.indexOf("||", startIndex = index + 2)
                    if (next > index + 1) {
                        val secret = text.substring(index + 2, next)
                        if (revealMasked) {
                            withStyle(
                                SpanStyle(
                                    background = secretRevealBackground,
                                    fontWeight = FontWeight.Medium,
                                ),
                            ) {
                                append(secret)
                            }
                        } else {
                            append("█".repeat(secret.length.coerceIn(2, 5)))
                        }
                        index = next + 2
                    } else {
                        append(text[index])
                        index += 1
                    }
                }

                else -> {
                    append(text[index])
                    index += 1
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendBraceToken(
    text: String,
    start: Int,
    prefix: String,
    style: SpanStyle,
): Int {
    val contentStart = start + prefix.length
    val end = text.indexOf('}', startIndex = contentStart)
    if (end <= contentStart) {
        append(text[start])
        return start + 1
    }
    withStyle(style) {
        append(text.substring(contentStart, end))
    }
    return end + 1
}
