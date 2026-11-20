package com.example.myapplication.model

/** 阅读凭证与 AllowedCompanionHistoryScope 无继承关系，也不能用于模型请求。 */
data class CompanionReadingBinding(
    val conversationId: String,
    val scenarioId: String,
    val runId: String,
    val title: String,
    val characterId: String = "",
    val scenario: RoleplayScenario? = null,
    val readOnly: Boolean = false,
)

data class CompanionReadingScope(
    val context: MemoryRuntimeContext,
    val bindings: List<CompanionReadingBinding>,
    val encounterIds: Set<String>,
    val readOnly: Boolean = false,
    /** 显式旧档以当前授权为入口；不能重新绑定旧运行或转为模型授权。 */
    val authorizationContext: MemoryRuntimeContext? = null,
)

data class CompanionReadingCursor(val createdAt: Long, val messageId: String) :
    Comparable<CompanionReadingCursor> {
    override fun compareTo(other: CompanionReadingCursor): Int =
        createdAt.compareTo(other.createdAt).takeIf { it != 0 } ?: companionReadingIdCompare(
            messageId,
            other.messageId
        )
}

/** 与 SQLite BINARY 的 UTF-8 字节序一致；不能用 UTF-16 比较推导分页窗口。 */
fun companionReadingIdCompare(first: String, second: String): Int {
    val left = first.toByteArray(Charsets.UTF_8)
    val right = second.toByteArray(Charsets.UTF_8)
    for (index in 0 until minOf(left.size, right.size)) {
        val result = (left[index].toInt() and 255).compareTo(right[index].toInt() and 255)
        if (result != 0) return result
    }
    return left.size.compareTo(right.size)
}

data class CompanionReadingRecord(
    val message: ChatMessage,
    val binding: CompanionReadingBinding,
    val encounterId: String? = null,
    val turnKind: CompanionTurnKind? = null,
    /** 待完成输入属于管理原文，不成为连续阅读或声音来源。 */
    val readerEligible: Boolean = message.companionSourceEligibility() != CompanionSourceEligibility.UNAVAILABLE,
) {
    val cursor: CompanionReadingCursor get() = CompanionReadingCursor(message.createdAt, message.id)
}

data class CompanionReadingPage(
    val records: List<CompanionReadingRecord>,
    val nextBefore: CompanionReadingCursor?,
    val hasOlder: Boolean,
)

/** 锚点由原文身份、可见段身份、排序值与像素偏移组成；不使用列表下标作持久身份。 */
data class CompanionReadingAnchor(
    val messageId: String,
    val itemId: String,
    val cursor: CompanionReadingCursor,
    val offset: Int,
    val followingBottom: Boolean = false,
    val beatRawStart: Int? = null,
)

data class CompanionReadingItem(
    val record: CompanionReadingRecord,
    val ui: RoleplayMessageUiModel,
) {
    val id: String get() = "${record.binding.conversationId}:${ui.uiItemId}"
}

/** D5 消费的稳定单位：正文显示范围与原文 UTF-16 范围分别保存。 */
data class CompanionSpeechUnit(
    val id: String,
    val source: CompanionReplySource,
    val beatId: String,
    val text: String,
    val rawSpan: CompanionRawSpan,
    val speaker: RoleplaySpeaker,
    val speakerName: String,
    val voiceEmotion: CompanionVoiceEmotion?,
    val contentType: RoleplayContentType = RoleplayContentType.DIALOGUE,
    val rawText: String = "",
    val languageTag: String? = null,
)

data class CompanionSpeechRange(val unitId: String, val start: Int, val endExclusive: Int)

data class CompanionSpeechSelection(
    val units: List<CompanionSpeechUnit>,
    val ranges: List<CompanionSpeechRange>,
) {
    val text: String
        get() = ranges.mapNotNull { range ->
            units.firstOrNull { it.id == range.unitId }?.text?.let { text ->
                if (range.start in 0..text.length && range.endExclusive in range.start..text.length)
                    text.substring(range.start, range.endExclusive) else null
            }
        }.joinToString("\n\n")
}
