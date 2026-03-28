package com.example.myapplication.model

enum class ProviderApiProtocol(
    val storageValue: String,
    val label: String,
) {
    OPENAI_COMPATIBLE(
        storageValue = "openai_compatible",
        label = "OpenAI 兼容",
    ),
    ANTHROPIC(
        storageValue = "anthropic",
        label = "Anthropic",
    );

    companion object {
        fun fromStorageValue(value: String?): ProviderApiProtocol? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            if (normalized.isBlank()) {
                return null
            }
            return entries.firstOrNull { it.storageValue == normalized }
        }
    }
}
