package com.example.myapplication.data.repository.context

import com.example.myapplication.model.ContextDataBundle
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_WORLD_BOOK_CONTENT_CHARS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_WORLD_BOOK_TITLE_CHARS
import com.example.myapplication.model.DEFAULT_WORLD_BOOK_PROBABILITY
import com.example.myapplication.model.WORLD_BOOK_MAX_PRIMARY_KEYWORDS
import com.example.myapplication.model.WORLD_BOOK_MAX_SECONDARY_KEYWORDS
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookMatchMode
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.model.deriveWorldBookBookId
import com.example.myapplication.model.limitForContextImport
import com.example.myapplication.model.normalizeContextImportExtrasObject
import com.example.myapplication.model.normalizeContextImportSourceBookName
import com.example.myapplication.model.normalizeContextImportStringList
import com.example.myapplication.model.normalizeWorldBookKeywords
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
                entriesElement.asJsonObject.entrySet().asSequence()
                    .mapNotNull { (_, value) -> value.asJsonObjectOrNull() }
                    .take(CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES)
                    .toList()
            }

            entriesElement.isJsonArray -> {
                entriesElement.asJsonArray.asSequence()
                    .mapNotNull { element -> element.asJsonObjectOrNull() }
                    .take(CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES)
                    .toList()
            }

            else -> emptyList()
        }
        if (entryObjects.isEmpty()) {
            return emptyList()
        }
        val baseTimestamp = System.currentTimeMillis()
        val normalizedBookName = normalizeContextImportSourceBookName(bookName)
        val bookId = deriveWorldBookBookId(normalizedBookName)
        return entryObjects.mapIndexedNotNull { index, entry ->
            val keys = normalizeWorldBookKeywords(
                values = parseStringList(entry.get("key")) + parseStringList(entry.get("keys")),
                matchMode = WorldBookMatchMode.WORD_CJK,
                maxItems = WORLD_BOOK_MAX_PRIMARY_KEYWORDS,
            )
            val secondaryKeys = normalizeWorldBookKeywords(
                values = parseStringList(entry.get("keysecondary")) +
                    parseStringList(entry.get("secondary_keys")),
                matchMode = WorldBookMatchMode.WORD_CJK,
                maxItems = WORLD_BOOK_MAX_SECONDARY_KEYWORDS,
            )
            val title = entry.getString("comment")
                .ifBlank { entry.getString("memo") }
                .ifBlank { entry.getString("name") }
                .ifBlank {
                    keys.firstOrNull()?.trim().orEmpty().ifBlank {
                        if (normalizedBookName.isNotBlank()) "$normalizedBookName ${index + 1}" else "条目 ${index + 1}"
                    }
                }
                .trim()
                .limitForContextImport(CONTEXT_IMPORT_MAX_WORLD_BOOK_TITLE_CHARS)
            val content = entry.getString("content")
                .ifBlank { entry.getString("entry") }
                .trim()
                .limitForContextImport(CONTEXT_IMPORT_MAX_WORLD_BOOK_CONTENT_CHARS)
            if (title.isBlank() && content.isBlank() && keys.isEmpty() && secondaryKeys.isEmpty()) {
                return@mapIndexedNotNull null
            }
            val stableId = deriveStableId(normalizedBookName, entry, index, title, content)
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
                probability = entry.getInt("probability", DEFAULT_WORLD_BOOK_PROBABILITY)
                    .coerceIn(0, 100),
                sourceBookName = normalizedBookName,
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
        val uidCandidate = entry.getString("uid").trim()
        val payload = if (uidCandidate.isNotEmpty()) {
            // uid 是 Tavern 条目的稳定身份：同 uid 的条目即便正文被微改也视为同一条，
            // 避免二次导入同一本书时增生孪生条目。
            "$bookName|$uidCandidate"
        } else {
            buildString {
                append(bookName)
                append('|')
                append(index)
                append('|')
                append(title)
                append('|')
                append(content.take(256))
            }
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
        return normalizeContextImportExtrasObject(extras, gson)
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
            .let(::normalizeContextImportSourceBookName)
    }

    private fun parseStringList(element: JsonElement?): List<String> {
        val rawValues = when {
            element == null || element.isJsonNull -> emptyList()
            element.isJsonArray -> buildList {
                for (item in element.asJsonArray) {
                    if (size >= WORLD_BOOK_MAX_PRIMARY_KEYWORDS) break
                    item.asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
                }
            }

            else -> element.asStringOrNull()
                ?.split(",", "，")
                ?.mapNotNull { it.trim().takeIf { value -> value.isNotEmpty() } }
                .orEmpty()
        }
        return normalizeContextImportStringList(
            values = rawValues,
            maxItems = WORLD_BOOK_MAX_PRIMARY_KEYWORDS,
            maxChars = CONTEXT_IMPORT_MAX_WORLD_BOOK_TITLE_CHARS,
        )
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
            "probability",
        )
    }
}
