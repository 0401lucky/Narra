package com.example.myapplication.model

import com.google.gson.annotations.SerializedName

data class ResponseApiRequest(
    val model: String,
    val input: List<Any>,
    val instructions: String? = null,
    val stream: Boolean = false,
    val temperature: Float? = null,
    @SerializedName("top_p")
    val topP: Float? = null,
    @SerializedName("max_output_tokens")
    val maxOutputTokens: Int? = null,
    val reasoning: ResponseApiReasoningDto? = null,
    val tools: List<ResponseApiToolDto> = emptyList(),
    @SerializedName("previous_response_id")
    val previousResponseId: String? = null,
)

data class ResponseApiReasoningDto(
    val effort: String? = null,
    val summary: String? = null,
)

data class ResponseApiInputItemDto(
    val role: String,
    val content: Any,
)

data class ResponseApiToolDto(
    val type: String = "function",
    val name: String,
    val description: String,
    val parameters: Map<String, Any>,
)

data class ResponseApiResponse(
    val id: String = "",
    val output: List<ResponseApiOutputItemDto> = emptyList(),
    val status: String? = null,
    @SerializedName("incomplete_details")
    val incompleteDetails: ResponseApiIncompleteDetailsDto? = null,
    val error: ResponseApiErrorDto? = null,
)

data class ResponseApiIncompleteDetailsDto(
    val reason: String? = null,
)

data class ResponseApiErrorDto(
    val code: String? = null,
)

data class ResponseApiOutputItemDto(
    val type: String = "",
    val content: List<ResponseApiOutputContentDto> = emptyList(),
    val summary: List<ResponseApiSummaryContentDto> = emptyList(),
    @SerializedName("call_id")
    val callId: String? = null,
    val name: String? = null,
    val arguments: String? = null,
    val status: String? = null,
)

data class ResponseApiOutputContentDto(
    val type: String = "",
    val text: String? = null,
    val refusal: String? = null,
)

data class ResponseApiSummaryContentDto(
    val type: String = "",
    val text: String? = null,
)
