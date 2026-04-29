package com.example.myapplication.model

data class PromptEnvelope(
    val preHistoryMessages: List<PromptEnvelopeMessage> = emptyList(),
    val postHistoryMessages: List<PromptEnvelopeMessage> = emptyList(),
    val sampler: PresetSamplerConfig = PresetSamplerConfig(),
    val stopSequences: List<String> = emptyList(),
    val statusCardsEnabled: Boolean = true,
    val hideStatusBlocksInBubble: Boolean = true,
) {
    val hasRuntimeOverrides: Boolean
        get() = preHistoryMessages.isNotEmpty() ||
            postHistoryMessages.isNotEmpty() ||
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
