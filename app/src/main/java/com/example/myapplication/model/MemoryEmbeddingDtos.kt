package com.example.myapplication.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class MemoryEmbeddingRequest(
    val model: String,
    val input: List<String>,
    @SerializedName("encoding_format") val encodingFormat: String = "float",
)

/** JsonElement 保留协议类型，避免 Gson 把小数序号或数字字符串静默转成合法数值。 */
data class MemoryEmbeddingResponse(
    val data: List<MemoryEmbeddingResponseItem?>? = null,
)

data class MemoryEmbeddingResponseItem(
    val index: JsonElement? = null,
    val embedding: JsonElement? = null,
)
