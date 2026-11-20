package com.example.myapplication.data.local

import com.example.myapplication.model.ProviderFunctionModelMode
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** 新增向量字段损坏时只关闭该用途，不丢弃同一份旧提供商配置。 */
internal object ProviderEmbeddingSettingsCodec {
    fun normalizeProvidersJson(rawJson: String): JsonElement {
        val providers = JsonParser.parseString(rawJson)
        if (providers.isJsonArray) {
            providers.asJsonArray.forEach { element ->
                if (element.isJsonObject) {
                    val provider = element.asJsonObject
                    val model = provider.stringField("embeddingModel").trim()
                    val mode = provider.stringField("embeddingModelMode")
                    provider.addProperty("embeddingModel", model)
                    provider.addProperty(
                        "embeddingModelMode",
                        if (mode == ProviderFunctionModelMode.CUSTOM.name && model.isNotBlank()) {
                            ProviderFunctionModelMode.CUSTOM.name
                        } else {
                            ProviderFunctionModelMode.DISABLED.name
                        },
                    )
                }
            }
        }
        return providers
    }

    fun normalizeAssignmentsJson(rawJson: String): JsonElement {
        val assignments = JsonParser.parseString(rawJson)
        if (assignments.isJsonObject) {
            val fields = assignments.asJsonObject
            fields.addProperty(
                "embeddingProviderId",
                fields.stringField("embeddingProviderId").trim()
            )
        }
        return assignments
    }

    private fun JsonObject.stringField(name: String): String = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString.orEmpty()
}
