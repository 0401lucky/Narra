package com.example.myapplication.conversation

import com.example.myapplication.model.MemoryEventSummaryMode
import com.example.myapplication.model.MemoryRecordKind
import com.example.myapplication.model.MemoryRecordStatus
import com.example.myapplication.model.MemoryRecordVersion
import com.example.myapplication.model.MemorySourceKind
import com.example.myapplication.model.MemorySourceStatus
import com.example.myapplication.testutil.MemoryEventPromptTestFixtures.member
import com.example.myapplication.testutil.MemoryEventPromptTestFixtures.snapshot
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEventSummaryFormatterTest {
    @Test
    fun format_fullUsesAllRawMembersAndDoesNotReuseStaleSummary() {
        val input = snapshot(25).copy(
            previousSummary = "旧摘要遗漏哨兵",
            previousState = "旧状态哨兵",
            previousCoverage = listOf(MemoryRecordVersion("stale-member", 1L)),
        )

        val json = MemoryEventSummaryFormatter.format(input)
        val data = JsonParser.parseString(json).asJsonObject

        assertEquals("full", data["mode"].asString)
        assertFalse(data.has("previous_summary"))
        assertFalse(json.contains("旧摘要遗漏哨兵"))
        assertFalse(json.contains("旧状态哨兵"))
        assertEquals(input.members.size, data.getAsJsonArray("members").size())
        input.members.forEachIndexed { index, member ->
            val actual = data.getAsJsonArray("members")[index].asJsonObject
            assertEquals("M$index", actual["ref"].asString)
            assertEquals(member.record.content, actual["content"].asString)
            assertEquals(member.record.revision, actual["revision"].asLong)
            assertEquals(
                member.sources.single().content,
                actual.getAsJsonArray("sources")[0].asJsonObject["text"].asString
            )
        }
    }

    @Test
    fun format_incrementalKeepsOldCoverageSeparateFromEveryNewMember() {
        val input = snapshot(mode = MemoryEventSummaryMode.INCREMENTAL)
        val data = JsonParser.parseString(MemoryEventSummaryFormatter.format(input)).asJsonObject
        val previous = data.getAsJsonObject("previous_summary")

        assertEquals(input.previousSummary, previous["summary"].asString)
        assertEquals(input.previousState, previous["current_state"].asString)
        assertEquals("published_summary", previous["origin"].asString)
        assertEquals(input.summaryRevision, previous["revision"].asLong)
        val coverage = previous.getAsJsonArray("covered_records").single().asJsonObject
        assertEquals("P0", coverage["ref"].asString)
        assertEquals(6L, coverage["revision"].asLong)
        assertFalse(coverage.has("id"))
        assertFalse(coverage.has("record_id"))
        assertEquals(3, data.getAsJsonArray("members").size())
    }

    @Test
    fun format_currentWorkPreviousSummaryKeepsRealZeroBaselineAndUnpublishedOrigin() {
        val input = snapshot(mode = MemoryEventSummaryMode.INCREMENTAL).copy(
            summaryRevision = 0L,
            previousSummaryFromCurrentWork = true,
        )
        val previous =
            JsonParser.parseString(MemoryEventSummaryFormatter.format(input)).asJsonObject
                .getAsJsonObject("previous_summary")

        assertEquals("current_work_completed_batches", previous["origin"].asString)
        assertEquals(0L, previous["base_summary_revision"].asLong)
        assertFalse(previous.has("revision"))
        assertEquals(input.previousSummary, previous["summary"].asString)
        assertRejected { MemoryEventSummaryFormatter.format(input.copy(mode = MemoryEventSummaryMode.FULL)) }
    }

    @Test
    fun format_incrementalRequiresReliableAndDisjointPreviousCoverage() {
        val input = snapshot(mode = MemoryEventSummaryMode.INCREMENTAL)
        val invalid = listOf(
            input.copy(previousSummary = " "),
            input.copy(previousState = " "),
            input.copy(summaryRevision = 0),
            input.copy(previousCoverage = emptyList()),
            input.copy(previousCoverage = input.previousCoverage + input.previousCoverage),
            input.copy(previousCoverage = listOf(MemoryRecordVersion("old", 0L))),
            input.copy(
                previousCoverage = listOf(
                    MemoryRecordVersion(
                        input.members[0].record.id,
                        1L
                    )
                )
            ),
        )
        invalid.forEach { assertRejected { MemoryEventSummaryFormatter.format(it) } }
    }

    @Test
    fun format_rejectsDuplicateStaleOrForeignMembers() {
        val input = snapshot(1)
        val first = input.members.single()
        val invalidRecords = listOf(
            first.record.copy(id = ""),
            first.record.copy(revision = 0),
            first.record.copy(content = " "),
            first.record.copy(status = MemoryRecordStatus.STALE),
            first.record.copy(status = MemoryRecordStatus.DELETED),
        )
        invalidRecords.forEach { record ->
            assertRejected {
                MemoryEventSummaryFormatter.format(
                    input.copy(
                        members = listOf(
                            first.copy(
                                record = record
                            )
                        )
                    )
                )
            }
        }
        assertRejected {
            MemoryEventSummaryFormatter.format(
                input.copy(
                    members = listOf(
                        first,
                        first
                    )
                )
            )
        }
        listOf(
            member(1).record.copy(characterId = "other-character"),
            member(1).record.copy(spaceId = "other-space"),
            member(1).record.copy(personaKey = "other-persona"),
        ).forEach { record ->
            assertRejected {
                MemoryEventSummaryFormatter.format(
                    input.copy(
                        members = listOf(
                            first,
                            member(1).copy(record = record)
                        )
                    )
                )
            }
        }
    }

    @Test
    fun format_rejectsUnavailableStaleForeignOrMissingSources() {
        val input = snapshot(1)
        val first = input.members.single()
        val source = first.sources.single()
        val invalidSources =
            MemorySourceStatus.entries.filter { it != MemorySourceStatus.AVAILABLE }
                .map { source.copy(status = it) } + listOf(
                source.copy(messageRevision = source.messageRevision + 1),
                source.copy(characterId = "other-character"),
                source.copy(spaceId = "other-space"),
                source.copy(personaKey = "other-persona"),
                source.copy(contentLength = source.contentLength + 1),
                source.copy(id = "other-source"),
                source.copy(kind = MemorySourceKind.LEGACY_HINT),
            )
        invalidSources.forEach { invalid ->
            assertRejected {
                MemoryEventSummaryFormatter.format(
                    input.copy(
                        members = listOf(
                            first.copy(
                                sources = listOf(invalid)
                            )
                        )
                    )
                )
            }
        }
        listOf(
            first.copy(sources = emptyList()),
            first.copy(sources = listOf(source, source)),
            first.copy(sourceRefs = emptyList()),
            first.copy(sourceRefs = first.sourceRefs + first.sourceRefs),
            first.copy(sourceRefs = listOf(first.sourceRefs.single().copy(start = -1))),
            first.copy(
                sourceRefs = listOf(
                    first.sourceRefs.single().copy(end = source.contentLength + 1)
                )
            ),
        ).forEach { invalid ->
            assertRejected { MemoryEventSummaryFormatter.format(input.copy(members = listOf(invalid))) }
        }
    }

    @Test
    fun format_sendsExactFullSourceSpanAndRejectsSplitSurrogate() {
        val prefix = "未关联前缀"
        val selected = "😊 来源完整尾部 {{char}} <user> 706"
        val first = member(0, sourceText = prefix + selected + "未关联后缀")
        val ref = first.sourceRefs.single()
            .copy(start = prefix.length, end = prefix.length + selected.length)
        val input = snapshot(1).copy(members = listOf(first.copy(sourceRefs = listOf(ref))))

        val data = JsonParser.parseString(MemoryEventSummaryFormatter.format(input)).asJsonObject
        val source =
            data.getAsJsonArray("members")[0].asJsonObject.getAsJsonArray("sources")[0].asJsonObject

        assertEquals(selected, source["text"].asString)
        assertEquals(ref.start, source["span_start"].asInt)
        assertEquals(ref.end, source["span_end"].asInt)
        listOf(
            ref.copy(start = prefix.length + 1),
            ref.copy(end = prefix.length + 1)
        ).forEach { invalid ->
            assertRejected {
                MemoryEventSummaryFormatter.format(
                    input.copy(
                        members = listOf(
                            first.copy(
                                sourceRefs = listOf(invalid)
                            )
                        )
                    )
                )
            }
        }
    }

    @Test
    fun format_keepsAllSourceSpansForOneMember() {
        val members = List(24) { member(it) }
        val first = members.first().copy(
            sourceRefs = members.flatMap { it.sourceRefs },
            sources = members.flatMap { it.sources },
        )
        val data = JsonParser.parseString(
            MemoryEventSummaryFormatter.format(
                snapshot(1).copy(
                    members = listOf(first)
                )
            )
        ).asJsonObject
        val sources = data.getAsJsonArray("members")[0].asJsonObject.getAsJsonArray("sources")

        assertEquals(24, sources.size())
        members.forEachIndexed { index, member ->
            assertEquals("M0S$index", sources[index].asJsonObject["ref"].asString)
            assertEquals(
                member.sources.single().content,
                sources[index].asJsonObject["text"].asString
            )
        }
    }

    @Test
    fun format_preservesKnowledgeKindsSourceKindsAndSeparateTimes() {
        val members =
            MemoryRecordKind.entries.mapIndexed { index, kind -> member(index, kind = kind) }
                .mapIndexed { index, member ->
                    member.copy(
                        record = member.record.copy(occurredAt = null, datePrecision = "unknown"),
                        sources = member.sources.map {
                            it.copy(
                                kind = if (index % 2 == 0) MemorySourceKind.IMPORTED else MemorySourceKind.MANUAL,
                                occurredAt = 0
                            )
                        },
                    )
                }
        val data =
            JsonParser.parseString(MemoryEventSummaryFormatter.format(snapshot().copy(members = members))).asJsonObject

        members.forEachIndexed { index, member ->
            val actual = data.getAsJsonArray("members")[index].asJsonObject
            assertEquals(member.record.kind.name.lowercase(), actual["kind"].asString)
            assertEquals("unknown", actual["date_precision"].asString)
            assertFalse(actual.has("occurred_at"))
            assertEquals(member.record.createdAt, actual["ingested_at"].asLong)
            val source = actual.getAsJsonArray("sources")[0].asJsonObject
            assertEquals(
                member.sources.single().kind.name.lowercase(),
                source["source_kind"].asString
            )
            assertEquals(0L, source["occurred_at"].asLong)
            assertEquals(member.sources.single().ingestedAt, source["ingested_at"].asLong)
        }
    }

    @Test
    fun format_limitCountsEscapedMaterialAndRulesWithoutTruncation() {
        val input = snapshot(1)
        val first = input.members.single()
        val initialLength =
            MemoryEventSummaryFormatter.format(input).length + MemoryEventSummaryFormatter.systemPrompt.length
        val fillerLength =
            MemoryEventSummaryFormatter.MAX_INPUT_CHARACTERS - initialLength + first.record.content.length
        val fullText = "x".repeat(fillerLength - 3) + "尾部证"
        val atLimit =
            input.copy(members = listOf(first.copy(record = first.record.copy(content = fullText))))

        val rendered = MemoryEventSummaryFormatter.format(atLimit)

        assertEquals(
            MemoryEventSummaryFormatter.MAX_INPUT_CHARACTERS,
            rendered.length + MemoryEventSummaryFormatter.systemPrompt.length
        )
        assertTrue(rendered.contains("尾部证"))
        assertRejected {
            MemoryEventSummaryFormatter.format(
                atLimit.copy(
                    members = listOf(
                        first.copy(
                            record = first.record.copy(
                                content = fullText + "x"
                            )
                        )
                    )
                )
            )
        }
        assertRejected {
            MemoryEventSummaryFormatter.format(
                atLimit.copy(
                    members = listOf(
                        first.copy(
                            record = first.record.copy(
                                content = fullText.dropLast(1) + "\n"
                            )
                        )
                    )
                )
            )
        }
    }

    private fun assertRejected(block: () -> Unit) {
        assertTrue(runCatching(block).exceptionOrNull() is IllegalArgumentException)
    }
}
