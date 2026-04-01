package com.example.myapplication.model

data class GatewayToolRuntimeContext(
    val promptMode: PromptMode,
    val assistant: Assistant? = null,
    val conversation: Conversation? = null,
    val userInputText: String = "",
    val recentMessages: List<ChatMessage> = emptyList(),
)
