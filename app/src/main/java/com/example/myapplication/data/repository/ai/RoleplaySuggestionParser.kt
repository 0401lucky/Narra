package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.RoleplaySuggestionAxis
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal object RoleplaySuggestionParser {
    private const val roleplaySuggestionCount = 3
    private val jsonCodeFenceRegex = Regex("""^```(?:json)?\s*([\s\S]*?)\s*```$""")
    private val structuredSuggestionKeyRegex = Regex(
        """(?i)(["']?(plot|info|emotion|axis|label|text|suggestions)["']?\s*[:：=])""",
    )

    fun parse(rawContent: String): List<RoleplaySuggestionUiModel> {
        val normalized = unwrapJsonCodeFence(rawContent)
        parseRoleplaySuggestionArray(normalized)?.let { parsed ->
            if (parsed.isNotEmpty()) {
                return parsed
            }
        }
        if (looksLikeStructuredPayload(normalized)) {
            return emptyList()
        }

        val blockCandidates = normalized
            .split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (blockCandidates.size >= 2) {
            val parsedBlocks = blockCandidates.mapIndexedNotNull { index, block ->
                parseFallbackRoleplaySuggestion(block, index)
            }
            if (parsedBlocks.isNotEmpty()) {
                return parsedBlocks.distinctBy { it.text }.take(roleplaySuggestionCount)
            }
        }

        return normalized.lines()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .removePrefix("•")
                    .replace(Regex("""^\d+[\.\)、]\s*"""), "")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .mapIndexedNotNull { index, item ->
                parseFallbackRoleplaySuggestion(item, index)
            }
            .distinctBy { it.text }
            .take(roleplaySuggestionCount)
    }

    private fun parseRoleplaySuggestionArray(
        rawContent: String,
    ): List<RoleplaySuggestionUiModel>? {
        val parsedJson = runCatching { JsonParser.parseString(rawContent.trim()) }.getOrNull() ?: return null
        return when {
            parsedJson.isJsonArray -> parseSuggestionArray(parsedJson.asJsonArray)
            parsedJson.isJsonObject -> {
                val suggestionObject = parsedJson.asJsonObject
                suggestionObject["suggestions"]
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?.let(::parseSuggestionArray)
                    ?: parseAxisSuggestionObject(suggestionObject)
            }
            else -> null
        }
    }

    fun looksLikeStructuredPayload(rawContent: String): Boolean {
        val normalized = unwrapJsonCodeFence(rawContent)
            .replace("\r\n", "\n")
            .trim()
        if (normalized.isBlank()) {
            return false
        }
        val startsLikeStructured = normalized.startsWith("{") ||
            normalized.startsWith("[") ||
            Regex("""(?i)^(plot|info|emotion|axis|label|text|suggestions)\s*[:：=]""")
                .containsMatchIn(normalized)
        if (!startsLikeStructured) {
            return false
        }
        return structuredSuggestionKeyRegex.findAll(normalized).count() >= 2
    }

    private fun parseSuggestionArray(
        suggestionArray: JsonArray,
    ): List<RoleplaySuggestionUiModel> {
        return suggestionArray.mapIndexedNotNull { index, element ->
            val suggestionObject = runCatching { element.asJsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            parseSuggestionObject(
                index = index,
                suggestionObject = suggestionObject,
            )
        }.distinctBy { it.text }.take(roleplaySuggestionCount)
    }

    private fun parseAxisSuggestionObject(
        rootObject: JsonObject,
    ): List<RoleplaySuggestionUiModel>? {
        val parsedSuggestions = rootObject.entrySet()
            .mapNotNull { (rawAxis, value) ->
                val axis = rawAxis.trim().toRoleplaySuggestionAxisOrNull() ?: return@mapNotNull null
                val suggestionObject = runCatching { value.asJsonObject }.getOrNull() ?: return@mapNotNull null
                axis to suggestionObject
            }
            .sortedBy { (axis, _) ->
                when (axis) {
                    RoleplaySuggestionAxis.PLOT -> 0
                    RoleplaySuggestionAxis.INFO -> 1
                    RoleplaySuggestionAxis.EMOTION -> 2
                }
            }
            .mapIndexedNotNull { index, (axis, suggestionObject) ->
                parseSuggestionObject(
                    index = index,
                    suggestionObject = suggestionObject,
                    explicitAxis = axis,
                )
            }
            .distinctBy { it.text }
            .take(roleplaySuggestionCount)
        return parsedSuggestions.takeIf { it.isNotEmpty() }
    }

    private fun parseSuggestionObject(
        index: Int,
        suggestionObject: JsonObject,
        explicitAxis: RoleplaySuggestionAxis? = null,
    ): RoleplaySuggestionUiModel? {
        val text = suggestionObject["text"]
            ?.let { value -> runCatching { value.asString }.getOrNull() }
            .orEmpty()
            .trim()
        if (text.isBlank()) {
            return null
        }
        val label = suggestionObject["label"]
            ?.let { value -> runCatching { value.asString }.getOrNull() }
            .orEmpty()
            .trim()
            .ifBlank { defaultRoleplaySuggestionLabel(index) }
        val axis = explicitAxis ?: suggestionObject["axis"]
            ?.let { value -> runCatching { value.asString }.getOrNull() }
            .orEmpty()
            .trim()
            .toRoleplaySuggestionAxisOrNull()
            ?: defaultRoleplaySuggestionAxis(index)
        return RoleplaySuggestionUiModel(
            id = buildRoleplaySuggestionId(index),
            label = label,
            text = text,
            axis = axis,
        )
    }

    private fun parseFallbackRoleplaySuggestion(
        rawItem: String,
        index: Int,
    ): RoleplaySuggestionUiModel? {
        val normalizedItem = rawItem
            .replace("\r\n", "\n")
            .trim()
            .removePrefix("-")
            .removePrefix("•")
            .replace(Regex("""^\d+[\.\)、]\s*"""), "")
            .trim()
        if (normalizedItem.isBlank()) {
            return null
        }

        val labelMatch = Regex("""^\s*([^：:\n]{2,12})[：:]\s*(.+)$""", RegexOption.DOT_MATCHES_ALL)
            .find(normalizedItem)
        val label = labelMatch?.groupValues?.get(1)?.trim().orEmpty()
        val text = (labelMatch?.groupValues?.get(2) ?: normalizedItem)
            .replace("\n", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (text.isBlank()) {
            return null
        }
        return RoleplaySuggestionUiModel(
            id = buildRoleplaySuggestionId(index),
            label = label.ifBlank { defaultRoleplaySuggestionLabel(index) },
            text = text,
            axis = defaultRoleplaySuggestionAxis(index),
        )
    }

    private fun unwrapJsonCodeFence(rawContent: String): String {
        val trimmed = rawContent.trim()
        val match = jsonCodeFenceRegex.find(trimmed)
        return match?.groupValues?.get(1)?.trim() ?: trimmed
    }

    private fun buildRoleplaySuggestionId(index: Int): String {
        return "roleplay-suggestion-${index + 1}"
    }

    private fun defaultRoleplaySuggestionLabel(index: Int): String {
        return when (index) {
            0 -> "试探推进"
            1 -> "信息探索"
            else -> "情绪拉扯"
        }
    }

    private fun defaultRoleplaySuggestionAxis(index: Int): RoleplaySuggestionAxis {
        return when (index) {
            0 -> RoleplaySuggestionAxis.PLOT
            1 -> RoleplaySuggestionAxis.INFO
            else -> RoleplaySuggestionAxis.EMOTION
        }
    }

    private fun String.toRoleplaySuggestionAxisOrNull(): RoleplaySuggestionAxis? {
        return when (this.lowercase()) {
            "plot", "推进", "剧情推进" -> RoleplaySuggestionAxis.PLOT
            "info", "information", "探索", "信息", "信息探索" -> RoleplaySuggestionAxis.INFO
            "emotion", "emotional", "情绪", "关系", "情绪拉扯" -> RoleplaySuggestionAxis.EMOTION
            else -> null
        }
    }
}
