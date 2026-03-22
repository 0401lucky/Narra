package com.example.myapplication.data.repository.context

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ContextDataBundle
import com.example.myapplication.model.DEFAULT_WORLD_BOOK_MAX_ENTRIES
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import java.util.UUID

class TavernCharacterAdapter(
    private val gson: Gson = Gson(),
) {
    fun decodeAsBundle(rawJson: String): ContextDataBundle? {
        val root = runCatching {
            gson.fromJson(rawJson, JsonObject::class.java)
        }.getOrNull() ?: return null
        val character = extractCharacterObject(root) ?: return null
        val assistant = buildAssistant(
            root = root,
            character = character,
            rawJson = rawJson,
        ) ?: return null
        val worldBookEntries = buildWorldBookEntries(
            root = root,
            character = character,
            assistant = assistant,
        )
        return ContextDataBundle(
            assistants = listOf(assistant),
            worldBookEntries = worldBookEntries,
        )
    }

    private fun extractCharacterObject(root: JsonObject): JsonObject? {
        val candidate = root.getAsJsonObject("data") ?: root
        val hasCharacterFields = listOf(
            "name",
            "description",
            "personality",
            "scenario",
            "first_mes",
            "mes_example",
        ).any { key ->
            candidate.has(key) && candidate.get(key)?.isJsonNull == false
        }
        return if (hasCharacterFields) candidate else null
    }

    private fun buildAssistant(
        root: JsonObject,
        character: JsonObject,
        rawJson: String,
    ): Assistant? {
        val name = character.getString("name").ifBlank { return null }
        val description = character.getString("description")
        val personality = character.getString("personality")
        val systemPrompt = buildSystemPrompt(
            explicitSystemPrompt = character.getString("system_prompt"),
            description = description,
            personality = personality,
        )
        val scenario = character.getString("scenario")
        val greeting = character.getString("first_mes")
        val exampleDialogues = parseExampleDialogues(character.getString("mes_example"))
        val creatorNotes = character.getString("creator_notes").ifBlank {
            character.getString("creatorcomment")
        }
        val tags = parseTags(character.get("tags"))
        val worldBookEntries = previewWorldBookEntries(
            root = root,
            character = character,
            characterName = name,
        )
        val stableId = UUID.nameUUIDFromBytes(
            buildString {
                append(name.trim())
                append('|')
                append(description.trim())
                append('|')
                append(personality.trim())
                append('|')
                append(scenario.trim())
                append('|')
                append(rawJson.trim().take(512))
            }.toByteArray(StandardCharsets.UTF_8),
        ).toString()

        return Assistant(
            id = stableId,
            name = name.trim(),
            description = description.ifBlank { personality }.trim(),
            systemPrompt = systemPrompt,
            scenario = scenario.trim(),
            greeting = greeting.trim(),
            exampleDialogues = exampleDialogues,
            creatorNotes = creatorNotes.trim(),
            tags = tags,
            memoryEnabled = true,
            worldBookMaxEntries = maxOf(
                DEFAULT_WORLD_BOOK_MAX_ENTRIES,
                worldBookEntries.count { it.enabled }.coerceAtMost(24),
            ),
        )
    }

    private fun buildWorldBookEntries(
        root: JsonObject,
        character: JsonObject,
        assistant: Assistant,
    ): List<WorldBookEntry> {
        val characterBook = character.getObject("character_book")
            ?: root.getObject("character_book")
            ?: return emptyList()
        val bookName = characterBook.getString("name").trim()
        val bookEntries = characterBook.getArray("entries") ?: return emptyList()
        val baseTimestamp = System.currentTimeMillis()
        return bookEntries.mapIndexedNotNull { index, element ->
            val entry = element.asJsonObjectOrNull() ?: return@mapIndexedNotNull null
            val keys = parseStringList(entry.get("keys"))
            val secondaryKeys = parseStringList(entry.get("secondary_keys"))
            val title = entry.getString("name").ifBlank {
                deriveWorldBookTitle(
                    index = index,
                    bookName = bookName,
                    keys = keys,
                )
            }
            val content = entry.getString("content").trim()
            if (title.isBlank() && content.isBlank() && keys.isEmpty() && secondaryKeys.isEmpty()) {
                return@mapIndexedNotNull null
            }
            val stableEntryId = UUID.nameUUIDFromBytes(
                buildString {
                    append(assistant.id)
                    append('|')
                    append(bookName)
                    append('|')
                    append(entry.getString("id"))
                    append('|')
                    append(index)
                    append('|')
                    append(title)
                    append('|')
                    append(content.take(256))
                }.toByteArray(StandardCharsets.UTF_8),
            ).toString()
            val entryTimestamp = baseTimestamp + index
            WorldBookEntry(
                id = stableEntryId,
                title = title,
                content = content,
                keywords = keys,
                secondaryKeywords = secondaryKeys,
                enabled = entry.getBoolean("enabled", defaultValue = true),
                alwaysActive = entry.getBoolean("constant", defaultValue = false),
                selective = entry.getBoolean("selective", defaultValue = false),
                caseSensitive = entry.getBoolean("case_sensitive", defaultValue = false),
                priority = entry.getInt("priority"),
                insertionOrder = entry.getInt("insertion_order", index),
                sourceBookName = bookName,
                scopeType = WorldBookScopeType.ASSISTANT,
                scopeId = assistant.id,
                createdAt = entryTimestamp,
                updatedAt = entryTimestamp,
            )
        }
    }

    private fun buildSystemPrompt(
        explicitSystemPrompt: String,
        description: String,
        personality: String,
    ): String {
        val sections = buildList {
            explicitSystemPrompt.trim().takeIf { it.isNotEmpty() }?.let(::add)
            description.trim().takeIf { it.isNotEmpty() }?.let {
                add("【角色描述】\n$it")
            }
            personality.trim().takeIf { it.isNotEmpty() }?.let {
                add("【性格特点】\n$it")
            }
        }
        return sections.joinToString(separator = "\n\n").trim()
    }

    private fun parseExampleDialogues(rawValue: String): List<String> {
        if (rawValue.isBlank()) return emptyList()
        return rawValue
            .replace("<START>", "\n\n")
            .replace("\r\n", "\n")
            .split(Regex("""\n\s*\n+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseTags(element: JsonElement?): List<String> {
        return parseStringList(element)
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

    private fun previewWorldBookEntries(
        root: JsonObject,
        character: JsonObject,
        characterName: String,
    ): List<PreviewWorldBookEntry> {
        val characterBook = character.getObject("character_book")
            ?: root.getObject("character_book")
            ?: return emptyList()
        val entries = characterBook.getArray("entries") ?: return emptyList()
        val bookName = characterBook.getString("name").trim()
        return entries.mapIndexedNotNull { index, element ->
            val entry = element.asJsonObjectOrNull() ?: return@mapIndexedNotNull null
            val title = entry.getString("name").ifBlank {
                deriveWorldBookTitle(
                    index = index,
                    bookName = bookName.ifBlank { characterName },
                    keys = parseStringList(entry.get("keys")),
                )
            }
            PreviewWorldBookEntry(
                title = title,
                enabled = entry.getBoolean("enabled", defaultValue = true),
            )
        }
    }

    private fun deriveWorldBookTitle(
        index: Int,
        bookName: String,
        keys: List<String>,
    ): String {
        val firstKey = keys.firstOrNull()?.trim().orEmpty()
        return when {
            firstKey.isNotEmpty() -> firstKey
            bookName.isNotBlank() -> "$bookName ${index + 1}"
            else -> "条目 ${index + 1}"
        }
    }

    private fun JsonObject.getString(key: String): String {
        return get(key)?.asStringOrNull().orEmpty()
    }

    private fun JsonObject.getObject(key: String): JsonObject? {
        return get(key)?.asJsonObjectOrNull()
    }

    private fun JsonObject.getArray(key: String): JsonArray? {
        return get(key)?.asJsonArrayOrNull()
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

    private fun JsonElement.asJsonArrayOrNull(): JsonArray? {
        return runCatching { asJsonArray }.getOrNull()
    }

    private data class PreviewWorldBookEntry(
        val title: String,
        val enabled: Boolean,
    )
}
