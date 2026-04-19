package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.textMessagePart

/**
 * 方案 C-A：在最新 user 消息 content 前注入格式重申
 *
 * 为什么这么做：长对话里 system prompt 的影响力会被消息历史稀释，
 * 模型倾向于把历史消息当成"格式该有的样子"继续复制。业界（SillyTavern
 * Author's Note、Anthropic 官方指南、OpenAI 社区测试）一致指向
 * "把运行时指令挂到最新 user 消息附近"是最稳定的对抗策略。
 *
 * 注入用 <format_reminder> XML 标签包裹，呼应 Anthropic 的数据/指令分隔
 * 建议，同时避免模型把重申当作用户原话引用。
 */
object RoleplayFormatReminderSupport {
    private const val ReminderOpenTag = "<format_reminder>"
    private const val ReminderCloseTag = "</format_reminder>"

    fun buildReminderText(scenario: RoleplayScenario): String? {
        return when {
            scenario.interactionMode == RoleplayInteractionMode.ONLINE_PHONE -> {
                "整条回复必须是合法 JSON 数组 [...]；单条也要用 [] 包裹；" +
                    "禁止 Markdown 代码块、<char>/<dialogue> 标签、裸对象或纯文本。"
            }

            scenario.longformModeEnabled ||
                scenario.interactionMode == RoleplayInteractionMode.OFFLINE_LONGFORM -> {
                "每句台词必须用 <char>\u201C…\u201D</char> 包裹，每段心声必须用 " +
                    "<thought>（…）</thought> 包裹；叙述裸写不加标记；" +
                    "禁止 Markdown、<dialogue>/<narration>、JSON 数组。"
            }

            scenario.enableRoleplayProtocol -> {
                val narrationClause = if (scenario.enableNarration) {
                    "，每段叙述用 <narration>…</narration>"
                } else {
                    "；本场景禁用 <narration>"
                }
                "每段对白必须用 <dialogue speaker=\"character\" emotion=\"具体情绪\">\u201C…\u201D</dialogue> 包裹" +
                    narrationClause +
                    "；禁止 <char>、裸对白、空 emotion。"
            }

            else -> null
        }
    }

    fun injectIntoLatestUser(
        messages: List<ChatMessage>,
        scenario: RoleplayScenario,
    ): List<ChatMessage> {
        val reminder = buildReminderText(scenario) ?: return messages
        val latestUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
        if (latestUserIndex < 0) return messages
        val original = messages[latestUserIndex]
        val prefixed = original.withFormatReminderPrefix(reminder)
        if (prefixed === original) return messages
        return messages.toMutableList().also { it[latestUserIndex] = prefixed }
    }

    private fun ChatMessage.withFormatReminderPrefix(reminder: String): ChatMessage {
        val wrapped = "$ReminderOpenTag\n$reminder\n$ReminderCloseTag\n\n"
        val existingTextIndex = parts.indexOfFirst { it.type == ChatMessagePartType.TEXT }
        if (existingTextIndex >= 0) {
            val existing = parts[existingTextIndex]
            val updatedText = wrapped + existing.text
            val newParts = parts.toMutableList().apply {
                set(existingTextIndex, existing.copy(text = updatedText))
            }
            return copy(parts = newParts)
        }
        if (parts.isEmpty()) {
            if (content.isBlank()) {
                // 极端边界：空消息（理论上被 hasSendableContent 过滤掉，这里保守处理）
                return this
            }
            return copy(content = wrapped + content)
        }
        // parts 只含非 TEXT（图片/附件等）：前插一个 TEXT part 承载 reminder
        return copy(parts = listOf(textMessagePart(wrapped)) + parts)
    }
}
