package com.example.myapplication.roleplay.script

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class RoleplayScriptJsonCodec(
    private val gson: Gson = Gson(),
) {
    fun encodeInput(input: RoleplayScriptInput): String {
        return gson.toJson(input)
    }

    fun toJsStringLiteral(value: String): String {
        return gson.toJson(value)
    }

    fun decodeOutput(raw: String): RoleplayScriptHostOutput {
        if (raw.length > RoleplayScriptOutputQuota.MAX_RAW_OUTPUT_CHARS) {
            return RoleplayScriptHostOutput()
        }
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            ?: return RoleplayScriptHostOutput()
        return RoleplayScriptOutputQuota.sanitizeScriptOutput(
            RoleplayScriptHostOutput(
                variables = root.objectOrNull("variables")?.asStringMap().orEmpty(),
                promptAdditions = root.arrayOrNull("promptAdditions")?.asStringList().orEmpty(),
                outgoingMessage = root.stringOrNull("outgoingMessage"),
                uiDirectives = root.arrayOrNull("uiDirectives")?.mapNotNull { element ->
                    val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    RoleplayScriptUiDirective(
                        type = item.stringOrNull("type").orEmpty(),
                        payload = item.stringOrNull("payload").orEmpty(),
                    ).takeIf { it.type.isNotBlank() }
                }.orEmpty(),
                logs = root.arrayOrNull("logs")?.asStringList().orEmpty(),
            ),
        )
    }

    private fun JsonObject.objectOrNull(name: String): JsonObject? {
        return get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun JsonObject.arrayOrNull(name: String): JsonArray? {
        return get(name)?.takeIf { it.isJsonArray }?.asJsonArray
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        return get(name)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.asStringMap(): Map<String, String> {
        return entrySet().mapNotNull { entry ->
            val value = entry.value.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
                ?: return@mapNotNull null
            entry.key.trim().takeIf { it.isNotBlank() }?.let { key -> key to value }
        }.toMap()
    }

    private fun JsonArray.asStringList(): List<String> {
        return mapNotNull { element ->
            element.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }
}
