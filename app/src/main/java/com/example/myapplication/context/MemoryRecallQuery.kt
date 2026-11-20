package com.example.myapplication.context

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.toPlainText

data class MemoryRecallQuery(
    val text: String,
    val terms: Set<String>,
    val datesAndNumbers: Set<String>,
    val weight: Double,
    val currentUser: Boolean,
)

object MemoryRecallQueryBuilder {
    fun build(userInputText: String, recentMessages: List<ChatMessage>): List<MemoryRecallQuery> {
        val available =
            recentMessages.filter { it.status == MessageStatus.COMPLETED && !it.isRecalled }
        val lastAssistant = available.indexOfLast { it.role == MessageRole.ASSISTANT }
        val burst = available.drop(lastAssistant + 1).filter { it.role == MessageRole.USER }
            .map { clean(it.parts.toPlainText().ifBlank { it.content }) }
            .filter(String::isNotBlank).toMutableList()
        val input = clean(userInputText)
        if (input.isNotBlank() && input !in burst) burst += input
        val queries = burst.distinct().mapNotNull { query(it, 1.0, true) }
        if (queries.isEmpty() && input.isNotBlank()) return emptyList()
        // 每条本轮用户发言保留独立主题；角色长回复只能提供有限、低权重的邻近背景。
        val background = available.take((lastAssistant + 1).coerceAtLeast(0)).takeLast(3)
            .mapNotNull { message ->
                query(
                    clean(message.parts.toPlainText().ifBlank { message.content }).take(240),
                    if (message.role == MessageRole.USER) 0.12 else 0.04,
                    false,
                )
            }
        return queries + background
    }

    private fun query(text: String, weight: Double, currentUser: Boolean): MemoryRecallQuery? {
        if (GREETING.matches(text.trim())) return null
        val terms = MemoryRecallText.terms(text).toSet()
        if (terms.isEmpty()) return null
        return MemoryRecallQuery(
            text,
            terms,
            MemoryRecallText.datesAndNumbers(text),
            weight,
            currentUser
        )
    }

    private fun clean(text: String): String = FORMAT_REMINDER.replace(text, "").trim()

    private val FORMAT_REMINDER =
        Regex("<format_reminder>.*?</format_reminder>", RegexOption.DOT_MATCHES_ALL)
    private val GREETING = Regex(
        "(?:你好|您好|早安|晚安|谢谢|好的|好呀|哈哈+|嗯+|hello|hi|thanks|ok|okay)[！!。.,，？?\\s]*",
        RegexOption.IGNORE_CASE
    )
}
