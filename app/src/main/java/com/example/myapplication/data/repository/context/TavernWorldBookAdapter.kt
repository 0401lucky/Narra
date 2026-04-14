package com.example.myapplication.data.repository.context

import com.example.myapplication.model.ContextDataBundle
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.model.deriveWorldBookBookId
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

class TavernWorldBookAdapter(
    private val gson: Gson = Gson(),
) {
    fun decodeAsBundle(
        rawJson: String,
        fileName: String = "",
    ): ContextDataBundle? {
        val root = runCatching {
            gson.fromJson(rawJson, JsonObject::class.java)
        }.getOrNull() ?: return null
        val entriesElement = root.get("entries") ?: return null
        val entries = buildEntries(
            bookName = resolveBookName(root, fileName),
            entriesElement = entriesElement,
        )
        if (entries.isEmpty()) {
            return null
        }
        return ContextDataBundle(worldBookEntries = entries)
    }

    private fun buildEntries(
        bookName: String,
        entriesElement: JsonElement,
    ): List<WorldBookEntry> {
        val entryObjects = when {
            entriesElement.isJsonObject -> {
                entriesElement.asJsonObject.entrySet()
                    .mapNotNull { (_, value) -> value.asJsonObjectOrNull() }
            }

            entriesElement.isJsonArray -> {
                entriesElement.asJsonArray.mapNotNull { element -> element.asJsonObjectOrNull() }
            }

            else -> emptyList()
        }
        if (entryObjects.isEmpty()) {
            return emptyList()
        }
        val baseTimestamp = System.currentTimeMillis()
        val bookId = deriveWorldBookBookId(bookName)
        return entryObjects.mapIndexedNotNull { index, entry ->
            val keys = parseStringList(entry.get("key")) + parseStringList(entry.get("keys"))
            val secondaryKeys = parseStringList(entry.get("keysecondary")) +
                parseStringList(entry.get("secondary_keys"))
            val title = entry.getString("comment")
                .ifBlank { entry.getString("memo") }
                .ifBlank { entry.getString("name") }
                .ifBlank {
                    keys.firstOrNull()?.trim().orEmpty().ifBlank {
                        if (bookName.isNotBlank()) "$bookName ${index + 1}" else "条目 ${index + 1}"
                    }
                }
            val content = entry.getString("content")
                .ifBlank { entry.getString("entry") }
                .trim()
            if (title.isBlank() && content.isBlank() && keys.isEmpty() && secondaryKeys.isEmpty()) {
                return@mapIndexedNotNull null
            }
            val stableId = UUID.nameUUIDFromBytes(
                buildString {
                    append(bookName)
                    append('|')
                    append(entry.getString("uid"))
                    append('|')
                    append(index)
                    append('|')
                    append(title)
                    append('|')
                    append(content.take(256))
                }.toByteArray(StandardCharsets.UTF_8),
            ).toString()
            val timestamp = baseTimestamp + index
            WorldBookEntry(
                id = stableId,
                bookId = bookId,
                title = title.trim(),
                content = content,
                keywords = keys.distinct(),
                secondaryKeywords = secondaryKeys.distinct(),
                enabled = !entry.getBoolean("disable", defaultValue = false) &&
                    entry.getBoolean("enabled", defaultValue = true),
                alwaysActive = entry.getBoolean("constant", defaultValue = false) ||
                    entry.getBoolean("alwaysActive", defaultValue = false),
                selective = entry.getBoolean("selective", defaultValue = false),
                caseSensitive = entry.getBoolean("caseSensitive", defaultValue = false),
                priority = entry.getInt("order"),
                insertionOrder = entry.getInt("position", index),
                sourceBookName = bookName,
                scopeType = WorldBookScopeType.ATTACHABLE,
                scopeId = "",
                createdAt = timestamp,
                updatedAt = timestamp,
            )
        }
    }

    private fun resolveBookName(
        root: JsonObject,
        fileName: String,
    ): String {
        return root.getString("name")
            .ifBlank { root.getString("title") }
            .ifBlank { root.getString("world_info_name") }
            .ifBlank { root.getString("lorebook") }
            .ifBlank { fileName.substringBeforeLast('.') }
            .ifBlank { "导入世界书" }
            .trim()
    }

    private fun parseStringList(element: JsonElement?): List<String> {
        return when {
            element == null || element.isJsonNull -> emptyList()
            element.isJsonArray -> element.asJsonArray.mapNotNull { item ->
                item.asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            }

            else -> element.asStringOrNull()
                ?.split(",", "，")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        }
    }

    private fun JsonObject.getString(key: String): String {
        return get(key)?.asStringOrNull().orEmpty()
    }

    private fun JsonObject.getInt(
        key: String,
        defaultValue: Int = 0,
    ): Int {
        return get(key)?.asIntOrNull() ?: defaultValue
    }

    private fun JsonObject.getBoolean(
        key: String,
        defaultValue: Boolean = false,
    ): Boolean {
        return get(key)?.asBooleanOrNull() ?: defaultValue
    }

    private fun JsonElement.asStringOrNull(): String? {
        return runCatching { asString }.getOrNull()
    }

    private fun JsonElement.asIntOrNull(): Int? {
        return runCatching { asInt }.getOrNull()
    }

    private fun JsonElement.asBooleanOrNull(): Boolean? {
        return runCatching { asBoolean }.getOrNull()
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return runCatching { asJsonObject }.getOrNull()
    }
}
