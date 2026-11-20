package com.example.myapplication.conversation

import com.example.myapplication.model.MemoryExtractionSpan
import com.example.myapplication.model.MemorySemanticText
import com.example.myapplication.model.MemorySourceEvidence
import com.example.myapplication.model.MemorySourceRef
import com.example.myapplication.model.MemorySourceStatus
import com.example.myapplication.roleplay.RoleplayTranscriptFormatter

/** 全量来源按固定规则拆分；进度覆盖仍以原始快照 UTF-16 半开区间计。 */
object MemoryExtractionBatcher {
    fun sourceSpans(
        source: MemorySourceEvidence,
        segments: List<MemorySemanticText>,
        characterId: String,
        selectedByUser: Boolean = false,
        maxSpanChars: Int = 2400,
        overlapChars: Int = 64,
    ): List<MemoryExtractionSpan> {
        require(maxSpanChars >= 64 && overlapChars in 0 until maxSpanChars / 2)
        require(source.status == MemorySourceStatus.AVAILABLE && source.content.isNotEmpty()) { "整理来源不可用" }
        require(segments.isNotEmpty() && segments.joinToString("\n\n") { it.text } == source.content) { "语义来源与冻结正文不一致" }
        val mediaLocations = RoleplayTranscriptFormatter.inlineMediaLocations(source.content)
        var offset = 0
        return segments.flatMapIndexed { index, segment ->
            val start = offset
            val end = start + segment.text.length + if (index < segments.lastIndex) 2 else 0
            offset = end
            val skip = when {
                segment.skipReason.isNotBlank() -> segment.skipReason
                segment.privateToSpeaker && source.speakerId != characterId -> "PRIVATE_TO_SPEAKER"
                !selectedByUser && characterId !in source.visibleCharacterIds -> "UNKNOWN_VISIBILITY"
                else -> ""
            }
            val visible =
                if (segment.privateToSpeaker) listOfNotNull(source.speakerId.takeIf(String::isNotBlank)) else source.visibleCharacterIds
            // 兼容来源仍可能保存旧 Base64。必须先定位完整负载，再拆 span，避免尾段失去 data: 前缀后漏出。
            val sections = buildList {
                var cursor = start
                mediaLocations.filter { it.first < end && it.last >= start }.forEach { location ->
                    val mediaStart = maxOf(start, location.first)
                    val mediaEnd = minOf(end, location.last + 1)
                    if (cursor < mediaStart) add(Triple(cursor, mediaStart, skip))
                    add(Triple(mediaStart, mediaEnd, skip.ifBlank { "MEDIA_LOCATION" }))
                    cursor = mediaEnd
                }
                if (cursor < end) add(Triple(cursor, end, skip))
            }
            sections.flatMap { (sectionStart, sectionEnd, sectionSkip) ->
                splitRange(
                    source.content,
                    sectionStart,
                    sectionEnd,
                    maxSpanChars,
                    overlapChars
                ).map { range ->
                    MemoryExtractionSpan(
                        MemorySourceRef(
                            source.id,
                            source.messageRevision,
                            range.first,
                            range.second
                        ),
                        source.messageId,
                        source.speakerId,
                        source.speakerRole,
                        source.personaKey,
                        visible,
                        source.occurredAt,
                        source.kind,
                        segment.kind,
                        if (sectionSkip.isNotEmpty()) "" else source.content.substring(
                            range.first,
                            range.second
                        ),
                        sectionSkip,
                    )
                }
            }
        }
    }

    fun batches(
        spans: List<MemoryExtractionSpan>,
        maxInputChars: Int = 6000
    ): List<List<MemoryExtractionSpan>> {
        require(maxInputChars >= 64)
        val result = mutableListOf<List<MemoryExtractionSpan>>()
        var current = mutableListOf<MemoryExtractionSpan>()
        var length = 0
        spans.forEach { span ->
            if (current.isNotEmpty() && (length + span.text.length > maxInputChars || current.size >= 100)) {
                result += current
                current = mutableListOf()
                length = 0
            }
            current += span
            length += span.text.length
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    fun covered(ref: MemorySourceRef, committed: List<MemorySourceRef>): Boolean {
        var end = ref.start
        committed.filter { it.sourceId == ref.sourceId && it.sourceRevision == ref.sourceRevision }
            .sortedBy { it.start }.forEach { span ->
                if (span.start <= end && span.end > end) end = span.end
            }
        return end >= ref.end
    }

    private fun splitRange(
        text: String,
        first: Int,
        last: Int,
        budget: Int,
        overlap: Int
    ): List<Pair<Int, Int>> = buildList {
        var start = first
        while (start < last) {
            var end = minOf(start + budget, last)
            if (end < last) {
                val paragraph = text.lastIndexOf('\n', end - 1)
                if (paragraph > start + budget / 2) end = paragraph + 1
                end = safeBoundary(text, end)
            }
            add(start to end)
            if (end == last) break
            start = safeBoundary(text, (end - overlap).coerceAtLeast(start + 1))
        }
    }

    private fun safeBoundary(text: String, position: Int): Int =
        if (position in 1 until text.length && text[position - 1].isHighSurrogate() && text[position].isLowSurrogate()) position - 1 else position

}
