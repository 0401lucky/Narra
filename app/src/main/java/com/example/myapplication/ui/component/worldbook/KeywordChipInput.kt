package com.example.myapplication.ui.component.worldbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.example.myapplication.ui.screen.settings.rememberSettingsOutlineColors
import com.example.myapplication.ui.screen.settings.rememberSettingsPalette
import com.example.myapplication.ui.theme.NarraUi
import com.example.myapplication.ui.theme.NarraShapes
import com.example.myapplication.ui.theme.NarraMinimumTouchTarget

/**
 * 判断一段文本是否像合法的 `/pattern/flags` 形式。
 *
 * 仅做字符级识别，不实际编译正则；用于 UI 层给正则 chip 一个强调色提示，
 * 以及在拆分关键词时避免把 `/foo,bar/i` 按逗号切散。允许的 flag 为
 * i / m / s / g / u（SillyTavern 生态常见集），大小写不敏感。
 */
fun looksLikeWorldBookRegexLiteral(token: String): Boolean {
    if (token.length < 3 || !token.startsWith('/')) return false
    var escaped = false
    var endIndex = -1
    for (index in 1 until token.length) {
        val current = token[index]
        if (current == '/' && !escaped) {
            endIndex = index
            break
        }
        escaped = current == '\\' && !escaped
        if (current != '\\') escaped = false
    }
    if (endIndex <= 1) return false
    val flags = token.substring(endIndex + 1)
    return flags.all { it.lowercaseChar() in "imsgu" }
}

/**
 * 把一整段用户输入切成关键词列表。相比 `split(",")`：
 * 1. 遇到 `/.../flags` 形态的正则字面量不切分其内部逗号；
 * 2. 同时支持半角 / 全角逗号、换行作为分隔符；
 * 3. 结果去空白、按首次出现顺序去重。
 */
fun splitKeywordsPreservingRegex(rawValue: String): List<String> {
    if (rawValue.isBlank()) return emptyList()
    val tokens = mutableListOf<String>()
    val buffer = StringBuilder()
    var cursor = 0
    while (cursor < rawValue.length) {
        val char = rawValue[cursor]
        if (char == '/' && buffer.isBlank()) {
            val regexEnd = findRegexLiteralEnd(rawValue, cursor)
            if (regexEnd != null) {
                tokens += rawValue.substring(cursor, regexEnd + 1)
                cursor = regexEnd + 1
                continue
            }
        }
        if (char == ',' || char == '，' || char == '\n' || char == '\r') {
            flushBuffer(buffer, tokens)
        } else {
            buffer.append(char)
        }
        cursor += 1
    }
    flushBuffer(buffer, tokens)
    return tokens
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

private fun findRegexLiteralEnd(source: String, start: Int): Int? {
    if (start >= source.length || source[start] != '/') return null
    var escaped = false
    var endIndex = -1
    for (index in start + 1 until source.length) {
        val current = source[index]
        if (current == '/' && !escaped) {
            endIndex = index
            break
        }
        escaped = current == '\\' && !escaped
        if (current != '\\') escaped = false
    }
    if (endIndex <= start + 1) return null
    var flagEnd = endIndex
    while (flagEnd + 1 < source.length && source[flagEnd + 1].lowercaseChar() in "imsgu") {
        flagEnd += 1
    }
    val candidate = source.substring(start, flagEnd + 1)
    return if (looksLikeWorldBookRegexLiteral(candidate)) flagEnd else null
}

private fun flushBuffer(buffer: StringBuilder, tokens: MutableList<String>) {
    if (buffer.isNotBlank()) {
        tokens += buffer.toString()
    }
    buffer.clear()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordChipInput(
    label: String,
    values: List<String>,
    onValuesChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "回车 / 逗号提交",
    enabled: Boolean = true,
) {
    val palette = rememberSettingsPalette()
    val outlineColors = rememberSettingsOutlineColors().copy(
        focusedLabelColor = palette.title,
        disabledTextColor = palette.body,
        disabledPlaceholderColor = palette.body,
        disabledContainerColor = palette.surfaceTint,
    )
    var draft by rememberSaveable(label) { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NarraUi.space12),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = palette.title,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { next ->
                if (next.endsWith(',') || next.endsWith('，') || next.endsWith('\n')) {
                    val newTokens = splitKeywordsPreservingRegex(next)
                    if (newTokens.isNotEmpty()) {
                        onValuesChange((values + newTokens).distinct())
                    }
                    draft = ""
                } else {
                    draft = next
                }
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            singleLine = true,
            enabled = enabled,
            placeholder = { Text(placeholder) },
            shape = NarraShapes.medium,
            colors = outlineColors,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    val tokens = splitKeywordsPreservingRegex(draft)
                    if (tokens.isNotEmpty()) {
                        onValuesChange((values + tokens).distinct())
                    }
                    draft = ""
                },
            ),
        )
        if (values.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(NarraUi.space8),
                verticalArrangement = Arrangement.spacedBy(NarraUi.space8),
                modifier = Modifier.fillMaxWidth(),
            ) {
                values.forEach { token ->
                    val isRegex = looksLikeWorldBookRegexLiteral(token)
                    AssistChip(
                        modifier = Modifier.sizeIn(
                            minWidth = NarraMinimumTouchTarget,
                            minHeight = NarraMinimumTouchTarget
                        ),
                        shape = NarraShapes.small,
                        onClick = { onValuesChange(values.filterNot { it == token }) },
                        label = {
                            Text(
                                text = if (isRegex) "正则 $token" else token,
                                fontWeight = if (isRegex) FontWeight.Medium else FontWeight.Normal,
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "移除 $token",
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isRegex) palette.accentSoft else palette.subtleChip,
                            labelColor = if (isRegex) MaterialTheme.colorScheme.onPrimaryContainer else palette.subtleChipContent,
                            trailingIconContentColor = if (isRegex) MaterialTheme.colorScheme.onPrimaryContainer else palette.subtleChipContent,
                            disabledContainerColor = if (isRegex) palette.accentSoft else palette.subtleChip,
                            disabledLabelColor = if (isRegex) MaterialTheme.colorScheme.onPrimaryContainer else palette.subtleChipContent,
                            disabledTrailingIconContentColor = if (isRegex) MaterialTheme.colorScheme.onPrimaryContainer else palette.subtleChipContent,
                        ),
                        enabled = enabled,
                    )
                }
            }
        }
    }
}
