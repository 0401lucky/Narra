package com.example.myapplication.ui.screen.chat

import android.text.format.DateUtils
import com.example.myapplication.model.Conversation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal data class DrawerConversationSection(
    val title: String,
    val conversations: List<Conversation>,
)

internal fun buildDrawerConversationSections(
    conversations: List<Conversation>,
    searchQuery: String,
): List<DrawerConversationSection> {
    val normalizedQuery = searchQuery.trim().lowercase(Locale.getDefault())
    val filteredConversations = conversations
        .sortedByDescending { it.updatedAt }
        .filter { conversation ->
            normalizedQuery.isBlank() ||
                conversation.title.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                conversation.model.lowercase(Locale.getDefault()).contains(normalizedQuery)
        }

    val sections = linkedMapOf<String, MutableList<Conversation>>()
    filteredConversations.forEach { conversation ->
        val sectionTitle = resolveConversationSectionTitle(conversation.updatedAt)
        sections.getOrPut(sectionTitle) { mutableListOf() }.add(conversation)
    }
    return sections.map { (title, items) ->
        DrawerConversationSection(title = title, conversations = items)
    }
}

internal fun buildConversationMetaText(conversation: Conversation): String {
    val timeLabel = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(conversation.updatedAt))
    return if (conversation.model.isBlank()) {
        timeLabel
    } else {
        "${conversation.model} · $timeLabel"
    }
}

private fun resolveConversationSectionTitle(timestamp: Long): String {
    if (DateUtils.isToday(timestamp)) {
        return "今天"
    }

    val targetCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (isSameDay(targetCalendar, yesterday)) {
        return "昨天"
    }

    val now = Calendar.getInstance()
    return if (now.get(Calendar.YEAR) == targetCalendar.get(Calendar.YEAR)) {
        SimpleDateFormat("M月d日", Locale.CHINA).format(Date(timestamp))
    } else {
        SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(timestamp))
    }
}

private fun isSameDay(
    first: Calendar,
    second: Calendar,
): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

internal fun resolveDrawerGreeting(hourOfDay: Int): String {
    return when (hourOfDay) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }
}
