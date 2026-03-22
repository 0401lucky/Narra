package com.example.myapplication.model

import com.google.gson.annotations.SerializedName

data class ModelsResponse(
    val data: List<ModelDto> = emptyList(),
)

data class ModelDto(
    val id: String,
    @SerializedName("object") val objectType: String? = null,
    @SerializedName("owned_by")
    val ownedBy: String? = null,
)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = false,
    @SerializedName("reasoning_effort")
    val reasoningEffort: String? = null,
    val thinking: ThinkingConfigDto? = null,
)

data class ThinkingConfigDto(
    val type: String = "enabled",
    @SerializedName("budget_tokens")
    val budgetTokens: Int,
)

data class ChatMessageDto(
    val role: String,
    val content: Any,
)

data class TextContentPartDto(
    val type: String = "text",
    val text: String,
)

data class ImageUrlContentPartDto(
    val type: String = "image_url",
    @SerializedName("image_url")
    val imageUrl: ImageUrlDto,
)

data class ImageUrlDto(
    val url: String,
)

data class ChatCompletionResponse(
    val choices: List<ChatChoiceDto> = emptyList(),
)

data class AssistantMessageDto(
    val role: String? = null,
    val content: Any? = null,
    val images: List<AssistantImagePartDto> = emptyList(),
    @SerializedName("reasoning_content")
    val reasoningContent: String? = null,
    val reasoning: String? = null,
    val thinking: String? = null,
)

data class ChatChoiceDto(
    val index: Int? = null,
    val message: AssistantMessageDto? = null,
    @SerializedName("finish_reason")
    val finishReason: String? = null,
)

data class ChatCompletionChunk(
    val choices: List<ChatChunkChoiceDto> = emptyList(),
)

data class ChatChunkChoiceDto(
    val index: Int? = null,
    val delta: ChatDeltaDto? = null,
    @SerializedName("finish_reason")
    val finishReason: String? = null,
)

data class ChatDeltaDto(
    val role: String? = null,
    val content: String? = null,
    val images: List<AssistantImagePartDto> = emptyList(),
    @SerializedName("reasoning_content")
    val reasoningContent: String? = null,
    val reasoning: String? = null,
    val thinking: String? = null,
)

data class AssistantImagePartDto(
    val type: String? = null,
    @SerializedName("image_url")
    val imageUrl: ImageUrlDto? = null,
    val url: String? = null,
    @SerializedName("b64_json")
    val b64Json: String? = null,
    @SerializedName("mime_type")
    val mimeType: String? = null,
)

data class ImageGenerationRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    @SerializedName("response_format")
    val responseFormat: String = "b64_json",
    val size: String = "1024x1024",
)

data class ImageGenerationResponse(
    val created: Long? = null,
    val data: List<ImageGenerationDataDto> = emptyList(),
)

data class ImageGenerationDataDto(
    val url: String? = null,
    @SerializedName("b64_json")
    val b64Json: String? = null,
    @SerializedName("revised_prompt")
    val revisedPrompt: String? = null,
)
