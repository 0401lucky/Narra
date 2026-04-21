package com.example.myapplication.data.repository.context

import com.example.myapplication.model.ContextDataBundle
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.model.deriveWorldBookBookId
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.logging.logFailure
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

class TavernWorldBookAdapter(
    private val gson: Gson = AppJson.gson,
) {
    fun decodeAsBundle(
        rawJson: String,
        fileName: String = "",
    ): ContextDataBundle? {
        val root = runCatching {
            gson.fromJson(rawJson, JsonObject::class.java)
        }.logFailure("TavernWBAdapter") { "import parse failed, raw.len=${rawJson.length}" }
            .getOrNull() ?: return null
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
            val stableId = deriveStableId(bookName, entry, index, title, content)
            val extrasPayload = buildExtrasPayload(entry)
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
                extrasJson = extrasPayload,
            )
        }
    }

    private fun deriveStableId(
        bookName: String,
        entry: JsonObject,
        index: Int,
        title: String,
        content: String,
    ): String {
        val payload = buildString {
            append(bookName)
            append('|')
            append(entry.getString("uid"))
            append('|')
            append(index)
            append('|')
            append(title)
            append('|')
            append(content.take(256))
        }
        return UUID.nameUUIDFromBytes(payload.toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private fun buildExtrasPayload(entry: JsonObject): String {
        val extras = JsonObject()
        entry.entrySet().forEach { (key, value) ->
            if (key !in KNOWN_FIELD_KEYS) {
                extras.add(key, value)
            }
        }
        return if (extras.size() > 0) gson.toJson(extras) else "{}"
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

    private companion object {
        /**
         * 本 Adapter 已识别并映射到 [WorldBookEntry] 的 Tavern 字段集合。
         * 其余未识别的键会原样写入 [WorldBookEntry.extrasJson]，
         * 以便未来导出回 Tavern 时无损还原（见 T15-C1）。
         */
        val KNOWN_FIELD_KEYS: Set<String> = setOf(
            "key",
            "keys",
            "keysecondary",
            "secondary_keys",
            "comment",
            "memo",
            "name",
            "content",
            "entry",
            "uid",
            "disable",
            "enabled",
            "constant",
            "alwaysActive",
            "selective",
            "caseSensitive",
            "order",
            "position",
        )
    }
}
