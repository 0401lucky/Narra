package com.example.myapplication.model

import androidx.compose.runtime.Immutable

@Immutable
data class ChatReasoningStep(
    val id: String,
    val text: String,
    val createdAt: Long,
    val finishedAt: Long? = null,
)

fun normalizeChatReasoningSteps(
    steps: List<ChatReasoningStep>,
): List<ChatReasoningStep> {
    if (steps.isEmpty()) {
        return emptyList()
    }
    return steps
        .map { step ->
            step.copy(
                text = step.text,
                finishedAt = step.finishedAt,
            )
        }
        .filter { step ->
            step.text.isNotBlank() || step.finishedAt == null
        }
}

fun reasoningStepsToContent(
    steps: List<ChatReasoningStep>,
): String {
    return normalizeChatReasoningSteps(steps)
        .mapNotNull { step ->
            step.text.trim().takeIf { it.isNotEmpty() }
        }
        .joinToString(separator = "\n\n")
}

fun legacyReasoningStepsFromContent(
    reasoningContent: String,
    createdAt: Long,
    finishedAt: Long? = createdAt,
    idPrefix: String = "legacy-reasoning",
): List<ChatReasoningStep> {
    val normalized = reasoningContent.trim()
    if (normalized.isBlank()) {
        return emptyList()
    }
    return listOf(
        ChatReasoningStep(
            id = "$idPrefix-$createdAt",
            text = normalized,
            createdAt = createdAt,
            finishedAt = finishedAt,
        ),
    )
}
