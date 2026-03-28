package com.example.myapplication.model

import com.google.gson.annotations.SerializedName

data class AnthropicModelsResponse(
    val data: List<AnthropicModelDto> = emptyList(),
)

data class AnthropicModelDto(
    val id: String = "",
    @SerializedName("display_name")
    val displayName: String? = null,
)

data class AnthropicMessageRequest(
    val model: String,
    val messages: List<AnthropicMessageDto>,
    @SerializedName("max_tokens")
    val maxTokens: Int,
    val system: String? = null,
    val stream: Boolean = false,
    val temperature: Float? = null,
    @SerializedName("top_p")
    val topP: Float? = null,
    val thinking: AnthropicThinkingDto? = null,
)

data class AnthropicMessageDto(
    val role: String,
    val content: List<AnthropicContentPartDto>,
)

data class AnthropicContentPartDto(
    val type: String,
    val text: String? = null,
    val source: AnthropicImageSourceDto? = null,
)

data class AnthropicImageSourceDto(
    val type: String,
    @SerializedName("media_type")
    val mediaType: String,
    val data: String,
)

data class AnthropicThinkingDto(
    val type: String = "enabled",
    @SerializedName("budget_tokens")
    val budgetTokens: Int,
)

data class AnthropicMessageResponse(
    val id: String = "",
    val model: String = "",
    val content: List<AnthropicResponseContentDto> = emptyList(),
    @SerializedName("stop_reason")
    val stopReason: String? = null,
)

data class AnthropicResponseContentDto(
    val type: String = "",
    val text: String? = null,
    val thinking: String? = null,
)
