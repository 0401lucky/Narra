package com.example.myapplication.data.repository.context

import android.app.Application
import com.example.myapplication.context.MemoryRecallEngine
import com.example.myapplication.model.*
import com.example.myapplication.testutil.MemoryExtractionFixture
import com.example.myapplication.testutil.recallRequest
import com.example.myapplication.testutil.remember
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryRecallRepositoryTest {
    @Test
    fun requestMetadataChange_doesNotReviseSourceOrQueueExtraction() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val message = f.add(context, "source", "芮珩说约定照旧。")
            val source = f.archive.listPendingIngress(context).single().source
            f.store.upsertMessages(listOf(message.copy(memoryRecallRequestId = "request-a")))
            f.store.upsertMessages(listOf(message.copy(memoryRecallRequestId = "request-b")))
            val saved = f.store.listMessages(context.conversationId).single()
            assertEquals(message.memoryRevision, saved.memoryRevision)
            assertEquals("request-b", saved.memoryRecallRequestId)
            assertEquals(listOf(source), f.archive.listPendingIngress(context).map { it.source })
        }
    }

    @Test
    fun failedNewAttempt_cannotBorrowAnOlderSentReceipt() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.remember(context, "芮珩带来了地图。")
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    context,
                    "芮珩",
                    emptyList(),
                    6,
                    2000,
                    false
                )
            )
            val first = f.recallRequest(plan)
            assertTrue(f.archive.prepareRecallReceipt(first))
            val firstSequence = requireNotNull(
                f.archive.prepareRecallTransmission(
                    first.requestId,
                    "first-fingerprint",
                    plan.materials.map { it.ref },
                    emptyList()
                )
            )
            assertTrue(f.archive.confirmRecallTransmission(first.requestId, firstSequence))
            val second = f.recallRequest(plan)
            assertTrue(f.archive.prepareRecallReceipt(second))
            f.archive.failRecallRequest(second.requestId, "NOT_STARTED")
            assertEquals(
                MemoryRecallStatus.SENT,
                f.archive.getRecallReceipt(context, first.requestId)!!.status
            )
            val failed = f.archive.getRecallReceipt(context, second.requestId)!!
            assertEquals(MemoryRecallStatus.FAILED, failed.status)
            assertTrue(failed.sent.isEmpty())
            assertNull(f.archive.getRecallReceipt(context, "this-round-has-no-receipt"))
        }
    }

    @Test
    fun newerUnconfirmedTransmission_hasNoOldSentMaterials() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.remember(context, "芮珩说明天到。")
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    context,
                    "芮珩",
                    emptyList(),
                    6,
                    2000,
                    false
                )
            )
            val request = f.recallRequest(plan)
            assertTrue(f.archive.prepareRecallReceipt(request))
            val first = requireNotNull(
                f.archive.prepareRecallTransmission(
                    request.requestId,
                    "one",
                    plan.materials.map { it.ref },
                    emptyList()
                )
            )
            assertTrue(f.archive.confirmRecallTransmission(request.requestId, first))
            assertNotNull(
                f.archive.prepareRecallTransmission(
                    request.requestId,
                    "two",
                    emptyList(),
                    emptyList()
                )
            )
            f.archive.failRecallRequest(request.requestId, "TRANSPORT_UNCONFIRMED")
            val receipt = f.archive.getRecallReceipt(context, request.requestId)!!
            assertTrue(receipt.sent.isEmpty())
            assertEquals("two", receipt.payloadFingerprint)
            assertEquals(MemoryRecallStatus.SENT, receipt.transmissions.first().status)
            assertEquals(MemoryRecallStatus.PREPARED, receipt.transmissions.last().status)
        }
    }

    @Test
    fun editedOrDeletedSourceAndWrongPersona_rejectPreparedMaterial() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val record = f.remember(context, "芮珩在731号室。")
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    context,
                    "731",
                    emptyList(),
                    6,
                    2000,
                    false
                )
            )
            assertTrue(f.archive.validateRecallMaterials(context, plan.materials.map { it.ref }))
            assertFalse(
                f.archive.validateRecallMaterials(
                    context.copy(personaKey = "foreign"),
                    plan.materials.map { it.ref })
            )
            f.archive.editRecord(context, record.id, 1, "芮珩搬家了。")
            assertFalse(f.archive.validateRecallMaterials(context, plan.materials.map { it.ref }))
            f.archive.deleteRecord(context, record.id, 2)
            assertFalse(f.archive.validateRecallRecords(context, emptyMap()))
        }
    }

    @Test
    fun receiptSurvivesDiskReopen_withoutStoringBodyOrProviderSecrets() = runBlocking {
        MemoryExtractionFixture(persistent = true).use { f ->
            val context = f.contexts().single()
            f.remember(context, "芮珩交付了绝密纸条内容哨兵。")
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    context,
                    "芮珩",
                    emptyList(),
                    6,
                    2000,
                    false
                )
            )
            val request = f.recallRequest(plan)
            assertTrue(f.archive.prepareRecallReceipt(request))
            val sequence = requireNotNull(
                f.archive.prepareRecallTransmission(
                    request.requestId,
                    "fingerprint",
                    plan.materials.map { it.ref },
                    emptyList()
                )
            )
            assertTrue(f.archive.confirmRecallTransmission(request.requestId, sequence))
            f.reopen()
            val receipt = requireNotNull(f.archive.getRecallReceipt(context, request.requestId))
            assertEquals(plan.materials.map { it.ref }, receipt.sent)
            val entity = requireNotNull(f.database.memoryArchiveDao().getReceipt(request.requestId))
            val metadata =
                entity.selectedJson + entity.sentJson + entity.omittedJson + entity.transmissionsJson
            assertFalse(metadata.contains("绝密纸条内容哨兵"))
            assertFalse(metadata.contains("synthetic-secret-sentinel"))
            assertFalse(metadata.contains("apiKey"))
        }
    }

    @Test
    fun receiptPublicationOccursAfterCommit_andNeverCreatesPreviewHistory() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.remember(context, "芮珩说明天见。")
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    context,
                    "芮珩",
                    emptyList(),
                    6,
                    2000,
                    false
                )
            )
            val request = f.recallRequest(plan)
            val logs = ContextLogStore()
            logs.push(ContextGovernanceSnapshot(id = "preview", hasMemoryRecallPlan = true))
            logs.push(
                ContextGovernanceSnapshot(
                    id = "actual",
                    hasMemoryRecallPlan = true,
                    memoryRecallRequestId = request.requestId
                )
            )
            val published = mutableListOf<MemoryRecallStatus>()
            val repository =
                RoomMemoryArchiveRepository(f.database, onRecallReceiptChanged = { receipt ->
                    assertFalse(f.database.inTransaction())
                    published += receipt.status
                    logs.attachMemoryReceipt(receipt)
                })
            assertTrue(repository.prepareRecallReceipt(request))
            val sequence = requireNotNull(
                repository.prepareRecallTransmission(
                    request.requestId,
                    "fingerprint",
                    plan.materials.map { it.ref },
                    emptyList()
                )
            )
            assertTrue(repository.confirmRecallTransmission(request.requestId, sequence))
            assertEquals(
                listOf(
                    MemoryRecallStatus.PREPARED,
                    MemoryRecallStatus.PREPARED,
                    MemoryRecallStatus.SENT
                ), published
            )
            assertEquals(
                MemoryRecallStatus.SENT,
                logs.findById("actual")!!.memoryRecallReceipt!!.status
            )
            assertNull(logs.findById("preview")!!.memoryRecallReceipt)
        }
    }
}
