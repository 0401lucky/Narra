package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.model.MessageCitation

data class ToolExecutionResult(
    val payload: String,
    val isError: Boolean = false,
    val citations: List<MessageCitation> = emptyList(),
)
