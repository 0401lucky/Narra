package com.example.myapplication.testutil

import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.model.MemoryCategory
import com.example.myapplication.model.MemoryEventSummaryMember
import com.example.myapplication.model.MemoryEventSummaryMode
import com.example.myapplication.model.MemoryEventSummarySnapshot
import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.model.MemoryRecordKind
import com.example.myapplication.model.MemoryRecordVersion
import com.example.myapplication.model.MemorySourceEvidence
import com.example.myapplication.model.MemorySourceKind
import com.example.myapplication.model.MemorySourceStatus
import com.example.myapplication.system.json.AppJson

/** 仅用于事件 Prompt/Parser 的合成材料，不读取应用设置、用户数据库或真实模型。 */
object MemoryEventPromptTestFixtures {
    const val ORIGIN_TIME = 1_800_000_000_000L

    fun member(
        index: Int,
        content: String = "合成事件成员 $index：阿澄与角色处理 706 号预约。",
        sourceText: String = "合成原始来源 $index：完整记载 706 号预约的进展。",
        kind: MemoryRecordKind = MemoryRecordKind.EPISODE,
    ): MemoryEventSummaryMember {
        val occurredAt = ORIGIN_TIME + index * 86_400_000L
        val source = MemorySourceEvidence(
            id = "synthetic-source-$index",
            spaceId = "synthetic-space",
            characterId = "synthetic-character",
            kind = MemorySourceKind.MESSAGE,
            conversationId = "synthetic-conversation",
            messageId = "synthetic-message-$index",
            messageRevision = index + 4L,
            speakerId = "synthetic-user",
            speakerRole = "USER",
            personaKey = "synthetic-persona",
            visibleCharacterIds = listOf("synthetic-character"),
            content = sourceText,
            contentLength = sourceText.length,
            status = MemorySourceStatus.AVAILABLE,
            occurredAt = occurredAt,
            ingestedAt = occurredAt + 1_000L,
            contentFingerprint = MemoryArchiveIdentity.fingerprint(sourceText),
            contentFormatVersion = 2,
        )
        return MemoryEventSummaryMember(
            record = MemoryRecord(
                id = "synthetic-record-$index",
                spaceId = source.spaceId,
                characterId = source.characterId,
                kind = kind,
                category = MemoryCategory.SHARED,
                content = content,
                revision = index + 2L,
                occurredAt = occurredAt,
                datePrecision = "day",
                personaKey = source.personaKey,
                createdAt = occurredAt + 3_000L,
                updatedAt = occurredAt + 3_000L,
            ),
            sourceRefs = listOf(source.fullRef()),
            sources = listOf(source),
        )
    }

    fun snapshot(
        count: Int = 3,
        mode: MemoryEventSummaryMode = MemoryEventSummaryMode.FULL,
    ): MemoryEventSummarySnapshot = MemoryEventSummarySnapshot(
        eventId = "synthetic-event",
        title = "合成预约事件",
        mode = mode,
        expectedGeneration = 8L,
        summaryRevision = if (mode == MemoryEventSummaryMode.INCREMENTAL) 3L else 0L,
        previousSummary = if (mode == MemoryEventSummaryMode.INCREMENTAL) "先前确认双方准备处理预约。" else "",
        previousState = if (mode == MemoryEventSummaryMode.INCREMENTAL) "等待后续结果。" else "",
        previousCoverage = if (mode == MemoryEventSummaryMode.INCREMENTAL) {
            listOf(MemoryRecordVersion("synthetic-prior-record", 6L))
        } else {
            emptyList()
        },
        members = List(count) { member(it) },
        timeZoneId = "Asia/Shanghai",
        characterName = "合成角色",
        userName = "合成用户",
    )

    fun artifact(
        refs: List<String> = listOf("M0", "M1", "M2"),
        summary: String = "双方先预约，随后确认场地，最终已在 706 号房完成会面。",
        state: String = "会面已完成，无未决事项。",
    ): String = AppJson.gson.toJson(
        mapOf(
            "schema_version" to 1,
            "summary" to summary,
            "current_state" to state,
            "covered_refs" to refs,
        ),
    )
}
