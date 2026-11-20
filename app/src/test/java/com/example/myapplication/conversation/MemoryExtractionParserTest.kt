package com.example.myapplication.conversation

import com.example.myapplication.model.MemoryExtractionFormat
import com.example.myapplication.model.MemoryExtractionSnapshot
import com.example.myapplication.model.MemoryExtractionSpan
import com.example.myapplication.model.MemoryExtractionTarget
import com.example.myapplication.model.MemoryRecordKind
import com.example.myapplication.model.MemorySemanticKind
import com.example.myapplication.model.MemorySourceKind
import com.example.myapplication.model.MemorySourceRef
import com.example.myapplication.model.PromptCompletionResult
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.system.json.AppJson
import org.junit.Assert.*
import org.junit.Test

class MemoryExtractionParserTest {
    private val ref = MemorySourceRef("source", 3, 0, 12)
    private val snapshot = MemoryExtractionSnapshot(
        listOf(
            MemoryExtractionSpan(
                ref, "message", "user", "USER", "persona", listOf("a"), 1_733_097_600_000L,
                MemorySourceKind.MESSAGE, MemorySemanticKind.SPEECH, "明天去706号房见阿澄"
            )
        ),
        listOf(MemoryExtractionTarget("old", 7, "旧地址")),
        "角色",
        "用户",
        timeZoneId = "Asia/Shanghai",
    )

    @Test
    fun empty_completeAndUnknownAreExplicitValidReceipts() {
        listOf(PromptCompletionStatus.COMPLETED, PromptCompletionStatus.UNKNOWN).forEach { status ->
            assertTrue(
                MemoryExtractionParser.parse(
                    PromptCompletionResult(empty(), status),
                    snapshot
                ).records.isEmpty()
            )
        }
    }

    @Test
    fun incompleteOrMissingBodyNeverBecomesEmpty() {
        PromptCompletionStatus.entries.filterNot {
            it in setOf(
                PromptCompletionStatus.COMPLETED,
                PromptCompletionStatus.UNKNOWN
            )
        }.forEach {
            assertFails {
                MemoryExtractionParser.parse(
                    PromptCompletionResult(empty(), it),
                    snapshot
                )
            }
        }
        assertFails { parse("") }
        assertFails { parse("   ") }
    }

    @Test
    fun duplicateKeysTrailingJsonAndCoercedFieldsAreRejected() {
        assertFails {
            parse(
                empty().replace(
                    "\"schema_version\":2",
                    "\"schema_version\":2,\"schema_version\":2"
                )
            )
        }
        assertFails { parse(empty() + "{}") }
        assertFails { parse(empty().replace("\"schema_version\":2", "\"schema_version\":\"2\"")) }
        assertFails { parse(empty().replace("\"memories\":[]", "\"memories\":null")) }
        assertFails { parse("```json\n${empty()}\n```") }
    }

    @Test
    fun recordsMapLocalSourcesAndKnownTargetsWithoutOwnerFields() {
        val result = parse(body(memories = listOf(memory())))
        assertEquals(ref, result.records.single().sources.single())
        assertEquals(MemoryRecordKind.EPISODE, result.records.single().kind)
        assertEquals("old", result.relations.single().targetRecordId)
    }

    @Test
    fun invalidSourceOwnerEnumAndDuplicateLocalIdRejectWholeBatch() {
        assertFails { parse(body(memories = listOf(memory() + ("source_refs" to listOf("M9"))))) }
        assertFails { parse(body(memories = listOf(memory() + ("owner" to "b")))) }
        assertFails { parse(body(memories = listOf(memory() + ("kind" to "certain_fact")))) }
        assertFails { parse(body(memories = listOf(memory(), memory()))) }
    }

    @Test
    fun relationSelfAndUnavailableTargetAreRejected() {
        assertFails { parse(body(memories = listOf(memory() + ("related_to" to listOf("N0"))))) }
        assertFails { parse(body(memories = listOf(memory() + ("related_to" to listOf("O8"))))) }
        assertFails { parse(body(memories = listOf(memory() + ("source_refs" to emptyList<String>())))) }
    }

    @Test
    fun correctionUsesFrozenExpectedRevisionAndPreservesId() {
        val correction = mapOf(
            "target_ref" to "O0",
            "expected_revision" to 7,
            "replacement" to "地址已改为706",
            "source_refs" to listOf("M0")
        )
        val result = parse(body(corrections = listOf(correction)))
        assertEquals("old", result.corrections.single().recordId)
        assertEquals(7L, result.corrections.single().expectedRevision)
        assertFails { parse(body(corrections = listOf(correction + ("expected_revision" to 8)))) }
        assertFails { parse(body(corrections = listOf(correction, correction))) }
    }

    @Test
    fun occurredDateUsesFrozenTimezoneAndUnknownRemainsNull() {
        assertNull(parse(body(memories = listOf(memory()))).records.single().occurredAt)
        val record =
            parse(body(memories = listOf(memory() + ("occurred_date" to "2024-12-02")))).records.single()
        assertEquals(
            java.time.ZonedDateTime.parse("2024-12-02T00:00:00+08:00[Asia/Shanghai]").toInstant()
                .toEpochMilli(), record.occurredAt
        )
        assertFails { parse(body(memories = listOf(memory() + ("occurred_date" to "2024-02-31")))) }
    }

    @Test
    fun deferredActionsRequireKnownSourcesAndStrictEnums() {
        val state = mapOf(
            "state_key" to "location",
            "operation" to "set",
            "content" to "706号房",
            "source_refs" to listOf("M0")
        )
        val resident = mapOf(
            "category" to "relationship",
            "content" to "角色觉得对方可信",
            "subjective" to true,
            "source_refs" to listOf("M0")
        )
        parse(body(states = listOf(state), residents = listOf(resident)))
        assertFails { parse(body(states = listOf(state + ("source_refs" to listOf("M99"))))) }
        assertFails { parse(body(residents = listOf(resident + ("subjective" to "true")))) }
    }

    @Test
    fun legacyTemplateIsExplicitAndLinksWholeBatch() {
        val legacy = snapshot.copy(
            format = MemoryExtractionFormat.LEGACY_LIST,
            promptOverride = "保留我的模板"
        )
        val result = MemoryExtractionParser.parse(
            PromptCompletionResult(
                "[\"旧格式的事实\"]",
                PromptCompletionStatus.COMPLETED
            ), legacy
        )
        assertEquals(MemoryRecordKind.LEGACY_UNCLASSIFIED, result.records.single().kind)
        assertEquals(listOf(ref), result.records.single().sources)
        assertFails {
            MemoryExtractionParser.parse(
                PromptCompletionResult(
                    "随便的解释文字",
                    PromptCompletionStatus.COMPLETED
                ), legacy
            )
        }
        assertFails { parse("[\"不能走旧解析回退\"]") }
    }

    private fun memory(): Map<String, Any?> = mapOf(
        "local_id" to "N0",
        "kind" to "episode",
        "category" to "shared",
        "content" to "约好去706号房见阿澄",
        "source_refs" to listOf("M0"),
        "related_to" to listOf("O0"),
        "occurred_date" to null
    )

    private fun empty() = body()
    private fun body(
        memories: List<Map<String, Any?>> = emptyList(),
        corrections: List<Map<String, Any?>> = emptyList(),
        states: List<Map<String, Any?>> = emptyList(),
        residents: List<Map<String, Any?>> = emptyList()
    ): String {
        val root = AppJson.gson.toJsonTree(
            mapOf(
                "schema_version" to 2, "memories" to memories, "corrections" to corrections,
                "state_updates" to states, "resident_candidates" to residents
            )
        ).asJsonObject
        root.getAsJsonArray("memories").forEachIndexed { index, item ->
            if (memories[index].containsKey("occurred_date") && memories[index]["occurred_date"] == null) item.asJsonObject.add(
                "occurred_date",
                com.google.gson.JsonNull.INSTANCE
            )
        }
        return root.toString()
    }

    private fun parse(text: String) = MemoryExtractionParser.parse(
        PromptCompletionResult(text, PromptCompletionStatus.COMPLETED),
        snapshot
    )

    private fun assertFails(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }
}
