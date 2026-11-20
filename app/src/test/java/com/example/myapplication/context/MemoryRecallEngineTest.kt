package com.example.myapplication.context

import android.app.Application
import com.example.myapplication.data.local.memory.MemoryCurrentStateEntity
import com.example.myapplication.data.local.memory.MemoryDerivedLinkEntity
import com.example.myapplication.data.local.memory.MemoryEventBoxEntity
import com.example.myapplication.data.local.memory.MemoryEventMemberEntity
import com.example.myapplication.data.local.memory.MemoryResidentEntryEntity
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.*
import com.example.myapplication.testutil.MemoryExtractionFixture
import com.example.myapplication.testutil.remember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryRecallEngineTest {
    @Test
    fun shortNameAndTwoUserTopics_beatGreetingAndLongCharacterReply() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val name = f.remember(context, "芮珩在海棠书店负责修复旧地图。")
            val number = f.remember(context, "寄存柜密码是 731，凭青色票根取件。")
            f.remember(context, "今晚谈咖啡和面包。".repeat(80), importance = 100)
            val messages = listOf(
                ChatMessage("a", role = MessageRole.ASSISTANT, content = "咖啡面包".repeat(2000)),
                ChatMessage("u1", role = MessageRole.USER, content = "芮珩呢？"),
                ChatMessage("u2", role = MessageRole.USER, content = "731 是什么？"),
                ChatMessage("u3", role = MessageRole.USER, content = "谢谢！"),
            )
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    context,
                    "谢谢！",
                    messages,
                    2,
                    2000,
                    false
                )
            )
            assertEquals(setOf(name.id, number.id), plan.materials.map { it.ref.id }.toSet())
            assertTrue(plan.materials.all { it.ref.sourceRefs.isNotEmpty() })
            assertEquals(
                2,
                MemoryRecallQueryBuilder.build("谢谢！", messages).count { it.currentUser })
        }
    }

    @Test
    fun archivedLongTail_dateAndRareNameRemainInBudgetedSpan() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val body =
                "走过长廊，反复核对旧目录。\n".repeat(600) + "芮珩约定2026年11月19日在731号室归还红色罗盘。"
            val record = f.remember(context, body)
            f.archive.archiveRecallOverflow(context, 0)
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    context,
                    "芮珩 731 2026-11-19",
                    emptyList(),
                    6,
                    600,
                    false
                )
            )
            val sent = plan.materials.single()
            assertEquals(record.id, sent.ref.id)
            assertTrue(sent.fragment)
            assertTrue(sent.content.contains("731"))
            assertEquals(body.substring(sent.ref.spanStart, sent.ref.spanEnd), sent.content)
            assertTrue(plan.estimatedTokens <= 600)
            assertTrue(plan.omitted.any { it.reason == MemoryRecallOmissionReason.PARTIAL_RECORD })
            val stored = requireNotNull(f.archive.getRecordDetails(context, record.id))
            assertEquals(body, stored.record.content)
            assertEquals(MemoryRecordStatus.ARCHIVED, stored.record.status)
            assertEquals(body, stored.revisions.single().content)
        }
    }

    @Test
    fun noSearchHit_keepsResidentPinAndCurrentState_butNotExpiredState() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val record = f.remember(context, "用户声明偏好淡茶，现在正在等待旧书修复。")
            addDerived(f, context, record.id)
            val dao = f.database.memoryArchiveDao()
            dao.upsertRecord(requireNotNull(dao.getRecord(record.id)).copy(pinned = true))
            dao.upsertCurrentState(
                MemoryCurrentStateEntity(
                    "expired", context.spaceId, context.characterId, "expired", context.personaKey,
                    "已结束的约定", 1, "ACTIVE", f.now - 1, null, f.now, f.now
                )
            )
            dao.upsertDerivedLink(
                MemoryDerivedLinkEntity(
                    "STATE",
                    "expired",
                    record.id,
                    record.revision
                )
            )
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    context,
                    "天气如何",
                    emptyList(),
                    0,
                    2000,
                    false
                )
            )
            assertEquals(
                setOf("resident", "state", record.id),
                plan.materials.map { it.ref.id }.toSet()
            )
            assertEquals(
                setOf(
                    MemoryRecallLayer.RESIDENT,
                    MemoryRecallLayer.PINNED,
                    MemoryRecallLayer.STATE
                ), plan.materials.map { it.layer }.toSet()
            )
        }
    }

    @Test
    fun longPinnedRecord_keepsItsMatchingTailWithoutDuplicatingOrChangingTheArchive() =
        runBlocking {
            MemoryExtractionFixture().use { f ->
                val context = f.contexts().single()
                val body = "反复核对旧目录。\n".repeat(700) + "芮珩约定在731号室归还红色罗盘。"
                val record = f.remember(context, body)
                val engine = MemoryRecallEngine(f.archive)
                val unpinned =
                    requireNotNull(engine.recall(context, "芮珩 731", emptyList(), 1, 400, false))
                assertTrue(unpinned.materials.single().content.contains("红色罗盘"))
                val dao = f.database.memoryArchiveDao()
                dao.upsertRecord(requireNotNull(dao.getRecord(record.id)).copy(pinned = true))

                val pinned =
                    requireNotNull(engine.recall(context, "芮珩 731", emptyList(), 0, 400, false))
                val material = pinned.materials.single()
                assertEquals(record.id, material.ref.id)
                assertEquals(MemoryRecallLayer.PINNED, material.layer)
                assertTrue(material.fragment)
                assertTrue(material.content.contains("芮珩"))
                assertTrue(material.content.contains("731"))
                assertEquals(
                    body.substring(material.ref.spanStart, material.ref.spanEnd),
                    material.content
                )
                assertTrue(pinned.estimatedTokens <= 400)
                assertTrue(pinned.omitted.any { it.ref.id == record.id && it.reason == MemoryRecallOmissionReason.PARTIAL_RECORD })
                assertEquals(body, f.archive.getRecordDetails(context, record.id)!!.record.content)
                assertEquals(
                    body,
                    f.archive.getRecordDetails(context, record.id)!!.revisions.single().content
                )
            }
        }

    @Test
    fun unrelatedOversizedPin_isOmittedInsteadOfInventingAMatchingFragment() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val record = f.remember(context, "很长的旧目录。".repeat(700))
            val dao = f.database.memoryArchiveDao()
            dao.upsertRecord(requireNotNull(dao.getRecord(record.id)).copy(pinned = true))
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    context,
                    "芮珩731",
                    emptyList(),
                    1,
                    400,
                    false
                )
            )
            assertTrue(plan.materials.isEmpty())
            assertEquals(MemoryRecallOmissionReason.TOKEN_BUDGET, plan.omitted.single().reason)
        }
    }

    @Test
    fun personaAndIndependentStory_areFilteredBeforeRanking() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val a = f.contexts(persona = "医生身份").single()
            val own = f.remember(a, "芮珩的约定是星期二。")
            val otherPersona = f.contexts(persona = "船长身份").single()
            f.remember(otherPersona, "芮珩的约定是星期五。")
            val b = f.contexts(characters = listOf("b")).single()
            f.remember(b, "芮珩的约定是星期六。")
            val story = f.contexts(purpose = MemoryPurpose.INDEPENDENT).single()
            f.remember(story, "芮珩是另一条剧情的敌人。")
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    a,
                    "芮珩",
                    emptyList(),
                    6,
                    2000,
                    false
                )
            )
            assertEquals(listOf(own.id), plan.materials.map { it.ref.id })
        }
    }

    @Test
    fun indexRevisionAndSourceEdits_removeOldFacts() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val record = f.remember(context, "芮珩住在 731。", id = "source")
            val engine = MemoryRecallEngine(f.archive)
            assertTrue(
                requireNotNull(
                    engine.recall(
                        context,
                        "731",
                        emptyList(),
                        6,
                        2000,
                        false
                    )
                ).materials.isNotEmpty()
            )
            f.archive.editRecord(context, record.id, 1, "芮珩搬到了 926。")
            assertTrue(
                requireNotNull(
                    engine.recall(
                        context,
                        "731",
                        emptyList(),
                        6,
                        2000,
                        false
                    )
                ).materials.isEmpty()
            )
            val latest = requireNotNull(
                engine.recall(
                    context,
                    "926",
                    emptyList(),
                    6,
                    2000,
                    false
                )
            ).materials.single()
            assertEquals(2L, latest.ref.revision)
            assertTrue(latest.content.contains("926"))
        }
    }

    @Test
    fun sourceChangedOutsideCache_isRejectedBeforeUse() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.remember(context, "芮珩约定明天送书。", id = "source")
            val engine = MemoryRecallEngine(f.archive)
            assertTrue(
                requireNotNull(
                    engine.recall(
                        context,
                        "芮珩",
                        emptyList(),
                        6,
                        2000,
                        false
                    )
                ).materials.isNotEmpty()
            )
            val source = f.store.listMessages(context.conversationId).single()
            f.store.upsertMessages(listOf(source.copy(content = "约定已取消。")))
            assertTrue(
                requireNotNull(
                    engine.recall(
                        context,
                        "芮珩",
                        emptyList(),
                        6,
                        2000,
                        false
                    )
                ).materials.isEmpty()
            )
        }
    }

    @Test
    fun sourceChangesDuringVectorQuery_cannotInjectTheOldSnapshot() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val record = f.remember(context, "芮珩住在731号室。")
            val vector = MemoryVectorSearch { _, _, _ ->
                f.archive.editRecord(context, record.id, 1, "芮珩搬家了。")
                emptyList()
            }
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive, vectorSearch = vector).recall(
                    context,
                    "731",
                    emptyList(),
                    6,
                    2000,
                    true
                )
            )
            assertTrue(plan.materials.isEmpty())
            assertTrue(plan.omitted.any { it.reason == MemoryRecallOmissionReason.SOURCE_CHANGED })
        }
    }

    @Test
    fun localIndexFailure_rebuildsScopedFallback() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val record = f.remember(context, "芮珩带来了红色罗盘。")
            val engine = MemoryRecallEngine(
                f.archive,
                localSearch = MemoryLocalSearch { _, _, _ -> error("synthetic index failure") })
            assertEquals(
                record.id,
                requireNotNull(
                    engine.recall(
                        context,
                        "芮珩",
                        emptyList(),
                        6,
                        2000,
                        false
                    )
                ).materials.single().ref.id
            )
        }
    }

    @Test
    fun vectorTimeoutAndFailure_preserveLocalRecall() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val record = f.remember(context, "芮珩负责731号包裹。")
            val timeout = MemoryRecallEngine(
                f.archive,
                vectorSearch = MemoryVectorSearch { _, _, _ -> delay(1000); emptyList() },
                vectorTimeoutMillis = 20
            )
            val timed = requireNotNull(timeout.recall(context, "731", emptyList(), 6, 2000, true))
            assertEquals(MemoryVectorAvailability.TIMED_OUT, timed.vectorAvailability)
            assertEquals(record.id, timed.materials.single().ref.id)
            val failed = requireNotNull(
                MemoryRecallEngine(
                    f.archive,
                    vectorSearch = MemoryVectorSearch { _, _, _ -> error("synthetic") })
                    .recall(context, "731", emptyList(), 6, 2000, true)
            )
            assertEquals(MemoryVectorAvailability.FAILED, failed.vectorAvailability)
            assertEquals(record.id, failed.materials.single().ref.id)
        }
    }

    @Test
    fun parentCancellation_isNotConvertedToLocalFallback() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.remember(context, "芮珩有约。")
            try {
                MemoryRecallEngine(
                    f.archive,
                    vectorSearch = MemoryVectorSearch { _, _, _ -> throw CancellationException("synthetic") })
                    .recall(context, "芮珩", emptyList(), 6, 2000, true)
                fail("必须继续传播取消")
            } catch (_: CancellationException) {
            }
        }
    }

    @Test
    fun recalledArchivedChild_includesOnlyCurrentEventCoverage() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val record = f.remember(context, "芮珩修复了损坏的罗盘。")
            val dao = f.database.memoryArchiveDao()
            dao.upsertRecord(requireNotNull(dao.getRecord(record.id)).copy(status = "ARCHIVED"))
            dao.upsertEventBox(
                MemoryEventBoxEntity(
                    "event",
                    context.spaceId,
                    context.characterId,
                    "罗盘修复",
                    "从委托到归还，罗盘修复完成。",
                    2,
                    "READY",
                    "ACTIVE",
                    "",
                    1,
                    f.now,
                    f.now,
                    personaKey = context.personaKey,
                )
            )
            dao.upsertEventMember(
                MemoryEventMemberEntity(
                    "event",
                    record.id,
                    record.revision,
                    "ARCHIVED",
                    f.now
                )
            )
            val engine = MemoryRecallEngine(f.archive)
            val plan = requireNotNull(engine.recall(context, "芮珩", emptyList(), 6, 2000, false))
            assertEquals(setOf(record.id, "event"), plan.materials.map { it.ref.id }.toSet())
            assertEquals(
                listOf(MemoryRecordVersion(record.id, 1)),
                plan.materials.single { it.ref.id == "event" }.ref.supportingRecords
            )
            f.archive.editRecord(context, record.id, 1, "芮珩还没有修复罗盘。")
            assertFalse(
                requireNotNull(
                    engine.recall(
                        context,
                        "芮珩",
                        emptyList(),
                        6,
                        2000,
                        false
                    )
                ).materials.any { it.ref.id == "event" })
        }
    }

    @Test
    fun activeCapacity_archivesOnlyThisCharactersEpisodes() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val a = f.contexts().single()
            val b = f.contexts(characters = listOf("b")).single()
            val records = (1..3).map { f.remember(a, "芮珩第${it}次交付。", importance = it * 10) }
            val other = f.remember(b, "另一角色的经历。")
            f.archive.archiveRecallOverflow(a, 1)
            assertEquals(
                2,
                f.archive.listRecords(a).count { it.status == MemoryRecordStatus.ARCHIVED })
            assertEquals(
                records.map { it.content }.toSet(),
                f.archive.listRecords(a).map { it.content }.toSet()
            )
            assertEquals(
                MemoryRecordStatus.ACTIVE,
                f.archive.getRecordDetails(b, other.id)!!.record.status
            )
        }
    }

    @Test
    fun emptyAndDisabledVector_neverCallsProvider() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val record = f.remember(context, "芮珩说晚安。")
            val engine = MemoryRecallEngine(
                f.archive,
                vectorSearch = MemoryVectorSearch { _, _, _ -> error("不可调用") })
            assertEquals(
                record.id,
                requireNotNull(
                    engine.recall(
                        context,
                        "芮珩",
                        emptyList(),
                        6,
                        2000,
                        false
                    )
                ).materials.single().ref.id
            )
            assertTrue(
                requireNotNull(
                    engine.recall(
                        context,
                        "晚安",
                        emptyList(),
                        6,
                        2000,
                        true
                    )
                ).materials.isEmpty()
            )
        }
    }

    @Test
    fun malformedOrForeignVectorHits_doNotEnterTheBudget() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val record = f.remember(context, "芮珩说过约定。")
            val vectors = MemoryVectorSearch { _, _, _ ->
                listOf(
                    MemoryVectorHit("foreign", 1, 0, 0, 5, 1.0),
                    MemoryVectorHit(record.id, 99, 0, 0, 5, 1.0),
                    MemoryVectorHit(record.id, 1, 0, 0, 5, Double.NaN),
                )
            }
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive, vectorSearch = vectors).recall(
                    context,
                    "无关天气",
                    emptyList(),
                    6,
                    2000,
                    true
                )
            )
            assertTrue(plan.materials.isEmpty())
        }
    }

    @Test
    fun pureGreetingAfterLongReply_doesNotRecallTheBackgroundTopic() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.remember(context, "咖啡店讨论了很多咖啡和面包。")
            val history = listOf(
                ChatMessage(
                    "reply",
                    role = MessageRole.ASSISTANT,
                    content = "咖啡面包".repeat(3000)
                )
            )
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    context,
                    "谢谢！",
                    history,
                    6,
                    2000,
                    false
                )
            )
            assertTrue(plan.materials.isEmpty())
            assertTrue(MemoryRecallQueryBuilder.build("谢谢！", history).isEmpty())
        }
    }

    @Test
    fun currentVectorIntent_keepsItsSpanInsteadOfTheSameRecordsBackgroundSpan() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val tail = "芮珩带来了遗失的红色罗盘，约定在731号室交还。".repeat(3)
            val body = "咖啡和面包。".repeat(500) + tail
            val record = f.remember(context, body)
            val vector = MemoryVectorSearch { _, _, _ ->
                listOf(
                    MemoryVectorHit(
                        record.id,
                        1,
                        3,
                        body.length - tail.length,
                        body.length,
                        0.99
                    )
                )
            }
            val background =
                listOf(ChatMessage("reply", role = MessageRole.ASSISTANT, content = "咖啡和面包"))
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive, vectorSearch = vector).recall(
                    context,
                    "找到的导航工具",
                    background,
                    1,
                    550,
                    true
                )
            )
            assertTrue(plan.materials.single().content.contains("红色罗盘"))
            assertTrue(plan.materials.single().ref.spanStart > 2000)
        }
    }

    @Test
    fun vectorCurrentIntent_hasPriorityOverRankedBackground() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val background = f.remember(context, "咖啡店仍在营业。")
            val current = f.remember(context, "罗盘已归还。")
            val local = MemoryLocalSearch { _, _, _ ->
                listOf(
                    MemoryLocalHit(
                        background.id,
                        1,
                        0,
                        background.content.length,
                        1.0,
                        false
                    )
                )
            }
            val vector = MemoryVectorSearch { _, _, _ ->
                listOf(
                    MemoryVectorHit(
                        current.id,
                        1,
                        0,
                        0,
                        current.content.length,
                        0.99
                    )
                )
            }
            val plan = requireNotNull(
                MemoryRecallEngine(
                    f.archive,
                    localSearch = local,
                    vectorSearch = vector
                ).recall(context, "导航工具", emptyList(), 1, 2000, true)
            )
            assertEquals(listOf(current.id), plan.materials.map { it.ref.id })
        }
    }

    private suspend fun addDerived(
        f: MemoryExtractionFixture,
        context: MemoryRuntimeContext,
        recordId: String
    ) {
        val dao = f.database.memoryArchiveDao()
        dao.upsertResidentEntry(
            MemoryResidentEntryEntity(
                "resident", context.spaceId, context.characterId, "USER", context.personaKey,
                "对方明确偏好淡茶。", 1, "READY", false, null, false, f.now, f.now
            )
        )
        dao.upsertCurrentState(
            MemoryCurrentStateEntity(
                "state", context.spaceId, context.characterId, "task", context.personaKey,
                "等待旧书修复。", 1, "ACTIVE", null, null, f.now, f.now
            )
        )
        dao.upsertDerivedLink(MemoryDerivedLinkEntity("RESIDENT", "resident", recordId, 1))
        dao.upsertDerivedLink(MemoryDerivedLinkEntity("STATE", "state", recordId, 1))
    }
}
