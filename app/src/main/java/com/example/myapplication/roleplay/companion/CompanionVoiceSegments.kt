package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.*

/** 不再次解析/清洗协议；只分配 D4 已显示的选区，完整保留来源和字符簇。 */
object CompanionVoiceSegments {
    const val MAX_SEGMENT_CHARS = 800

    fun from(
        selection: CompanionSpeechSelection,
        maxChars: Int = MAX_SEGMENT_CHARS
    ): List<CompanionVoiceSegment> {
        require(maxChars > 0)
        val units = selection.units.associateBy { it.id }
        require(units.size == selection.units.size) { "选区片段重复，请重新选择" }
        var previousUnitIndex = -1
        var previousEnd = 0
        return selection.ranges.flatMap { range ->
            val unit = requireNotNull(units[range.unitId]) { "选区片段已失效，请重新选择" }
            val unitIndex = selection.units.indexOf(unit)
            require(unitIndex >= previousUnitIndex && (unitIndex > previousUnitIndex || range.start >= previousEnd)) {
                "选区顺序无效，请重新选择"
            }
            require(range.start in 0 until range.endExclusive && range.endExclusive <= unit.text.length) { "选区范围无效，请重新选择" }
            val allEnds = companionGraphemeEnds(unit.text)
            require((range.start == 0 || range.start in allEnds) && range.endExclusive in allEnds) { "请按完整字符重新选择" }
            previousUnitIndex = unitIndex
            previousEnd = range.endExclusive
            val result = mutableListOf<CompanionVoiceSegment>()
            var start = range.start
            while (start < range.endExclusive) {
                val candidates = allEnds.filter { it > start && it <= range.endExclusive }
                val fitting = candidates.filter { it - start <= maxChars }
                val limit = fitting.lastOrNull() ?: candidates.first()
                val end = fitting.lastOrNull { offset -> unit.text[offset - 1] in SENTENCE_ENDINGS }
                    ?: limit
                val text = unit.text.substring(start, end)
                if (text.isNotBlank()) result += CompanionVoiceSegment(
                    unit, CompanionSpeechRange(unit.id, start, end), text,
                )
                start = end
            }
            result
        }.also { require(it.isNotEmpty()) { "所选范围没有可朗读文字" } }
    }

    private const val SENTENCE_ENDINGS = "。！？!?；;\n"
}
