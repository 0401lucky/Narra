package com.example.myapplication.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CompanionHistoryQuery(
    val encounterId: String? = null,
    val date: String? = null,
    val month: String? = null,
    val conversationId: String? = null,
    val legacyOnly: Boolean = false,
    val zoneId: String = "UTC",
) {
    fun validate() {
        ZoneId.of(zoneId)
        date?.let { require(LocalDate.parse(it).toString() == it) { "日期格式应为 YYYY-MM-DD" } }
        month?.let { require(YearMonth.parse(it).toString() == it) { "月份格式应为 YYYY-MM" } }
        require(encounterId == null || encounterId.isNotBlank()) { "场次无效" }
        require(conversationId == null || conversationId.isNotBlank()) { "会话无效" }
        require(!legacyOnly || encounterId == null) { "旧未分场记录不能选择场次" }
    }
}

data class CompanionHistoryGroup(
    val id: String,
    val title: String,
    val encounterId: String?,
    val dateLabels: List<String>,
    val recordCount: Int,
    val firstMessageId: String?,
    val legacy: Boolean,
    val createdAt: Long,
)

data class CompanionHistoryArchiveChoice(
    val spaceId: String,
    val runId: String,
    val title: String,
)

data class CompanionHistoryPage(
    val groups: List<CompanionHistoryGroup>,
    val records: List<CompanionReadingRecord>,
    val totalCount: Int,
    val nextBefore: CompanionReadingCursor?,
    val hasOlder: Boolean,
)

data class CompanionHistoryEdit(
    val messageId: String,
    val expectedRevision: Long,
    val content: String
)

/** null 正文表示删除；保留原会话和所见来源修订，不接受把 link 剥成 legacy。 */
data class CompanionMessageMutation(
    val source: CompanionMessageRevision,
    val content: String? = null
)

sealed interface CompanionHistoryMutationResult {
    data class Applied(val messageIds: Set<String>) : CompanionHistoryMutationResult
    data object Conflict : CompanionHistoryMutationResult
    data object ReadOnly : CompanionHistoryMutationResult
}

data class CompanionTextExportRequest(
    val id: String,
    val fileName: String,
    val scope: CompanionReadingScope,
    val query: CompanionHistoryQuery,
)

sealed interface CompanionTextExportResult {
    data class Saved(val recordCount: Int) : CompanionTextExportResult
    data object Cancelled : CompanionTextExportResult
    data class Failed(val message: String) : CompanionTextExportResult
}
