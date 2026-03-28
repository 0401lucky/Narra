package com.example.myapplication.model

import com.google.gson.annotations.SerializedName

data class ResponseApiRequest(
    val model: String,
    val input: List<ResponseApiInputItemDto>,
    val instructions: String? = null,
    val stream: Boolean = false,
    val temperature: Float? = null,
    @SerializedName("top_p")
    val topP: Float? = null,
    val reasoning: ResponseApiReasoningDto? = null,
)

data class ResponseApiReasoningDto(
    val effort: String? = null,
    val summary: String? = null,
)

data class ResponseApiInputItemDto(
    val role: String,
    val content: Any,
)

data class ResponseApiResponse(
    val id: String = "",
    val output: List<ResponseApiOutputItemDto> = emptyList(),
)

data class ResponseApiOutputItemDto(
    val type: String = "",
    val content: List<ResponseApiOutputContentDto> = emptyList(),
    val summary: List<ResponseApiSummaryContentDto> = emptyList(),
)

data class ResponseApiOutputContentDto(
    val type: String = "",
    val text: String? = null,
)

data class ResponseApiSummaryContentDto(
    val type: String = "",
    val text: String? = null,
)
