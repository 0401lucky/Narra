package com.example.myapplication.model

data class PromptEnvelope(
    val preHistoryMessages: List<PromptEnvelopeMessage> = emptyList(),
    val postHistoryMessages: List<PromptEnvelopeMessage> = emptyList(),
    val historyInjections: List<PromptHistoryInjection> = emptyList(),
    val sampler: PresetSamplerConfig = PresetSamplerConfig(),
    val stopSequences: List<String> = emptyList(),
    val statusCardsEnabled: Boolean = true,
    val hideStatusBlocksInBubble: Boolean = true,
) {
    val hasRuntimeOverrides: Boolean
        get() = preHistoryMessages.isNotEmpty() ||
            postHistoryMessages.isNotEmpty() ||
            historyInjections.isNotEmpty() ||
            sampler.hasAnyValue() ||
            stopSequences.isNotEmpty()
}

data class PromptEnvelopeMessage(
    val role: PresetPromptRole = PresetPromptRole.SYSTEM,
    val content: String = "",
) {
    fun normalized(): PromptEnvelopeMessage? {
        val normalizedContent = content.replace("\r\n", "\n").trim()
        if (normalizedContent.isBlank()) {
            return null
        }
        return copy(content = normalizedContent)
    }
}

data class PromptHistoryInjection(
    val role: PresetPromptRole = PresetPromptRole.SYSTEM,
    val content: String = "",
    val depth: Int = 0,
    val order: Int = 100,
    val sourceTitle: String = "",
) {
    fun normalized(): PromptHistoryInjection? {
        val normalizedContent = content.replace("\r\n", "\n").trim()
        if (normalizedContent.isBlank()) {
            return null
        }
        return copy(
            content = normalizedContent,
            depth = depth.coerceAtLeast(0),
            sourceTitle = sourceTitle.trim(),
        )
    }
}

fun PresetSamplerConfig.hasAnyValue(): Boolean {
    return temperature != null ||
        topP != null ||
        topK != null ||
        minP != null ||
        repetitionPenalty != null ||
        frequencyPenalty != null ||
        presencePenalty != null ||
        maxOutputTokens != null
}
