package com.example.myapplication.model

data class RoleplayChatSummary(
    val scenario: RoleplayScenario,
    val session: RoleplaySession? = null,
    val lastMessageText: String = "",
    val lastMessageAt: Long = 0L,
    val lastActiveAt: Long = 0L,
    val lastMessageRole: MessageRole? = null,
) {
    val hasSession: Boolean
        get() = session != null

    val hasHistory: Boolean
        get() = lastMessageText.isNotBlank() && lastMessageAt > 0L
}
