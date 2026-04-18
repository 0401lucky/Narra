package com.example.myapplication.ui.component.roleplay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    accent: Color,
): AnnotatedString {
    val isDark = primaryText.luminance() > 0.5f
    val highlightBackground = accent.copy(alpha = if (isDark) 0.32f else 0.22f)
    val underlineColor = accent
    val emphasisColor = accent.copy(alpha = 0.92f)
    val mutedHandwriting = primaryText.copy(alpha = 0.78f)
    val secretRevealBackground = primaryText.copy(alpha = 0.16f)
    val secretBlockColor = primaryText.copy(alpha = 0.55f)
    return remember(text, revealMasked, primaryText, accent) {
        buildRoleplayDiaryAnnotatedString(
            text = text,
            revealMasked = revealMasked,
            primaryText = primaryText,
            highlightBackground = highlightBackground,
            underlineColor = underlineColor,
            emphasisColor = emphasisColor,
            mutedHandwriting = mutedHandwriting,
            secretRevealBackground = secretRevealBackground,
            secretBlockColor = secretBlockColor,
        )
    }
}

/**
 * 去除日记标记后的纯文本，供屏幕阅读器/复制使用。
 * 在 `revealMasked=false` 时，涂黑段落仍以方块占位，避免泄露明文长度或内容。
 */
internal fun stripRoleplayDiaryMarkers(
    text: String,
    revealMasked: Boolean,
): String {
    val builder = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val next = text.indexOf("**", startIndex = index + 2)
                if (next > index + 1) {
                    builder.append(text, index + 2, next)
                    index = next + 2
                } else {
                    builder.append(text[index]); index += 1
                }
            }
            text.startsWith("~~", index) -> {
                val next = text.indexOf("~~", startIndex = index + 2)
                if (next > index + 1) {
                    builder.append(text, index + 2, next)
                    index = next + 2
                } else {
                    builder.append(text[index]); index += 1
                }
            }
            text.startsWith("!h{", index) ||
                text.startsWith("!u{", index) ||
                text.startsWith("!e{", index) ||
                text.startsWith("!w{", index) ||
                text.startsWith("!m{", index) -> {
                val contentStart = index + 3
                val end = text.indexOf('}', startIndex = contentStart)
                if (end > contentStart) {
                    builder.append(text, contentStart, end)
                    index = end + 1
                } else {
                    builder.append(text[index]); index += 1
                }
            }
            text.startsWith("||", index) -> {
                val next = text.indexOf("||", startIndex = index + 2)
                if (next > index + 1) {
                    if (revealMasked) {
                        builder.append(text, index + 2, next)
                    } else {
                        builder.append("▇▇")
                    }
                    index = next + 2
                } else {
                    builder.append(text[index]); index += 1
                }
            }
            else -> {
                builder.append(text[index]); index += 1
            }
        }
    }
    return builder.toString()
}

private fun buildRoleplayDiaryAnnotatedString(
    text: String,
    revealMasked: Boolean,
    primaryText: Color,
    highlightBackground: Color,
    underlineColor: Color,
    emphasisColor: Color,
    mutedHandwriting: Color,
    secretRevealBackground: Color,
    secretBlockColor: Color,
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
                            background = highlightBackground,
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
                            color = primaryText,
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
                            color = mutedHandwriting,
                            letterSpacing = 0.4.sp,
                        ),
                    )
                }

                text.startsWith("||", index) -> {
                    val next = text.indexOf("||", startIndex = index + 2)
                    if (next > index + 1) {
                        if (revealMasked) {
                            withStyle(
                                SpanStyle(
                                    background = secretRevealBackground,
                                    color = primaryText,
                                    fontWeight = FontWeight.Medium,
                                ),
                            ) {
                                append(text.substring(index + 2, next))
                            }
                        } else {
                            // 不把原文写入 AnnotatedString，彻底避免复制/可访问性泄露。
                            withStyle(
                                SpanStyle(
                                    background = secretBlockColor,
                                    color = Color.Transparent,
                                ),
                            ) {
                                append("▇▇▇")
                            }
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
