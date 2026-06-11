package com.example.myapplication.data.repository.ai.tooling

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonParser

internal object ToolArgumentSupport {
    fun stringArgument(
        invocation: ToolInvocation,
        name: String,
    ): String? {
        invocation.argumentsMap[name]
            ?.takeIf { it is String }
            ?.toString()
            ?.let { return it }
        val element = invocation.jsonObject()?.get(name) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive || !element.asJsonPrimitive.isStringValue()) {
            return null
        }
        return runCatching { element.asString }.getOrNull()
    }

    fun intArgument(
        invocation: ToolInvocation,
        name: String,
    ): Int? {
        invocation.argumentsMap[name]
            ?.let(::toIntOrNull)
            ?.let { return it }
        val element = invocation.jsonObject()?.get(name) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) {
            return null
        }
        return runCatching { element.asInt }.getOrNull()
            ?: runCatching { element.asString.toIntOrNull() }.getOrNull()
    }

    fun hasArgument(
        invocation: ToolInvocation,
        name: String,
    ): Boolean {
        if (invocation.argumentsMap.containsKey(name)) {
            return true
        }
        return invocation.jsonObject()?.has(name) == true
    }

    fun argumentNames(invocation: ToolInvocation): Set<String> {
        val mapNames = invocation.argumentsMap.keys
        val jsonNames = invocation.jsonObject()?.keySet().orEmpty()
        return mapNames + jsonNames
    }

    private fun ToolInvocation.jsonObject(): JsonObject? {
        val normalized = argumentsJson.orEmpty().trim()
        if (normalized.isBlank()) {
            return null
        }
        return runCatching {
            JsonParser.parseString(normalized)
                .takeIf { it.isJsonObject }
                ?.asJsonObject
        }.getOrNull()
    }

    private fun toIntOrNull(value: Any): Int? {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> value.toString().trim().toIntOrNull()
        }
    }

    private fun JsonPrimitive.isStringValue(): Boolean {
        return runCatching { isString }.getOrDefault(false)
    }
}
