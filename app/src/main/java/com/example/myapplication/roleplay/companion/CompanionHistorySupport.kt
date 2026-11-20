package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.CompanionEncounter
import com.example.myapplication.model.CompanionHistoryGroup
import com.example.myapplication.model.CompanionHistoryQuery
import com.example.myapplication.model.CompanionReadingRecord
import com.example.myapplication.model.CompanionReadingBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CompanionHistorySupport {
    fun dateLabel(record: CompanionReadingRecord, zoneId: String): String =
        if (record.message.createdAt == 0L) "日期未知"
        else Instant.ofEpochMilli(record.message.createdAt).atZone(ZoneId.of(zoneId)).toLocalDate()
            .toString()

    fun filter(
        records: List<CompanionReadingRecord>,
        query: CompanionHistoryQuery
    ): List<CompanionReadingRecord> {
        query.validate()
        return records.filter { record ->
            val date = dateLabel(record, query.zoneId)
            (query.encounterId == null || record.encounterId == query.encounterId) &&
                    (query.conversationId == null || record.message.conversationId == query.conversationId) &&
                    (!query.legacyOnly || record.encounterId == null) &&
                    (query.date == null || date == query.date) &&
                    (query.month == null || date.startsWith(query.month + "-"))
        }.distinctBy { it.message.id }
            .sortedWith { left, right -> left.cursor.compareTo(right.cursor) }
    }

    fun groups(
        records: List<CompanionReadingRecord>,
        query: CompanionHistoryQuery,
        encounters: Map<String, CompanionEncounter> = emptyMap(),
    ): List<CompanionHistoryGroup> = records.groupBy { record ->
        record.encounterId?.let { "encounter:$it" }
            ?: "legacy:${record.message.conversationId}:${dateLabel(record, query.zoneId)}"
    }.map { (id, values) ->
        val first = values.first()
        val encounter = first.encounterId?.let(encounters::get)
        val createdAt = encounter?.createdAt ?: first.message.createdAt
        val dates = values.map { dateLabel(it, query.zoneId) }.distinct()
        val title = if (first.encounterId == null) "旧记录，未分场 · ${first.binding.title}"
        else encounterTitle(first.binding.title, createdAt, query.zoneId)
        CompanionHistoryGroup(
            id,
            title,
            first.encounterId,
            dates,
            values.size,
            values.firstOrNull { it.readerEligible }?.message?.id,
            first.encounterId == null,
            createdAt
        )
    }.sortedWith(compareBy({ it.createdAt }, { it.id }))

    fun emptyEncounterGroup(
        encounter: CompanionEncounter,
        binding: CompanionReadingBinding,
        query: CompanionHistoryQuery,
    ): CompanionHistoryGroup? {
        val date = if (encounter.createdAt == 0L) "日期未知"
        else Instant.ofEpochMilli(encounter.createdAt).atZone(ZoneId.of(query.zoneId)).toLocalDate()
            .toString()
        if (query.legacyOnly || query.encounterId?.let { it != encounter.id } == true ||
            query.conversationId?.let { it != encounter.context.conversationId } == true ||
            query.date?.let { it != date } == true || query.month?.let { !date.startsWith("$it-") } == true) return null
        return CompanionHistoryGroup(
            "encounter:${encounter.id}",
            encounterTitle(binding.title, encounter.createdAt, query.zoneId),
            encounter.id,
            listOf(date),
            0,
            null,
            false,
            encounter.createdAt
        )
    }

    private fun encounterTitle(title: String, createdAt: Long, zoneId: String): String {
        val time = if (createdAt == 0L) "时间未知" else Instant.ofEpochMilli(createdAt)
            .atZone(ZoneId.of(zoneId))
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        return "场次 · $title · $time"
    }
}
