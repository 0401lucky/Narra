package com.example.myapplication.model

enum class OpenAiTextApiMode(
    val storageValue: String,
    val label: String,
) {
    CHAT_COMPLETIONS(
        storageValue = "chat_completions",
        label = "Chat Completions",
    ),
    RESPONSES(
        storageValue = "responses",
        label = "Responses API",
    );

    companion object {
        fun fromStorageValue(value: String?): OpenAiTextApiMode? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            if (normalized.isBlank()) {
                return null
            }
            return entries.firstOrNull { it.storageValue == normalized }
        }
    }
}

const val DEFAULT_CHAT_COMPLETIONS_PATH = "/chat/completions"
