package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.aiPhotoMessagePart
import com.example.myapplication.model.emojiMessagePart
import com.example.myapplication.model.isActionPart
import com.example.myapplication.model.isSpecialPlayPart
import com.example.myapplication.model.locationMessagePart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.pokeMessagePart
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.toActionCopyText
import com.example.myapplication.model.toSpecialPlayCopyText
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.model.videoCallMessagePart
import com.example.myapplication.model.voiceMessageActionPart
import com.example.myapplication.model.TransferDirection
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal data class OnlineActionProtocolParseResult(
    val parts: List<ChatMessagePart>,
    val directives: List<OnlineActionDirective> = emptyList(),
)

internal sealed class OnlineActionDirective {
    data object RecallPreviousAssistant : OnlineActionDirective()
}

internal object OnlineActionProtocolParser {
    fun parse(
        rawContent: String,
        characterName: String,
    ): OnlineActionProtocolParseResult? {
        val trimmed = rawContent.trim()
        if (!trimmed.startsWith("[")) {
            return null
        }
        val parsedRoot = runCatching { JsonParser.parseString(trimmed) }.getOrNull() ?: return null
        if (!parsedRoot.isJsonArray) {
            return null
        }
        return parseArray(
            array = parsedRoot.asJsonArray,
            characterName = characterName,
        )
    }

    fun extractStreamingPreview(rawContent: String): String {
        val result = parse(rawContent = rawContent, characterName = "角色") ?: return ""
        return result.parts.joinToString(separator = "\n") { part ->
            when {
                part.text.isNotBlank() -> part.text.trim()
                else -> part.toActionOrSpecialCopyText()
            }
        }.trim()
    }

    private fun parseArray(
        array: JsonArray,
        characterName: String,
    ): OnlineActionProtocolParseResult {
        val parts = mutableListOf<ChatMessagePart>()
        val directives = mutableListOf<OnlineActionDirective>()
        array.forEach { element ->
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    val content = element.asString.trim()
                    if (content.isNotBlank()) {
                        parts += textMessagePart(content)
                    }
                }

                element.isJsonObject -> {
                    parseObject(
                        item = element.asJsonObject,
                        characterName = characterName,
                        parts = parts,
                        directives = directives,
                    )
                }
            }
        }
        return OnlineActionProtocolParseResult(
            parts = normalizeChatMessageParts(parts),
            directives = directives.distinct(),
        )
    }

    private fun parseObject(
        item: JsonObject,
        characterName: String,
        parts: MutableList<ChatMessagePart>,
        directives: MutableList<OnlineActionDirective>,
    ) {
        when (item.stringValue("type").lowercase()) {
            "reply_to" -> {
                val content = item.stringValue("content")
                if (content.isNotBlank()) {
                    parts += textMessagePart(
                        text = content,
                        replyToMessageId = item.stringValue("message_id"),
                        replyToPreview = item.stringValue("reply_preview")
                            .ifBlank { item.stringValue("preview") },
                        replyToSpeakerName = item.stringValue("reply_speaker")
                            .ifBlank { item.stringValue("speaker_name") },
                    )
                }
            }

            "recall" -> {
                if (item.stringValue("target").lowercase() == "previous") {
                    directives += OnlineActionDirective.RecallPreviousAssistant
                }
            }

            "emoji" -> {
                item.stringValue("description")
                    .takeIf { it.isNotBlank() }
                    ?.let { description ->
                        parts += emojiMessagePart(description)
                    }
            }

            "voice_message" -> {
                item.stringValue("content")
                    .takeIf { it.isNotBlank() }
                    ?.let { content ->
                        parts += voiceMessageActionPart(content)
                    }
            }

            "ai_photo" -> {
                item.stringValue("description")
                    .takeIf { it.isNotBlank() }
                    ?.let { description ->
                        parts += aiPhotoMessagePart(description)
                    }
            }

            "location" -> {
                val locationName = item.stringValue("locationName")
                    .ifBlank { item.stringValue("name") }
                    .ifBlank { item.stringValue("location_name") }
                if (locationName.isNotBlank()) {
                    parts += locationMessagePart(
                        locationName = locationName,
                        coordinates = item.stringValue("coordinates"),
                        address = item.stringValue("address"),
                    )
                }
            }

            "transfer" -> {
                val amount = item.numericStringValue("amount")
                if (amount.isNotBlank()) {
                    parts += transferMessagePart(
                        direction = TransferDirection.ASSISTANT_TO_USER,
                        counterparty = characterName,
                        amount = amount,
                        note = item.stringValue("note"),
                    )
                }
            }

            "poke" -> {
                parts += pokeMessagePart()
            }

            "video_call" -> {
                item.stringValue("reason")
                    .takeIf { it.isNotBlank() }
                    ?.let { reason ->
                        parts += videoCallMessagePart(reason)
                    }
            }
        }
    }

    private fun JsonObject.stringValue(key: String): String {
        return runCatching { get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asString.orEmpty() }
            .getOrDefault("")
            .trim()
    }

    private fun JsonObject.numericStringValue(key: String): String {
        val element = get(key) ?: return ""
        return when {
            element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> element.asNumber.toString()
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString.trim()
            else -> ""
        }
    }

    private fun ChatMessagePart.toActionOrSpecialCopyText(): String {
        return when {
            isActionPart() -> toActionCopyText()
            isSpecialPlayPart() -> toSpecialPlayCopyText()
            else -> text.trim()
        }
    }
}
