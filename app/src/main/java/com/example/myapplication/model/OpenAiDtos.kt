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
    val temperature: Float? = null,
    @SerializedName("top_p")
    val topP: Float? = null,
    @SerializedName("reasoning_effort")
    val reasoningEffort: String? = null,
    val thinking: ThinkingConfigDto? = null,
    val tools: List<ChatToolDto> = emptyList(),
    @SerializedName("tool_choice")
    val toolChoice: String? = null,
)

data class ThinkingConfigDto(
    val type: String = "enabled",
    @SerializedName("budget_tokens")
    val budgetTokens: Int,
)

data class ChatMessageDto(
    val role: String,
    val content: Any,
    val name: String? = null,
    @SerializedName("tool_call_id")
    val toolCallId: String? = null,
    @SerializedName("tool_calls")
    val toolCalls: List<ChatToolCallDto> = emptyList(),
)

data class ChatToolDto(
    val type: String = "function",
    val function: ChatFunctionDefinitionDto,
)

data class ChatFunctionDefinitionDto(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>,
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
    @SerializedName("tool_calls")
    val toolCalls: List<ChatToolCallDto> = emptyList(),
)

data class ChatToolCallDto(
    val id: String = "",
    val type: String = "function",
    val function: ChatToolFunctionDto = ChatToolFunctionDto(),
)

data class ChatToolFunctionDto(
    val name: String = "",
    val arguments: String = "",
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

data class ImageEditRequest(
    val model: String,
    val prompt: String,
    val images: List<ImageEditInputImageDto>,
    val n: Int = 1,
    @SerializedName("response_format")
    val responseFormat: String = "b64_json",
    val size: String = "1024x1024",
)

data class ImageEditInputImageDto(
    @SerializedName("image_url")
    val imageUrl: String? = null,
    @SerializedName("file_id")
    val fileId: String? = null,
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
