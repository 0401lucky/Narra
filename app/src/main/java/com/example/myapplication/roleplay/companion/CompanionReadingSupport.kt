package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.*
import com.example.myapplication.roleplay.RoleplayMessageUiMapper
import com.example.myapplication.roleplay.RoleplayOutputParser

object CompanionReadingSupport {
    fun items(
        records: List<CompanionReadingRecord>,
        settings: AppSettings
    ): List<CompanionReadingItem> {
        val parser = RoleplayOutputParser()
        return records.distinctBy { it.message.id }
            .sortedWith { first, second -> first.cursor.compareTo(second.cursor) }
            .flatMap { record ->
                val characterId = record.message.companionReplyMetadata?.speakerId
                    ?: record.binding.characterId.ifBlank { record.message.speakerId.ifBlank { settings.selectedAssistantId } }
                val assistant = settings.resolvedAssistants().firstOrNull { it.id == characterId }
                val scenario = record.binding.scenario ?: RoleplayScenario(
                    id = record.binding.scenarioId,
                    title = record.binding.title,
                    assistantId = characterId,
                    interactionMode = record.message.roleplayInteractionMode
                        ?: RoleplayInteractionMode.OFFLINE_DIALOGUE
                )
                val mapped = RoleplayMessageUiMapper.mapMessages(
                    scenario, assistant, settings, listOf(record.message), null, parser,
                    nowProvider = { record.message.createdAt })
                val readable = mapped.ifEmpty {
                    if (record.message.parts.any { it.type == ChatMessagePartType.IMAGE } || record.message.attachments.any { it.type == AttachmentType.IMAGE })
                        listOf(
                            RoleplayMessageUiModel(
                                "${record.message.id}:images",
                                record.message.id,
                                RoleplayContentType.ACTION,
                                if (record.message.role == MessageRole.USER) RoleplaySpeaker.USER else RoleplaySpeaker.CHARACTER,
                                record.message.speakerName,
                                content = "图片",
                                createdAt = record.message.createdAt
                            )
                        ) else emptyList()
                }
                readable.map { CompanionReadingItem(record, it) }
            }
    }

    fun anchorIndex(anchor: CompanionReadingAnchor, items: List<CompanionReadingItem>): Int? {
        if (items.isEmpty()) return null
        if (anchor.followingBottom) return items.lastIndex
        items.indexOfFirst { it.id == anchor.itemId }.takeIf { it >= 0 }?.let { return it }
        val sameSource = items.withIndex().filter { it.value.record.message.id == anchor.messageId }
        if (sameSource.isNotEmpty()) return anchor.beatRawStart?.let { start ->
            sameSource.minByOrNull {
                kotlin.math.abs(
                    (it.value.ui.companionBeat?.rawSpan?.start ?: 0) - start
                )
            }?.index
        } ?: sameSource.first().index
        return items.indexOfFirst { it.record.cursor >= anchor.cursor }.takeIf { it >= 0 }
            ?: items.lastIndex
    }

    fun refreshedWindow(records: List<CompanionReadingRecord>): List<CompanionReadingRecord> =
        records
            .groupBy { it.message.id }.values.map { versions -> versions.maxBy { it.message.memoryRevision } }
            .sortedWith { first, second -> first.cursor.compareTo(second.cursor) }
}

object CompanionSpeechSupport {
    fun units(reply: ParsedCompanionReply): List<CompanionSpeechUnit> =
        reply.beats.filter { it.content.isNotBlank() }.map { beat ->
            CompanionSpeechUnit(
                "${reply.source.messageId}:${reply.source.revision}:${beat.id}:${reply.source.rawHash}",
                reply.source,
                beat.id,
                beat.content,
                beat.rawSpan,
                beat.speaker,
                beat.speakerName,
                beat.voiceEmotion,
                beat.contentType,
                reply.raw.substring(
                    beat.rawSpan.start.coerceIn(0, reply.raw.length),
                    beat.rawSpan.endExclusive.coerceIn(
                        beat.rawSpan.start.coerceIn(
                            0,
                            reply.raw.length
                        ), reply.raw.length
                    )
                ),
            )
        }

    fun units(items: List<CompanionReadingItem>): List<CompanionSpeechUnit> =
        items.mapNotNull { it.ui.companionReply }
            .distinctBy { it.source }.flatMap(::units)

    fun select(
        units: List<CompanionSpeechUnit>, firstId: String, lastId: String = firstId,
        start: Int = 0, endExclusive: Int? = null
    ): CompanionSpeechSelection? {
        val first = units.indexOfFirst { it.id == firstId }
        val last = units.indexOfFirst { it.id == lastId }
        if (first < 0 || last < first) return null
        val selected = units.subList(first, last + 1)
        val end = endExclusive ?: selected.last().text.length
        if (start !in 0..selected.first().text.length || end !in 0..selected.last().text.length ||
            (first == last && end <= start) || !boundary(selected.first().text, start) || !boundary(
                selected.last().text,
                end
            )
        ) return null
        val ranges = selected.mapIndexed { index, unit ->
            CompanionSpeechRange(
                unit.id,
                if (index == 0) start else 0,
                if (index == selected.lastIndex) end else unit.text.length
            )
        }.filter { it.start < it.endExclusive }
        return CompanionSpeechSelection(selected, ranges).takeIf { it.ranges.isNotEmpty() }
    }

    fun isCurrent(selection: CompanionSpeechSelection, units: List<CompanionSpeechUnit>): Boolean =
        selection.units.all { selected -> units.any { it == selected } }

    private fun boundary(text: String, offset: Int): Boolean =
        offset == 0 || offset == text.length || !(text[offset - 1].isHighSurrogate() && text[offset].isLowSurrogate())
}
