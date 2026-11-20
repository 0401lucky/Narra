package com.example.myapplication.system.json

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.CompanionMemoryState
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/** 缺省旧字段与显式损坏的新字段不同；反序列化绝不授予 Room 来源验证凭据。 */
internal class ChatMessageCompatibilityAdapter : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType != ChatMessage::class.java) return null
        val delegate = gson.getDelegateAdapter(this, type)
        val treeAdapter = gson.getAdapter(JsonElement::class.java)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T) = delegate.write(out, value)

            @Suppress("UNCHECKED_CAST")
            override fun read(input: JsonReader): T {
                val tree = treeAdapter.read(input)
                val value = delegate.fromJsonTree(tree)
                if (value !is ChatMessage || !tree.isJsonObject) return value
                val obj = tree.asJsonObject
                val legacy = !obj.has("companionTurnId") && !obj.has("companionMemoryState")
                val linkNode = obj.get("companionTurnId")
                val stateNode = obj.get("companionMemoryState")
                val link =
                    linkNode?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                val state =
                    stateNode?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                        ?.let { raw -> CompanionMemoryState.entries.firstOrNull { it.name == raw || it.storageValue == raw } }
                val valid = link != null && state != null &&
                        ((link.isBlank() && state == CompanionMemoryState.LEGACY) ||
                                (link.isNotBlank() && state != CompanionMemoryState.LEGACY))
                return value.copy(
                    companionTurnId = link.orEmpty(),
                    companionMemoryState = if (legacy) CompanionMemoryState.LEGACY
                    else if (valid) state!! else CompanionMemoryState.UNKNOWN,
                    companionSourceProof = null,
                ) as T
            }
        }
    }
}
