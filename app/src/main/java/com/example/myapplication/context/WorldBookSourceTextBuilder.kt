package com.example.myapplication.context

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.toPlainText

internal const val WORLD_BOOK_SOURCE_TEXT_MAX_CHARS = 2000

/**
 * 构造世界书匹配用的 sourceText。
 *
 * - `scanDepth <= 0` → 仅返回 `userInputText.trim()`；若为空，退回到 `recentMessages` 中最近一条 USER 消息。
 * - `scanDepth > 0` → 当前输入 + 最近 `scanDepth` 条消息（含 assistant）按时间顺序（旧→新）拼接，
 *   消息之间用换行分隔；当前输入追加在最后。
 * - `maxChars` 从头部截断，确保 `userInputText` 的内容始终保留在末尾。
 */
internal fun buildWorldBookSourceText(
    userInputText: String,
    recentMessages: List<ChatMessage>,
    scanDepth: Int,
    maxChars: Int = WORLD_BOOK_SOURCE_TEXT_MAX_CHARS,
): String {
    val latestUserInput = userInputText.trim()
    val effectiveDepth = scanDepth.coerceAtLeast(0)
    if (effectiveDepth == 0) {
        if (latestUserInput.isNotBlank()) return latestUserInput
        return recentMessages
            .asReversed()
            .firstOrNull { it.role == MessageRole.USER }
            ?.let { message ->
                message.parts.toPlainText().ifBlank { message.content }.trim()
            }
            .orEmpty()
    }

    val tail = recentMessages.takeLast(effectiveDepth)
        .mapNotNull { message ->
            message.parts.toPlainText().ifBlank { message.content }.trim().takeIf { it.isNotEmpty() }
        }
    val joined = buildString {
        tail.forEach { text ->
            append(text)
            append('\n')
        }
        if (latestUserInput.isNotBlank()) append(latestUserInput)
    }.trim()

    if (joined.length <= maxChars) return joined
    return joined.takeLast(maxChars)
}
