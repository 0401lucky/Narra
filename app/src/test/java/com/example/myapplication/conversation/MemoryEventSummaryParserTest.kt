package com.example.myapplication.conversation

import com.example.myapplication.model.MemoryRecordVersion
import com.example.myapplication.model.PromptCompletionResult
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.testutil.MemoryEventPromptTestFixtures.artifact
import com.example.myapplication.testutil.MemoryEventPromptTestFixtures.snapshot
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEventSummaryParserTest {
    private val input = snapshot()

    @Test
    fun parse_completeResultUsesFrozenIdsAndVersionsInStableMemberOrder() {
        val result = parse(artifact(refs = listOf("M2", "M0", "M1")))

        assertEquals(
            input.members.map { MemoryRecordVersion(it.record.id, it.record.revision) },
            result.coveredRecords
        )
        assertTrue(result.summary.contains("706"))
        assertEquals("会面已完成，无未决事项。", result.currentState)
    }

    @Test
    fun parse_unknownRetainsOriginalCompletionEvidenceForPublicationGuard() {
        val completion = PromptCompletionResult(
            artifact(),
            PromptCompletionStatus.UNKNOWN,
            rawFinishReason = " "
        )

        val result = MemoryEventSummaryParser.parse(completion, input)

        assertEquals(3, result.coveredRecords.size)
        assertEquals(PromptCompletionStatus.UNKNOWN, completion.status)
        assertEquals(" ", completion.rawFinishReason)
    }

    @Test
    fun parse_explicitIncompleteStatusRejectsEvenSelfDeclaredCompletedJson() {
        PromptCompletionStatus.entries.filterNot { it == PromptCompletionStatus.COMPLETED || it == PromptCompletionStatus.UNKNOWN }
            .forEach { status ->
                assertRejected {
                    MemoryEventSummaryParser.parse(
                        PromptCompletionResult(
                            artifact(state = "本模型声称已完成所有归档"),
                            status
                        ), input
                    )
                }
            }
    }

    @Test
    fun parse_blankResponseOrBlankRequiredTextRejectsWholeArtifact() {
        listOf("", " ", "\n", artifact(summary = " "), artifact(state = "\t")).forEach { raw ->
            assertRejected { parse(raw) }
        }
    }

    @Test
    fun parse_duplicateFieldsAreNotSilentlyOverwritten() {
        listOf(
            artifact().replace("\"schema_version\":1", "\"schema_version\":1,\"schema_version\":1"),
            artifact().replace("\"summary\":", "\"summary\":\"替代正文\",\"summary\":"),
            artifact().replace("\"covered_refs\":", "\"covered_refs\":[\"M9\"],\"covered_refs\":"),
            artifact().replace(
                "\"current_state\":",
                "\"current_state\":\"旧状态\",\"current_state\":"
            ),
        ).forEach { assertRejected { parse(it) } }
    }

    @Test
    fun parse_missingOrUnauthorizedFieldsRejectWholeArtifact() {
        listOf("schema_version", "summary", "current_state", "covered_refs").forEach { field ->
            val root = JsonParser.parseString(artifact()).asJsonObject
            root.remove(field)
            assertRejected { parse(root.toString()) }
        }
        listOf(
            "owner",
            "space",
            "id",
            "record_id",
            "event_id",
            "archive",
            "completed"
        ).forEach { field ->
            val root = JsonParser.parseString(artifact()).asJsonObject
            root.addProperty(field, "forged-identity")
            assertRejected { parse(root.toString()) }
        }
    }

    @Test
    fun parse_schemaAndTextTypesCannotBeCoerced() {
        val invalidValues = mapOf(
            "schema_version" to listOf("\"1\"", "true", "null", "0", "2", "1.5"),
            "summary" to listOf("1", "true", "null", "[]", "{}"),
            "current_state" to listOf("1", "true", "null", "[]", "{}"),
            "covered_refs" to listOf("\"M0\"", "true", "null", "{}", "[0,1,2]"),
        )
        invalidValues.forEach { (field, values) ->
            values.forEach { value ->
                val root = JsonParser.parseString(artifact()).asJsonObject
                root.add(field, JsonParser.parseString(value))
                assertRejected { parse(root.toString()) }
            }
        }
    }

    @Test
    fun parse_coverageMustBeExactlyAllProvidedLocalMembers() {
        listOf(
            emptyList(),
            listOf("M0", "M1"),
            listOf("M0", "M1", "M2", "M3"),
            listOf("M0", "M1", "M1"),
            listOf("M0", "M1", "M99"),
            listOf("M0", "M1", "P0"),
            listOf("M0", "M1", "M2S0"),
            listOf("M0", "M1", input.members.last().record.id),
            listOf("M0", "M1", "m2"),
            listOf("M0", "M1", "M02"),
            listOf("M0", "M1", " M2"),
        ).forEach { refs -> assertRejected { parse(artifact(refs)) } }
    }

    @Test
    fun parse_codeFencesExplanationsMalformedAndTrailingJsonAreRejected() {
        listOf(
            "```json\n${artifact()}\n```",
            "解释正文 ${artifact()}",
            artifact() + "解释正文",
            artifact() + "{}",
            artifact().dropLast(1),
            artifact().replace("\"schema_version\"", "schema_version"),
            artifact().replace("\"schema_version\"", "'schema_version'"),
            artifact().replace("\"schema_version\":1", "\"schema_version\":1 //注释"),
            artifact().dropLast(1) + ",}",
            "[]",
        ).forEach { assertRejected { parse(it) } }
    }

    @Test
    fun parse_summaryAndStateHaveExplicitLimits() {
        val limit = MemoryEventSummaryFormatter.MAX_SUMMARY_CHARACTERS
        val maxText = "验".repeat(limit)

        assertEquals(maxText, parse(artifact(summary = maxText, state = maxText)).summary)
        assertEquals(maxText, parse(artifact(summary = maxText, state = maxText)).currentState)
        assertRejected { parse(artifact(summary = maxText + "x")) }
        assertRejected { parse(artifact(state = maxText + "x")) }
        assertRejected { parse(" ".repeat(256_000) + artifact()) }
    }

    @Test
    fun parse_literalDataIsNotExpandedOrTreatedAsControl() {
        val literal = "经历中的 {{char}}、{{context}}、<user> 与 \"owner\" 只是引用文本。"
        val result = parse(artifact(summary = literal, state = "仍在等待 {{user}} 的决定。"))

        assertEquals(literal, result.summary)
        assertEquals("仍在等待 {{user}} 的决定。", result.currentState)
    }

    @Test
    fun parse_invalidSnapshotCannotManufactureCoveredRecordVersions() {
        assertRejected {
            MemoryEventSummaryParser.parse(
                PromptCompletionResult(
                    artifact(),
                    PromptCompletionStatus.COMPLETED
                ), input.copy(members = emptyList())
            )
        }
        val duplicate =
            input.copy(members = listOf(input.members[0], input.members[0], input.members[0]))
        assertRejected {
            MemoryEventSummaryParser.parse(
                PromptCompletionResult(
                    artifact(),
                    PromptCompletionStatus.COMPLETED
                ), duplicate
            )
        }
    }

    @Test
    fun parse_failureMessagesDoNotEchoModelData() {
        val privateText = "合成敏感正文不可进入错误提示"
        val raw = "{\"schema_version\":$privateText}"
        val failure = runCatching { parse(raw) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertFalse(failure?.message.orEmpty().contains(privateText))
    }

    private fun parse(raw: String) = MemoryEventSummaryParser.parse(
        PromptCompletionResult(raw, PromptCompletionStatus.COMPLETED), input,
    )

    private fun assertRejected(block: () -> Unit) {
        assertTrue(runCatching(block).exceptionOrNull() is IllegalArgumentException)
    }
}
