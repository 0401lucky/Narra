package com.example.myapplication.data.repository.context

import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.model.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class MemoryArchiveTransferCodecTest {
    private val codec = ContextTransferCodec()

    @Test
    fun strictGraphRejectsMalformedPackages() {
        val original = bundle()
        val validV3 = JsonParser.parseString(codec.encode(original)).asJsonObject.apply {
            addProperty("version", 3)
            remove("companions")
        }
        assertFalse(validV3.has("companions"))
        val decodedV3 = codec.decode(validV3.toString())
        assertEquals(3, decodedV3.version)
        assertNull(decodedV3.companions)
        assertEquals(original.memoryArchive, decodedV3.memoryArchive)
        val cases: List<(JsonObject) -> Unit> = listOf(
            { it.addProperty("version", 4) },
            { it.remove("exportedAt") },
            { it.archive().remove("sources") },
            { it.archive().addProperty("sources", "not-an-array") },
            { it.archive().getAsJsonArray("sources")[0].asJsonObject.remove("contentFingerprint") },
            {
                it.archive().getAsJsonArray("sources")[0].asJsonObject.addProperty(
                    "status",
                    "UNSUPPORTED"
                )
            },
            {
                it.archive().getAsJsonArray("sources")[0].asJsonObject.addProperty(
                    "contentLength",
                    99
                )
            },
            {
                it.archive().getAsJsonArray("sources")[0].asJsonObject.addProperty(
                    "contentFingerprint",
                    "0".repeat(64)
                )
            },
            {
                it.archive().getAsJsonArray("sources")[0].asJsonObject.addProperty(
                    "characterId",
                    "other"
                )
            },
            {
                it.archive().getAsJsonArray("records")
                    .add(it.archive().getAsJsonArray("records")[0].deepCopy())
            },
            {
                it.archive().getAsJsonArray("recordRevisions")[0].asJsonObject.addProperty(
                    "revision",
                    1.5
                )
            },
            {
                it.archive()
                    .getAsJsonArray("recordSources")[0].asJsonObject.getAsJsonObject("source")
                    .addProperty("sourceId", "missing")
            },
            {
                it.archive()
                    .getAsJsonArray("recordSources")[0].asJsonObject.getAsJsonObject("source")
                    .addProperty("end", 2)
            },
            {
                it.archive().getAsJsonArray("recordRevisions")[0].asJsonObject.addProperty(
                    "content",
                    "different body"
                )
            },
            { it.archive().add("jobs", com.google.gson.JsonArray()) },
        )
        cases.forEachIndexed { index, mutate ->
            val root = JsonParser.parseString(codec.encode(bundle())).asJsonObject
            mutate(root)
            try {
                codec.decode(root.toString())
                fail("坏包 $index 应被拒绝")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertRejected("{\"version\":2,\"version\":1}")
        assertRejected(codec.encode(bundle()) + " trailing")
    }

    @Test
    fun sourceOnlyAndUnavailableHistoryRemainExplicit() {
        val base = requireNotNull(bundle().memoryArchive)
        val resident = MemoryResidentEntry(
            "resident", "space", "a", "unknown", MemoryResidentCategory.USER,
            "仅有直接来源的认知", 1L, "READY", false, false, null, 1L, 1L
        )
        val revision = MemoryKnowledgeRevision(
            MemoryMaterialKind.RESIDENT,
            resident.id,
            1L,
            resident.content,
            "READY",
            MemoryChangeOrigin.EXTRACTION,
            false,
            null,
            null,
            listOf(base.sources.single().fullRef()),
            emptyList(),
            1L
        )
        val graph = base.copy(
            records = emptyList(), recordRevisions = emptyList(), recordSources = emptyList(),
            residents = listOf(resident), knowledgeRevisions = listOf(revision)
        )
        val decoded = codec.decode(codec.encode(bundle().copy(memoryArchive = graph)))
        assertFalse(requireNotNull(decoded.memoryArchive).isEmpty)
        assertTrue(decoded.memoryArchive!!.records.isEmpty())
        val unknown = graph.copy(sources = graph.sources.map {
            it.copy(
                status = MemorySourceStatus.CHANGED,
                content = ""
            )
        })
        val restored =
            codec.decode(codec.encode(bundle().copy(memoryArchive = unknown))).memoryArchive!!
        assertEquals(MemorySourceStatus.CHANGED, restored.sources.single().status)
        assertEquals("", restored.sources.single().content)
        assertEquals(4, restored.sources.single().contentLength)
        assertRejected(codec.encode(bundle().copy(memoryArchive = graph.copy(knowledgeRevisions = emptyList()))))
    }

    @Test
    fun canonicalV2DoesNotImportLegacyProjectionTwice() {
        val withProjection = bundle().copy(
            memoryEntries = listOf(
                MemoryEntry(
                    id = "record",
                    content = "兼容副本",
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        )
        val data =
            MemoryTransferLegacyConversion.materialize(codec.decode(codec.encode(withProjection)))
        assertEquals(1, data.records.size)
        assertEquals("精确正文\n保持空格  ", data.records.single().content)
        assertEquals(1, data.sources.size)
    }

    @Test
    fun unknownVersionCannotBecomeAnEmptyBundle() {
        assertRejected("{\"version\":999,\"assistants\":[]}")
        assertRejected(codec.encode(bundle()).replace("\"version\":2", "\"version\":1"))
    }

    @Test
    fun malformedLegacyMemoryIsRejectedBeforeMapping() {
        assertRejected("""{"version":1,"memoryEntries":[{"content":"缺少原ID"}]}""")
        assertRejected("""{"version":1,"memoryEntries":[{"id":"old","content":null}]}""")
        assertRejected("""{"version":1,"memoryEntries":[{"id":"old","content":"正文","scopeType":"INVALID"}]}""")
        assertRejected("""{"version":1,"conversationSummaries":[{"conversationId":"c","summary":42}]}""")
        assertRejected("""{"version":1,"memoryEntries":[{"id":"old","content":"正文","importance":4294967296}]}""")
    }

    @Test
    fun provenanceCannotPromoteUnavailableSources() {
        val graph = requireNotNull(bundle().memoryArchive)
        for (status in MemorySourceStatus.entries.filter { it != MemorySourceStatus.AVAILABLE }) {
            val origin = MemoryTransferOrigin(
                "prior-import", 2, 1L, 2L, "original-source", "original-space", "a", "unknown", 1L,
                originalSourceKind = MemorySourceKind.MESSAGE, originalSourceStatus = status
            )
            val linked =
                MemoryTransferOriginLink(MemoryTransferObjectKind.SOURCE, "source", 1L, origin)
            assertRejected(
                codec.encode(
                    bundle().copy(
                        memoryArchive = graph.copy(
                            origins = listOf(
                                linked
                            )
                        )
                    )
                )
            )
        }
        val deleted = graph.copy(sources = graph.sources.map {
            it.copy(
                status = MemorySourceStatus.DELETED,
                content = ""
            )
        })
        // 删除标记不仅阻止召回；旧记录缓存也不能再恢复已删除的正文。
        assertRejected(codec.encode(bundle().copy(memoryArchive = deleted)))
        val missingVersion = MemoryTransferOriginLink(
            MemoryTransferObjectKind.RECORD, "record", 99L,
            MemoryTransferOrigin(
                "prior-import",
                2,
                1L,
                2L,
                "original",
                "space",
                "a",
                "unknown",
                99L
            )
        )
        assertRejected(
            codec.encode(
                bundle().copy(
                    memoryArchive = graph.copy(
                        origins = listOf(
                            missingVersion
                        )
                    )
                )
            )
        )
    }

    @Test
    fun eventCachesCannotRestoreDeletedMemberOrCoveredSources() {
        val base = requireNotNull(bundle().memoryArchive)
        val event = MemoryEventBox(
            "event", "space", "a", "unknown", "事件标题", "缓存摘要", 1L,
            MemoryEventSummaryStatus.READY, MemoryEventStatus.ACTIVE, "缓存进展", 1L, 1L, 1L
        )
        val member =
            MemoryTransferEventMember("event", "record", 1L, MemoryEventMemberStatus.ARCHIVED, 1L)
        val graph = base.copy(events = listOf(event), eventMembers = listOf(member))
        val clearedRecord = graph.copy(
            records = graph.records.map {
                it.copy(
                    content = "",
                    status = MemoryRecordStatus.STALE
                )
            },
            recordRevisions = graph.recordRevisions.map { it.copy(content = "") },
            sources = graph.sources.map {
                it.copy(
                    content = "",
                    status = MemorySourceStatus.DELETED
                )
            },
        )
        val candidates = listOf(
            clearedRecord,
            clearedRecord.copy(
                eventMembers = listOf(
                    member.copy(
                        coveredRevision = null,
                        status = MemoryEventMemberStatus.LIVE
                    )
                )
            ),
            clearedRecord.copy(
                records = clearedRecord.records.map { it.copy(status = MemoryRecordStatus.DELETED) },
                sources = base.sources
            ),
        )
        for (candidate in candidates) {
            // 三种缓存字段均不能独自保存被删材料；不能只拦 READY 摘要。
            for (cached in listOf(
                event.copy(summary = "", currentState = ""),
                event.copy(title = "", currentState = ""),
                event.copy(title = "", summary = "", summaryStatus = MemoryEventSummaryStatus.STALE)
            )) {
                assertRejected(
                    codec.encode(
                        bundle().copy(
                            memoryArchive = candidate.copy(
                                events = listOf(
                                    cached
                                )
                            )
                        )
                    )
                )
            }
            val tombstone = candidate.copy(
                events = listOf(
                    event.copy(
                        title = "", summary = "", currentState = "",
                        summaryStatus = MemoryEventSummaryStatus.STALE
                    )
                )
            )
            assertEquals(
                "",
                codec.decode(codec.encode(bundle().copy(memoryArchive = tombstone))).memoryArchive!!.events.single().summary
            )
        }
        // 记录当前人工版本可用，但摘要覆盖的旧版本已删源，也不得保留旧事件缓存。
        val currentSource =
            base.sources.single().copy(id = "manual-source", kind = MemorySourceKind.MANUAL)
        val revised = clearedRecord.copy(
            records = base.records.map { it.copy(revision = 2L) },
            recordRevisions = clearedRecord.recordRevisions + base.recordRevisions.map {
                it.copy(
                    revision = 2L
                )
            },
            sources = clearedRecord.sources + currentSource,
            recordSources = clearedRecord.recordSources + MemoryTransferRecordSource(
                "record",
                2L,
                currentSource.fullRef()
            ),
        )
        assertRejected(codec.encode(bundle().copy(memoryArchive = revised)))
        for (status in listOf(MemorySourceStatus.UNKNOWN, MemorySourceStatus.CHANGED)) {
            val history =
                graph.copy(sources = graph.sources.map { it.copy(status = status, content = "") })
            val decoded =
                codec.decode(codec.encode(bundle().copy(memoryArchive = history))).memoryArchive!!
            assertEquals("缓存摘要", decoded.events.single().summary)
            assertEquals(status, decoded.sources.single().status)
        }
    }

    private fun bundle(): ContextDataBundle {
        val source = MemorySourceEvidence(
            "source", "space", "a", MemorySourceKind.MESSAGE, "original-conversation",
            "original-message", 1L, "user", "USER", "unknown", listOf("a"), "a🌠b", 4,
            MemorySourceStatus.AVAILABLE, 1L, 1L, MemoryArchiveIdentity.fingerprint("a🌠b")
        )
        val record = MemoryRecord(
            "record",
            "space",
            "a",
            MemoryRecordKind.EPISODE,
            MemoryCategory.DAILY,
            "精确正文\n保持空格  ",
            createdAt = 1L,
            updatedAt = 1L
        )
        return ContextDataBundle(
            version = 2, exportedAt = 1L, memoryArchive = MemoryArchiveTransferData(
                spaces = listOf(
                    MemorySpace(
                        "space",
                        "a",
                        MemoryPurpose.CONTINUOUS,
                        createdAt = 1L,
                        updatedAt = 1L
                    )
                ),
                records = listOf(record), sources = listOf(source),
                recordRevisions = listOf(
                    MemoryRecordRevision(
                        "record",
                        1L,
                        record.content,
                        MemoryChangeOrigin.EXTRACTION,
                        1L
                    )
                ),
                recordSources = listOf(MemoryTransferRecordSource("record", 1L, source.fullRef())),
            )
        )
    }

    private fun JsonObject.archive() = getAsJsonObject("memoryArchive")
    private fun assertRejected(raw: String) {
        try {
            codec.decode(raw); fail("非法资料包应被拒绝")
        } catch (_: IllegalArgumentException) {
        }
    }
}
