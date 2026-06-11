package com.example.myapplication.model

import com.example.myapplication.system.json.AppJson
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

const val CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES: Int = 500
const val CONTEXT_IMPORT_MAX_WORLD_BOOK_TITLE_CHARS: Int = 160
const val CONTEXT_IMPORT_MAX_WORLD_BOOK_CONTENT_CHARS: Int = 20_000
const val CONTEXT_IMPORT_MAX_WORLD_BOOK_SOURCE_NAME_CHARS: Int = 160
const val CONTEXT_IMPORT_MAX_WORLD_BOOK_EXTRAS_CHARS: Int = 16 * 1024
const val CONTEXT_IMPORT_MAX_WORLD_BOOK_EXTRAS_FIELD_CHARS: Int = 4 * 1024

const val CONTEXT_IMPORT_MAX_ASSISTANTS: Int = 100
const val CONTEXT_IMPORT_MAX_ASSISTANT_NAME_CHARS: Int = 120
const val CONTEXT_IMPORT_MAX_ASSISTANT_ICON_CHARS: Int = 64
const val CONTEXT_IMPORT_MAX_ASSISTANT_URI_CHARS: Int = 1_024
const val CONTEXT_IMPORT_MAX_ASSISTANT_TEXT_CHARS: Int = 20_000
const val CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUES: Int = 24
const val CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUE_CHARS: Int = 4_000
const val CONTEXT_IMPORT_MAX_ASSISTANT_TAGS: Int = 32
const val CONTEXT_IMPORT_MAX_ASSISTANT_TAG_CHARS: Int = 64
const val CONTEXT_IMPORT_MAX_ASSISTANT_LINKED_WORLD_BOOK_IDS: Int = CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES
const val CONTEXT_IMPORT_MAX_ASSISTANT_LINKED_WORLD_BOOK_ID_CHARS: Int = 160
const val CONTEXT_IMPORT_MAX_ASSISTANT_WORLD_BOOK_MAX_ENTRIES: Int = 64
const val CONTEXT_IMPORT_MAX_ASSISTANT_WORLD_BOOK_SCAN_DEPTH: Int = 8

fun ContextDataBundle.normalizedForContextImport(
    gson: Gson = AppJson.gson,
): ContextDataBundle {
    return copy(
        assistants = assistants
            .take(CONTEXT_IMPORT_MAX_ASSISTANTS)
            .map { assistant -> assistant.normalizedForContextImport() },
        worldBookEntries = worldBookEntries.normalizedWorldBookEntriesForContextImport(gson),
    )
}

fun List<WorldBookEntry>.normalizedWorldBookEntriesForContextImport(
    gson: Gson = AppJson.gson,
): List<WorldBookEntry> {
    return take(CONTEXT_IMPORT_MAX_WORLD_BOOK_ENTRIES)
        .map { entry -> entry.normalizedForContextImport(gson) }
}

fun Assistant.normalizedForContextImport(): Assistant {
    return copy(
        name = name.trim().limitForContextImport(CONTEXT_IMPORT_MAX_ASSISTANT_NAME_CHARS),
        iconName = iconName.trim().limitForContextImport(CONTEXT_IMPORT_MAX_ASSISTANT_ICON_CHARS),
        avatarUri = avatarUri.trim().limitForContextImport(CONTEXT_IMPORT_MAX_ASSISTANT_URI_CHARS),
        description = description.limitForContextImport(CONTEXT_IMPORT_MAX_ASSISTANT_TEXT_CHARS),
        systemPrompt = systemPrompt.limitForContextImport(CONTEXT_IMPORT_MAX_ASSISTANT_TEXT_CHARS),
        scenario = scenario.limitForContextImport(CONTEXT_IMPORT_MAX_ASSISTANT_TEXT_CHARS),
        greeting = greeting.limitForContextImport(CONTEXT_IMPORT_MAX_ASSISTANT_TEXT_CHARS),
        exampleDialogues = normalizeContextImportStringList(
            values = exampleDialogues,
            maxItems = CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUES,
            maxChars = CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUE_CHARS,
        ),
        creatorNotes = creatorNotes.limitForContextImport(CONTEXT_IMPORT_MAX_ASSISTANT_TEXT_CHARS),
        linkedWorldBookIds = normalizeContextImportStringList(
            values = linkedWorldBookIds,
            maxItems = CONTEXT_IMPORT_MAX_ASSISTANT_LINKED_WORLD_BOOK_IDS,
            maxChars = CONTEXT_IMPORT_MAX_ASSISTANT_LINKED_WORLD_BOOK_ID_CHARS,
        ),
        linkedWorldBookBookIds = normalizeContextImportStringList(
            values = linkedWorldBookBookIds,
            maxItems = CONTEXT_IMPORT_MAX_ASSISTANT_LINKED_WORLD_BOOK_IDS,
            maxChars = CONTEXT_IMPORT_MAX_ASSISTANT_LINKED_WORLD_BOOK_ID_CHARS,
        ),
        worldBookMaxEntries = worldBookMaxEntries.coerceIn(
            0,
            CONTEXT_IMPORT_MAX_ASSISTANT_WORLD_BOOK_MAX_ENTRIES,
        ),
        worldBookScanDepth = worldBookScanDepth.coerceIn(
            0,
            CONTEXT_IMPORT_MAX_ASSISTANT_WORLD_BOOK_SCAN_DEPTH,
        ),
        tags = normalizeContextImportStringList(
            values = tags,
            maxItems = CONTEXT_IMPORT_MAX_ASSISTANT_TAGS,
            maxChars = CONTEXT_IMPORT_MAX_ASSISTANT_TAG_CHARS,
        ),
    )
}

fun WorldBookEntry.normalizedForContextImport(
    gson: Gson = AppJson.gson,
): WorldBookEntry {
    return copy(
        title = title.limitForContextImport(CONTEXT_IMPORT_MAX_WORLD_BOOK_TITLE_CHARS),
        content = content.limitForContextImport(CONTEXT_IMPORT_MAX_WORLD_BOOK_CONTENT_CHARS),
        keywords = normalizeWorldBookKeywords(
            values = keywords,
            matchMode = matchMode,
            maxItems = WORLD_BOOK_MAX_PRIMARY_KEYWORDS,
        ),
        aliases = normalizeWorldBookKeywords(
            values = aliases,
            matchMode = matchMode,
            maxItems = WORLD_BOOK_MAX_PRIMARY_KEYWORDS,
        ),
        secondaryKeywords = normalizeWorldBookKeywords(
            values = secondaryKeywords,
            matchMode = matchMode,
            maxItems = WORLD_BOOK_MAX_SECONDARY_KEYWORDS,
        ),
        probability = probability.coerceIn(0, 100),
        sourceBookName = normalizeContextImportSourceBookName(sourceBookName),
        extrasJson = normalizeContextImportExtrasJson(extrasJson, gson),
    )
}

fun normalizeContextImportSourceBookName(value: String): String {
    return value.trim().limitForContextImport(CONTEXT_IMPORT_MAX_WORLD_BOOK_SOURCE_NAME_CHARS)
}

fun normalizeContextImportStringList(
    values: List<String>,
    maxItems: Int,
    maxChars: Int,
): List<String> {
    val normalized = linkedSetOf<String>()
    for (value in values) {
        if (normalized.size >= maxItems) break
        val item = value.trim()
            .takeIf { it.isNotEmpty() }
            ?.limitForContextImport(maxChars)
            ?: continue
        normalized += item
    }
    return normalized.toList()
}

fun normalizeContextImportExtrasJson(
    rawJson: String,
    gson: Gson = AppJson.gson,
): String {
    val trimmed = rawJson.trim()
    if (trimmed.isBlank()) return "{}"
    val extras = runCatching {
        JsonParser.parseString(trimmed).asJsonObject
    }.getOrNull() ?: return "{}"
    return normalizeContextImportExtrasObject(extras, gson)
}

fun normalizeContextImportExtrasObject(
    extras: JsonObject,
    gson: Gson = AppJson.gson,
): String {
    if (extras.size() <= 0) return "{}"
    val fullJson = gson.toJson(extras)
    if (fullJson.length <= CONTEXT_IMPORT_MAX_WORLD_BOOK_EXTRAS_CHARS) {
        return fullJson
    }

    val kept = JsonObject()
    extras.entrySet().forEach { (key, value) ->
        val singleField = JsonObject().apply {
            add(key, value)
        }
        if (gson.toJson(singleField).length > CONTEXT_IMPORT_MAX_WORLD_BOOK_EXTRAS_FIELD_CHARS) {
            return@forEach
        }
        kept.add(key, value)
        if (gson.toJson(kept).length > CONTEXT_IMPORT_MAX_WORLD_BOOK_EXTRAS_CHARS) {
            kept.remove(key)
        }
    }
    return kept.takeIf { it.size() > 0 }?.let(gson::toJson) ?: "{}"
}

fun String.limitForContextImport(maxChars: Int): String {
    return if (length <= maxChars) this else take(maxChars)
}
