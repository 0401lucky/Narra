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
    @SerializedName("top_k")
    val topK: Int? = null,
    @SerializedName("stop_sequences")
    val stopSequences: List<String> = emptyList(),
    val thinking: AnthropicThinkingDto? = null,
    val tools: List<AnthropicToolDto> = emptyList(),
)

data class AnthropicMessageDto(
    val role: String,
    val content: List<AnthropicContentPartDto>,
)

data class AnthropicContentPartDto(
    val type: String,
    val text: String? = null,
    val thinking: String? = null,
    val source: AnthropicImageSourceDto? = null,
    val id: String? = null,
    val name: String? = null,
    val input: Map<String, Any>? = null,
    @SerializedName("tool_use_id")
    val toolUseId: String? = null,
    val content: String? = null,
    @SerializedName("is_error")
    val isError: Boolean? = null,
)

data class AnthropicToolDto(
    val name: String,
    val description: String,
    @SerializedName("input_schema")
    val inputSchema: Map<String, Any>,
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
    val id: String? = null,
    val name: String? = null,
    val input: Map<String, Any>? = null,
)
