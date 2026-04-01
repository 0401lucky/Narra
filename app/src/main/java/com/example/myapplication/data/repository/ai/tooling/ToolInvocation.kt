package com.example.myapplication.data.repository.ai.tooling

data class ToolInvocation(
    val id: String,
    val name: String,
    val argumentsJson: String? = null,
    val argumentsMap: Map<String, Any> = emptyMap(),
)
