package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.*
import com.example.myapplication.roleplay.RoleplayOutputParser
import com.example.myapplication.roleplay.RoleplayXmlEntities
import com.google.gson.JsonParser
import java.util.Locale

/** 沿原 XML 正文解析器派生拍；可选观测先独立分离，原文及坐标不做规范化。 */
class CompanionReplyParser {
    private val legacy = RoleplayOutputParser(preserveCompanionText = true)
    private val bodyOpen = Regex("(?is)<(narration|thought|dialogue)\\b([^>]{0,4096})>")
    // 超长属性仍属于观测块，先识别再检查限额，不能让 field 值掉入正文兜底。
    private val observeOpen = Regex("(?is)<observe\\b([^>]*)>")
    private val observeClose = Regex("(?is)</observe\\s*>")
    private val fieldPattern = Regex("(?is)<field\\b([^>]{0,1024})>(.*?)</field\\s*>")
    private val closing = listOf("narration", "thought", "dialogue")
        .associateWith { Regex("(?is)</$it\\s*>") }

    fun hasReadableBody(raw: String): Boolean = parse(
        raw,
        CompanionReplySource(
            "validation",
            "",
            "",
            0,
            PROTOCOL_VERSION,
            PARSER_VERSION,
            companionRawHash(raw)
        ),
        null, "角色", allowObservation = false,
    ).beats.any { it.content.isNotBlank() }

    fun parse(
        raw: String,
        source: CompanionReplySource,
        presentation: CompanionPresentationSnapshot?,
        characterName: String,
        allowObservation: Boolean = true,
        initialDiagnostics: Set<CompanionReplyDiagnostic> = emptySet(),
    ): ParsedCompanionReply {
        val diagnostics = initialDiagnostics.toMutableSet()
        if (source.protocolVersion != PROTOCOL_VERSION) diagnostics += CompanionReplyDiagnostic.UNSUPPORTED_PROTOCOL
        if (presentation == null) diagnostics += CompanionReplyDiagnostic.MISSING_PRESENTATION
        else if (presentation.snapshotVersion != 1) diagnostics += CompanionReplyDiagnostic.UNSUPPORTED_PRESENTATION

        val excluded = mutableListOf<CompanionRawSpan>()
        val observations = mutableListOf<CompanionObservation?>()
        var observeCursor = 0
        while (true) {
            val open = observeOpen.find(raw, observeCursor) ?: break
            val close = observeClose.find(raw, open.range.last + 1)
            val nextBody = bodyOpen.find(raw, open.range.last + 1)
            val complete =
                close != null && (nextBody == null || close.range.first < nextBody.range.first)
            val end = if (complete) close!!.range.last + 1 else nextBody?.range?.first ?: raw.length
            excluded += CompanionRawSpan(open.range.first, end)
            if (open.groupValues[1].length > MAX_OBSERVATION_ATTRIBUTES_CHARS) {
                diagnostics += CompanionReplyDiagnostic.OBSERVATION_LIMIT_EXCEEDED
                observations += null
            } else if (!complete) {
                diagnostics += CompanionReplyDiagnostic.MALFORMED_OBSERVATION
                observations += null
            } else {
                observations += parseObservation(
                    open.groupValues[1], raw.substring(open.range.last + 1, close!!.range.first),
                    presentation, diagnostics,
                )
            }
            observeCursor = end.coerceAtLeast(open.range.last + 1)
        }
        if (observations.size > 1) diagnostics += CompanionReplyDiagnostic.MALFORMED_OBSERVATION

        val beats = mutableListOf<CompanionBeat>()
        fun appendFragment(start: Int, end: Int, attributes: Map<String, String> = emptyMap()) {
            if (start >= end) return
            val fragment = raw.substring(start, end)
            if (isJsonPatchOnly(fragment)) return
            val sprite = CompanionExpressionKeys.normalize(attributes["sprite"])
            if (attributes["sprite"] != null && sprite == null) diagnostics += CompanionReplyDiagnostic.INVALID_SPRITE
            val voice = CompanionVoiceEmotion.fromProtocol(attributes["voice_emotion"])
            if (attributes["voice_emotion"] != null && voice == null) diagnostics += CompanionReplyDiagnostic.INVALID_VOICE_EMOTION
            legacy.parseAssistantOutput(fragment, characterName, allowNarration = true)
                .forEach { segment ->
                    if (segment.content.isBlank()) return@forEach
                    val index = beats.size
                    val id = "companion:" + companionRawHash(
                        "${source.messageId}\u0000${source.revision}\u0000${source.protocolVersion}\u0000${source.parserVersion}\u0000${source.rawHash}\u0000$start:$end:$index"
                    )
                    beats += CompanionBeat(
                        id = id,
                        contentType = segment.contentType,
                        speaker = segment.speaker,
                        speakerName = segment.speakerName,
                        content = segment.content,
                        rawSpan = CompanionRawSpan(start, end),
                        spriteKey = sprite,
                        voiceEmotion = voice,
                        emotion = segment.emotion,
                        replyToMessageId = segment.replyToMessageId,
                        replyToPreview = segment.replyToPreview,
                        replyToSpeakerName = segment.replyToSpeakerName,
                    )
                }
        }

        fun appendRange(start: Int, end: Int) {
            var cursor = start
            while (cursor < end) {
                val open = bodyOpen.find(raw, cursor)?.takeIf { it.range.first < end }
                if (open == null) {
                    appendFragment(cursor, end)
                    break
                }
                appendFragment(cursor, open.range.first)
                val close = closing.getValue(open.groupValues[1].lowercase(Locale.ROOT))
                    .find(raw, open.range.last + 1)
                    ?.takeIf { it.range.last < end }
                val next = bodyOpen.find(raw, open.range.last + 1)?.takeIf { it.range.first < end }
                if (close == null || next != null && next.range.first < close.range.first) {
                    val fragmentEnd = next?.range?.first ?: end
                    appendFragment(open.range.first, fragmentEnd)
                    cursor = fragmentEnd
                } else {
                    appendFragment(
                        open.range.first,
                        close.range.last + 1,
                        legacy.parseAttributes(open.groupValues[2])
                    )
                    cursor = close.range.last + 1
                }
            }
        }

        var cursor = 0
        excluded.forEach { range ->
            appendRange(cursor, range.start)
            cursor = range.endExclusive
        }
        appendRange(cursor, raw.length)

        val observation = observations.singleOrNull()?.takeIf {
            allowObservation && source.protocolVersion == PROTOCOL_VERSION && presentation?.snapshotVersion == 1 &&
                    presentation.observation.enabled && diagnostics.none { diagnostic -> diagnostic in OBSERVATION_ERRORS }
        }
        return ParsedCompanionReply(source, raw, beats, observation, diagnostics, presentation)
    }

    private fun parseObservation(
        attributes: String,
        content: String,
        presentation: CompanionPresentationSnapshot?,
        diagnostics: MutableSet<CompanionReplyDiagnostic>,
    ): CompanionObservation? {
        if (content.length > MAX_OBSERVATION_CHARS) {
            diagnostics += CompanionReplyDiagnostic.OBSERVATION_LIMIT_EXCEEDED
            return null
        }
        if (legacy.parseAttributes(attributes)["version"] != "1") {
            diagnostics += CompanionReplyDiagnostic.MALFORMED_OBSERVATION
            return null
        }
        val fields = fieldPattern.findAll(content).toList()
        if (fields.size > 10 || fields.isEmpty()) {
            diagnostics += CompanionReplyDiagnostic.OBSERVATION_LIMIT_EXCEEDED
            return null
        }
        var cursor = 0
        val values = linkedMapOf<String, String>()
        for (field in fields) {
            if (content.substring(cursor, field.range.first)
                    .isNotBlank() || '<' in field.groupValues[2]
            ) {
                diagnostics += CompanionReplyDiagnostic.MALFORMED_OBSERVATION
                return null
            }
            val id = legacy.parseAttributes(field.groupValues[1])["id"].orEmpty()
            if (id.isBlank() || id in values) {
                diagnostics += CompanionReplyDiagnostic.MALFORMED_OBSERVATION
                return null
            }
            val value = RoleplayXmlEntities.decodeOnce(field.groupValues[2]).trim()
            if (value.length > MAX_OBSERVATION_VALUE) {
                diagnostics += CompanionReplyDiagnostic.OBSERVATION_LIMIT_EXCEEDED
                return null
            }
            values[id] = value
            cursor = field.range.last + 1
        }
        if (content.substring(cursor).isNotBlank()) {
            diagnostics += CompanionReplyDiagnostic.MALFORMED_OBSERVATION
            return null
        }
        val settings = presentation?.observation ?: return null
        if (CompanionWritingValidation.observationError(settings) != null) {
            diagnostics += CompanionReplyDiagnostic.MALFORMED_OBSERVATION
            return null
        }
        val definitions = settings.enabledFields()
        if (values.keys.any { id -> definitions.none { it.id == id } }) {
            diagnostics += CompanionReplyDiagnostic.UNKNOWN_OBSERVATION_FIELD
            return null
        }
        return CompanionObservation(
            definitions.mapNotNull { field ->
                values[field.id]?.takeIf(String::isNotBlank)
                    ?.let { CompanionObservedField(field.id, field.label, it) }
            },
            settings.style,
        ).takeIf { it.fields.isNotEmpty() }
    }

    private fun isJsonPatchOnly(value: String): Boolean {
        val text = value.trim()
        if (!text.startsWith('[') || !text.endsWith(']')) return false
        val json = runCatching { JsonParser.parseString(text) }.getOrNull()
            ?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
        return json.size() > 0 && json.all { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@all false
            val op = item.get("op")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            val path = item.get("path")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            op in setOf("add", "remove", "replace", "move", "copy", "test") && path != null
        }
    }

    companion object {
        const val PROTOCOL_VERSION = 1
        const val PARSER_VERSION = 1
        private const val MAX_OBSERVATION_ATTRIBUTES_CHARS = 4_096
        private const val MAX_OBSERVATION_CHARS = 8_192
        private const val MAX_OBSERVATION_VALUE = 400
        private val OBSERVATION_ERRORS = setOf(
            CompanionReplyDiagnostic.MALFORMED_OBSERVATION,
            CompanionReplyDiagnostic.UNKNOWN_OBSERVATION_FIELD,
            CompanionReplyDiagnostic.OBSERVATION_LIMIT_EXCEEDED,
        )
    }
}
