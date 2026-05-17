package com.example.myapplication.roleplay

import com.example.myapplication.model.Assistant

object RoleplaySummaryWindowSupport {
    const val DEFAULT_RECENT_MESSAGE_WINDOW = 8
    const val MIN_RECENT_MESSAGE_WINDOW = 4

    fun resolveRecentWindow(assistant: Assistant?): Int {
        return (assistant?.contextMessageSize?.takeIf { it > 0 }
            ?: DEFAULT_RECENT_MESSAGE_WINDOW)
            .coerceAtLeast(MIN_RECENT_MESSAGE_WINDOW)
    }
}
