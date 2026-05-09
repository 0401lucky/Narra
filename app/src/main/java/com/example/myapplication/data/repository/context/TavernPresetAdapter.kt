package com.example.myapplication.data.repository.context

import com.example.myapplication.model.ContextDataBundle
import com.example.myapplication.model.Preset
import com.example.myapplication.model.PresetCompatMetadata
import com.example.myapplication.model.PresetPromptEntry
import com.example.myapplication.model.PresetPromptEntryKind
import com.example.myapplication.model.PresetPromptInjectionPosition
import com.example.myapplication.model.PresetPromptRole
import com.example.myapplication.model.PresetSamplerConfig
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.logging.logFailure
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import java.util.UUID

class TavernPresetAdapter(
    private val gson: Gson = AppJson.gson,
) {
    fun decodeAsBundle(
        rawJson: String,
        fileName: String = "",
    ): ContextDataBundle? {
        val root = runCatching {
            gson.fromJson(rawJson, JsonObject::class.java)
        }.logFailure("TavernPresetAdapter") { "import parse failed, raw.len=${rawJson.length}" }
            .getOrNull() ?: return null
        val promptObjects = root.getArray("prompts")
            ?.mapNotNull { it.asJsonObjectOrNull() }
            .orEmpty()
        if (promptObjects.isEmpty()) {
            return null
        }

        val promptOrder = resolvePromptOrder(root)
        val orderedPromptRefs = promptOrder.order
        val promptsByIdentifier = promptObjects
            .mapNotNull { prompt ->
                val identifier = prompt.getString("identifier").trim()
                identifier.takeIf { it.isNotBlank() }?.let { it to prompt }
            }
            .toMap()
        val orderedEntries = buildList {
            orderedPromptRefs.forEachIndexed { index, reference ->
                val prompt = promptsByIdentifier[reference.identifier] ?: return@forEachIndexed
                add(toPresetEntry(prompt, reference.enabled, index))
            }
        }
        if (orderedEntries.isEmpty()) {
            return null
        }

        val presetName = root.getString("name")
            .ifBlank { root.getString("display_name") }
            .ifBlank { fileName.substringBeforeLast('.') }
            .ifBlank { "SillyTavern 预设" }
            .trim()
        val now = System.currentTimeMillis()
        val preset = Preset(
            id = deriveStablePresetId(presetName, rawJson),
            name = presetName,
            description = "从 SillyTavern Chat Completion 预设导入。",
            sampler = toSampler(root),
            entries = orderedEntries,
            compatMetadata = PresetCompatMetadata(
                source = SOURCE_SILLYTAVERN_CHAT,
                sourceName = presetName,
                rootExtrasJson = buildExtrasPayload(root, ROOT_KNOWN_KEYS),
                promptOrderCharacterId = promptOrder.characterId,
                promptOrderExtrasJson = promptOrder.extrasJson,
            ),
            builtIn = false,
            userModified = true,
            createdAt = now,
            updatedAt = now,
        ).normalized()

        return ContextDataBundle(presets = listOf(preset))
    }

    fun encodePreset(preset: Preset): String {
        val normalized = preset.normalized()
        val root = parseObject(normalized.compatMetadata.rootExtrasJson) ?: JsonObject()
        root.addProperty("chat_completion_source", root.getString("chat_completion_source").ifBlank { "openai" })
        root.addProperty("name", normalized.name.ifBlank { "Narra Preset" })
        putSampler(root, normalized.sampler)

        val orderedEntries = normalized.entries.sortedBy(PresetPromptEntry::order)
        val prompts = JsonArray()
        val order = JsonArray()
        orderedEntries.forEach { entry ->
            val identifier = entry.sourceIdentifier.ifBlank { entry.id }
            prompts.add(toTavernPrompt(entry, identifier))
            order.add(
                JsonObject().apply {
                    addProperty("identifier", identifier)
                    addProperty("enabled", entry.enabled)
                },
            )
        }
        root.add("prompts", prompts)
        root.add(
            "prompt_order",
            JsonArray().apply {
                add(
                    (parseObject(normalized.compatMetadata.promptOrderExtrasJson) ?: JsonObject()).apply {
                        addProperty(
                            "character_id",
                            normalized.compatMetadata.promptOrderCharacterId.ifBlank { DEFAULT_PROMPT_ORDER_CHARACTER_ID },
                        )
                        add("order", order)
                    },
                )
            },
        )
        return gson.toJson(root)
    }

    private fun toPresetEntry(
        prompt: JsonObject,
        enabledFromOrder: Boolean,
        index: Int,
    ): PresetPromptEntry {
        val identifier = prompt.getString("identifier").trim().ifBlank {
            UUID.randomUUID().toString()
        }
        val kind = identifier.toPresetKind()
        val marker = prompt.getBoolean("marker", false)
        val content = prompt.getString("content").ifBlank {
            if (marker) kind.defaultMarkerContent(identifier) else ""
        }
        return PresetPromptEntry(
            id = UUID.nameUUIDFromBytes("tavern-prompt|$identifier|$index".toByteArray(StandardCharsets.UTF_8)).toString(),
            title = prompt.getString("name").ifBlank { identifier },
            role = PresetPromptRole.fromStorageValue(prompt.getString("role").ifBlank { "system" }),
            kind = kind,
            content = content,
            enabled = enabledFromOrder,
            order = index * 10,
            locked = false,
            sourceIdentifier = identifier,
            marker = marker,
            injectionPosition = PresetPromptInjectionPosition.fromTavernValue(prompt.getIntOrNull("injection_position")),
            injectionDepth = prompt.getIntOrNull("injection_depth"),
            injectionOrder = prompt.getIntOrNull("injection_order"),
            injectionTriggers = parseStringList(prompt.get("injection_trigger")),
            extrasJson = buildExtrasPayload(prompt, PROMPT_KNOWN_KEYS),
        ).normalized(index * 10)
    }

    private fun toTavernPrompt(
        entry: PresetPromptEntry,
        identifier: String,
    ): JsonObject {
        val prompt = parseObject(entry.extrasJson) ?: JsonObject()
        prompt.addProperty("identifier", identifier)
        prompt.addProperty("name", entry.title.ifBlank { entry.kind.label })
        prompt.addProperty("role", entry.role.storageValue)
        prompt.addProperty("content", exportPromptContent(entry, identifier))
        prompt.addProperty("system_prompt", identifier in SYSTEM_PROMPT_IDENTIFIERS)
        prompt.addProperty("marker", entry.marker)
        prompt.addProperty("injection_position", entry.injectionPosition.tavernValue)
        entry.injectionDepth?.let { prompt.addProperty("injection_depth", it) }
        entry.injectionOrder?.let { prompt.addProperty("injection_order", it) }
        if (entry.injectionTriggers.isNotEmpty()) {
            prompt.add(
                "injection_trigger",
                JsonArray().apply {
                    entry.injectionTriggers.forEach(::add)
                },
            )
        }
        prompt.addProperty("enabled", entry.enabled)
        return prompt
    }

    private fun exportPromptContent(
        entry: PresetPromptEntry,
        identifier: String,
    ): String {
        if (entry.marker && identifier in MARKER_IDENTIFIERS && entry.content.trim() == entry.kind.defaultMarkerContent(identifier)) {
            return ""
        }
        return entry.content
    }

    private fun toSampler(root: JsonObject): PresetSamplerConfig {
        return PresetSamplerConfig(
            temperature = root.getFloatOrNull("temperature"),
            topP = root.getFloatOrNull("top_p"),
            topK = root.getIntOrNull("top_k")?.takeIf { it != 0 },
            minP = root.getFloatOrNull("min_p")?.takeIf { it != 0f },
            repetitionPenalty = root.getFloatOrNull("repetition_penalty")?.takeIf { it != 1f },
            frequencyPenalty = root.getFloatOrNull("frequency_penalty"),
            presencePenalty = root.getFloatOrNull("presence_penalty"),
            maxOutputTokens = root.getIntOrNull("openai_max_tokens"),
        )
    }

    private fun putSampler(root: JsonObject, sampler: PresetSamplerConfig) {
        root.addProperty("temperature", sampler.temperature ?: root.getFloatOrNull("temperature") ?: 1f)
        root.addProperty("top_p", sampler.topP ?: root.getFloatOrNull("top_p") ?: 1f)
        root.addProperty("top_k", sampler.topK ?: root.getIntOrNull("top_k") ?: 0)
        root.addProperty("min_p", sampler.minP ?: root.getFloatOrNull("min_p") ?: 0f)
        root.addProperty(
            "repetition_penalty",
            sampler.repetitionPenalty ?: root.getFloatOrNull("repetition_penalty") ?: 1f,
        )
        sampler.frequencyPenalty?.let { root.addProperty("frequency_penalty", it) }
        sampler.presencePenalty?.let { root.addProperty("presence_penalty", it) }
        root.addProperty("openai_max_tokens", sampler.maxOutputTokens ?: root.getIntOrNull("openai_max_tokens") ?: 300)
    }

    private fun resolvePromptOrder(root: JsonObject): ResolvedPromptOrder {
        val orderObjects = root.getArray("prompt_order")
            ?.mapNotNull { it.asJsonObjectOrNull() }
            .orEmpty()
        val selected = orderObjects.firstOrNull {
            it.getString("character_id") == DEFAULT_PROMPT_ORDER_CHARACTER_ID
        } ?: orderObjects.firstOrNull()
        val promptOrder = selected?.getArray("order")
            ?.mapNotNull { it.asJsonObjectOrNull() }
            ?.mapNotNull { item ->
                val identifier = item.getString("identifier").trim()
                if (identifier.isBlank()) {
                    null
                } else {
                    PromptOrderReference(
                        identifier = identifier,
                        enabled = item.getBoolean("enabled", true),
                    )
                }
            }
            .orEmpty()
        val fallbackOrder = if (promptOrder.isEmpty()) {
            root.getArray("prompts")
                ?.mapNotNull { it.asJsonObjectOrNull()?.getString("identifier")?.trim()?.takeIf(String::isNotBlank) }
                ?.map { PromptOrderReference(identifier = it, enabled = true) }
                .orEmpty()
        } else {
            promptOrder
        }
        return ResolvedPromptOrder(
            characterId = selected?.getString("character_id").orEmpty().ifBlank { DEFAULT_PROMPT_ORDER_CHARACTER_ID },
            order = fallbackOrder,
            extrasJson = selected?.let { buildExtrasPayload(it, PROMPT_ORDER_KNOWN_KEYS) }.orEmpty().ifBlank { "{}" },
        )
    }

    private fun String.toPresetKind(): PresetPromptEntryKind {
        return when (this) {
            "main" -> PresetPromptEntryKind.MAIN_PROMPT
            "nsfw" -> PresetPromptEntryKind.NSFW_PROMPT
            "jailbreak" -> PresetPromptEntryKind.POST_HISTORY
            "chatHistory" -> PresetPromptEntryKind.CHAT_HISTORY
            "worldInfoBefore" -> PresetPromptEntryKind.WORLD_INFO_BEFORE
            "worldInfoAfter" -> PresetPromptEntryKind.WORLD_INFO_AFTER
            "charDescription" -> PresetPromptEntryKind.CHARACTER_DESCRIPTION
            "charPersonality" -> PresetPromptEntryKind.CHARACTER_PROMPT
            "scenario" -> PresetPromptEntryKind.SCENARIO
            "personaDescription" -> PresetPromptEntryKind.USER_PERSONA
            "dialogueExamples" -> PresetPromptEntryKind.EXAMPLE_DIALOGUE
            else -> PresetPromptEntryKind.CUSTOM
        }
    }

    private fun PresetPromptEntryKind.defaultMarkerContent(identifier: String): String {
        return when (this) {
            PresetPromptEntryKind.CHAT_HISTORY -> ""
            PresetPromptEntryKind.WORLD_INFO_AFTER -> ""
            PresetPromptEntryKind.MAIN_PROMPT -> ""
            PresetPromptEntryKind.NSFW_PROMPT -> ""
            PresetPromptEntryKind.CONTEXT_TEMPLATE -> "{{context}}"
            PresetPromptEntryKind.CHARACTER_DESCRIPTION -> "{{description}}"
            PresetPromptEntryKind.CHARACTER_PROMPT -> "{{char_prompt}}"
            PresetPromptEntryKind.USER_PERSONA -> "{{persona}}"
            PresetPromptEntryKind.SCENARIO -> "{{scenario}}"
            PresetPromptEntryKind.EXAMPLE_DIALOGUE -> "{{example_dialogue}}"
            PresetPromptEntryKind.WORLD_INFO_BEFORE -> "{{world_info}}"
            PresetPromptEntryKind.LONG_MEMORY -> "{{long_memory}}"
            PresetPromptEntryKind.SUMMARY -> "{{summary}}"
            PresetPromptEntryKind.PHONE_CONTEXT -> "{{phone_context}}"
            PresetPromptEntryKind.POST_HISTORY -> "{{post_history}}"
            PresetPromptEntryKind.STATUS_RULES -> "{{status_rules}}"
            PresetPromptEntryKind.CUSTOM -> if (identifier in MARKER_IDENTIFIERS) "" else "{{custom}}"
        }
    }

    private fun buildExtrasPayload(
        source: JsonObject,
        knownKeys: Set<String>,
    ): String {
        val extras = JsonObject()
        source.entrySet().forEach { (key, value) ->
            if (key !in knownKeys) {
                extras.add(key, value.deepCopy())
            }
        }
        return if (extras.size() > 0) gson.toJson(extras) else "{}"
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

    private fun parseObject(rawJson: String): JsonObject? {
        if (rawJson.isBlank()) {
            return null
        }
        return runCatching {
            gson.fromJson(rawJson, JsonObject::class.java)
        }.getOrNull()
    }

    private fun deriveStablePresetId(name: String, rawJson: String): String {
        return UUID.nameUUIDFromBytes(
            "sillytavern-preset|$name|${rawJson.trim().take(2048)}".toByteArray(StandardCharsets.UTF_8),
        ).toString()
    }

    private fun JsonObject.getString(key: String): String {
        return get(key)?.asStringOrNull().orEmpty()
    }

    private fun JsonObject.getArray(key: String): JsonArray? {
        return get(key)?.asJsonArrayOrNull()
    }

    private fun JsonObject.getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean {
        return get(key)?.asBooleanOrNull() ?: defaultValue
    }

    private fun JsonObject.getIntOrNull(key: String): Int? {
        return get(key)?.asIntOrNull()
    }

    private fun JsonObject.getFloatOrNull(key: String): Float? {
        return get(key)?.asFloatOrNull()
    }

    private fun JsonElement.asStringOrNull(): String? {
        return runCatching { asString }.getOrNull()
    }

    private fun JsonElement.asIntOrNull(): Int? {
        return runCatching { asInt }.getOrNull()
    }

    private fun JsonElement.asFloatOrNull(): Float? {
        return runCatching { asFloat }.getOrNull()
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

    private data class PromptOrderReference(
        val identifier: String,
        val enabled: Boolean,
    )

    private data class ResolvedPromptOrder(
        val characterId: String,
        val order: List<PromptOrderReference>,
        val extrasJson: String,
    )

    companion object {
        const val SOURCE_SILLYTAVERN_CHAT = "sillytavern_chat_completion"
        private const val DEFAULT_PROMPT_ORDER_CHARACTER_ID = "100000"
        private val SYSTEM_PROMPT_IDENTIFIERS = setOf("main", "nsfw", "jailbreak", "enhanceDefinitions")
        private val MARKER_IDENTIFIERS = setOf(
            "chatHistory",
            "worldInfoBefore",
            "worldInfoAfter",
            "charDescription",
            "charPersonality",
            "scenario",
            "personaDescription",
            "dialogueExamples",
        )
        private val ROOT_KNOWN_KEYS = setOf(
            "name",
            "display_name",
            "prompts",
            "prompt_order",
            "temperature",
            "top_p",
            "top_k",
            "min_p",
            "repetition_penalty",
            "frequency_penalty",
            "presence_penalty",
            "openai_max_tokens",
        )
        private val PROMPT_KNOWN_KEYS = setOf(
            "identifier",
            "name",
            "role",
            "content",
            "enabled",
            "system_prompt",
            "marker",
            "injection_position",
            "injection_depth",
            "injection_order",
            "injection_trigger",
        )
        private val PROMPT_ORDER_KNOWN_KEYS = setOf("character_id", "order")
    }
}
