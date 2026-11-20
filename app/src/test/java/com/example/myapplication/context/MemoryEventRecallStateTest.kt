package com.example.myapplication.context

import android.app.Application
import com.example.myapplication.model.*
import com.example.myapplication.testutil.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryEventRecallStateTest {
    @Test
    fun beforeSummaryThreshold_latestMatchExpandsOriginalCauseAndProgress() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, records) = f.createEvent(
                context,
                listOf("上周公园遗失蓝色钥匙", "隔天管理员收到失物", "今日凭条码731领取成功")
            )
            val ai = RecordingMemoryEventAi()
            f.eventCoordinator(ai).processAutomatic(context)
            assertTrue(ai.snapshots.isEmpty())
            val snapshot = f.archive.getRecallSnapshot(context)!!
            assertTrue(snapshot.events.isEmpty())
            assertEquals(3, snapshot.eventMemberships.getValue(event.id).size)
            val plan =
                MemoryRecallEngine(f.archive).recall(context, "731", emptyList(), 6, 2000, false)!!
            assertEquals(records.map { it.id }.toSet(), plan.materials.map { it.ref.id }.toSet())
            assertTrue(plan.materials.all { it.ref.kind == MemoryMaterialKind.RECORD && it.ref.revision == 1L && it.ref.supportingRecords.isEmpty() })
            assertTrue(plan.section.contains("公园遗失蓝色钥匙"))
            assertTrue(plan.section.contains("管理员收到失物"))
        }
    }

    @Test
    fun initialSummaryFailure_doesNotEraseLegalEventContinuity() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, records) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            ai.answer = { memoryEventResponse(it, status = PromptCompletionStatus.TRUNCATED) }
            assertEquals(
                "INVALID_RESPONSE",
                f.eventCoordinator(ai).processAutomatic(context).failureCode
            )
            val plan =
                MemoryRecallEngine(f.archive).recall(context, "706", emptyList(), 6, 2000, false)!!
            assertEquals(records.map { it.id }.toSet(), plan.materials.map { it.ref.id }.toSet())
            assertTrue(plan.materials.all { it.ref.kind == MemoryMaterialKind.RECORD })
            assertEquals(0L, f.archive.getEventDetails(context, event.id)!!.event.summaryRevision)
            assertTrue(f.archive.getRecallSnapshot(context)!!.events.isEmpty())
        }
    }

    @Test
    fun staleSummary_expandsOnlyCurrentRawRevisionsWithCurrentSources() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, records) = f.createEvent(context)
            f.eventCoordinator(RecordingMemoryEventAi()).processAutomatic(context)
            f.archive.editRecord(context, records.first().id, 1, "人工更正：阿澄委托检查航海仪")
            val plan =
                MemoryRecallEngine(f.archive).recall(context, "706", emptyList(), 6, 2000, false)!!
            assertTrue(plan.materials.none { it.ref.kind == MemoryMaterialKind.EVENT })
            val corrected = plan.materials.single { it.ref.id == records.first().id }
            assertEquals(2L, corrected.ref.revision)
            assertTrue(corrected.content.contains("阿澄"))
            val details = f.archive.getRecordDetails(context, records.first().id)!!
            assertEquals(MemorySourceKind.MANUAL, details.sources.single().kind)
            assertEquals(details.sourceRefs.toSet(), corrected.ref.sourceRefs.toSet())
            assertEquals(
                MemoryEventSummaryStatus.STALE,
                f.archive.getEventDetails(context, event.id)!!.event.summaryStatus
            )
            assertTrue(f.archive.validateRecallMaterials(context, plan.materials.map { it.ref }))
        }
    }

    @Test
    fun expandedRelationReceipt_acceptsNewMemberButRejectsUnlinkBeforeSending() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val (event, records) = f.createEvent(
                context,
                listOf("公园遗失蓝色钥匙", "管理员收到失物", "凭条码731领取成功")
            )
            val plan =
                MemoryRecallEngine(f.archive).recall(context, "731", emptyList(), 6, 2000, false)!!
            val expanded = plan.materials.single { it.ref.id == records.first().id }
            assertEquals(event.id, expanded.ref.eventContext!!.eventId)
            assertEquals(
                MemoryRecordVersion(records.last().id, records.last().revision),
                expanded.ref.eventContext!!.anchor
            )
            val request = f.recallRequest(plan)
            assertTrue(f.archive.prepareRecallReceipt(request))
            val next = f.remember(context, "补充：领取后更换了钥匙扣")
            f.archive.linkEventMembers(
                context,
                MemoryRecordVersion(next.id, next.revision),
                MemoryRecordVersion(records.first().id, records.first().revision)
            )
            assertTrue(f.archive.validateRecallMaterials(context, plan.materials.map { it.ref }))
            assertNotNull(
                f.archive.prepareRecallTransmission(
                    request.requestId,
                    "before-unlink",
                    plan.materials.map { it.ref },
                    plan.omitted
                )
            )
            f.archive.unlinkEventMember(context, event.id, records.first().id, event.generation)
            assertFalse(f.archive.validateRecallMaterials(context, plan.materials.map { it.ref }))
            assertNull(
                f.archive.prepareRecallTransmission(
                    request.requestId,
                    "after-unlink",
                    plan.materials.map { it.ref },
                    plan.omitted
                )
            )
            assertEquals(
                records.first().content,
                f.archive.getRecordDetails(context, records.first().id)!!.record.content
            )
        }
    }

    @Test
    fun directlyMatchedExpandedRecord_retainsItsRealTailRange() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val body = "长篇细节".repeat(2000) + "尾部733173专项线索"
            val (_, records) = f.createEvent(context, listOf("首个话题381", body))
            val tail = body.indexOf("733173")
            val search = MemoryLocalSearch { _, _, _ ->
                listOf(
                    MemoryLocalHit(records[0].id, 1, 0, records[0].content.length, 2.0, true),
                    MemoryLocalHit(
                        records[1].id,
                        1,
                        tail - 2,
                        body.length,
                        1.0,
                        true,
                        tail,
                        tail + 6
                    ),
                )
            }
            val plan = MemoryRecallEngine(f.archive, localSearch = search).recall(
                context,
                "381和733173",
                emptyList(),
                2,
                500,
                false
            )!!
            val hit = plan.materials.single { it.ref.id == records[1].id }
            assertTrue(hit.fragment)
            assertTrue(hit.content.contains("733173"))
            assertEquals(body.substring(hit.ref.spanStart, hit.ref.spanEnd), hit.content)
            assertNull(hit.ref.eventContext)
            assertEquals(body, f.archive.getRecordDetails(context, records[1].id)!!.record.content)
        }
    }

    @Test
    fun relatedUnmatchedMembers_doNotDisplaceAnotherCurrentTopic() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val (_, records) = f.createEvent(
                context,
                listOf("本轮话题111", "同事件但未查询的前情", "同事件但未查询的进展")
            )
            val other = f.remember(context, "另一个本轮主题222")
            val search = MemoryLocalSearch { _, _, _ ->
                listOf(
                    MemoryLocalHit(records[0].id, 1, 0, records[0].content.length, 2.0, true),
                    MemoryLocalHit(other.id, 1, 0, other.content.length, 1.0, true),
                )
            }
            val plan = MemoryRecallEngine(f.archive, localSearch = search).recall(
                context,
                "111和222",
                emptyList(),
                2,
                2000,
                false
            )!!
            assertEquals(
                setOf(records.first().id, other.id),
                plan.materials.map { it.ref.id }.toSet()
            )
            assertTrue(plan.materials.all { it.ref.eventContext == null })
            assertTrue(plan.omitted.any { it.ref.id == records[1].id && it.reason == MemoryRecallOmissionReason.ITEM_LIMIT })
        }
    }

    @Test
    fun shortVectorTail_remainsACompleteValidHitWithRealSourceProof() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val tail = "芮珩😀733173"
            val body = "🧭旧背景".repeat(1000) + tail
            val record = f.remember(context, body)
            val start = body.length - tail.length
            val vector = MemoryVectorSearch { _, _, _ ->
                listOf(MemoryVectorHit(record.id, record.revision, 7, start, body.length, 0.9))
            }
            val local = MemoryLocalSearch { _, _, _ -> emptyList() }

            val plan = MemoryRecallEngine(f.archive, localSearch = local, vectorSearch = vector)
                .recall(context, "遗失导航工具的编号", emptyList(), 1, 320, true)!!

            val sent = plan.materials.single()
            assertTrue(sent.fragment)
            assertTrue(sent.ref.spanStart < start)
            assertEquals(body.length, sent.ref.spanEnd)
            assertTrue(sent.content.endsWith(tail))
            assertEquals(body.substring(sent.ref.spanStart, sent.ref.spanEnd), sent.content)
            assertEquals(
                MemoryRecallText.safeBoundary(body, sent.ref.spanStart),
                sent.ref.spanStart
            )
            assertEquals(MemoryRecallText.safeBoundary(body, sent.ref.spanEnd), sent.ref.spanEnd)
            assertNull(sent.ref.eventContext)
            assertEquals(
                f.archive.getRecordDetails(context, record.id)!!.sourceRefs.toSet(),
                sent.ref.sourceRefs.toSet()
            )
            assertTrue(f.archive.validateRecallMaterials(context, listOf(sent.ref)))
            assertEquals(body, f.archive.getRecordDetails(context, record.id)!!.record.content)
            assertTrue(plan.estimatedTokens <= 320)
        }
    }
}
