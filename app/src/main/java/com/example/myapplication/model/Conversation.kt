package com.example.myapplication.model

const val DEFAULT_CONVERSATION_TITLE = "新对话"

data class Conversation(
    val id: String,
    val title: String = DEFAULT_CONVERSATION_TITLE,
    val model: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val assistantId: String = DEFAULT_ASSISTANT_ID,
)
