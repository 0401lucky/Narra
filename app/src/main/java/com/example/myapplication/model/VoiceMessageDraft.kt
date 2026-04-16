package com.example.myapplication.model

import kotlin.math.ceil

data class VoiceMessageDraft(
    val content: String = "",
    val durationSeconds: Int? = null,
)

fun estimateVoiceMessageDurationSeconds(content: String): Int {
    val normalizedLength = content.trim().length.coerceAtLeast(1)
    return ceil(normalizedLength / 6.0).toInt().coerceIn(1, 60)
}

fun resolveVoiceMessageDurationSeconds(
    content: String,
    preferredDurationSeconds: Int?,
): Int {
    return preferredDurationSeconds?.coerceIn(1, 60)
        ?: estimateVoiceMessageDurationSeconds(content)
}
